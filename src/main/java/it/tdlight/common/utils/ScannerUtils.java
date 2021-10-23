package it.tdlight.common.utils;

import java.io.Console;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;

public final class ScannerUtils {

	private static final Object lock = new Object();
	private static Scanner SCANNER = null;

	public static String askParameter(String displayName, String question) {
		Console console = System.console();
		if (console != null) {
			return console.readLine("[%s] %s", displayName, question);
		} else {
			synchronized (lock) {
				if (SCANNER == null) {
					SCANNER = new Scanner(System.in);
					Runtime.getRuntime().addShutdownHook(new Thread(() -> SCANNER.close()));
				}
			}
			System.out.printf("[%s] %s: ", displayName, question);
			return SCANNER.nextLine();
		}
	}
}
