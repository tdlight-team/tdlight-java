package it.tdlight.tdnative;

import it.tdlight.jni.TdApi;

@SuppressWarnings("rawtypes")
public class NativeClient {

	protected static native int createNativeClient();

	protected static native void nativeClientSend(int nativeClientId, long eventId, TdApi.Function function);

	protected static native int nativeClientReceive(int[] clientIds,
			long[] eventIds,
			TdApi.Object[] events,
			double timeout);

	protected static native TdApi.Object nativeClientExecute(TdApi.Function function);

	protected static native void nativeClientSetLogMessageHandler(int maxVerbosityLevel, LogMessageHandler logMessageHandler);

	/**
	 * Interface for handler of messages that are added to the internal TDLib log.
	 */
	public interface LogMessageHandler {
		/**
		 * Callback called on messages that are added to the internal TDLib log.
		 *
		 * @param verbosityLevel Log verbosity level with which the message was added from -1 up to 1024.
		 *                       If 0, then TDLib will crash as soon as the callback returns.
		 *                       None of the TDLib methods can be called from the callback.
		 * @param message        The message added to the internal TDLib log.
		 */
		void onLogMessage(int verbosityLevel, String message);
	}
}
