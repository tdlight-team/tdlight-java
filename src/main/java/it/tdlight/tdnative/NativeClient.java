package it.tdlight.tdnative;

import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Object;

public class NativeClient {

    protected static native int createNativeClient();

    protected static native void nativeClientSend(int nativeClientId, long eventId, Function function);

    protected static native int nativeClientReceive(int[] clientIds, long[] eventIds, Object[] events, double timeout);

    protected static native Object nativeClientExecute(Function function);
}
