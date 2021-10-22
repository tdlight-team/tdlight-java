package it.tdlight.common;

public interface EventsHandler {

	void handleClientEvents(int clientId,
			boolean isClosed,
			long[] clientEventIds,
			Object[] clientEvents,
			int arrayOffset,
			int arrayLength);
}
