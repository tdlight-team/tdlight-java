package it.tdlight.common.utils;

public class SpinWaitSupport {

	private SpinWaitSupport() {
	}

	public static void onSpinWait() {
		java.lang.Thread.onSpinWait();
	}
}
