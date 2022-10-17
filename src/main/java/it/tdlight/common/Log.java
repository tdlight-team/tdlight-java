package it.tdlight.common;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.LogStreamDefault;
import it.tdlight.jni.TdApi.LogStreamFile;
import it.tdlight.jni.TdApi.SetLogVerbosityLevel;
import it.tdlight.tdnative.NativeClient;
import it.tdlight.tdnative.NativeClient.LogMessageHandler;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Class used for managing internal TDLib logging. Use TdApi.*Log* methods instead.
 */
public final class Log {

	static {
		try {
			Init.start();
		} catch (Throwable throwable) {
			throwable.printStackTrace();
			System.exit(0);
		}
	}

	private static final AtomicReference<String> LOG_FILE_PATH = new AtomicReference<>(null);
	private static final AtomicLong LOG_MAX_FILE_SIZE = new AtomicLong(10 * 1024 * 1024);

	private static boolean updateLog() {
		try {
			String path = LOG_FILE_PATH.get();
			long maxSize = LOG_MAX_FILE_SIZE.get();
			if (path == null) {
				NativeClientAccess.execute(new TdApi.SetLogStream(new LogStreamDefault()));
			} else {
				NativeClientAccess.execute(new TdApi.SetLogStream(new LogStreamFile(path, maxSize, false)));
			}
		} catch (Throwable ex) {
			ex.printStackTrace();
			return false;
		}
		return true;
	}

	/**
	 * Sets file path for writing TDLib internal log. By default TDLib writes logs to the System.err. Use this method to
	 * write the log to a file instead.
	 *
	 * @param filePath Path to a file for writing TDLib internal log. Use an empty path to switch back to logging to the
	 *                 System.err.
	 * @return whether opening the log file succeeded.
	 * @deprecated As of TDLib 1.4.0 in favor of {@link TdApi.SetLogStream}, to be removed in the future.
	 */
	@Deprecated
	public static boolean setFilePath(String filePath) {
		LOG_FILE_PATH.set(filePath);
		return updateLog();
	}

	/**
	 * Changes the maximum size of TDLib log file.
	 *
	 * @param maxFileSize The maximum size of the file to where the internal TDLib log is written before the file will be
	 *                    auto-rotated. Must be positive. Defaults to 10 MB.
	 * @deprecated As of TDLib 1.4.0 in favor of {@link TdApi.SetLogStream}, to be removed in the future.
	 */
	@Deprecated
	public static void setMaxFileSize(long maxFileSize) {
		LOG_MAX_FILE_SIZE.set(maxFileSize);
		updateLog();
	}

	/**
	 * Changes TDLib log verbosity.
	 *
	 * @param verbosityLevel New value of log verbosity level. Must be non-negative. Value 0 corresponds to fatal errors,
	 *                       value 1 corresponds to java.util.logging.Level.SEVERE, value 2 corresponds to
	 *                       java.util.logging.Level.WARNING, value 3 corresponds to java.util.logging.Level.INFO, value 4
	 *                       corresponds to java.util.logging.Level.FINE, value 5 corresponds to
	 *                       java.util.logging.Level.FINER, value greater than 5 can be used to enable even more logging.
	 *                       Default value of the log verbosity level is 5.
	 * @deprecated As of TDLib 1.4.0 in favor of {@link TdApi.SetLogVerbosityLevel}, to be removed in the future.
	 */
	@Deprecated
	public static void setVerbosityLevel(int verbosityLevel) {
		NativeClientAccess.execute(new SetLogVerbosityLevel(verbosityLevel));
		updateLog();
	}

	/**
	 *
	 * Sets the log message handler
	 *
	 * @param maxVerbosityLevel Log verbosity level with which the message was added from -1 up to 1024.
	 *                       If 0, then TDLib will crash as soon as the callback returns.
	 *                       None of the TDLib methods can be called from the callback.
	 * @param logMessageHandler handler
	 */
	public static void setLogMessageHandler(int maxVerbosityLevel, LogMessageHandler logMessageHandler) {
		NativeClientAccess.setLogMessageHandler(maxVerbosityLevel, logMessageHandler);
	}
}
