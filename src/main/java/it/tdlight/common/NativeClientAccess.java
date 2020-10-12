package it.tdlight.common;

import it.tdlight.jni.NativeClient;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Function;

class NativeClientAccess extends NativeClient {

	public static int create() {
		return NativeClientAccess.createNativeClient();
	}

	public static TdApi.Object execute(Function function) {
		return NativeClientAccess.nativeClientExecute(function);
	}

	public static void send(int nativeClientId, long eventId, TdApi.Function function) {
		NativeClientAccess.nativeClientSend(nativeClientId, eventId, function);
	}

	public static int receive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout) {
		return NativeClientAccess.nativeClientReceive(clientIds, eventIds, events, timeout);
	}
}
