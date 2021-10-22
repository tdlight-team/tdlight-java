package it.tdlight.tdnative;

import it.tdlight.jni.TdApi;

public class NativeClient {

	protected static native int createNativeClient();

	protected static native <R extends TdApi.Object> void nativeClientSend(int nativeClientId,
			long eventId,
			TdApi.Function<R> function);

	protected static native int nativeClientReceive(int[] clientIds,
			long[] eventIds,
			TdApi.Object[] events,
			double timeout);

	protected static native <R extends TdApi.Object> TdApi.Object nativeClientExecute(TdApi.Function<R> function);
}
