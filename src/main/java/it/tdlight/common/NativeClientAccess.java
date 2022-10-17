package it.tdlight.common;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.tdnative.NativeClient;

final class NativeClientAccess extends NativeClient {

	public static <R extends TdApi.Object> TdApi.Object execute(Function<R> function) {
		return nativeClientExecute(function);
	}

	public static void setLogMessageHandler(int maxVerbosityLevel, LogMessageHandler logMessageHandler) {
		nativeClientSetLogMessageHandler(maxVerbosityLevel, logMessageHandler);
	}
}
