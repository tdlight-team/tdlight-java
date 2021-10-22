package it.tdlight.common.internal;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.tdnative.NativeClient;

final class NativeClientAccess extends NativeClient {

	public static int create() {
		return NativeClientAccess.createNativeClient();
	}

	public static <R extends TdApi.Object> TdApi.Object execute(Function<R> function) {
		return NativeClientAccess.nativeClientExecute(function);
	}

	public static <R extends TdApi.Object> void send(int nativeClientId, long eventId, TdApi.Function<R> function) {
		NativeClientAccess.nativeClientSend(nativeClientId, eventId, function);
	}

	public static int receive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout) {
		return NativeClientAccess.nativeClientReceive(clientIds, eventIds, events, timeout);
	}
}
