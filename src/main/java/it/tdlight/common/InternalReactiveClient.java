package it.tdlight.common;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Error;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Object;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalReactiveClient implements ClientEventsHandler, ReactiveTelegramClient {

	private static final Logger logger = LoggerFactory.getLogger(TelegramClient.class);
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
					var item = ReactiveItem.ofUpdate(updateItem);
					if (subscriber != null && requested.getAndUpdate(n -> n == 0 ? 0 : (n - 1)) > 0) {
						subscriber.onNext(item);
					} else {
						backpressureQueue.add(item);
					}
				},
				updateEx -> {
					var item = ReactiveItem.ofUpdateException(updateEx);
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
			var subscription = new Subscription() {

				@Override
				public void request(long n) {
					if (!backpressureQueue.isEmpty()) {
						while (!backpressureQueue.isEmpty() && n > 0) {
							var item = backpressureQueue.poll();
							subscriber.onNext(item);
							n--;
						}
					}
					if (n > 0) {
						requested.addAndGet(n);
					}
					if (isClosed.get()) {
						if (alreadyCompleted.compareAndSet(false, true)) {
							subscriber.onComplete();
						}
					}
				}

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

			createAndRegisterClient();
			subscriber.onSubscribe(subscription);
		} else {
			throw new IllegalStateException("Already subscribed");
		}
	}

	private void createAndRegisterClient() {
		if (clientId != null) throw new UnsupportedOperationException("Can't initialize the same client twice!");
		clientId = NativeClientAccess.create();
		clientManager.registerClient(clientId, this);
		logger.info("Registered new client {}", clientId);

		// Send a dummy request because @levlam is too lazy to fix race conditions in a better way
		this.send(new TdApi.GetAuthorizationState()).subscribe(new Subscriber<>() {
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

	@Override
	public Publisher<TdApi.Object> send(Function query) {
		AtomicBoolean alreadySubscribed = new AtomicBoolean(false);
		AtomicBoolean cancelled = new AtomicBoolean(false);
		return subscriber -> {
			if (alreadySubscribed.compareAndSet(false, true)) {
				AtomicBoolean alreadyRequested = new AtomicBoolean(false);
				var subscription = new Subscription() {

					@Override
					public void request(long n) {
						if (alreadyRequested.compareAndSet(false, true) && !cancelled.get()) {
							if (isClosedAndMaybeThrow(query)) {
								subscriber.onNext(new TdApi.Ok());
								subscriber.onComplete();
							} else if (clientId == null) {
								subscriber.onError(
										new IllegalStateException("Can't send a request to TDLib before calling \"initialize\" function!")
								);
							} else {
								long queryId = clientManager.getNextQueryId();
								handlers.put(queryId, new Handler(result -> {
									if (!cancelled.get()) {
										subscriber.onNext(result);
										subscriber.onComplete();
									}
								}, throwable -> {
									if (!cancelled.get()) {
										subscriber.onError(throwable);
									}
								}));
								NativeClientAccess.send(clientId, queryId, query);
							}
						}
					}

					@Override
					public void cancel() {
						cancelled.set(true);
					}
				};
				subscriber.onSubscribe(subscription);
			} else {
				throw new IllegalStateException("Already subscribed");
			}
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
