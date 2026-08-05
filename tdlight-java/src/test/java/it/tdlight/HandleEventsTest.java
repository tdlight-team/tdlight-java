package it.tdlight;

import static org.junit.jupiter.api.Assertions.*;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Object;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;

public class HandleEventsTest {

	@Test
	public void test() throws InterruptedException {
		List<Event> initialEvents = new ArrayList<>();
		initialEvents.add(new Event(2, 0, new TdApi.LogVerbosityLevel(0)));
		initialEvents.add(new Event(1, 1, new TdApi.LogVerbosityLevel(1)));
		initialEvents.add(new Event(7, 3, new TdApi.LogVerbosityLevel(2)));
		initialEvents.add(new Event(7, 0, new TdApi.LogVerbosityLevel(3)));
		initialEvents.add(new Event(1, 7, new TdApi.LogVerbosityLevel(4)));
		initialEvents.add(new Event(2, 398, new TdApi.LogVerbosityLevel(5)));
		initialEvents.add(new Event(7, 98832, new TdApi.LogVerbosityLevel(6)));
		initialEvents.add(new Event(2, 32832, new TdApi.LogVerbosityLevel(7)));
		initialEvents.add(new Event(1, 39484, new TdApi.LogVerbosityLevel(8)));
		initialEvents.add(new Event(1, 39485, new TdApi.LogVerbosityLevel(9)));
		initialEvents.add(new Event(1, 39486, new TdApi.LogVerbosityLevel(10)));
		initialEvents.add(new Event(1, 39487, new TdApi.LogVerbosityLevel(11)));
		initialEvents.add(new Event(1, 39488, new TdApi.LogVerbosityLevel(12)));
		CountDownLatch eventsQueueEmptied = new CountDownLatch(1);
		ArrayBlockingQueue<Event> eventsQueue = new ArrayBlockingQueue<>(1024);
		eventsQueue.addAll(initialEvents);
		ArrayBlockingQueue<Result> results = new ArrayBlockingQueue<>(1024);
		ResponseReceiver responseReceiver = new ResponseReceiver((clientId, isClosed, clientEventIds, clientEvents, arrayOffset, arrayLength) -> {
			results.add(new Result(clientId,
					isClosed,
					Arrays.copyOf(clientEventIds, clientEventIds.length),
					Arrays.copyOf(clientEvents, clientEvents.length),
					arrayOffset,
					arrayLength
			));
		}, false) {
			@Override
			public int receive(int[] clientIds, long[] eventIds, Object[] events, double timeout) {
				int i = 0;
				while (!eventsQueue.isEmpty() && i < clientIds.length) {
					Event event = eventsQueue.poll();
					clientIds[i] = event.clientId();
					eventIds[i] = event.eventId();
					events[i] = event.event();
					i++;
				}
				if (eventsQueue.isEmpty()) {
					eventsQueueEmptied.countDown();
				}
				return i;
			}
		};
		responseReceiver.registerClient(2);
		responseReceiver.registerClient(1);
		responseReceiver.registerClient(7);
		responseReceiver.start();
		eventsQueueEmptied.await();
		responseReceiver.interrupt();
		responseReceiver.close();
		HashMap<Integer, Result> resultsMap = new HashMap<>();
		while (!results.isEmpty()) {
			Result part = results.poll();
			if (part.arrayLength() > 0) {
				Result prev = resultsMap.get(part.clientId());
				if (prev == null) {
					prev = new Result(part.clientId(), false, new long[0], new TdApi.Object[0], 0, 0);
				}
				int newSize = part.arrayLength() + prev.arrayLength();
				long[] newIds = new long[newSize];
				TdApi.Object[] newEvents = new TdApi.Object[newSize];
				int i = 0;
				for (int i1 = 0; i1 < prev.arrayLength(); i1++, i++) {
					newIds[i] = prev.clientEventIds()[i1 + prev.arrayOffset()];
					newEvents[i] = prev.clientEvents()[i1 + prev.arrayOffset()];
				}
				for (int i1 = 0; i1 < part.arrayLength(); i1++, i++) {
					newIds[i] = part.clientEventIds()[i1 + part.arrayOffset()];
					newEvents[i] = part.clientEvents()[i1 + part.arrayOffset()];
				}
				resultsMap.put(part.clientId(), new Result(part.clientId(), part.isClosed() || prev.isClosed(), newIds, newEvents, 0, newSize));
			}
		}
		Result client2Results = resultsMap.remove(2);
		Result client1Results = resultsMap.remove(1);
		Result client7Results = resultsMap.remove(7);
		assertTrue(resultsMap.isEmpty());
		assertEquals(0, results.size());

		assertEquals(2, client2Results.clientId());
		assertEquals(1, client1Results.clientId());
		assertEquals(7, client7Results.clientId());

		assertClientEvents(initialEvents, client2Results);
		assertClientEvents(initialEvents, client1Results);
		assertClientEvents(initialEvents, client7Results);

		assertEquals(initialEvents.size() + 3,
				client2Results.arrayLength() + client1Results.arrayLength() + client7Results.arrayLength());

		assertEquals(4, client2Results.arrayLength());
		assertEquals(8, client1Results.arrayLength());
		assertEquals(4, client7Results.arrayLength());
	}

	private void assertClientEvents(List<Event> initialEvents, Result actual) {
		List<Event> expected = new ArrayList<>();
		for (Event event : initialEvents) {
			if (event.clientId() == actual.clientId()) {
				expected.add(event);
			}
		}

		assertTrue(actual.isClosed());
		assertEquals(expected.size() + 1, actual.arrayLength());
		for (int i = 0; i < expected.size(); i++) {
			Event expectedEvent = expected.get(i);
			int actualIndex = actual.arrayOffset() + i;
			assertEquals(expectedEvent.eventId(), actual.clientEventIds()[actualIndex]);
			assertSame(expectedEvent.event(), actual.clientEvents()[actualIndex]);
		}
		int closeIndex = actual.arrayOffset() + expected.size();
		assertEquals(0, actual.clientEventIds()[closeIndex]);
		assertTrue(actual.clientEvents()[closeIndex] instanceof TdApi.UpdateAuthorizationState);
		TdApi.UpdateAuthorizationState closeUpdate = (TdApi.UpdateAuthorizationState) actual.clientEvents()[closeIndex];
		assertTrue(closeUpdate.authorizationState instanceof TdApi.AuthorizationStateClosed);
	}
}
