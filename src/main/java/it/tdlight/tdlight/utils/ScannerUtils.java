package it.tdlight.tdlight.utils;

import java.io.PrintStream;
import java.util.Scanner;
import java.util.concurrent.locks.ReentrantLock;

public class ScannerUtils {
	private static final Scanner scanner = new Scanner(System.in);
	private static final ReentrantLock lock = new ReentrantLock();
	private static final PrintStream emptyOut = new java.io.PrintStream(new java.io.OutputStream() {
		@Override
		public void write(int b) {
		}
	}) {
		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}

		@Override
		public void write(int b) {
		}

		@Override
		public void write(byte[] b) {
		}

		@Override
		public void write(byte[] buf, int off, int len) {
		}

		@Override
		public void print(boolean b) {
		}

		@Override
		public void print(char c) {
		}

		@Override
		public void print(int i) {
		}

		@Override
		public void print(long l) {
		}

		@Override
		public void print(float f) {
		}

		@Override
		public void print(double d) {
		}

		@Override
		public void print(char[] s) {
		}

		@Override
		public void print(String s) {
		}

		@Override
		public void print(Object obj) {
		}

		@Override
		public void println() {
		}

		@Override
		public void println(boolean x) {
		}

		@Override
		public void println(char x) {
		}

		@Override
		public void println(int x) {
		}

		@Override
		public void println(long x) {
		}

		@Override
		public void println(float x) {
		}

		@Override
		public void println(double x) {
		}

		@Override
		public void println(char[] x) {
		}

		@Override
		public void println(String x) {
		}

		@Override
		public void println(Object x) {
		}

		@Override
		public java.io.PrintStream printf(String format, Object... args) {
			return this;
		}

		@Override
		public java.io.PrintStream printf(java.util.Locale l, String format, Object... args) {
			return this;
		}

		@Override
		public java.io.PrintStream format(String format, Object... args) {
			return this;
		}

		@Override
		public java.io.PrintStream format(java.util.Locale l, String format, Object... args) {
			return this;
		}

		@Override
		public java.io.PrintStream append(CharSequence csq) {
			return this;
		}

		@Override
		public java.io.PrintStream append(CharSequence csq, int start, int end) {
			return this;
		}

		@Override
		public java.io.PrintStream append(char c) {
			return this;
		}
	};

	public static String askParameter(String displayName, String question) {
		try {
			lock.lock();
			StringBuilder toPrint = new StringBuilder();
			PrintStream oldOut = System.out;
			PrintStream oldErr = System.err;
			System.setOut(emptyOut);
			System.setErr(emptyOut);
			oldOut.print("[" + displayName + "] " + question + ": ");
			String result = scanner.nextLine();
			System.setOut(oldOut);
			System.setErr(oldErr);
			return result;
		} finally {
			lock.unlock();
		}
	}
}
