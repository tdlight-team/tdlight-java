package it.tdlight.tdlight.utils;

import java.util.Scanner;

public class ScannerUtils {
	private static final Scanner scanner;
	private static final Object lock = new Object();

	static {
		synchronized (lock) {
			scanner = new Scanner(System.in);
		}
	}

	public static String askParameter(String displayName, String question) {
		synchronized (lock) {
			System.out.print("[" + displayName + "] " + question + ": ");
			return scanner.nextLine();
		}
	}
}
