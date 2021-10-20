package it.tdlight.common.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.locks.LockSupport;

public class SpinWaitSupport {

	private SpinWaitSupport() {
	}

	public static void onSpinWait() {
		// park for 10ms
		LockSupport.parkNanos(10 * 1000L * 1000L);
	}
}
