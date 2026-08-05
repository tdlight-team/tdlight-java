package it.tdlight;

import static it.tdlight.util.TdApiObjectDescriptor.describe;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Object;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

final class InternalClient implements ClientEventsHandler, TelegramClient {

	@FunctionalInterface
	interface ClientSender {

		void send(int clientId, long queryId, TdApi.Function<?> query);
	}

	static final Marker TG_MARKER = MarkerFactory.getMarker("TG");
	static final Logger logger = LoggerFactory.getLogger(TelegramClient.class);

	private ClientRegistrationEventHandler clientRegistrationEventHandler;
	private final Map<Long, Handler<?>> handlers = new ConcurrentHashMap<>();

	private volatile Integer clientId = null;
	private final InternalClientsState clientManagerState;
	private final ClientSender clientSender;
	private final Runnable clientClosedHandler;
	private Handler<TdApi.Update> updateHandler;
	private MultiHandler updatesHandler;
	private ExceptionHandler defaultExceptionHandler;
	private boolean initializationAttempted;
	private volatile boolean initialized;

	private final java.lang.Object closeLock = new java.lang.Object();
	private volatile boolean closed = false;

	public InternalClient(InternalClientsState clientManagerState,
			ClientRegistrationEventHandler clientRegistrationEventHandler) {
		this(clientManagerState, clientRegistrationEventHandler, NativeClientAccess::send, () -> {
		});
	}

	InternalClient(InternalClientsState clientManagerState,
			ClientRegistrationEventHandler clientRegistrationEventHandler,
			ClientSender clientSender) {
		this(clientManagerState, clientRegistrationEventHandler, clientSender, () -> {
		});
	}

	InternalClient(InternalClientsState clientManagerState,
			ClientRegistrationEventHandler clientRegistrationEventHandler,
			ClientSender clientSender,
			Runnable clientClosedHandler) {
		this.clientManagerState = clientManagerState;
		this.clientRegistrationEventHandler = clientRegistrationEventHandler;
		this.clientSender = clientSender;
		this.clientClosedHandler = clientClosedHandler;
	}

	@Override
	public int getClientId() {
		return Objects.requireNonNull(clientId, "Can't obtain the client id before initialization");
	}

	@Override
	public void handleEvents(boolean isClosed, long[] eventIds, TdApi.Object[] events, int arrayOffset, int arrayLength) {
		if (updatesHandler != null) {
			List<Object> updatesList = new ArrayList<>(arrayLength);

			for (int i = arrayOffset; i < arrayOffset + arrayLength; i++) {
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
			handleClose();
		}
	}

	private void handleClose() {
		Map<Long, Handler<?>> pendingHandlers = transitionToClosed();
		if (pendingHandlers == null) {
			return;
		}
		logger.debug(TG_MARKER, "Received close");
		completePendingHandlers(pendingHandlers);
		notifyClientClosed();
		logger.debug(TG_MARKER, "Client closed {}", clientId);
	}

	private Map<Long, Handler<?>> transitionToClosed() {
		Map<Long, Handler<?>> pendingHandlers = new HashMap<>();
		synchronized (closeLock) {
			if (closed) {
				return null;
			}
			closed = true;
			initialized = false;
			handlers.forEach((eventId, handler) -> {
				if (handlers.remove(eventId, handler)) {
					pendingHandlers.put(eventId, handler);
				}
			});
		}
		return pendingHandlers;
	}

	private void completePendingHandlers(Map<Long, Handler<?>> pendingHandlers) {
		TdApi.Error instanceClosed = new TdApi.Error(500, "Instance closed");
		pendingHandlers.forEach((eventId, handler) -> handleResponse(eventId, instanceClosed, handler));
	}

	private void notifyClientClosed() {
		try {
			clientClosedHandler.run();
		} catch (Throwable ex) {
			logger.warn(TG_MARKER, "Failed to clean up client {} after close", clientId, ex);
		}
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
			logger.trace(TG_MARKER,
					"Client {}, request event id is not registered \"{}\"; dropped response type {}",
					clientId,
					eventId,
					describe(event)
			);
		}
	}

	/**
	 * Handles a response or an update
	 */
	private void handleEvent(long eventId, TdApi.Object event) {
		logger.trace(TG_MARKER, "Client {}, response received for request {} with type {}",
				clientId,
				eventId,
				describe(event)
		);
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
		initializeClient(null, new MultiHandler(updatesHandler, updateExceptionHandler), defaultExceptionHandler);
	}

