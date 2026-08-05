package it.tdlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.tdlight.jni.TdApi;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class CallbackLifecycleTest {

	@Test
	public void clientCallbackCanRegisterAnotherClientWithoutRegistryDeadlock() throws Exception {
		AtomicInteger nextClientId = new AtomicInteger(40);
		InternalClientsState state = new InternalClientsState(nextClientId::incrementAndGet);
		AtomicInteger registeredFromCallback = new AtomicInteger();
		ClientEventsHandler firstHandler = new ClientEventsHandler() {
			@Override
			public int getClientId() {
				return 41;
			}

			@Override
			public void handleEvents(boolean isClosed,
					long[] eventIds,
					TdApi.Object[] events,
					int arrayOffset,
					int arrayLength) {
				int clientId = state.createAndRegisterClient(new NoOpClientEventsHandler(42));
				registeredFromCallback.set(clientId);
			}
		};
		assertEquals(41, state.createAndRegisterClient(firstHandler));

		ExecutorService executor = Executors.newSingleThreadExecutor();
		try {
			Future<?> dispatch = executor.submit(() -> ClientFactoryImpl.handleClientEvents(state,
					41,
					false,
					new long[] {0},
					new TdApi.Object[] {new TdApi.LogVerbosityLevel(1)},
					0,
					1
			));
			dispatch.get(5, TimeUnit.SECONDS);
		} finally {
			executor.shutdownNow();
		}

		assertEquals(42, registeredFromCallback.get());
		assertNotNull(state.getClientEventsHandler(42));
	}

	@Test
	public void receiverCanBeClosedFromItsOwnCallbackWithoutSelfDeadlock() throws Exception {
		AtomicReference<ResponseReceiver> receiverReference = new AtomicReference<>();
		AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
		AtomicInteger callbacks = new AtomicInteger();
		ResponseReceiver receiver = new ResponseReceiver((clientId,
				isClosed,
				clientEventIds,
				clientEvents,
				arrayOffset,
				arrayLength) -> {
			if (arrayLength > 0 && callbacks.getAndIncrement() == 0) {
				try {
					receiverReference.get().close();
				} catch (Throwable ex) {
					callbackFailure.set(ex);
				}
			}
		}, false, (clientId, eventId, query) -> {}, new AtomicLong(500)::incrementAndGet) {
			private final AtomicInteger receives = new AtomicInteger();

			@Override
			public int receive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout) {
				int receive = receives.getAndIncrement();
				clientIds[0] = 71;
				eventIds[0] = 0;
				if (receive == 0) {
					events[0] = new TdApi.LogVerbosityLevel(1);
					return 1;
				}
				if (receive == 1) {
					events[0] = new TdApi.UpdateAuthorizationState(new TdApi.AuthorizationStateClosed());
					return 1;
				}
				return 0;
			}
		};
		receiverReference.set(receiver);
		receiver.registerClient(71);
		receiver.start();
		try {
			receiver.join(TimeUnit.SECONDS.toMillis(5));
			assertFalse(receiver.isAlive(), "The receiver callback deadlocked while closing its own thread");
			assertNull(callbackFailure.get());
			assertTrue(callbacks.get() >= 2);
		} finally {
			if (receiver.isAlive()) {
				receiver.interrupt();
				receiver.join(TimeUnit.SECONDS.toMillis(5));
			}
			receiver.close();
		}
	}

	@Test
	public void reactiveStartupCallbacksAreDrainedWithoutHoldingCloseLock() {
		InternalReactiveClient client = new InternalReactiveClient(new InternalClientsState(() -> 79),
				(clientId, queryId, query) -> {});
		client.createAndRegisterClient();
		client.handleEvents(false,
				new long[] {0},
				new TdApi.Object[] {new TdApi.LogVerbosityLevel(3)},
				0,
				1
		);

		AtomicBoolean closeStarted = new AtomicBoolean();
		AtomicInteger closedSignals = new AtomicInteger();
		client.setListener(signal -> {
			if (signal.isUpdate() && closeStarted.compareAndSet(false, true)) {
				Thread closeThread = new Thread(
						() -> client.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0),
						"reactive-close-test"
				);
				closeThread.start();
				try {
					closeThread.join(TimeUnit.SECONDS.toMillis(5));
				} catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new AssertionError(ex);
				}
				assertFalse(closeThread.isAlive(), "A startup callback was invoked while closeLock was held");
			} else if (signal.isClosed()) {
				closedSignals.incrementAndGet();
			}
		});

		assertTrue(closeStarted.get());
		assertEquals(1, closedSignals.get());
	}

	@Test
	public void throwingReactiveCloseListenerDoesNotStopOtherClients() throws Exception {
		AtomicInteger nextClientId = new AtomicInteger(100);
		InternalClientsState state = new InternalClientsState(nextClientId::incrementAndGet);
		InternalReactiveClient firstClient = new InternalReactiveClient(state, (clientId, queryId, query) -> {});
		InternalReactiveClient secondClient = new InternalReactiveClient(state, (clientId, queryId, query) -> {});
		firstClient.createAndRegisterClient();
		secondClient.createAndRegisterClient();
		firstClient.setListener(signal -> {
			if (signal.isClosed()) {
				throw new IllegalStateException("listener close failure");
			}
		});
		CountDownLatch secondClientUpdate = new CountDownLatch(1);
		secondClient.setListener(signal -> {
			if (signal.isUpdate()) {
				secondClientUpdate.countDown();
			}
		});

		ResponseReceiver receiver = new ResponseReceiver((clientId,
				isClosed,
				clientEventIds,
				clientEvents,
				arrayOffset,
				arrayLength) -> ClientFactoryImpl.handleClientEvents(state,
				clientId,
				isClosed,
				clientEventIds,
				clientEvents,
				arrayOffset,
				arrayLength
		), true) {
			private final AtomicInteger batches = new AtomicInteger();

			@Override
			public int receive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout) {
				int batch = batches.getAndIncrement();
				if (batch == 0) {
					clientIds[0] = firstClient.getClientId();
					eventIds[0] = 0;
					events[0] = new TdApi.UpdateAuthorizationState(new TdApi.AuthorizationStateClosed());
					clientIds[1] = secondClient.getClientId();
					eventIds[1] = 0;
					events[1] = new TdApi.UpdateAuthorizationState(new TdApi.AuthorizationStateReady());
					return 2;
				}
				if (batch == 1) {
					clientIds[0] = secondClient.getClientId();
					eventIds[0] = 0;
					events[0] = new TdApi.UpdateAuthorizationState(new TdApi.AuthorizationStateClosed());
					return 1;
				}
				return 0;
			}
		};
		receiver.registerClient(firstClient.getClientId());
		receiver.registerClient(secondClient.getClientId());
		receiver.start();
		try {
			assertTrue(secondClientUpdate.await(5, TimeUnit.SECONDS),
					"A throwing close listener terminated the shared receiver");
		} finally {
			receiver.close();
			firstClient.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0);
			secondClient.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0);
		}
	}

	@Test
	public void receiverFailureSendsNativeCloseBeforeRawAndReactiveLocalClosure() throws Exception {
		AtomicInteger nextClientId = new AtomicInteger(120);
		InternalClientsState state = new InternalClientsState(nextClientId::incrementAndGet);
		List<String> lifecycleEvents = new ArrayList<>();
		InternalClient rawClient = new InternalClient(state, null, (clientId, queryId, query) -> {});
		rawClient.initialize((ResultHandler<TdApi.Update>) update -> {
			if (update instanceof TdApi.UpdateAuthorizationState
					&& ((TdApi.UpdateAuthorizationState) update).authorizationState
					.getConstructor() == TdApi.AuthorizationStateClosed.CONSTRUCTOR) {
				lifecycleEvents.add("raw-authorization-closed");
			} else {
				lifecycleEvents.add("raw-unexpected-update");
			}
		}, null, null);
		rawClient.send(new TdApi.GetAuthorizationState(), result -> {
			if (result instanceof TdApi.Error && ((TdApi.Error) result).code == 500) {
				lifecycleEvents.add("raw-pending-500");
			}
		}, null);
		InternalReactiveClient reactiveClient = new InternalReactiveClient(state, (clientId, queryId, query) -> {});
		reactiveClient.createAndRegisterClient();
		reactiveClient.setListener(signal -> {
			if (signal.isUpdate()
					&& signal.getUpdate() instanceof TdApi.UpdateAuthorizationState
					&& ((TdApi.UpdateAuthorizationState) signal.getUpdate()).authorizationState
					.getConstructor() == TdApi.AuthorizationStateClosed.CONSTRUCTOR) {
				lifecycleEvents.add("reactive-authorization-closed");
			} else if (signal.isUpdate()) {
				lifecycleEvents.add("reactive-unexpected-update");
			} else if (signal.isClosed()) {
				lifecycleEvents.add("reactive-closed");
			}
		});
		List<Integer> emergencyClientIds = new ArrayList<>();
		List<Long> emergencyEventIds = new ArrayList<>();
		List<TdApi.Function<?>> emergencyQueries = new ArrayList<>();

		ResponseReceiver receiver = new ResponseReceiver((clientId,
				isClosed,
				clientEventIds,
				clientEvents,
				arrayOffset,
				arrayLength) -> ClientFactoryImpl.handleClientEvents(state,
				clientId,
				isClosed,
				clientEventIds,
				clientEvents,
				arrayOffset,
				arrayLength
		), true, (clientId, eventId, query) -> {
			emergencyClientIds.add(clientId);
			emergencyEventIds.add(eventId);
			emergencyQueries.add(query);
			lifecycleEvents.add("native-close-" + clientId);
		}, new AtomicLong(700)::incrementAndGet) {
			@Override
			public int receive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout) {
				throw new IllegalStateException("synthetic receiver failure");
			}
		};
		receiver.registerClient(rawClient.getClientId());
		receiver.registerClient(reactiveClient.getClientId());
		receiver.start();
		try {
			receiver.join(TimeUnit.SECONDS.toMillis(5));
			assertFalse(receiver.isAlive());
		} finally {
			if (receiver.isAlive()) {
				receiver.interrupt();
				receiver.join(TimeUnit.SECONDS.toMillis(5));
			}
			receiver.close();
		}

		assertEquals(2, emergencyClientIds.size());
		assertTrue(emergencyClientIds.contains(rawClient.getClientId()));
		assertTrue(emergencyClientIds.contains(reactiveClient.getClientId()));
		assertEquals(2, emergencyEventIds.size());
		assertTrue(emergencyEventIds.get(0) != 0);
		assertTrue(emergencyEventIds.get(1) != 0);
		assertTrue(!emergencyEventIds.get(0).equals(emergencyEventIds.get(1)));
		assertEquals(2, emergencyQueries.size());
		assertTrue(emergencyQueries.get(0) instanceof TdApi.Close);
		assertTrue(emergencyQueries.get(1) instanceof TdApi.Close);
		int rawNativeClose = lifecycleEvents.indexOf("native-close-" + rawClient.getClientId());
		int reactiveNativeClose = lifecycleEvents.indexOf("native-close-" + reactiveClient.getClientId());
		assertTrue(rawNativeClose >= 0);
		assertTrue(reactiveNativeClose >= 0);
		assertTrue(rawNativeClose < lifecycleEvents.indexOf("raw-authorization-closed"));
		assertTrue(rawNativeClose < lifecycleEvents.indexOf("reactive-authorization-closed"));
		assertTrue(reactiveNativeClose < lifecycleEvents.indexOf("raw-authorization-closed"));
		assertTrue(reactiveNativeClose < lifecycleEvents.indexOf("reactive-authorization-closed"));
		assertTrue(lifecycleEvents.indexOf("raw-authorization-closed")
				< lifecycleEvents.indexOf("raw-pending-500"));
		assertTrue(lifecycleEvents.indexOf("reactive-authorization-closed")
				< lifecycleEvents.indexOf("reactive-closed"));
		assertFalse(lifecycleEvents.contains("raw-unexpected-update"));
		assertFalse(lifecycleEvents.contains("reactive-unexpected-update"));
		assertNull(state.getClientEventsHandler(rawClient.getClientId()));
		assertNull(state.getClientEventsHandler(reactiveClient.getClientId()));
	}

	@Test
	public void failedEmergencyCloseFallsBackToAsynchronousLocalClosure() throws Exception {
		InternalClientsState state = new InternalClientsState(() -> 123);
		CountDownLatch authorizationClosed = new CountDownLatch(1);
		InternalClient client = new InternalClient(state, null, (clientId, queryId, query) -> {});
		client.initialize((ResultHandler<TdApi.Update>) update -> {
			if (update instanceof TdApi.UpdateAuthorizationState
					&& ((TdApi.UpdateAuthorizationState) update).authorizationState
					.getConstructor() == TdApi.AuthorizationStateClosed.CONSTRUCTOR) {
				authorizationClosed.countDown();
			}
		}, null, null);
		AtomicLong emergencyQueryIds = new AtomicLong(800);
		List<Long> attemptedQueryIds = new ArrayList<>();
		ResponseReceiver receiver = new ResponseReceiver((clientId,
				isClosed,
				clientEventIds,
				clientEvents,
				arrayOffset,
				arrayLength) -> ClientFactoryImpl.handleClientEvents(state,
				clientId,
				isClosed,
				clientEventIds,
				clientEvents,
				arrayOffset,
				arrayLength
		), true, (clientId, eventId, query) -> {
			attemptedQueryIds.add(eventId);
			throw new IllegalStateException("emergency send failed");
		}, emergencyQueryIds::incrementAndGet) {
			@Override
			public int receive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout) {
				return 0;
			}
		};
		receiver.registerClient(client.getClientId());
		receiver.start();
		try {
			receiver.requestClose();
			receiver.awaitClose();
			assertTrue(authorizationClosed.await(5, TimeUnit.SECONDS));
		} finally {
			if (receiver.isAlive()) {
				receiver.interrupt();
				receiver.join(TimeUnit.SECONDS.toMillis(5));
			}
		}

		assertEquals(1, attemptedQueryIds.size());
		assertTrue(attemptedQueryIds.get(0) > 0);
		assertNull(state.getClientEventsHandler(client.getClientId()));
	}

	@Test
	public void emergencyCloseResponseIsConsumedBeforeClientDispatch() throws Exception {
		int clientId = 124;
		AtomicLong emergencyEventId = new AtomicLong();
		CountDownLatch emergencyCloseSent = new CountDownLatch(1);
		List<Long> dispatchedEventIds = new CopyOnWriteArrayList<>();
		List<TdApi.Object> dispatchedEvents = new CopyOnWriteArrayList<>();
		ResponseReceiver receiver = new ResponseReceiver((receivedClientId,
				isClosed,
				clientEventIds,
				clientEvents,
				arrayOffset,
				arrayLength) -> {
			assertEquals(clientId, receivedClientId);
			for (int i = arrayOffset; i < arrayOffset + arrayLength; i++) {
				dispatchedEventIds.add(clientEventIds[i]);
				dispatchedEvents.add(clientEvents[i]);
			}
		}, true, (receivedClientId, eventId, query) -> {
			assertEquals(clientId, receivedClientId);
			assertTrue(query instanceof TdApi.Close);
			emergencyEventId.set(eventId);
			emergencyCloseSent.countDown();
		}, new AtomicLong(900)::incrementAndGet) {
			private final AtomicInteger receiveCount = new AtomicInteger();

			@Override
			public int receive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout) {
				int receive = receiveCount.getAndIncrement();
				if (receive == 0) {
					try {
						if (!emergencyCloseSent.await(5, TimeUnit.SECONDS)) {
							throw new AssertionError("Emergency close was not sent");
						}
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						throw new AssertionError(ex);
					}
					clientIds[0] = clientId;
					eventIds[0] = emergencyEventId.get();
					events[0] = new TdApi.Ok();
					return 1;
				}
				if (receive == 1) {
					clientIds[0] = clientId;
					eventIds[0] = 0;
					events[0] = new TdApi.UpdateAuthorizationState(new TdApi.AuthorizationStateClosed());
					return 1;
				}
				return 0;
			}
		};
		receiver.registerClient(clientId);
		receiver.start();
		try {
			receiver.requestClose();
			receiver.awaitClose();
		} finally {
			if (receiver.isAlive()) {
				receiver.interrupt();
				receiver.join(TimeUnit.SECONDS.toMillis(5));
			}
		}

		assertTrue(emergencyEventId.get() > 0);
		assertEquals(1, dispatchedEventIds.size());
		assertEquals(Long.valueOf(0), dispatchedEventIds.get(0));
		assertEquals(1, dispatchedEvents.size());
		assertTrue(dispatchedEvents.get(0) instanceof TdApi.UpdateAuthorizationState);
	}

	@Test
	public void receiverFailureSerializesLateRegistrationWithTerminalDrain() throws Exception {
		CountDownLatch receiveFailed = new CountDownLatch(1);
		List<Integer> locallyClosedClientIds = new CopyOnWriteArrayList<>();
		ResponseReceiver receiver = new ResponseReceiver((clientId,
				isClosed,
				clientEventIds,
				clientEvents,
				arrayOffset,
				arrayLength) -> {
			if (isClosed) {
				locallyClosedClientIds.add(clientId);
			}
		}, true, (clientId, eventId, query) -> {}, new AtomicLong(1_000)::incrementAndGet) {
			@Override
			public int receive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout) {
				receiveFailed.countDown();
				throw new IllegalStateException("synthetic receiver failure");
			}
		};
		receiver.registerClient(125);
		Field registeredClientsLockField = ResponseReceiver.class.getDeclaredField("registeredClientsLock");
		registeredClientsLockField.setAccessible(true);
		Object registeredClientsLock = registeredClientsLockField.get(receiver);

		try {
			synchronized (registeredClientsLock) {
				receiver.start();
				assertTrue(receiveFailed.await(5, TimeUnit.SECONDS));
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				while (receiver.isAlive()
						&& receiver.getState() != Thread.State.BLOCKED
						&& System.nanoTime() < deadline) {
					Thread.yield();
				}
				assertEquals(Thread.State.BLOCKED, receiver.getState(),
						"Terminal shutdown must acquire the registration lock before closing registration");
				receiver.registerClient(126);
			}
			receiver.join(TimeUnit.SECONDS.toMillis(5));
			assertFalse(receiver.isAlive());
		} finally {
			if (receiver.isAlive()) {
				receiver.interrupt();
				receiver.join(TimeUnit.SECONDS.toMillis(5));
			}
			receiver.close();
		}

		assertTrue(locallyClosedClientIds.contains(125));
		assertTrue(locallyClosedClientIds.contains(126));
		assertEquals(2, locallyClosedClientIds.size());
		assertThrows(IllegalStateException.class, () -> receiver.registerClient(127));
	}

	private static final class NoOpClientEventsHandler implements ClientEventsHandler {

		private final int clientId;

		private NoOpClientEventsHandler(int clientId) {
			this.clientId = clientId;
		}

		@Override
		public int getClientId() {
			return clientId;
		}

		@Override
		public void handleEvents(boolean isClosed,
				long[] eventIds,
				TdApi.Object[] events,
				int arrayOffset,
				int arrayLength) {
		}
	}
}
