package it.tdlight;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import it.tdlight.util.SimpleIntQueue;
import it.tdlight.util.IntSwapper;
import it.tdlight.util.SpinWaitSupport;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.CountDownLatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

abstract class ResponseReceiver extends Thread implements AutoCloseable {

	private static final Logger LOG = LoggerFactory.getLogger(ResponseReceiver.class);
	private static final String FLAG_USE_OPTIMIZED_DISPATCHER = "tdlight.dispatcher.use_optimized_dispatcher";
	private static final boolean USE_OPTIMIZED_DISPATCHER
			= Boolean.parseBoolean(System.getProperty(FLAG_USE_OPTIMIZED_DISPATCHER, "true"));
	private static final int MAX_EVENTS = 100;
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
	private int eventsLastUsedLength = 0;

	private final long[] clientEventIds = new long[MAX_EVENTS];
	private final TdApi.Object[] clientEvents = new TdApi.Object[MAX_EVENTS];
	private int clientEventsLastUsedLength = 0;

	private final CountDownLatch closeWait = new CountDownLatch(1);
	private final Object registeredClientsLock = new Object();
	// Do not modify the int[] directly, this should be replaced
	private volatile int[] registeredClients = new int[0];
	private volatile boolean closeRequested;


	public ResponseReceiver(EventsHandler eventsHandler) {
		super("TDLib thread");
		this.eventsHandler = eventsHandler;
		this.setDaemon(true);
	}