	@Override
	public void initialize(ResultHandler<TdApi.Update> updateHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		initializeClient(new Handler<>(updateHandler, updateExceptionHandler), null, defaultExceptionHandler);
	}

	private void initializeClient(Handler<TdApi.Update> updateHandler,
			MultiHandler updatesHandler,
			ExceptionHandler defaultExceptionHandler) {
		synchronized (this) {
			if (initializationAttempted) {
				throw new UnsupportedOperationException("Can't initialize the same client twice!");
			}
			initializationAttempted = true;
			this.updateHandler = updateHandler;
			this.updatesHandler = updatesHandler;
			this.defaultExceptionHandler = defaultExceptionHandler;
			try {
				clientManagerState.createAndRegisterClient(this, newClientId -> {
					synchronized (closeLock) {
						this.clientId = newClientId;
						this.initialized = true;
					}
					ClientRegistrationEventHandler registrationHandler = clientRegistrationEventHandler;
					if (registrationHandler != null) {
						registrationHandler.onClientRegistered(newClientId, clientManagerState::getNextQueryId);
						clientRegistrationEventHandler = null;
					}
				});
			} catch (RuntimeException | Error ex) {
				synchronized (closeLock) {
					initialized = false;
					closed = true;
				}
				notifyClientClosed();
				throw ex;
			}
			logger.info(TG_MARKER, "Registered new client {}", clientId);
		}

		// Send a dummy request to start TDLib
		logger.debug(TG_MARKER, "Sending dummy startup request as client {}", clientId);
		TdApi.Function<?> dummyRequest = new TdApi.GetOption("version");
		try {
			this.send(dummyRequest, null, null);
		} catch (RuntimeException | Error ex) {
			rollbackFailedStartup(ex);
			throw ex;
		}
	}

	private void rollbackFailedStartup(Throwable startupFailure) {
		Map<Long, Handler<?>> pendingHandlers = transitionToClosed();
		if (pendingHandlers == null) {
			return;
		}
		Integer currentClientId = clientId;
		if (currentClientId != null) {
			clientManagerState.removeClientEventHandlers(currentClientId, this);
			try {
				clientSender.send(currentClientId, clientManagerState.getNextQueryId(), new TdApi.Close());
			} catch (Throwable closeFailure) {
				if (closeFailure != startupFailure) {
					startupFailure.addSuppressed(closeFailure);
				}
			}
		}
		completePendingHandlers(pendingHandlers);
		notifyClientClosed();
	}

	@Override
	public <R extends TdApi.Object> void send(Function<R> query,
			ResultHandler<R> resultHandler,
			ExceptionHandler exceptionHandler) {
		Objects.requireNonNull(query, "Query is null");
		logger.trace(TG_MARKER, "Trying to send async request type {}", describe(query));

		TdApi.Object specialResult;
		synchronized (closeLock) {
			specialResult = tryHandleSpecial(query);
			if (specialResult == null) {
				long queryId = clientManagerState.getNextQueryId();
				Handler<R> handler = resultHandler != null ? new Handler<>(resultHandler, exceptionHandler) : null;
				if (handler != null) {
					handlers.put(queryId, handler);
				}
				try {
					clientSender.send(clientId, queryId, query);
				} catch (RuntimeException | Error ex) {
					if (handler != null) {
						handlers.remove(queryId, handler);
					}
					throw ex;
				}
				return;
			}
		}

		if (specialResult != null) {
			logger.trace(TG_MARKER,
					"Handling special result type {} for async request type {}",
					describe(specialResult),
					describe(query)
			);
			if (resultHandler != null) {
				handleResponse(0, specialResult, new Handler<>(resultHandler, exceptionHandler));
			}
		}
	}

	@Override
	public <R extends TdApi.Object> TdApi.Object execute(Function<R> query) {
		Objects.requireNonNull(query, "Query is null");
		logger.trace(TG_MARKER, "Trying to execute sync request type {}", describe(query));

		// Handle special requests
		TdApi.Object specialResult;
		synchronized (closeLock) {
			specialResult = tryHandleSpecial(query);
		}
		if (specialResult != null) {
			logger.trace(TG_MARKER,
					"Handling special result type {} for sync request type {}",
					describe(specialResult),
					describe(query)
			);
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
		} else if (!initialized) {
			return new TdApi.Error(503, "Client not initialized. TDLib is not available until \"initialize\" is called!");
		} else {
			return null;
		}
	}
}
