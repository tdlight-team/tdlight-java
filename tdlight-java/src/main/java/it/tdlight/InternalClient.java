package it.tdlight;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Object;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

final class InternalClient implements ClientEventsHandler, TelegramClient {

	private static final Marker TG_MARKER = MarkerFactory.getMarker("TG");
	private static final Logger logger = LoggerFactory.getLogger(TelegramClient.class);

	private final ConcurrentHashMap<Long, Handler<?>> handlers = new ConcurrentHashMap<>();

	private final Thread shutdownHook = new Thread(this::onJVMShutdown);

	private volatile Integer clientId = null;
	private final InternalClientsState clientManagerState;
	private Handler<TdApi.Update> updateHandler;
	private MultiHandler updatesHandler;
	private ExceptionHandler defaultExceptionHandler;

	private final AtomicBoolean isClosed = new AtomicBoolean();

	public InternalClient(InternalClientsState clientManagerState) {
		this.clientManagerState = clientManagerState;
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	@Override
	public int getClientId() {
		return Objects.requireNonNull(clientId, "Can't obtain the client id before initialization");
	}

	@Override
	public void handleEvents(boolean isClosed, long[] eventIds, TdApi.Object[] events, int arrayOffset, int arrayLength) {
		if (updatesHandler != null) {
			List<Object> updatesList = new ArrayList<>(arrayLength);

			for (int i = (arrayOffset + arrayLength) - 1; i >= arrayOffset; i--) {
				if (eventIds[i] != 0) {
					long eventId = eventIds[i];
					TdApi.Object event = events[i];

					Handler<?> handler = handlers.remove(eventId);
					handleResponse(eventId, event, handler);
				} else {
					updatesList.add(events[i]);
				}
			}

			try {
				updatesHandler.getUpdatesHandler().onUpdates(updatesList);
			} catch (Throwable cause) {
				handleException(updatesHandler.getExceptionHandler(), cause);
			}
		} else {
			for (int i = arrayOffset; i < (arrayOffset + arrayLength); i++) {
				handleEvent(eventIds[i], events[i]);
			}
		}

		if (isClosed) {
			if (this.isClosed.compareAndSet(false, true)) {
				handleClose();
			}
		}
	}

	private void handleClose() {
		logger.trace(TG_MARKER, "Received close");
		try {
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		} catch (IllegalStateException ignored) {
			logger.trace(TG_MARKER, "Can't remove shutdown hook because the JVM is already shutting down");
		}
		handlers.forEach((eventId, handler) ->
				handleResponse(eventId, new TdApi.Error(500, "Instance closed"), handler));
		handlers.clear();
		logger.info(TG_MARKER, "Client closed {}", clientId);
	}

	/**
	 * Handles only a response (not an update!)
	 */
	private void handleResponse(long eventId, TdApi.Object event, Handler<?> handler) {
		if (handler != null) {
			try {
				handler.getResultHandler().onResult(event);
			} catch (Throwable cause) {
				handleException(handler.getExceptionHandler(), cause);
			}
		} else {
			logger.error(TG_MARKER, "Unknown event id \"{}\", the event has been dropped! {}", eventId, event);
		}
	}

	/**
	 * Handles a response or an update
	 */
	private void handleEvent(long eventId, TdApi.Object event) {
		logger.trace(TG_MARKER, "Received response {}: {}", eventId, event);
		if (updatesHandler != null || updateHandler == null) {
			throw new IllegalStateException();
		}
		Handler<?> handler = eventId == 0 ? updateHandler : handlers.remove(eventId);
		handleResponse(eventId, event, handler);
	}

	private void handleException(ExceptionHandler exceptionHandler, Throwable cause) {
		if (exceptionHandler == null) {
			exceptionHandler = defaultExceptionHandler;
		}
		if (exceptionHandler != null) {
			try {
				exceptionHandler.onException(cause);
			} catch (Throwable ignored) {
			}
		}
	}

	@Override
	public void initialize(UpdatesHandler updatesHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		this.updateHandler = null;
		this.updatesHandler = new MultiHandler(updatesHandler, updateExceptionHandler);
		this.defaultExceptionHandler = defaultExceptionHandler;
		createAndRegisterClient();
	}

	@Override
	public void initialize(ResultHandler<TdApi.Update> updateHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		this.updateHandler = new Handler<>(updateHandler, updateExceptionHandler);
		this.updatesHandler = null;
		this.defaultExceptionHandler = defaultExceptionHandler;
		createAndRegisterClient();
	}

	private void createAndRegisterClient() {
		synchronized (this) {
			if (clientId != null) {
				throw new UnsupportedOperationException("Can't initialize the same client twice!");
			}
			clientId = NativeClientAccess.create();
		}
		clientManagerState.registerClient(clientId, this);
		logger.info(TG_MARKER, "Registered new client {}", clientId);

		// Send a dummy request to start TDLib
		this.send(new TdApi.GetOption("version"), (result) -> {}, ex -> {});
	}

	@Override
	public <R extends TdApi.Object> void send(Function<R> query,
			ResultHandler<R> resultHandler,
			ExceptionHandler exceptionHandler) {
		logger.trace(TG_MARKER, "Trying to send {}", query);
		if (isClosedAndMaybeThrow(query)) {
			resultHandler.onResult(new TdApi.Ok());
		}
		if (clientId == null) {
			ExceptionHandler handler = exceptionHandler == null ? defaultExceptionHandler : exceptionHandler;
			handler.onException(new IllegalStateException(
					"Can't send a request to TDLib before calling \"initialize\" function!"));
			return;
		}
		long queryId = clientManagerState.getNextQueryId();
		if (resultHandler != null) {
			handlers.put(queryId, new Handler<>(resultHandler, exceptionHandler));
		}
		NativeClientAccess.send(clientId, queryId, query);
	}

	@Override
	public <R extends TdApi.Object> TdApi.Object execute(Function<R> query) {
		logger.trace(TG_MARKER, "Trying to execute {}", query);
		if (isClosedAndMaybeThrow(query)) {
			return new TdApi.Ok();
		}
		return NativeClientAccess.execute(query);
	}

	private void onJVMShutdown() {
		if ("true".equalsIgnoreCase(System.getProperty("it.tdlight.enableShutdownHooks", "true"))) {
			try {
				logger.info(TG_MARKER, "Client {} is shutting down because the JVM is shutting down", clientId);
				this.send(new TdApi.Close(), result -> {}, ex -> {});
			} catch (Throwable ex) {
				logger.debug("Failed to send shutdown request to session {}", clientId);
			}
		}
	}

	/**
	 * @param function function used to check if the check will be enforced or not. Can be null
	 * @return true if closed
	 */
	private boolean isClosedAndMaybeThrow(Function<?> function) {
		boolean closed = isClosed.get();
		if (closed) {
			if (function != null && function.getConstructor() == TdApi.Close.CONSTRUCTOR) {
				return true;
			} else {
				throw new IllegalStateException("The client is closed!");
			}
		}
		return false;
	}
}
