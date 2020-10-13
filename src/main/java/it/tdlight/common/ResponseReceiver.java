package it.tdlight.common;

import it.tdlight.jni.TdApi;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class ResponseReceiver extends Thread implements AutoCloseable {

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


	public ResponseReceiver(EventsHandler eventsHandler) {
		super("TDLib thread");
		this.eventsHandler = eventsHandler;

		this.setDaemon(true);

		this.start();
	}

	@SuppressWarnings({"UnnecessaryLocalVariable", "InfiniteLoopStatement"})
	@Override
	public void run() {
		int[] sortIndex;
		try {
			while(true) {
				int resultsCount = NativeClientAccess.receive(clientIds, eventIds, events, 2.0 /*seconds*/);

				if (resultsCount <= 0)
					continue;

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
				Arrays.fill(events, null);
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

	@Override
	public void close() throws InterruptedException {
		this.closeWait.await();
	}
}
