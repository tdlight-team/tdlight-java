package it.tdlight.util;

public class SpinWaitSupport {

	private SpinWaitSupport() {
	}

	public static void onSpinWait() {
		java.lang.Thread.onSpinWait();
	}
}