	/**
	 * @return results count
	 */
	public abstract int receive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout);

	@Override
	public void run() {
		LOG.debug("ResponseReceiver is now running");
		int[] sortIndex;
		final SimpleIntQueue closedClients = new SimpleIntQueue();
		try {
			boolean interrupted;
			while (!(interrupted = Thread.interrupted()) && (!closeRequested || registeredClients.length > 0)) {
				// Timeout is expressed in seconds
				int resultsCount = receive(clientIds, eventIds, events, 2.0);
				LOG.trace("Received {} events", resultsCount);

				if (resultsCount <= 0) {
					SpinWaitSupport.onSpinWait();
					continue;
				}

				closedClients.reset();

				if (USE_OPTIMIZED_DISPATCHER) {
					// Generate a list of indices sorted by client id, from 0 to resultsCount
					sortIndex = generateSortIndex(0, resultsCount, clientIds);

					int clientId = clientIds[sortIndex[0]];
					int lastClientIdEventsCount = 0;
					boolean lastClientClosed = false;

					for (int i = 0; i <= resultsCount; i++) {
						if (i == resultsCount || (i != 0 && clientIds[sortIndex[i]] != clientId)) {
							if (lastClientIdEventsCount > 0) {
								for (int j = 0; j < lastClientIdEventsCount; j++) {
									long clientEventId = eventIds[sortIndex[i - lastClientIdEventsCount + j]];
									clientEventIds[j] = clientEventId;
									TdApi.Object clientEvent = events[sortIndex[i - lastClientIdEventsCount + j]];
									clientEvents[j] = clientEvent;

									if (clientEventId == 0 && clientEvent.getConstructor() == UpdateAuthorizationState.CONSTRUCTOR) {
										UpdateAuthorizationState update = (UpdateAuthorizationState) clientEvent;
										TdApi.AuthorizationState authorizationState = update.authorizationState;
										if (authorizationState.getConstructor() == TdApi.AuthorizationStateClosed.CONSTRUCTOR) {
											lastClientClosed = true;
											closedClients.add(clientId);
										}
									}
								}
								cleanClientEventsArray(lastClientIdEventsCount);

								assert areBoundsValid(clientEvents, 0, lastClientIdEventsCount);

								eventsHandler.handleClientEvents(clientId,
										lastClientClosed,
										clientEventIds,
										clientEvents,
										0,
										lastClientIdEventsCount
								);
							}

							if (i < resultsCount) {
								clientId = clientIds[sortIndex[i]];
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
						public final TdApi.Object event;

						public Event(int clientId, long eventId, TdApi.Object event) {
							this.clientId = clientId;
							this.eventId = eventId;
							this.event = event;
						}

						@Override
						public boolean equals(Object o) {
							if (this == o) {
								return true;
							}
							if (o == null || getClass() != o.getClass()) {
								return false;
							}
							Event event1 = (Event) o;
							return clientId == event1.clientId && eventId == event1.eventId && Objects.equals(event, event1.event);
						}

						@Override
						public int hashCode() {
							return Objects.hash(clientId, eventId, event);
						}

						@Override
						public String toString() {
							return new StringJoiner(", ", Event.class.getSimpleName() + "[", "]")
									.add("clientId=" + clientId)
									.add("eventId=" + eventId)
									.add("event=" + event)
									.toString();
						}
					}

					List<Event> eventsList = new ArrayList<>(resultsCount);
					for (int i = 0; i < resultsCount; i++) {
						eventsList.add(new Event(clientIds[i], eventIds[i], events[i]));
					}
					Set<Integer> clientIds = eventsList.stream().map(e -> e.clientId).collect(toSet());
					for (int clientId : clientIds) {
						List<Event> clientEventsList = eventsList.stream().filter(e -> e.clientId == clientId).collect(toList());
						boolean closed = false;
						for (int i = 0; i < clientEventsList.size(); i++) {
							Event e = clientEventsList.get(i);
							clientEventIds[i] = e.eventId;
							clientEvents[i] = e.event;

							if (e.eventId == 0 && e.event.getConstructor() == UpdateAuthorizationState.CONSTRUCTOR) {
								TdApi.AuthorizationState authorizationState = ((UpdateAuthorizationState) e.event).authorizationState;
								if (authorizationState.getConstructor() == TdApi.AuthorizationStateClosed.CONSTRUCTOR) {
									closed = true;
									closedClients.add(clientId);
								}
							}
						}
						cleanClientEventsArray(clientEventsList.size());
						eventsHandler.handleClientEvents(clientId,
								closed,
								clientEventIds,
								clientEvents,
								0,
								clientEventsList.size()
						);
					}
				}

				cleanEventsArray(resultsCount);

				if (closedClients.isContentful()) {
					synchronized (this.registeredClientsLock) {
						Set<Integer> remainingRegisteredClients = ArrayUtil.toSet(this.registeredClients);
						closedClients.drain(remainingRegisteredClients::remove);
						this.registeredClients = ArrayUtil.copyFromCollection(remainingRegisteredClients);
					}
				}
			}

			LOG.trace("ResponseReceiver will no longer process updates");

			if (interrupted) {
				for (int clientId : registeredClients) {
					eventsHandler.handleClientEvents(clientId, true, clientEventIds, clientEvents, 0, 0);
				}
			}
		} finally {
			LOG.debug("ResponseReceiver stopped");
			this.closeWait.countDown();
		}
	}

	public static boolean areBoundsValid(Object[] clientEvents, int start, int length) {
		if (start > length) {
			return false;
		}
		for (int i = start; i < length; i++) {
			if (clientEvents[i] == null) {
				return false;
			}
		}
		return true;
	}

	private void cleanEventsArray(int eventsCount) {
		if (eventsLastUsedLength > eventsCount) {
			Arrays.fill(events, eventsCount, eventsLastUsedLength, null);
		}
		eventsLastUsedLength = eventsCount;
	}

	private void cleanClientEventsArray(int clientEventsCount) {
		if (clientEventsLastUsedLength > clientEventsCount) {
			Arrays.fill(clientEvents, clientEventsCount, clientEventsLastUsedLength, null);
		}
		clientEventsLastUsedLength = clientEventsCount;
	}

	@SuppressWarnings("SameParameterValue")
	private int[] generateSortIndex(int from, int to, int[] data) {
		int[] sortedIndices = Arrays.copyOfRange(originalSortingSource, from, to);
		ArrayUtil.mergeSort(from,
				to,
				(o1, o2) -> Integer.compare(data[sortedIndices[o1]], data[sortedIndices[o2]]),
				new IntSwapper(sortedIndices)
		);
		return sortedIndices;
	}

	public void registerClient(int clientId) {
		synchronized (registeredClientsLock) {
			Set<Integer> modifiableRegisteredClients = ArrayUtil.toSet(this.registeredClients);
			modifiableRegisteredClients.add(clientId);
			this.registeredClients = ArrayUtil.copyFromCollection(modifiableRegisteredClients);
		}
	}

	@Override
	public void close() throws InterruptedException {
		this.closeRequested = true;
		this.closeWait.await();
		if (registeredClients.length == 0) {
			LOG.debug("Interrupting response receiver");
			ResponseReceiver.this.interrupt();
		}
	}
}
