package it.tdlight.jni;

public class NativeClient {

    protected static native int createNativeClient();

    protected static native void nativeClientSend(int nativeClientId, long eventId, TdApi.Function function);

    protected static native int nativeClientReceive(int[] clientIds, long[] eventIds, TdApi.Object[] events, double timeout);

    protected static native TdApi.Object nativeClientExecute(TdApi.Function function);
}
