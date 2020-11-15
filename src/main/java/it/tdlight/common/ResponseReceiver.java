package it.tdlight.common;

import it.tdlight.common.utils.IntSwapper;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Object;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

public class ResponseReceiver extends Thread implements AutoCloseable {

	private static final boolean USE_OPTIMIZED_DISPATCHER = Boolean.parseBoolean(System.getProperty(
			"tdlight.dispatcher.use_optimized_dispatcher",
			"true"
	));
	private static final int MAX_EVENTS = 1000;
	private static final int[] originalSortingSource = new int[MAX_EVENTS];
	static {
		for (int i = 0; i < originalSortingSource.length; i++) {
			originalSortingSource[i] = i;
		}
	}

	private final EventsHandler eventsHandler;
	private final int[] clientIds = new int[MAX_EVENTS];
	private final long[] eventIds = new long[MAX_EVENTS];
	private final TdApi.Object[] events = new TdApi.Object[MAX_EVENTS];

	private final CountDownLatch closeWait = new CountDownLatch(1);
	private final Set<Integer> registeredClients = new ConcurrentSkipListSet<>();
	private volatile boolean closeRequested = false;


	public ResponseReceiver(EventsHandler eventsHandler) {
		super("TDLib thread");
		this.eventsHandler = eventsHandler;

		this.setDaemon(true);

		this.start();
	}

	@SuppressWarnings({"UnnecessaryLocalVariable"})
	@Override
	public void run() {
		int[] sortIndex;
		try {
			while(!closeRequested || !registeredClients.isEmpty()) {
				int resultsCount = NativeClientAccess.receive(clientIds, eventIds, events, 2.0 /*seconds*/);

				if (resultsCount <= 0)
					continue;

				Set<Integer> closedClients = new HashSet<>();

				if (USE_OPTIMIZED_DISPATCHER) {
					// Generate a list of indices sorted by client id, from 0 to resultsCount
					sortIndex = generateSortIndex(0, resultsCount, clientIds);

					int lastClientId = clientIds[sortIndex[0]];
					int lastClientIdEventsCount = 0;
					boolean lastClientClosed = false;

					for (int i = 0; i <= resultsCount; i++) {
						if (i == resultsCount || (i != 0 && clientIds[sortIndex[i]] != lastClientId)) {
							if (lastClientIdEventsCount > 0) {
								int clientId = lastClientId;
								long[] clientEventIds = new long[lastClientIdEventsCount];
								TdApi.Object[] clientEvents = new TdApi.Object[lastClientIdEventsCount];
								for (int j = 0; j < lastClientIdEventsCount; j++) {
									clientEventIds[j] = eventIds[sortIndex[i - lastClientIdEventsCount + j]];
									clientEvents[j] = events[sortIndex[i - lastClientIdEventsCount + j]];

									if (clientEventIds[j] == 0 && clientEvents[j] instanceof TdApi.UpdateAuthorizationState) {
										TdApi.AuthorizationState authorizationState = ((TdApi.UpdateAuthorizationState) clientEvents[j]).authorizationState;
										if (authorizationState instanceof TdApi.AuthorizationStateClosed) {
											lastClientClosed = true;
											closedClients.add(clientId);
										}
									}
								}

								eventsHandler.handleClientEvents(clientId, lastClientClosed, clientEventIds, clientEvents);
							}

							if (i < resultsCount) {
								lastClientId = clientIds[sortIndex[i]];
								lastClientIdEventsCount = 0;
								lastClientClosed = false;
							}
						}

						if (i < resultsCount) {
							lastClientIdEventsCount++;
						}
					}
				} else {
					class Event {

						public final int clientId;
						public final long eventId;
						public final Object event;

						public Event(int clientId, long eventId, Object event) {
							this.clientId = clientId;
							this.eventId = eventId;
							this.event = event;
						}
					}

					List<Event> eventsList = new ArrayList<>(resultsCount);
					for (int i = 0; i < resultsCount; i++) {
						eventsList.add(new Event(clientIds[i], eventIds[i], events[i]));
					}
					Set<Integer> clientIds = eventsList.stream().map(e -> e.clientId).collect(Collectors.toSet());
					for (int clientId : clientIds) {
						List<Event> clientEventsList = eventsList.stream().filter(e -> e.clientId == clientId).collect(Collectors.toList());
						long[] clientEventIds = new long[clientEventsList.size()];
						Object[] clientEvents = new Object[clientEventsList.size()];
						boolean closed = false;
						for (int i = 0; i < clientEventsList.size(); i++) {
							Event e = clientEventsList.get(i);
							clientEventIds[i] = e.eventId;
							clientEvents[i] = e.event;

							if (e.eventId == 0 && e.event instanceof TdApi.UpdateAuthorizationState) {
								TdApi.AuthorizationState authorizationState = ((TdApi.UpdateAuthorizationState) e.event).authorizationState;
								if (authorizationState instanceof TdApi.AuthorizationStateClosed) {
									closed = true;
									closedClients.add(clientId);
								}
							}
						}
						eventsHandler.handleClientEvents(clientId, closed, clientEventIds, clientEvents);
					}
				}

				Arrays.fill(clientIds, 0);
				Arrays.fill(eventIds, 0);
				Arrays.fill(events, null);

				if (!closedClients.isEmpty()) {
					this.registeredClients.addAll(closedClients);
				}
			}
		} finally {
			this.closeWait.countDown();
		}
	}

	@SuppressWarnings("SameParameterValue")
	private int[] generateSortIndex(int from, int to, int[] data) {
		int[] sortedIndices = Arrays.copyOfRange(originalSortingSource, from, to);
		it.unimi.dsi.fastutil.Arrays.mergeSort(from, to, (o1, o2) -> {
			return Integer.compare(data[sortedIndices[o1]], data[sortedIndices[o2]]);
		}, new IntSwapper(sortedIndices));
		return sortedIndices;
	}

	public void registerClient(int clientId) {
		registeredClients.add(clientId);
	}

	@Override
	public void close() throws InterruptedException {
		this.closeRequested = true;
		this.closeWait.await();
	}
}
