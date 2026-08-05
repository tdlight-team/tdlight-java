package it.tdlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.tdlight.jni.TdApi;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public class EventOrderTest {

	@Test
	public void batchedUpdateHandlerRetainsNativeOrder() {
		InternalClient client = new InternalClient(new InternalClientsState(() -> 81), null, (clientId, queryId, query) -> {});
		List<TdApi.Object> observed = new ArrayList<>();
		client.initialize((UpdatesHandler) observed::addAll, null, null);

		TdApi.Object first = new TdApi.LogVerbosityLevel(1);
		TdApi.Object second = new TdApi.LogVerbosityLevel(2);
		TdApi.Object third = new TdApi.LogVerbosityLevel(3);
		client.handleEvents(false,
				new long[] {99, 0, 0, 0, 100},
				new TdApi.Object[] {new TdApi.Ok(), first, second, third, new TdApi.Ok()},
				1,
				3
		);

		assertEquals(Arrays.asList(first, second, third), observed);
		client.handleEvents(true, new long[0], new TdApi.Object[0], 0, 0);
	}

	@Test
	public void optimizedDispatcherRetainsPerClientNativeOrder() throws Exception {
		assertDispatcherOrder(true);
	}

	@Test
	public void referenceDispatcherRetainsPerClientNativeOrder() throws Exception {
		assertDispatcherOrder(false);
	}

	private static void assertDispatcherOrder(boolean optimized) throws Exception {
		List<Integer> sourceClientIds = new ArrayList<>();
		List<Long> sourceEventIds = new ArrayList<>();
		List<TdApi.Object> sourceEvents = new ArrayList<>();
		Map<Integer, List<Long>> expected = new ConcurrentHashMap<>();
		expected.put(101, new ArrayList<>());
		expected.put(202, new ArrayList<>());
		for (int i = 1; i <= 40; i++) {
			int clientId = i % 3 == 0 ? 202 : 101;
			long eventId = i;
			sourceClientIds.add(clientId);
			sourceEventIds.add(eventId);
			sourceEvents.add(new TdApi.LogVerbosityLevel(i));
			expected.get(clientId).add(eventId);
		}
		for (int clientId : new int[] {101, 202}) {
			sourceClientIds.add(clientId);
			sourceEventIds.add(0L);
			sourceEvents.add(new TdApi.UpdateAuthorizationState(new TdApi.AuthorizationStateClosed()));
			expected.get(clientId).add(0L);
		}

		Map<Integer, List<Long>> actual = new ConcurrentHashMap<>();
		CountDownLatch deliveries = new CountDownLatch(2);
		ResponseReceiver receiver = new ResponseReceiver((clientId,
				isClosed,
				clientEventIds,
				clientEvents,
				arrayOffset,
				arrayLength) -> {
			if (arrayLength > 0) {
				List<Long> ids = new ArrayList<>(arrayLength);
				for (int i = arrayOffset; i < arrayOffset + arrayLength; i++) {
					ids.add(clientEventIds[i]);
				}
				actual.put(clientId, ids);
				deliveries.countDown();
			}
		}, optimized, (clientId, eventId, query) -> {}, new AtomicLong(900)::incrementAndGet) {
			private boolean delivered;

			@Override
			public int receive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout) {
				if (delivered) {
					return 0;
				}
				delivered = true;
				for (int i = 0; i < sourceEvents.size(); i++) {
					clientIds[i] = sourceClientIds.get(i);
					eventIds[i] = sourceEventIds.get(i);
					events[i] = sourceEvents.get(i);
				}
				return sourceEvents.size();
			}
		};
		receiver.registerClient(101);
		receiver.registerClient(202);
		receiver.start();
		try {
			assertTrue(deliveries.await(5, TimeUnit.SECONDS));
			receiver.close();
			receiver.join(TimeUnit.SECONDS.toMillis(5));
			assertFalse(receiver.isAlive());
		} finally {
			if (receiver.isAlive()) {
				receiver.interrupt();
				receiver.join(TimeUnit.SECONDS.toMillis(5));
			}
		}

		assertEquals(expected.get(101), actual.get(101));
		assertEquals(expected.get(202), actual.get(202));
	}
}
