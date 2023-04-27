package it.tdlight.utils;

public class SpinWaitSupport {

	private SpinWaitSupport() {
	}

	public static void onSpinWait() {
		java.lang.Thread.onSpinWait();
	}
}
