package it.tdlight.common.internal;

import it.tdlight.common.ClientEventsHandler;
import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.ReactiveItem;
import it.tdlight.common.ReactiveTelegramClient;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Error;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Object;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalReactiveClient implements ClientEventsHandler, ReactiveTelegramClient {

	private static boolean ENABLE_BACKPRESSURE_QUEUE = false;
	private static final Logger logger = LoggerFactory.getLogger(InternalReactiveClient.class);
	private final ConcurrentHashMap<Long, Handler> handlers = new ConcurrentHashMap<Long, Handler>();
	private final ConcurrentLinkedQueue<ReactiveItem> backpressureQueue = new ConcurrentLinkedQueue<>();
	private final ExceptionHandler defaultExceptionHandler;
	private final Handler updateHandler;

	private volatile Integer clientId = null;
	private final InternalClientManager clientManager;

	private final AtomicBoolean isClosed = new AtomicBoolean();
	private final AtomicBoolean updatesAlreadySubscribed = new AtomicBoolean(false);
	private final AtomicLong requested = new AtomicLong(0);
	private volatile Subscriber<? super ReactiveItem> subscriber;

	public InternalReactiveClient(InternalClientManager clientManager) {
		this.clientManager = clientManager;
		this.updateHandler = new Handler(
				updateItem -> {
					ReactiveItem item = ReactiveItem.ofUpdate(updateItem);
					if (subscriber != null && requested.getAndUpdate(n -> n == 0 ? 0 : (n - 1)) > 0) {
						subscriber.onNext(item);
					} else {
						backpressureQueue.add(item);
					}
				},
				updateEx -> {
					ReactiveItem item = ReactiveItem.ofUpdateException(updateEx);
					if (subscriber != null && requested.getAndUpdate(n -> n == 0 ? 0 : (n - 1)) > 0) {
						subscriber.onNext(item);
					} else {
						backpressureQueue.add(item);
					}
				}
		);
		this.defaultExceptionHandler = ex -> backpressureQueue.add(ReactiveItem.ofHandleException(ex));
	}

	@Override
	public int getClientId() {
		return Objects.requireNonNull(clientId, "Can't obtain the client id before initialization");
	}

	@Override
	public void handleEvents(boolean isClosed, long[] eventIds, Object[] events) {
		for (int i = 0; i < eventIds.length; i++) {
			handleEvent(eventIds[i], events[i]);
		}

		if (isClosed) {
			if (this.isClosed.compareAndSet(false, true)) {
				handleClose();
			}
		}
 	}

	private void handleClose() {
		handlers.forEach((eventId, handler) -> {
			handleResponse(eventId, new Error(500, "Instance closed"), handler);
		});
		handlers.clear();
	}

	/**
	 * Handles only a response (not an update!)
	 */
	private void handleResponse(long eventId, Object event, Handler handler) {
		if (handler != null) {
			try {
				if (eventId == 0) {
					logger.trace("Client {} received an event: {}", clientId, event);
				} else {
					logger.trace("Client {} received a response for query id {}: {}", clientId, eventId, event);
				}
				handler.getResultHandler().onResult(event);
			} catch (Throwable cause) {
				handleException(handler.getExceptionHandler(), cause);
			}
		} else {
			logger.error("Unknown event id \"{}\", the event has been dropped! {}", eventId, event);
		}
	}

	/**
	 * Handles a response or an update
	 */
	private void handleEvent(long eventId, Object event) {
		Handler handler = eventId == 0 ? updateHandler : handlers.remove(eventId);
		handleResponse(eventId, event, handler);
	}

	private void handleException(ExceptionHandler exceptionHandler, Throwable cause) {
		if (exceptionHandler == null) {
			exceptionHandler = defaultExceptionHandler;
		}
		if (exceptionHandler != null) {
			try {
				exceptionHandler.onException(cause);
			} catch (Throwable ignored) {}
		}
	}

	@Override
	public void subscribe(Subscriber<? super ReactiveItem> subscriber) {
		AtomicBoolean alreadyCompleted = new AtomicBoolean();
		if (updatesAlreadySubscribed.compareAndSet(false, true)) {
			Subscription subscription = new Subscription() {

				@Override
				public void request(long n) {
					ReactiveItem item;
					while (n > 0 && (item = backpressureQueue.poll()) != null) {
						subscriber.onNext(item);
						n--;
					}
					if (n > 0) {
						requested.addAndGet(n);
					}
					if (isClosed.get()) {
						if (alreadyCompleted.compareAndSet(false, true)) {
							subscriber.onComplete();
							logger.info("Client closed {}", clientId);
						}
					}
				}

				@SuppressWarnings("ReactiveStreamsSubscriberImplementation")
				@Override
				public void cancel() {
					if (!isClosed.get()) {
						send(new TdApi.Close()).subscribe(new Subscriber<>() {
							@Override
							public void onSubscribe(Subscription subscription) {
								subscription.request(1);
							}

							@Override
							public void onNext(Object item) {

							}

							@Override
							public void onError(Throwable throwable) {

							}

							@Override
							public void onComplete() {

							}
						});
					}
				}
			};
			this.subscriber = subscriber;

			subscriber.onSubscribe(subscription);
		} else {
			throw new IllegalStateException("Already subscribed");
		}
	}

	@SuppressWarnings("ReactiveStreamsSubscriberImplementation")
	public void createAndRegisterClient() {
		if (clientId != null) throw new UnsupportedOperationException("Can't initialize the same client twice!");
		logger.debug("Creating new client");
		clientId = NativeClientAccess.create();
		logger.debug("Registering new client {}", clientId);
		clientManager.registerClient(clientId, this);

		CountDownLatch registeredClient = new CountDownLatch(1);

		// Send a dummy request because @levlam is too lazy to fix race conditions in a better way
		this.send(new TdApi.GetAuthorizationState()).subscribe(new Subscriber<>() {
			@Override
			public void onSubscribe(Subscription subscription) {
				subscription.request(1);
			}

			@Override
			public void onNext(Object item) {
				registeredClient.countDown();
			}

			@Override
			public void onError(Throwable throwable) {
				registeredClient.countDown();
			}

			@Override
			public void onComplete() {
				registeredClient.countDown();
			}
		});

		try {
			registeredClient.await();
		} catch (InterruptedException e) {
			throw new IllegalStateException(e);
		}
		logger.debug("Registered new client {}", clientId);
	}

	@Override
	public Publisher<TdApi.Object> send(Function query) {
		return subscriber -> {
			Subscription subscription = new Subscription() {

				private final AtomicBoolean alreadyRequested = new AtomicBoolean(false);
				private volatile boolean cancelled = false;

				@Override
				public void request(long n) {
					if (n > 0 && alreadyRequested.compareAndSet(false, true)) {
						if (isClosedAndMaybeThrow(query)) {
							logger.trace("Client {} is already closed, sending \"Ok\" to: {}", clientId, query);
							subscriber.onNext(new TdApi.Ok());
							subscriber.onComplete();
						} else if (clientId == null) {
							logger.trace("Can't send a request to TDLib before calling \"createAndRegisterClient\" function!");
							subscriber.onError(
									new IllegalStateException("Can't send a request to TDLib before calling \"createAndRegisterClient\" function!")
							);
						} else {
							long queryId = clientManager.getNextQueryId();
							handlers.put(queryId, new Handler(result -> {
								if (!cancelled) {
									subscriber.onNext(result);
								}
								if (!cancelled) {
									subscriber.onComplete();
								}
							}, t -> {
								if (!cancelled) {
									subscriber.onError(t);
								}
							}));
							logger.trace("Client {} is requesting with query id {}: {}", clientId, queryId, query);
							NativeClientAccess.send(clientId, queryId, query);
							logger.trace("Client {} requested with query id {}: {}", clientId, queryId, query);
						}
					} else {
						logger.debug("Client {} tried to request again the same request, ignored: {}", clientId, query);
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
	public Object execute(Function query) {
		if (isClosedAndMaybeThrow(query)) {
			return new TdApi.Ok();
		}
		return NativeClientAccess.execute(query);
	}

	/**
	 *
	 * @param function function used to check if the check will be enforced or not. Can be null
	 * @return true if closed
	 */
	private boolean isClosedAndMaybeThrow(Function function) {
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
