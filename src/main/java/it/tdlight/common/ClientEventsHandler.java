package it.tdlight.common;

import it.tdlight.jni.TdApi.Object;

public interface ClientEventsHandler {

	int getClientId();

	void handleEvents(boolean isClosed, long[] eventIds, Object[] events);
}
