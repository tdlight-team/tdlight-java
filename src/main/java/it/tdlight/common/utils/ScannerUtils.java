package it.tdlight.common.utils;

public final class ScannerUtils {

	public static String askParameter(String displayName, String question) {
		return System.console().readLine("[%s] %s", displayName, question);
	}
}
