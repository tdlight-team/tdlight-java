package it.tdlight.common;

import it.tdlight.common.Init;
import it.tdlight.jni.FatalErrorCallbackPtr;
import it.tdlight.jni.NativeLog;
import it.tdlight.jni.TdApi;

/**
 * Class used for managing internal TDLib logging.
 * Use TdApi.*Log* methods instead.
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


	/**
	 * Sets file path for writing TDLib internal log. By default TDLib writes logs to the System.err.
	 * Use this method to write the log to a file instead.
	 *
	 * @deprecated As of TDLib 1.4.0 in favor of {@link TdApi.SetLogStream}, to be removed in the future.
	 * @param filePath Path to a file for writing TDLib internal log. Use an empty path to
	 *                 switch back to logging to the System.err.
	 * @return whether opening the log file succeeded.
	 */
	@Deprecated
	public static synchronized boolean setFilePath(String filePath) {
		return NativeLog.setFilePath(filePath);
	}

	/**
	 * Changes the maximum size of TDLib log file.
	 *
	 * @deprecated As of TDLib 1.4.0 in favor of {@link TdApi.SetLogStream}, to be removed in the future.
	 * @param maxFileSize The maximum size of the file to where the internal TDLib log is written
	 *                    before the file will be auto-rotated. Must be positive. Defaults to 10 MB.
	 */
	@Deprecated
	public static synchronized void setMaxFileSize(long maxFileSize) {
		NativeLog.setMaxFileSize(maxFileSize);
	}

	/**
	 * Changes TDLib log verbosity.
	 *
	 * @deprecated As of TDLib 1.4.0 in favor of {@link TdApi.SetLogVerbosityLevel}, to be removed in the future.
	 * @param verbosityLevel New value of log verbosity level. Must be non-negative.
	 *                       Value 0 corresponds to fatal errors,
	 *                       value 1 corresponds to java.util.logging.Level.SEVERE,
	 *                       value 2 corresponds to java.util.logging.Level.WARNING,
	 *                       value 3 corresponds to java.util.logging.Level.INFO,
	 *                       value 4 corresponds to java.util.logging.Level.FINE,
	 *                       value 5 corresponds to java.util.logging.Level.FINER,
	 *                       value greater than 5 can be used to enable even more logging.
	 *                       Default value of the log verbosity level is 5.
	 */
	@Deprecated
	public static synchronized void setVerbosityLevel(int verbosityLevel) {
		NativeLog.setVerbosityLevel(verbosityLevel);
	}

	/**
	 * Sets the callback that will be called when a fatal error happens. None of the TDLib methods can be called from the callback. The TDLib will crash as soon as callback returns. By default the callback set to print in stderr.
	 * @param fatalErrorCallback Callback that will be called when a fatal error happens. Pass null to restore default callback.
	 */
	public static synchronized void setFatalErrorCallback(FatalErrorCallbackPtr fatalErrorCallback) {
		NativeLog.setFatalErrorCallback(fatalErrorCallback);
	}
}
