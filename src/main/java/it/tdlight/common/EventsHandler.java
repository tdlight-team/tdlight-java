package it.tdlight.common;

import it.tdlight.jni.TdApi.Object;

public interface EventsHandler {

	void handleClientEvents(int clientId,
			boolean isClosed,
			long[] clientEventIds,
			Object[] clientEvents,
			int arrayOffset,
			int arrayLength);
}
