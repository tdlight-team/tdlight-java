package it.tdlight.common;

public interface ClientEventsHandler {

	int getClientId();

	void handleEvents(boolean isClosed, long[] eventIds, Object[] events, int arrayOffset, int arrayLength);
}
