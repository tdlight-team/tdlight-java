package it.tdlight;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.tdnative.NativeClient;

final class NativeClientAccess extends NativeClient {

	static int create() {
		return NativeClientAccess.createNativeClient();
	}

	public static <R extends TdApi.Object> TdApi.Object execute(Function<R> function) {
		return NativeClientAccess.nativeClientExecute(function);
	}

	static <R extends TdApi.Object> void send(int nativeClientId, long eventId, TdApi.Function<R> function) {
		NativeClientAccess.nativeClientSend(nativeClientId, eventId, function);
	}

	static int receive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout) {
		return NativeClientAccess.nativeClientReceive(clientIds, eventIds, events, timeout);
	}

	public static void setLogMessageHandler(int maxVerbosityLevel, LogMessageHandler logMessageHandler) {
		NativeClientAccess.nativeClientSetLogMessageHandler(maxVerbosityLevel, logMessageHandler);
	}
}
