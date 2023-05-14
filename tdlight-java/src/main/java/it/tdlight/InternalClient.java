package it.tdlight;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Object;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.StampedLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

final class InternalClient implements ClientEventsHandler, TelegramClient {

	static final Marker TG_MARKER = MarkerFactory.getMarker("TG");
	static final Logger logger = LoggerFactory.getLogger(TelegramClient.class);

	private ClientRegistrationEventHandler clientRegistrationEventHandler;
	private final Map<Long, Handler<?>> handlers = new ConcurrentHashMap<>();

	private volatile Integer clientId = null;
	private final InternalClientsState clientManagerState;
	private Handler<TdApi.Update> updateHandler;
	private MultiHandler updatesHandler;
	private ExceptionHandler defaultExceptionHandler;

	private final java.lang.Object closeLock = new java.lang.Object();
	private volatile boolean closed = false;

	public InternalClient(InternalClientsState clientManagerState,
			ClientRegistrationEventHandler clientRegistrationEventHandler) {
		this.clientManagerState = clientManagerState;
		this.clientRegistrationEventHandler = clientRegistrationEventHandler;
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

		if (isClosed && !closed) {
				synchronized (closeLock) {
					if (!closed) {
						closed = true;
						handleClose();
					}
				}
		}
	}

	private void handleClose() {
		logger.debug(TG_MARKER, "Received close");
		handlers.forEach((eventId, handler) ->
				handleResponse(eventId, new TdApi.Error(500, "Instance closed"), handler));
		handlers.clear();
		logger.debug(TG_MARKER, "Client closed {}", clientId);
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
			logger.trace(TG_MARKER, "Client {}, request event id is not registered \"{}\", the following response has been dropped. {}", clientId, eventId, event);
		}
	}

	/**
	 * Handles a response or an update
	 */
	private void handleEvent(long eventId, TdApi.Object event) {
		logger.trace(TG_MARKER, "Client {}, response received for request {}: {}", clientId, eventId, event);
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
		InternalClientsState clientManagerState = this.clientManagerState;
		final StampedLock eventsHandlingLock = clientManagerState.getEventsHandlingLock();
		var stamp = eventsHandlingLock.writeLock();
		try {
			if (clientId != null) {
				throw new UnsupportedOperationException("Can't initialize the same client twice!");
			}
			this.clientId = NativeClientAccess.create();
			if (clientRegistrationEventHandler != null) {
				clientRegistrationEventHandler.onClientRegistered(clientId, clientManagerState::getNextQueryId);
				// Remove the event handler
				clientRegistrationEventHandler = null;
			}
			logger.debug(TG_MARKER, "Registering new client {}", clientId);
			clientManagerState.registerClient(clientId, this);
			logger.info(TG_MARKER, "Registered new client {}", clientId);
		} finally {
			eventsHandlingLock.unlockWrite(stamp);
		}

		// Send a dummy request to start TDLib
		logger.debug(TG_MARKER, "Sending dummy startup request as client {}", clientId);
		TdApi.Function<?> dummyRequest = new TdApi.GetOption("version");
		this.send(dummyRequest, null, null);
		// test Client.execute
		this.execute(new TdApi.GetTextEntities("@telegram /test_command https://telegram.org telegram.me @gif @test"));
	}

	@Override
	public <R extends TdApi.Object> void send(Function<R> query,
			ResultHandler<R> resultHandler,
			ExceptionHandler exceptionHandler) {
		logger.trace(TG_MARKER, "Trying to send async request {}", query);

		// Handle special requests
		TdApi.Object specialResult = tryHandleSpecial(query);
		if (specialResult != null) {
			logger.trace(TG_MARKER, "Handling special result for async request {}: {}", query, specialResult);
			if (resultHandler != null) {
				resultHandler.onResult(specialResult);
			}
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
		logger.trace(TG_MARKER, "Trying to execute sync request {}", query);

		// Handle special requests
		TdApi.Object specialResult = tryHandleSpecial(query);
		if (specialResult != null) {
			logger.trace(TG_MARKER, "Handling special result for sync request {}: {}", query, specialResult);
			return specialResult;
		}

		return NativeClientAccess.execute(query);
	}

	/**
	 * @param function function used to check if the check will be enforced or not. Can be null
	 * @return not null if closed. The result, if present, must be sent to the client
	 */
	private <R extends TdApi.Object> TdApi.Object tryHandleSpecial(Function<R> function) {
		if (this.closed) {
			if (function != null && function.getConstructor() == TdApi.Close.CONSTRUCTOR) {
				return new TdApi.Ok();
			} else {
				return new TdApi.Error(503, "Client closed");
			}
		} else if (clientId == null) {
			return new TdApi.Error(503, "Client not initialized. TDLib is not available until \"initialize\" is called!");
		} else {
			return null;
		}
	}
}
