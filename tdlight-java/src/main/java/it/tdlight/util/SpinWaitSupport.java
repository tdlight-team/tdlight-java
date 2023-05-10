package it.tdlight.util;

import java.util.concurrent.locks.LockSupport;

public class SpinWaitSupport {

	private SpinWaitSupport() {
	}

	public static void onSpinWait() {
		// park for 10ms
		LockSupport.parkNanos(10 * 1000L * 1000L);
	}
}
