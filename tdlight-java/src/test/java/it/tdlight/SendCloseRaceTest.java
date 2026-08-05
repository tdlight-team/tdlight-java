package it.tdlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.tdlight.jni.TdApi;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class SendCloseRaceTest {

	@Test
	public void callbackClientCloseWaitsForSendAndCompletesPendingHandler() throws Exception {
		InternalClientsState state = new InternalClientsState(() -> 17);
		CountDownLatch sendEntered = new CountDownLatch(1);
		CountDownLatch releaseSend = new CountDownLatch(1);
		AtomicInteger nativeSends = new AtomicInteger();
		InternalClient client = new InternalClient(state, null, (clientId, queryId, query) -> {
			nativeSends.incrementAndGet();
			if (query.getConstructor() == TdApi.GetAuthorizationState.CONSTRUCTOR) {
				sendEntered.countDown();
				await(releaseSend);
			}
		});
		client.initialize((UpdatesHandler) updates -> {}, null, null);
		assertEquals(1, nativeSends.get(), "Initialization sends one startup request");

		AtomicInteger callbacks = new AtomicInteger();
		AtomicReference<TdApi.Object> response = new AtomicReference<>();
		CountDownLatch closeStarted = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> send = executor.submit(() -> client.send(new TdApi.GetAuthorizationState(), result -> {
				callbacks.incrementAndGet();
				response.set(result);
			}));
			assertTrue(sendEntered.await(5, TimeUnit.SECONDS));
			Future<?> close = executor.submit(() -> {
				closeStarted.countDown();
				client.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0);
			});
			assertTrue(closeStarted.await(5, TimeUnit.SECONDS));
			assertFalse(close.isDone(), "Close must not overtake an in-flight native send");

			releaseSend.countDown();
			send.get(5, TimeUnit.SECONDS);
			close.get(5, TimeUnit.SECONDS);
		} finally {
			releaseSend.countDown();
			executor.shutdownNow();
		}

		assertEquals(1, callbacks.get());
		TdApi.Error closeError = assertInstanceOf(TdApi.Error.class, response.get());
		assertEquals(500, closeError.code);
		assertEquals("Instance closed", closeError.message);

		int sendsBeforeClosedRequest = nativeSends.get();
		AtomicReference<TdApi.Object> closedResponse = new AtomicReference<>();
		client.send(new TdApi.GetAuthorizationState(), closedResponse::set);
		TdApi.Error closedError = assertInstanceOf(TdApi.Error.class, closedResponse.get());
		assertEquals(503, closedError.code);
		assertEquals(sendsBeforeClosedRequest, nativeSends.get());
	}

	@Test
	public void reactiveClientCloseWaitsForSendAndCompletesPendingSubscriber() throws Exception {
		InternalClientsState state = new InternalClientsState(() -> 23);
		CountDownLatch sendEntered = new CountDownLatch(1);
		CountDownLatch releaseSend = new CountDownLatch(1);
		AtomicInteger nativeSends = new AtomicInteger();
		InternalReactiveClient client = new InternalReactiveClient(state, (clientId, queryId, query) -> {
			nativeSends.incrementAndGet();
			if (query.getConstructor() == TdApi.GetAuthorizationState.CONSTRUCTOR) {
				sendEntered.countDown();
				await(releaseSend);
			}
		});
		client.createAndRegisterClient();

		RecordingSubscriber subscriber = new RecordingSubscriber(true);
		CountDownLatch closeStarted = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> send = executor.submit(() ->
					client.send(new TdApi.GetAuthorizationState(), Duration.ofSeconds(30)).subscribe(subscriber));
			assertTrue(sendEntered.await(5, TimeUnit.SECONDS));
			Future<?> close = executor.submit(() -> {
				closeStarted.countDown();
				client.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0);
			});
			assertTrue(closeStarted.await(5, TimeUnit.SECONDS));
			assertFalse(close.isDone(), "Close must not overtake an in-flight reactive send");

			releaseSend.countDown();
			send.get(5, TimeUnit.SECONDS);
			close.get(5, TimeUnit.SECONDS);
		} finally {
			releaseSend.countDown();
			executor.shutdownNow();
		}

		assertNull(subscriber.failure.get());
		assertTrue(subscriber.completed);
		assertEquals(1, subscriber.items.size());
		TdApi.Error closeError = assertInstanceOf(TdApi.Error.class, subscriber.items.get(0));
		assertEquals(500, closeError.code);
		assertEquals(0, client.pendingResponseCount());

		int sendsBeforeClosedRequest = nativeSends.get();
		RecordingSubscriber closedSubscriber = new RecordingSubscriber(true);
		client.send(new TdApi.GetAuthorizationState(), Duration.ofSeconds(30)).subscribe(closedSubscriber);
		assertInstanceOf(IllegalStateException.class, closedSubscriber.failure.get());
		assertTrue(closedSubscriber.items.isEmpty());
		assertEquals(sendsBeforeClosedRequest, nativeSends.get());
	}

	@Test
	public void reactiveCancellationRemovesHandlerWithoutRetainingQueryId() {
		InternalClientsState state = new InternalClientsState(() -> 29);
		InternalReactiveClient client = new InternalReactiveClient(state, (clientId, queryId, query) -> {});
		client.createAndRegisterClient();
		RecordingSubscriber subscriber = new RecordingSubscriber(false);
		client.send(new TdApi.GetAuthorizationState(), Duration.ofDays(1)).subscribe(subscriber);

		subscriber.subscription.get().request(1);
		assertEquals(1, client.pendingResponseCount());
		subscriber.subscription.get().cancel();
		assertEquals(0, client.pendingResponseCount());
		assertEquals(0, client.retainedTimeoutCount());

		client.handleEvents(false, new long[] {1}, new TdApi.Object[] {new TdApi.Ok()}, 0, 1);
		assertTrue(subscriber.items.isEmpty());
		assertNull(subscriber.failure.get());
		assertEquals(0, client.retainedTimeoutCount());
		client.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0);
	}

	private static void await(CountDownLatch latch) {
		try {
			assertTrue(latch.await(5, TimeUnit.SECONDS));
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new AssertionError(ex);
		}
	}

	private static final class RecordingSubscriber implements Subscriber<TdApi.Object> {

		private final boolean requestImmediately;
		private final AtomicReference<Subscription> subscription = new AtomicReference<>();
		private final List<TdApi.Object> items = new CopyOnWriteArrayList<>();
		private final AtomicReference<Throwable> failure = new AtomicReference<>();
		private volatile boolean completed;

		private RecordingSubscriber(boolean requestImmediately) {
			this.requestImmediately = requestImmediately;
		}

		@Override
		public void onSubscribe(Subscription subscription) {
			this.subscription.set(subscription);
			if (requestImmediately) {
				subscription.request(1);
			}
		}

		@Override
		public void onNext(TdApi.Object item) {
			items.add(item);
		}

		@Override
		public void onError(Throwable throwable) {
			failure.set(throwable);
		}

		@Override
		public void onComplete() {
			completed = true;
		}
	}
}
