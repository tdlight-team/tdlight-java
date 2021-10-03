package it.tdlight.common.internal;

import it.tdlight.common.ClientEventsHandler;
import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.Signal;
import it.tdlight.common.ReactiveTelegramClient;
import it.tdlight.common.SignalListener;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Error;
import it.tdlight.jni.TdApi.Function;
import java.time.Duration;
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

public final class InternalReactiveClient implements ClientEventsHandler, ReactiveTelegramClient {

	private static final Marker TG_MARKER = MarkerFactory.getMarker("TG");
	private static final Logger logger = LoggerFactory.getLogger(InternalReactiveClient.class);
	private static final Handler EMPTY_HANDLER = new Handler(r -> {}, ex -> {});

	private final ConcurrentHashMap<Long, Handler> handlers = new ConcurrentHashMap<>();
	private final Set<Long> timedOutHandlers = new ConcurrentHashMap<Long, Object>().keySet(new Object());
	private final ScheduledExecutorService timers = Executors.newSingleThreadScheduledExecutor();
	private final ExceptionHandler defaultExceptionHandler;
	private final Handler updateHandler;

	private volatile Integer clientId = null;
	private final InternalClientManager clientManager;

	private final AtomicBoolean alreadyReceivedClosed = new AtomicBoolean();
	private final AtomicReference<SignalListener> signalListener = new AtomicReference<>(new ReplayStartupUpdatesListener());

	public InternalReactiveClient(InternalClientManager clientManager) {
		this.clientManager = clientManager;
		this.updateHandler = new Handler(this::onUpdateFromHandler, this::onUpdateException);
		this.defaultExceptionHandler = this::onDefaultException;
	}

	@Override
	public int getClientId() {
		return Objects.requireNonNull(clientId, "Can't obtain the client id before initialization");
	}

	@Override
	public void handleEvents(boolean isClosed, long[] eventIds, TdApi.Object[] events) {
		for (int i = 0; i < eventIds.length; i++) {
			handleEvent(eventIds[i], events[i]);
		}

		if (isClosed) {
			if (this.alreadyReceivedClosed.compareAndSet(false, true)) {
				handleClose();
			}
		}
	}

	/**
	 * This method will be called exactly once
	 */
	private void handleClose() {
		TdApi.Error instanceClosedError = new Error(500, "Instance closed");
		handlers.forEach((eventId, handler) -> this.handleResponse(eventId, instanceClosedError, handler));
		handlers.clear();
		this.timedOutHandlers.clear();
		timers.shutdown();
		try {
			boolean terminated = timers.awaitTermination(1, TimeUnit.MINUTES);
			if (!terminated) {
				timers.shutdownNow();
			}
		} catch (InterruptedException e) {
			logger.debug(TG_MARKER, "Interrupted", e);
		}
		SignalListener signalListener = this.signalListener.get();
		// Close the signal listener if it still exists
		if (signalListener != null) {
			signalListener.onSignal(Signal.ofClosed());
		}
	}

