package it.tdlight.common.utils;

import java.util.Scanner;

public final class ScannerUtils {

	private static final Scanner scanner;

	static {
		scanner = new Scanner(System.in);
	}

	public static String askParameter(String displayName, String question) {
		System.out.print("[" + displayName + "] " + question + ": ");
		return scanner.nextLine();
	}
}
