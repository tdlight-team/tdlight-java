package it.tdlight.tdlight;

import it.tdlight.tdlib.NativeLog;
import it.tdlight.tdlight.utils.ObjectsUtils;

/**
 * Interface for managing the internal logging of TDLib. By default TDLib writes logs to stderr or an OS specific log and uses a verbosity level of 5.
 */
public class Log {

	static {
		try {
			Init.start();
		} catch (Throwable throwable) {
			throwable.printStackTrace();
			System.exit(0);
		}
	}

	private static final FatalErrorCallbackPtr defaultFatalErrorCallbackPtr = System.err::println;
	private static FatalErrorCallbackPtr fatalErrorCallback = defaultFatalErrorCallbackPtr;


	/**
	 * Sets the path to the file to where the internal TDLib log will be written. By default TDLib writes logs to stderr or an OS specific log. Use this method to write the log to a file instead.
	 * @param filePath Path to a file where the internal TDLib log will be written. Use an empty path to switch back to the default logging behaviour.
	 * @return True on success, or false otherwise, i.e. if the file can't be opened for writing.
	 * @deprecated Unfortunately TDLib redirects all STDERR to a file, try to avoid this function.
	 */
	@Deprecated
	public static synchronized boolean setFilePath(String filePath) {
		return NativeLog.setFilePath(filePath);
	}

	/**
	 * Sets maximum size of the file to where the internal TDLib log is written before the file will be auto-rotated. Unused if log is not written to a file. Defaults to 10 MB.
	 * @param maxFileSize Maximum size of the file to where the internal TDLib log is written before the file will be auto-rotated. Should be positive.
	 */
	public static synchronized void setMaxFileSize(long maxFileSize) {
		NativeLog.setMaxFileSize(maxFileSize);
	}

	/**
	 * Sets the verbosity level of the internal logging of TDLib. By default the TDLib uses a verbosity level of 5 for logging.
	 * @param verbosityLevel New value of the verbosity level for logging. Value 0 corresponds to fatal errors, value 1 corresponds to errors, value 2 corresponds to warnings and debug warnings, value 3 corresponds to informational, value 4 corresponds to debug, value 5 corresponds to verbose debug, value greater than 5 and up to 1024 can be used to enable even more logging.
	 */
	public static synchronized void setVerbosityLevel(int verbosityLevel) {
		NativeLog.setVerbosityLevel(verbosityLevel);
	}

	/**
	 * Sets the callback that will be called when a fatal error happens. None of the TDLib methods can be called from the callback. The TDLib will crash as soon as callback returns. By default the callback set to print in stderr.
	 * @param fatalErrorCallback Callback that will be called when a fatal error happens. Pass null to restore default callback.
	 */
	public static synchronized void setFatalErrorCallback(FatalErrorCallbackPtr fatalErrorCallback) {
		Log.fatalErrorCallback = ObjectsUtils.requireNonNullElse(fatalErrorCallback, defaultFatalErrorCallbackPtr);
	}

	private static synchronized void onFatalError(String errorMessage) {
		new Thread(() -> Log.fatalErrorCallback.onFatalError(errorMessage)).start();
	}
}
