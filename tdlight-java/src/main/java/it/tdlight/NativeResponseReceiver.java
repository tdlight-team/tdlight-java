package it.tdlight;

import it.tdlight.jni.TdApi.Object;
import java.util.function.LongSupplier;

class NativeResponseReceiver extends ResponseReceiver {

	public NativeResponseReceiver(EventsHandler eventsHandler) {
		super(eventsHandler);
	}

	NativeResponseReceiver(EventsHandler eventsHandler, LongSupplier emergencyQueryIdSupplier) {
		super(eventsHandler, emergencyQueryIdSupplier);
	}

	@Override
	public int receive(int[] clientIds, long[] eventIds, Object[] events, double timeout) {
		return NativeClientAccess.receive(clientIds, eventIds, events, timeout);
	}
}
