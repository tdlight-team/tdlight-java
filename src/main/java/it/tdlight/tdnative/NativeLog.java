package it.tdlight.tdnative;

import it.tdlight.jni.TdApi;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Class used for managing internal TDLib logging.
 */
public class NativeLog {

	private static final Consumer<String> defaultFatalErrorCallback = NativeLog::printFatalError;
	private static final AtomicReference<Consumer<String>> fatalErrorCallback
			= new AtomicReference<>(defaultFatalErrorCallback);

	/**
	 * This function is called from the JNI when a fatal error happens to provide a better error message.
	 * The function does not return.
	 *
	 * @param errorMessage Error message.
	 */
	private static void onFatalError(String errorMessage) {
		new Thread(() -> NativeLog.fatalErrorCallback.get().accept(errorMessage)).start();
	}

	/**
	 * Sets the callback that will be called when a fatal error happens.
	 * None of the TDLib methods can be called from the callback.
	 * TDLib will crash as soon as the callback returns.
	 * By default the callback set to print in stderr.
	 * @param fatalErrorCallback Callback that will be called when a fatal error happens.
	 *                           Pass null to restore default callback.
	 */
	public static void setFatalErrorCallback(Consumer<String> fatalErrorCallback) {
		if (fatalErrorCallback == null) {
			fatalErrorCallback = defaultFatalErrorCallback;
		}
		NativeLog.fatalErrorCallback.set(fatalErrorCallback);
	}

	private static void printFatalError(String errorMessage) {
		System.err.println("TDLib fatal error: " + errorMessage);
	}
}
