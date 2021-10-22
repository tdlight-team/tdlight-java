package it.tdlight.common;

import it.tdlight.jni.TdApi;

public interface ClientEventsHandler {

	int getClientId();

	void handleEvents(boolean isClosed, long[] eventIds, TdApi.Object[] events, int arrayOffset, int arrayLength);
}
