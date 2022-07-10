package it.tdlight.common.utils;

import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ScannerUtils {

	private static final Object LOCK = new Object();
	private static InputStreamReader scanner = null;

	public static String askParameter(String displayName, String question) {
		synchronized (LOCK) {
			Console console = System.console();
			if (console != null) {
				return console.readLine("[%s] %s: ", displayName, question);
			} else {
				if (scanner == null) {
					scanner = new InputStreamReader(System.in);
				}
				System.out.printf("[%s] %s: ", displayName, question);
				try {
					return interruptibleReadLine(scanner);
				} catch (InterruptedException | IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	/**
	 * https://stackoverflow.com/questions/3595926/how-to-interrupt-bufferedreaders-readline
	 */
	private static String interruptibleReadLine(Reader reader) throws InterruptedException, IOException {
		Pattern line = Pattern.compile("^(.*)\\R");
		Matcher matcher;
		boolean interrupted;

		StringBuilder result = new StringBuilder();
		int chr = -1;
		do {
			if (reader.ready()) {
				chr = reader.read();
			}
			if (chr > -1) {
				result.append((char) chr);
			}
			matcher = line.matcher(result.toString());
			interrupted = Thread.interrupted(); // resets flag, call only once
		} while (!interrupted && !matcher.matches());
		if (interrupted) {
			throw new InterruptedException();
		}
		return (matcher.matches() ? matcher.group(1) : "");
	}
}
