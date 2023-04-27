package it.tdlight;

import it.tdlight.jni.TdApi.Object;

class NativeResponseReceiver extends ResponseReceiver {

	public NativeResponseReceiver(EventsHandler eventsHandler) {
		super(eventsHandler);
	}

	@Override
	public int receive(int[] clientIds, long[] eventIds, Object[] events, double timeout) {
		return NativeClientAccess.receive(clientIds, eventIds, events, timeout);
	}
}
