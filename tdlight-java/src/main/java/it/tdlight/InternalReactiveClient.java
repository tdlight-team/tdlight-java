package it.tdlight;

import static it.tdlight.util.TdApiObjectDescriptor.describe;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Error;
import it.tdlight.jni.TdApi.Function;
import java.time.Duration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

final class InternalReactiveClient implements ClientEventsHandler, ReactiveTelegramClient {

	@FunctionalInterface
	interface ClientSender {

		void send(int clientId, long queryId, TdApi.Function<?> query);
	}

	private static final Marker TG_MARKER = MarkerFactory.getMarker("TG");
	private static final Logger logger = LoggerFactory.getLogger(InternalReactiveClient.class);
	private static final Handler<?> EMPTY_HANDLER = new Handler<>(r -> {}, ex -> {});
	private static final int MAX_RETAINED_TIMED_OUT_HANDLERS = 4096;

	private final Map<Long, Handler<?>> handlers = new ConcurrentHashMap<>();
	private final Object timedOutHandlersLock = new Object();
	private final Set<Long> timedOutHandlers = new LinkedHashSet<>();
	private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r);
		t.setName("TDLight-Timers");
		t.setDaemon(true);
		return t;
	});
	private final ExceptionHandler defaultExceptionHandler;
	private final Handler<TdApi.Update> updateHandler;

	private volatile Integer clientId = null;
	private volatile Thread shutdownHook;
	private final InternalClientsState clientManagerState;
	private final ClientSender clientSender;
	private final Runnable clientClosedHandler;
	private final java.lang.Object closeLock = new java.lang.Object();
	private boolean initializationAttempted;
	private boolean listenerRegistered;
	private volatile boolean initialized;

	private final AtomicBoolean alreadyReceivedClosed = new AtomicBoolean();
	private volatile SignalListener signalListener = new ReplayStartupUpdatesListener();

	public InternalReactiveClient(InternalClientsState clientManagerState) {
		this(clientManagerState, NativeClientAccess::send, () -> {});
	}

	InternalReactiveClient(InternalClientsState clientManagerState, ClientSender clientSender) {
		this(clientManagerState, clientSender, () -> {});
	}

	InternalReactiveClient(InternalClientsState clientManagerState,
			ClientSender clientSender,
			Runnable clientClosedHandler) {
		this.clientManagerState = clientManagerState;
		this.clientSender = clientSender;
		this.clientClosedHandler = clientClosedHandler;
		this.updateHandler = new Handler<>(this::onUpdateFromHandler, this::onUpdateException);
		this.defaultExceptionHandler = this::onDefaultException;
	}

	@Override
	public int getClientId() {
		return Objects.requireNonNull(clientId, "Can't obtain the client id before initialization");
	}

	@Override
	public void handleEvents(boolean isClosed, long[] eventIds, TdApi.Object[] events, int arrayOffset, int arrayLength) {
		for (int i = arrayOffset; i < arrayOffset + arrayLength; i++) {
			handleEvent(eventIds[i], events[i]);
		}

		if (isClosed) {
			handleClose();
		}
	}

	/**
	 * This method will be called exactly once
	 */
	private void handleClose() {
		Map<Long, Handler<?>> pendingHandlers = new HashMap<>();
		synchronized (closeLock) {
			if (!this.alreadyReceivedClosed.compareAndSet(false, true)) {
				return;
			}
			logger.debug(TG_MARKER, "Received close");
			initialized = false;
			handlers.forEach((eventId, handler) -> {
				if (handlers.remove(eventId, handler)) {
					pendingHandlers.put(eventId, handler);
				}
			});
			timers.shutdownNow();
		}
		removeShutdownHook();
		TdApi.Error instanceClosedError = new Error(500, "Instance closed");
		pendingHandlers.forEach((eventId, handler) -> this.handleResponse(eventId, instanceClosedError, handler));
		synchronized (timedOutHandlersLock) {
			this.timedOutHandlers.clear();
		}
		SignalListener signalListener = this.signalListener;
		// Close the signal listener if it still exists
		if (signalListener != null) {
			try {
				signalListener.onSignal(Signal.ofClosed());
			} catch (Throwable ex) {
				logger.warn(TG_MARKER, "Client {} signal listener failed while closing", clientId, ex);
			}
		}
		notifyClientClosed();
		logger.info(TG_MARKER, "Client closed {}", clientId);
	}

	/**
	 * Handles only a response (not an update!)
	 */
	private void handleResponse(long eventId, TdApi.Object event, Handler<?> handler) {
		if (handler != null) {
			try {
				if (eventId == 0) {
					logger.trace(TG_MARKER, "Client {} received an event of type {}", clientId, describe(event));
				} else {
					logger.trace(TG_MARKER,
							"Client {} received a response of type {} for query id {}",
							clientId,
							describe(event),
							eventId
					);
				}
				handler.getResultHandler().onResult(event);
			} catch (Throwable cause) {
				handleException(handler.getExceptionHandler(), cause);
			}
		} else {
			if (event.getConstructor() == Error.CONSTRUCTOR) {
				TdApi.Error error = (TdApi.Error) event;
				if (error.code == 500 && "Request aborted".equals(error.message)) {
					return;
				}
			}
			if (forgetTimedOutHandler(eventId)) {
				logger.trace(TG_MARKER,
						"Received event id \"{}\", but the event has been dropped because it timed out; type {}",
						eventId,
						describe(event)
				);
			} else {
				logger.error(TG_MARKER,
						"Unknown event id \"{}\"; dropped event type {}",
						eventId,
						describe(event)
				);
			}
		}
	}

	private void rememberTimedOutHandler(long eventId) {
		synchronized (timedOutHandlersLock) {
			timedOutHandlers.add(eventId);
			if (timedOutHandlers.size() > MAX_RETAINED_TIMED_OUT_HANDLERS) {
				Iterator<Long> iterator = timedOutHandlers.iterator();
				iterator.next();
				iterator.remove();
			}
		}
	}

	private boolean forgetTimedOutHandler(long eventId) {
		synchronized (timedOutHandlersLock) {
			return timedOutHandlers.remove(eventId);
		}
	}

	int pendingResponseCount() {
		return handlers.size();
	}

	int retainedTimeoutCount() {
		synchronized (timedOutHandlersLock) {
			return timedOutHandlers.size();
		}
	}

	/**
	 * Handles a response or an update
	 */
	private void handleEvent(long eventId, TdApi.Object event) {
		Handler<?> handler = eventId == 0 ? updateHandler : handlers.remove(eventId);
		handleResponse(eventId, event, handler);
	}

	private void handleException(ExceptionHandler exceptionHandler, Throwable cause) {
		if (exceptionHandler == null) {
			exceptionHandler = defaultExceptionHandler;
		}
		try {
			exceptionHandler.onException(cause);
		} catch (Throwable ignored) {
		}
	}

	public synchronized void createAndRegisterClient() {
		if (initializationAttempted) {
			throw new UnsupportedOperationException("Can't initialize the same client twice!");
		}
		initializationAttempted = true;
		logger.debug(TG_MARKER, "Creating new client");
		try {
			clientManagerState.createAndRegisterClient(this, newClientId -> {
				synchronized (closeLock) {
					clientId = newClientId;
					initialized = true;
					installShutdownHook(newClientId);
				}
			});
			logger.info(TG_MARKER, "Registered new client {}", clientId);
		} catch (RuntimeException | java.lang.Error ex) {
			synchronized (closeLock) {
				initialized = false;
				alreadyReceivedClosed.set(true);
			}
			timers.shutdownNow();
			removeShutdownHook();
			notifyClientClosed();
			throw ex;
		}
	}

	private void notifyClientClosed() {
		try {
			clientClosedHandler.run();
		} catch (Throwable ex) {
			logger.warn(TG_MARKER, "Failed to release client {} owner after close", clientId, ex);
		}
	}

	private void installShutdownHook(int newClientId) {
		Thread newShutdownHook = new Thread(this::onJVMShutdown, "TDLight reactive client shutdown " + newClientId);
		try {
			Runtime.getRuntime().addShutdownHook(newShutdownHook);
			shutdownHook = newShutdownHook;
		} catch (IllegalStateException ex) {
			onJVMShutdown();
		} catch (SecurityException ex) {
			logger.warn(TG_MARKER, "Can't install shutdown hook for client {}", newClientId, ex);
		}
	}

	private void removeShutdownHook() {
		Thread currentShutdownHook = shutdownHook;
		if (currentShutdownHook == null) {
			return;
		}
		shutdownHook = null;
		try {
			Runtime.getRuntime().removeShutdownHook(currentShutdownHook);
		} catch (IllegalStateException | SecurityException ignored) {
			logger.debug(TG_MARKER, "Can't remove shutdown hook because the JVM is already shutting down");
		}
	}

	@Override
	public <R extends TdApi.Object> Publisher<TdApi.Object> send(Function<R> query, Duration responseTimeout) {
		Objects.requireNonNull(query, "Query is null");
		Objects.requireNonNull(responseTimeout, "Response timeout is null");
		return subscriber -> {
			Subscription subscription = new Subscription() {

				private final AtomicBoolean alreadyRequested = new AtomicBoolean(false);
				private volatile boolean cancelled = false;
				private volatile long queryId;
				private volatile ScheduledFuture<?> timeoutFuture;

				@Override
				public void request(long n) {
					if (n <= 0) {
						if (alreadyRequested.compareAndSet(false, true)) {
							subscriber.onError(new IllegalArgumentException("A positive request amount is required"));
						}
						return;
					}
					if (cancelled || !alreadyRequested.compareAndSet(false, true)) {
						logger.debug(TG_MARKER,
								"Client {} tried to request again the same request type {}; ignored",
								clientId,
								describe(query)
						);
						return;
					}

					TdApi.Object immediateResult = null;
					Throwable immediateFailure = null;
					synchronized (closeLock) {
						if (cancelled) {
							return;
						}
						if (alreadyReceivedClosed.get()) {
							if (query.getConstructor() == TdApi.Close.CONSTRUCTOR) {
								immediateResult = new TdApi.Ok();
							} else {
								immediateFailure = new IllegalStateException("The client is closed!");
							}
						} else if (!initialized) {
							immediateFailure = new IllegalStateException(
									"Can't send a request to TDLib before calling \"createAndRegisterClient\" function!");
						} else {
							queryId = clientManagerState.getNextQueryId();
							Handler<R> responseHandler = new Handler<>(result -> {
								logger.trace(TG_MARKER,
										"Client {} is replying to query id {}: request type {}, result type {}",
										clientId,
										queryId,
										describe(query),
										describe(result)
								);
								this.timeoutFuture.cancel(false);
								if (!cancelled) {
									subscriber.onNext(result);
								}
								if (!cancelled) {
									subscriber.onComplete();
								}
							}, t -> {
								logger.trace(TG_MARKER,
										"Client {} has failed query id {} of type {}",
										clientId,
										queryId,
										describe(query)
								);
								this.timeoutFuture.cancel(false);
								if (!cancelled) {
									subscriber.onError(t);
								}
							});
							handlers.put(queryId, responseHandler);
							try {
								timeoutFuture = timers.schedule(() -> {
									logger.trace(TG_MARKER,
											"Client {} timed out on query id {} of type {}",
											clientId,
											queryId,
											describe(query)
									);
									boolean deliverTimeout;
									synchronized (closeLock) {
										deliverTimeout = !cancelled && handlers.remove(queryId, responseHandler);
										if (deliverTimeout) {
											rememberTimedOutHandler(queryId);
										}
									}
									if (deliverTimeout && !cancelled) {
										subscriber.onNext(new Error(408, "Request Timeout"));
										subscriber.onComplete();
									}
								}, responseTimeout.toMillis(), TimeUnit.MILLISECONDS);
							} catch (RuntimeException | java.lang.Error ex) {
								handlers.remove(queryId, responseHandler);
								immediateFailure = ex;
							}
							try {
								if (immediateFailure == null) {
									logger.trace(TG_MARKER,
											"Client {} is requesting query id {} of type {}",
											clientId,
											queryId,
											describe(query)
									);
									clientSender.send(clientId, queryId, query);
									logger.trace(TG_MARKER,
											"Client {} requested query id {} of type {}",
											clientId,
											queryId,
											describe(query)
									);
								}
							} catch (RuntimeException | java.lang.Error ex) {
								boolean responsePending = handlers.remove(queryId, responseHandler);
								timeoutFuture.cancel(false);
								if (responsePending) {
									immediateFailure = ex;
								}
							}
						}
					}

					if (immediateFailure != null) {
						subscriber.onError(immediateFailure);
					} else if (immediateResult != null) {
						subscriber.onNext(immediateResult);
						subscriber.onComplete();
					}
				}

				@Override
				public void cancel() {
					synchronized (closeLock) {
						cancelled = true;
						long currentQueryId = queryId;
						if (currentQueryId != 0) {
							handlers.remove(currentQueryId);
						}
						ScheduledFuture<?> currentTimeout = timeoutFuture;
						if (currentTimeout != null) {
							currentTimeout.cancel(false);
						}
					}
				}
			};
			subscriber.onSubscribe(subscription);
		};
	}

	@Override
	public <R extends TdApi.Object> TdApi.Object execute(Function<R> query) {
		if (isClosedAndMaybeThrow(query)) {
			return new TdApi.Ok();
		}
		return NativeClientAccess.execute(query);
	}

	@Override
	public void setListener(SignalListener listener) {
		Objects.requireNonNull(listener, "Listener is null");
		logger.debug(TG_MARKER, "Setting handler of client {}", clientId);
		ReplayStartupUpdatesListener replayStartupUpdatesListener;
		synchronized (closeLock) {
			if (alreadyReceivedClosed.get()) {
				throw new IllegalStateException("The client is closed!");
			}
			if (!initialized) {
				throw new IllegalStateException(
						"Can't set a listener before calling \"createAndRegisterClient\" function!");
			}

			SignalListener prevSignalListener = this.signalListener;
			if (listenerRegistered || !(prevSignalListener instanceof ReplayStartupUpdatesListener)) {
				throw new IllegalStateException("Already subscribed");
			}
			replayStartupUpdatesListener = (ReplayStartupUpdatesListener) prevSignalListener;

			TdApi.GetAuthorizationState query = new TdApi.GetAuthorizationState();
			long queryId = clientManagerState.getNextQueryId();

			// Send a dummy request to effectively start the TDLib session
			handlers.put(queryId, EMPTY_HANDLER);
			try {
				logger.trace(TG_MARKER,
						"Client {} is requesting query id {} of type {}",
						clientId,
						queryId,
						describe(query)
				);
				clientSender.send(clientId, queryId, query);
				logger.trace(TG_MARKER,
						"Client {} requested query id {} of type {}",
						clientId,
						queryId,
						describe(query)
				);
			} catch (RuntimeException | java.lang.Error ex) {
				handlers.remove(queryId, EMPTY_HANDLER);
				throw ex;
			}
			listenerRegistered = true;
		}
		// User callbacks are drained only after releasing closeLock.
		replayStartupUpdatesListener.activate(listener);

		logger.debug(TG_MARKER, "Set handler of client {}", clientId);
	}

	@Override
	public void cancel() {
		logger.debug(TG_MARKER, "Client {} is being cancelled", clientId);
		this.sendCloseAndIgnoreResponse();
	}

	@Override
	public void dispose() {
		logger.debug(TG_MARKER, "Client {} is being disposed", clientId);
		this.sendCloseAndIgnoreResponse();
	}

	private void sendCloseAndIgnoreResponse() {
		synchronized (closeLock) {
			if (alreadyReceivedClosed.get() || clientId == null) {
				return;
			}
			TdApi.Close query = new TdApi.Close();
			long queryId = clientManagerState.getNextQueryId();

			handlers.put(queryId, EMPTY_HANDLER);
			try {
				logger.trace(TG_MARKER,
						"Client {} is requesting query id {} of type {}",
						clientId,
						queryId,
						describe(query)
				);
				clientSender.send(clientId, queryId, query);
				logger.trace(TG_MARKER,
						"Client {} requested query id {} of type {}",
						clientId,
						queryId,
						describe(query)
				);
			} catch (RuntimeException | java.lang.Error ex) {
				handlers.remove(queryId, EMPTY_HANDLER);
				throw ex;
			}
		}
	}

	private void onJVMShutdown() {
		if ("true".equalsIgnoreCase(System.getProperty("it.tdlight.enableShutdownHooks", "true"))) {
			try {
				logger.info(TG_MARKER, "Client {} is shutting down because the JVM is shutting down", clientId);
				sendCloseAndIgnoreResponse();
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
		boolean closed = alreadyReceivedClosed.get();
		if (closed) {
			if (function != null && function.getConstructor() == TdApi.Close.CONSTRUCTOR) {
				return true;
			} else {
				throw new IllegalStateException("The client is closed!");
			}
		}
		return false;
	}

	private void onDefaultException(Throwable updateEx) {
		Signal item = Signal.ofUpdateException(updateEx);
		SignalListener signalListener = this.signalListener;
		if (signalListener != null) {
			signalListener.onSignal(item);
		} else {
			logger.error(TG_MARKER, "No signal listener set. Dropped default error {}", (Object) updateEx);
		}
	}

	private void onUpdateException(Throwable updateEx) {
		Signal item = Signal.ofUpdateException(updateEx);
		SignalListener signalListener = this.signalListener;
		if (signalListener != null) {
			signalListener.onSignal(item);
		} else {
			logger.error(TG_MARKER, "No signal listener set. Dropped update error {}", (Object) updateEx);
		}
	}

	private void onUpdateFromHandler(TdApi.Object updateItem) {
		Signal item = Signal.ofUpdate(updateItem);
		SignalListener signalListener = this.signalListener;
		if (signalListener != null) {
			signalListener.onSignal(item);
		} else {
			logger.error(TG_MARKER, "No signal listener set. Dropped update type {}", describe(updateItem));
		}
	}

	private class ReplayStartupUpdatesListener implements SignalListener {

		private final ConcurrentLinkedQueue<Signal> queue = new ConcurrentLinkedQueue<>();
		private final Object dispatchLock = new Object();
		private final AtomicReference<SignalListener> listener = new AtomicReference<>(null);
		private boolean dispatching;

		@Override
		public void onSignal(Signal signal) {
			SignalListener currentListener;
			synchronized (dispatchLock) {
				currentListener = listener.get();
				if (currentListener == null || dispatching) {
					queue.add(signal);
					return;
				}
				dispatching = true;
			}
			drainSignals(currentListener, signal);
		}

		public void activate(SignalListener newListener) {
			Signal firstSignal;
			synchronized (dispatchLock) {
				if (!listener.compareAndSet(null, newListener)) {
					throw new IllegalStateException("Already subscribed");
				}
				firstSignal = queue.poll();
				if (firstSignal == null) {
					return;
				}
				dispatching = true;
			}
			drainSignals(newListener, firstSignal);
		}

		private void drainSignals(SignalListener currentListener, Signal firstSignal) {
			RuntimeException runtimeFailure = null;
			java.lang.Error errorFailure = null;
			Signal signal = firstSignal;
			while (signal != null) {
				try {
					currentListener.onSignal(signal);
				} catch (RuntimeException ex) {
					if (runtimeFailure == null && errorFailure == null) {
						runtimeFailure = ex;
					} else if (runtimeFailure != null) {
						runtimeFailure.addSuppressed(ex);
					} else {
						errorFailure.addSuppressed(ex);
					}
				} catch (java.lang.Error ex) {
					if (runtimeFailure == null && errorFailure == null) {
						errorFailure = ex;
					} else if (runtimeFailure != null) {
						runtimeFailure.addSuppressed(ex);
					} else {
						errorFailure.addSuppressed(ex);
					}
				}
				synchronized (dispatchLock) {
					signal = queue.poll();
					if (signal == null) {
						dispatching = false;
					}
				}
			}
			if (runtimeFailure != null) {
				throw runtimeFailure;
			}
			if (errorFailure != null) {
				throw errorFailure;
			}
		}
	}
}
