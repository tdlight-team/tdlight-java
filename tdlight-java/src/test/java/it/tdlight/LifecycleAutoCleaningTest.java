package it.tdlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.tdlight.jni.TdApi;
import it.tdlight.util.CleanSupport.CleanableSupport;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

public class LifecycleAutoCleaningTest {

	@Test
	public void cleanupCallbacksDoNotRetainTheClientWrapper() throws Exception {
		AtomicInteger closeRequests = new AtomicInteger();
		AutoCleaningTelegramClient wrapper = new AutoCleaningTelegramClient(new InternalClientsState(() -> 111),
				(clientId, queryId, query) -> {
					if (query.getConstructor() == TdApi.Close.CONSTRUCTOR) {
						closeRequests.incrementAndGet();
					}
				});
		InternalClient internalClient = (InternalClient) getField(wrapper, "client");
		Object registrationCallback = getField(internalClient, "clientRegistrationEventHandler");
		Object closedCallback = getField(internalClient, "clientClosedHandler");
		Object cleanable = getField(wrapper, "cleanable");

		Class<?> cleanupStateClass = Class.forName(AutoCleaningTelegramClient.class.getName() + "$CleanupState");
		assertTrue(Modifier.isStatic(cleanupStateClass.getModifiers()));
		assertFalse(retainsIdentity(registrationCallback, wrapper, new IdentityHashMap<>(), 4));
		assertFalse(retainsIdentity(closedCallback, wrapper, new IdentityHashMap<>(), 4));
		assertFalse(retainsIdentity(cleanable, wrapper, new IdentityHashMap<>(), 4));

		wrapper.initialize((ResultHandler<TdApi.Update>) update -> {}, null, null);
		((CleanableSupport) cleanable).clean();
		((CleanableSupport) cleanable).clean();
		assertEquals(1, closeRequests.get(), "Automatic cleanup must send close exactly once");
		internalClient.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0);
	}

	@Test
	public void shutdownHookRetriesCleanerSendFailureWithoutDuplicatingSuccess() throws Exception {
		AtomicInteger closeAttempts = new AtomicInteger();
		IllegalStateException firstFailure = new IllegalStateException("first close send failed");
		AutoCleaningTelegramClient wrapper = new AutoCleaningTelegramClient(new InternalClientsState(() -> 112),
				(clientId, queryId, query) -> {
					if (query.getConstructor() == TdApi.Close.CONSTRUCTOR
							&& closeAttempts.incrementAndGet() == 1) {
						throw firstFailure;
					}
				});
		InternalClient internalClient = (InternalClient) getField(wrapper, "client");
		Object registrationCallback = getField(internalClient, "clientRegistrationEventHandler");
		Class<?> cleanupStateClass = Class.forName(AutoCleaningTelegramClient.class.getName() + "$CleanupState");
		Object cleanupState = findCapturedInstance(registrationCallback, cleanupStateClass);
		CleanableSupport cleanable = (CleanableSupport) getField(wrapper, "cleanable");
		wrapper.initialize((ResultHandler<TdApi.Update>) update -> {}, null, null);

		assertTrue(cleanupState != null);
		assertTrue(assertThrows(IllegalStateException.class, cleanable::clean) == firstFailure);
		Thread shutdownHook = (Thread) getField(cleanupState, "shutdownHook");
		shutdownHook.run();
		shutdownHook.run();

		assertEquals(2, closeAttempts.get(), "A failed close send must be retried exactly once");
		internalClient.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0);
	}

	@Test
	public void uninitializedReactiveClientIsNotRootedByAShutdownHook() throws Exception {
		InternalReactiveClient client = new InternalReactiveClient(new InternalClientsState(() -> 113),
				(clientId, queryId, query) -> {});

		assertNull(getField(client, "shutdownHook"));
		client.createAndRegisterClient();
		assertTrue(getField(client, "shutdownHook") instanceof Thread);
		client.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0);
		assertNull(getField(client, "shutdownHook"));
	}

	@Test
	public void failedReactiveRegistrationRemovesItsShutdownHook() throws Exception {
		IllegalStateException registrationFailure = new IllegalStateException("registration failed");
		InternalClientsState state = new InternalClientsState(() -> 114) {
			@Override
			protected void onClientRegistered(int clientId) {
				throw registrationFailure;
			}
		};
		InternalReactiveClient client = new InternalReactiveClient(state, (clientId, queryId, query) -> {});

		assertSame(registrationFailure,
				assertThrows(IllegalStateException.class, client::createAndRegisterClient));
		assertNull(getField(client, "shutdownHook"));
	}

	@Test
	public void liveClientWrappersAnchorFactoryWithoutReceiverOrCleanerBackReferences() throws Exception {
		ClientFactoryImpl factory = new ClientFactoryImpl(false);
		TelegramClient client = factory.createClient();
		ReactiveTelegramClient reactiveClient = factory.createReactive();
		Object lifecycle = getField(factory, "lifecycle");
		Object state = getField(lifecycle, "state");
		Object receiver = getField(lifecycle, "responseReceiver");
		Object factoryCleanable = getField(factory, "cleanable");

		assertTrue(retainsIdentity(client, factory, new IdentityHashMap<>(), 8));
		assertTrue(retainsIdentity(reactiveClient, factory, new IdentityHashMap<>(), 8));
		assertFalse(retainsIdentity(state, factory, new IdentityHashMap<>(), 8));
		assertFalse(retainsIdentity(receiver, factory, new IdentityHashMap<>(), 8));
		assertFalse(retainsIdentity(factoryCleanable, factory, new IdentityHashMap<>(), 8));

		factory.close();
	}

	@Test
	public void standardClientCloseReleasesItsFactoryLease() throws Exception {
		ClientFactoryImpl factory = new ClientFactoryImpl(false);
		ClientFactoryImpl.FactoryLease ownerLease = new ClientFactoryImpl.FactoryLease(factory);
		AutoCleaningTelegramClient client = new AutoCleaningTelegramClient(new InternalClientsState(() -> 115),
				(clientId, queryId, query) -> {},
				ownerLease);
		InternalClient internalClient = (InternalClient) getField(client, "client");
		assertTrue(retainsIdentity(client, factory, new IdentityHashMap<>(), 6));

		client.initialize((ResultHandler<TdApi.Update>) update -> {}, null, null);
		internalClient.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0);

		assertFalse(retainsIdentity(client, factory, new IdentityHashMap<>(), 6));
		factory.close();
	}

	@Test
	public void reactivePublisherAndOutstandingSubscriptionRetainFactoryLeaseUntilCancel() throws Exception {
		ClientFactoryImpl factory = new ClientFactoryImpl(false);
		ReactiveTelegramClient client = factory.createReactive();
		Publisher<TdApi.Object> publisher = client.send(new TdApi.GetAuthorizationState(), Duration.ofSeconds(30));

		assertTrue(retainsIdentity(publisher, factory, new IdentityHashMap<>(), 8),
				"A cold publisher must retain the creating factory");

		AtomicReference<Subscription> subscriptionReference = new AtomicReference<>();
		publisher.subscribe(new Subscriber<TdApi.Object>() {
			@Override
			public void onSubscribe(Subscription subscription) {
				subscriptionReference.set(subscription);
			}

			@Override
			public void onNext(TdApi.Object item) {
			}

			@Override
			public void onError(Throwable throwable) {
			}

			@Override
			public void onComplete() {
			}
		});
		Subscription subscription = subscriptionReference.get();
		assertTrue(subscription != null);
		assertTrue(retainsIdentity(subscription, factory, new IdentityHashMap<>(), 8),
				"An outstanding subscription must retain the creating factory");

		subscription.cancel();

		assertFalse(retainsIdentity(subscription, factory, new IdentityHashMap<>(), 8),
				"Cancellation must release the operation's factory lease");

		AtomicReference<Subscription> terminalSubscriptionReference = new AtomicReference<>();
		AtomicReference<Throwable> terminalFailure = new AtomicReference<>();
		publisher.subscribe(new Subscriber<TdApi.Object>() {
			@Override
			public void onSubscribe(Subscription terminalSubscription) {
				terminalSubscriptionReference.set(terminalSubscription);
				terminalSubscription.request(1);
			}

			@Override
			public void onNext(TdApi.Object item) {
			}

			@Override
			public void onError(Throwable throwable) {
				terminalFailure.set(throwable);
			}

			@Override
			public void onComplete() {
			}
		});
		Subscription terminalSubscription = terminalSubscriptionReference.get();
		assertTrue(terminalFailure.get() instanceof IllegalStateException);
		assertFalse(retainsIdentity(terminalSubscription, factory, new IdentityHashMap<>(), 8),
				"A terminal signal must release the operation's factory lease");
		factory.close();
	}

	private static Object getField(Object instance, String name) throws ReflectiveOperationException {
		Field field = instance.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(instance);
	}

	private static boolean retainsIdentity(Object candidate,
			Object target,
			IdentityHashMap<Object, Boolean> visited,
			int remainingDepth) throws IllegalAccessException {
		if (candidate == target) {
			return true;
		}
		if (candidate == null || remainingDepth == 0 || visited.put(candidate, Boolean.TRUE) != null) {
			return false;
		}
		if (!candidate.getClass().getName().startsWith("it.tdlight.")) {
			return false;
		}
		for (Class<?> type = candidate.getClass();
				type != null && type.getName().startsWith("it.tdlight.");
				type = type.getSuperclass()) {
			for (Field field : type.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) && !field.getType().isPrimitive()) {
					field.setAccessible(true);
					if (retainsIdentity(field.get(candidate), target, visited, remainingDepth - 1)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private static Object findCapturedInstance(Object callback, Class<?> expectedClass) throws IllegalAccessException {
		for (Field field : callback.getClass().getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers())) {
				field.setAccessible(true);
				Object value = field.get(callback);
				if (expectedClass.isInstance(value)) {
					return value;
				}
			}
		}
		return null;
	}
}
