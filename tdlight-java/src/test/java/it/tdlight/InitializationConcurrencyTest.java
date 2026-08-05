package it.tdlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import it.tdlight.jni.TdApi;
import it.tdlight.util.UnsupportedNativeLibraryException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class InitializationConcurrencyTest {

	@Test
	public void concurrentCallersPublishOnlyCompletedInitialization() throws Exception {
		Init.Initializer initializer = new Init.Initializer();
		AtomicInteger actions = new AtomicInteger();
		CountDownLatch actionEntered = new CountDownLatch(1);
		CountDownLatch releaseAction = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<?> first = executor.submit(() -> {
				initializer.initialize(() -> {
					actions.incrementAndGet();
					actionEntered.countDown();
					assertTrue(releaseAction.await(5, TimeUnit.SECONDS));
				});
				return null;
			});
			assertTrue(actionEntered.await(5, TimeUnit.SECONDS));
			Future<?> second = executor.submit(() -> {
				initializer.initialize(() -> fail("Initialization ran twice"));
				return null;
			});

			releaseAction.countDown();
			first.get(5, TimeUnit.SECONDS);
			second.get(5, TimeUnit.SECONDS);
			assertEquals(1, actions.get());
		} finally {
			releaseAction.countDown();
			executor.shutdownNow();
		}
	}

	@Test
	public void failedAttemptIsPropagatedAndCanBeRetried() throws Exception {
		Init.Initializer initializer = new Init.Initializer();
		AtomicInteger actions = new AtomicInteger();
		IllegalStateException failure = new IllegalStateException("transient failure");

		IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> initializer.initialize(() -> {
			actions.incrementAndGet();
			throw failure;
		}));
		assertSame(failure, thrown);

		initializer.initialize(actions::incrementAndGet);
		initializer.initialize(() -> fail("Successful initialization must be idempotent"));
		assertEquals(2, actions.get());
	}

	@Test
	public void recursiveFailureDoesNotPoisonLaterInitialization() throws Exception {
		Init.Initializer initializer = new Init.Initializer();
		assertThrows(IllegalStateException.class,
				() -> initializer.initialize(() -> initializer.initialize(() -> fail("Recursive action ran"))));

		AtomicInteger actions = new AtomicInteger();
		initializer.initialize(actions::incrementAndGet);
		assertEquals(1, actions.get());
	}

	@Test
	public void clientIsInitializedBeforeItBecomesVisibleToUpdates() {
		AtomicReference<InternalClient> clientReference = new AtomicReference<>();
		AtomicReference<TdApi.Object> sendResult = new AtomicReference<>();
		AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
		AtomicInteger authorizationStateSends = new AtomicInteger();
		InternalClientsState state = new InternalClientsState(() -> 91) {
			@Override
			protected void onClientRegistered(int clientId) {
				ClientEventsHandler publishedClient = getClientEventsHandler(clientId);
				publishedClient.handleEvents(false,
						new long[] {0},
						new TdApi.Object[] {
								new TdApi.UpdateAuthorizationState(new TdApi.AuthorizationStateReady())
						},
						0,
						1
				);
			}
		};
		InternalClient client = new InternalClient(state, null, (clientId, queryId, query) -> {
			if (query.getConstructor() == TdApi.GetAuthorizationState.CONSTRUCTOR) {
				authorizationStateSends.incrementAndGet();
				clientReference.get().handleEvents(false,
						new long[] {queryId},
						new TdApi.Object[] {new TdApi.Ok()},
						0,
						1
				);
			}
		});
		clientReference.set(client);

		client.initialize((ResultHandler<TdApi.Update>) update -> client.send(new TdApi.GetAuthorizationState(),
				sendResult::set,
				callbackFailure::set
		), callbackFailure::set, callbackFailure::set);

		assertEquals(1, authorizationStateSends.get());
		assertTrue(sendResult.get() instanceof TdApi.Ok, "The immediate update observed an uninitialized client");
		assertNull(callbackFailure.get());
		client.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0);
	}

	@Test
	public void startupRequestFailureRollsBackPublishedClientAndPreservesCloseFailure() {
		IllegalStateException startupFailure = new IllegalStateException("startup request failed");
		IllegalStateException closeFailure = new IllegalStateException("close request failed");
		InternalClientsState state = new InternalClientsState(() -> 92);
		AtomicInteger closeAttempts = new AtomicInteger();
		AtomicInteger closeCallbacks = new AtomicInteger();
		InternalClient client = new InternalClient(state, null, (clientId, queryId, query) -> {
			if (query.getConstructor() == TdApi.GetOption.CONSTRUCTOR) {
				throw startupFailure;
			}
			if (query.getConstructor() == TdApi.Close.CONSTRUCTOR) {
				closeAttempts.incrementAndGet();
				throw closeFailure;
			}
		}, closeCallbacks::incrementAndGet);

		IllegalStateException thrown = assertThrows(IllegalStateException.class,
				() -> client.initialize((ResultHandler<TdApi.Update>) update -> {}, null, null));

		assertSame(startupFailure, thrown);
		assertEquals(1, thrown.getSuppressed().length);
		assertSame(closeFailure, thrown.getSuppressed()[0]);
		assertEquals(1, closeAttempts.get());
		assertEquals(1, closeCallbacks.get());
		assertNull(state.getClientEventsHandler(client.getClientId()));
		AtomicReference<TdApi.Object> afterFailure = new AtomicReference<>();
		client.send(new TdApi.GetAuthorizationState(), afterFailure::set, null);
		assertTrue(afterFailure.get() instanceof TdApi.Error);
		assertEquals(503, ((TdApi.Error) afterFailure.get()).code);
	}

	@Test
	public void constructorDetectorPropagatesInitializationFailuresTruthfully() {
		IllegalStateException runtimeFailure = new IllegalStateException("runtime failure");
		AssertionError errorFailure = new AssertionError("error failure");
		UnsupportedNativeLibraryException nativeFailure = new UnsupportedNativeLibraryException("native failure");

		assertSame(runtimeFailure,
				assertThrows(IllegalStateException.class, () -> ConstructorDetector.tryInit(() -> {
					throw runtimeFailure;
				})));
		assertSame(errorFailure,
				assertThrows(AssertionError.class, () -> ConstructorDetector.tryInit(() -> {
					throw errorFailure;
				})));
		IllegalStateException wrapped = assertThrows(IllegalStateException.class,
				() -> ConstructorDetector.tryInit(() -> {
					throw nativeFailure;
				}));
		assertSame(nativeFailure, wrapped.getCause());
	}
}