	/**
	 * Handles only a response (not an update!)
	 */
	private void handleResponse(long eventId, TdApi.Object event, Handler handler) {
		if (handler != null) {
			try {
				if (eventId == 0) {
					logger.trace(TG_MARKER, "Client {} received an event: {}", clientId, event);
				} else {
					logger.trace(TG_MARKER, "Client {} received a response for query id {}: {}", clientId, eventId, event);
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
			if (timedOutHandlers.remove(eventId)) {
				logger.trace(TG_MARKER, "Received event id \"{}\", but the event has been dropped because it"
						+ "timed out some time ago! {}", eventId, event);
			} else {
				logger.error(TG_MARKER, "Unknown event id \"{}\", the event has been dropped! {}", eventId, event);
			}
		}
	}

	/**
	 * Handles a response or an update
	 */
	private void handleEvent(long eventId, TdApi.Object event) {
		Handler handler = eventId == 0 ? updateHandler : handlers.remove(eventId);
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

	@SuppressWarnings({"ReactiveStreamsSubscriberImplementation", "Convert2Diamond"})
	public void createAndRegisterClient() {
		if (clientId != null) {
			throw new UnsupportedOperationException("Can't initialize the same client twice!");
		}
		logger.debug(TG_MARKER, "Creating new client");
		clientId = NativeClientAccess.create();
		logger.debug(TG_MARKER, "Registering new client {}", clientId);
		clientManager.registerClient(clientId, this);
		logger.debug(TG_MARKER, "Registered new client {}", clientId);
	}

	@Override
	public Publisher<TdApi.Object> send(Function query, Duration responseTimeout) {
		return subscriber -> {
			Subscription subscription = new Subscription() {

				private final AtomicBoolean alreadyRequested = new AtomicBoolean(false);
				private volatile boolean cancelled = false;

				@Override
				public void request(long n) {
					if (n > 0 && alreadyRequested.compareAndSet(false, true)) {
						if (isClosedAndMaybeThrow(query)) {
							logger.trace(TG_MARKER, "Client {} is already closed, sending \"Ok\" to: {}", clientId, query);
							subscriber.onNext(new TdApi.Ok());
							subscriber.onComplete();
						} else if (clientId == null) {
							logger.trace(TG_MARKER,
									"Can't send a request to TDLib before calling \"createAndRegisterClient\" function!"
							);
							subscriber.onError(new IllegalStateException(
									"Can't send a request to TDLib before calling \"createAndRegisterClient\" function!"));
						} else {
							long queryId = clientManager.getNextQueryId();

							// Handle timeout
							ScheduledFuture<?> timeoutFuture = timers.schedule(() -> {
								logger.trace(TG_MARKER, "Client {} timed out on query id {}: {}", clientId, queryId, query);
								if (handlers.remove(queryId) != null) {
									if (!cancelled) {
										timedOutHandlers.add(queryId);

										subscriber.onNext(new Error(408, "Request Timeout"));
									}
									if (!cancelled) {
										subscriber.onComplete();
									}
								}
							}, responseTimeout.toMillis(), TimeUnit.MILLISECONDS);

							handlers.put(queryId, new Handler(result -> {
								logger.trace(TG_MARKER,
										"Client {} is replying the query id {}: request: {} result: {}",
										clientId,
										queryId,
										query,
										result
								);
								boolean timeoutCancelled = timeoutFuture.cancel(false);
								if (!cancelled && timeoutCancelled) {
									subscriber.onNext(result);
								}
								if (!cancelled && timeoutCancelled) {
									subscriber.onComplete();
								}
							}, t -> {
								logger.trace(TG_MARKER, "Client {} has failed the query id {}: {}", clientId, queryId, query);
								boolean timeoutCancelled = timeoutFuture.cancel(false);
								if (!cancelled && timeoutCancelled) {
									subscriber.onError(t);
								}
							}));
							logger.trace(TG_MARKER, "Client {} is requesting with query id {}: {}", clientId, queryId, query);
							NativeClientAccess.send(clientId, queryId, query);
							logger.trace(TG_MARKER, "Client {} requested with query id {}: {}", clientId, queryId, query);
						}
					} else {
						logger.debug(TG_MARKER, "Client {} tried to request again the same request, ignored: {}", clientId, query);
					}
				}

				@Override
				public void cancel() {
					cancelled = true;
				}
			};
			subscriber.onSubscribe(subscription);
		};
	}

	@Override
	public TdApi.Object execute(Function query) {
		if (isClosedAndMaybeThrow(query)) {
			return new TdApi.Ok();
		}
		return NativeClientAccess.execute(query);
	}

	@Override
	public void setListener(SignalListener listener) {
		logger.debug(TG_MARKER, "Setting handler of client {}", clientId);
		SignalListener resultListener = this.signalListener.updateAndGet(previousListener -> {
			if (previousListener instanceof ReplayStartupUpdatesListener) {
				ReplayStartupUpdatesListener replayListener = (ReplayStartupUpdatesListener) previousListener;
				replayListener.setNewListener(listener);
				return replayListener;
			} else if (previousListener != null) {
				throw new IllegalStateException("Already subscribed");
			} else {
				return listener;
			}
		});

		// Drain startup queue
		if (resultListener instanceof ReplayStartupUpdatesListener) {
			ReplayStartupUpdatesListener replayStartupUpdatesListener = (ReplayStartupUpdatesListener) resultListener;
			replayStartupUpdatesListener.drain();
		}

		TdApi.GetAuthorizationState query = new TdApi.GetAuthorizationState();
		long queryId = clientManager.getNextQueryId();

		// Send a dummy request to effectively start the TDLib session
		{
			handlers.put(queryId, EMPTY_HANDLER);
			logger.trace(TG_MARKER, "Client {} is requesting with query id {}: {}", clientId, queryId, query);
			NativeClientAccess.send(clientId, queryId, query);
			logger.trace(TG_MARKER, "Client {} requested with query id {}: {}", clientId, queryId, query);
		}

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
		if (!alreadyReceivedClosed.get()) {
			TdApi.Close query = new TdApi.Close();
			long queryId = clientManager.getNextQueryId();

			handlers.put(queryId, EMPTY_HANDLER);
			logger.trace(TG_MARKER, "Client {} is requesting with query id {}: {}", clientId, queryId, query);
			NativeClientAccess.send(clientId, queryId, query);
			logger.trace(TG_MARKER, "Client {} requested with query id {}: {}", clientId, queryId, query);
		}
	}

	/**
	 * @param function function used to check if the check will be enforced or not. Can be null
	 * @return true if closed
	 */
	private boolean isClosedAndMaybeThrow(Function function) {
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
		SignalListener signalListener = this.signalListener.get();
		if (signalListener != null) {
			signalListener.onSignal(item);
		} else {
			logger.error(TG_MARKER, "No signal listener set. Dropped default error {}", (Object) updateEx);
		}
	}

	private void onUpdateException(Throwable updateEx) {
		Signal item = Signal.ofUpdateException(updateEx);
		SignalListener signalListener = this.signalListener.get();
		if (signalListener != null) {
			signalListener.onSignal(item);
		} else {
			logger.error(TG_MARKER, "No signal listener set. Dropped update error {}", (Object) updateEx);
		}
	}

	private void onUpdateFromHandler(TdApi.Object updateItem) {
		Signal item = Signal.ofUpdate(updateItem);
		SignalListener signalListener = this.signalListener.get();
		if (signalListener != null) {
			signalListener.onSignal(item);
		} else {
			logger.error(TG_MARKER, "No signal listener set. Dropped update {}", updateItem);
		}
	}

	private static class ReplayStartupUpdatesListener implements SignalListener {

		private final ConcurrentLinkedQueue<Signal> queue = new ConcurrentLinkedQueue<>();
		private final AtomicReference<SignalListener> listener = new AtomicReference<>(null);

		@Override
		public void onSignal(Signal signal) {
			SignalListener listener;
			if ((listener = this.listener.get()) != null) {
				drainQueue(listener);
				assert queue.isEmpty();
				listener.onSignal(signal);
			} else {
				queue.add(signal);
			}
		}

		public void setNewListener(SignalListener listener) {
			this.listener.set(listener);
		}

		public void drain() {
			SignalListener listener;
			if ((listener = this.listener.get()) != null) {
				drainQueue(listener);
				assert queue.isEmpty();
			}
		}

		private void drainQueue(SignalListener listener) {
			Signal elem;
			while ((elem = queue.poll()) != null) {
				listener.onSignal(elem);
			}
		}
	}
}
