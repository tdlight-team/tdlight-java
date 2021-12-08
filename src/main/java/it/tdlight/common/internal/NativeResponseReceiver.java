package it.tdlight.common.internal;

import it.tdlight.common.EventsHandler;
import it.tdlight.jni.TdApi.Object;

public class NativeResponseReceiver extends ResponseReceiver {

	public NativeResponseReceiver(EventsHandler eventsHandler) {
		super(eventsHandler);
	}

	@Override
	public int receive(int[] clientIds, long[] eventIds, Object[] events, double timeout) {
		return NativeClientAccess.receive(clientIds, eventIds, events, timeout);
	}
}
