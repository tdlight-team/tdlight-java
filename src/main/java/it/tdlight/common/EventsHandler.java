package it.tdlight.common;

import it.tdlight.jni.TdApi;

public interface EventsHandler {

	void handleClientEvents(int clientId,
			boolean isClosed,
			long[] clientEventIds,
			TdApi.Object[] clientEvents,
			int arrayOffset,
			int arrayLength);
}
