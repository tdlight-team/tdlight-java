package it.tdlight.common.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class SpinWaitSupport {
	private static final MethodHandle onSpinWaitHandle;

	static {
		MethodHandles.Lookup lookup = MethodHandles.lookup();

		MethodHandle handle;
		try {
			handle = lookup.findStatic(java.lang.Thread.class, "onSpinWait", MethodType.methodType(void.class));
		} catch (Exception e) {
			handle = null;
		}

		onSpinWaitHandle = handle;
	}

	private SpinWaitSupport() {
	}

	public static void onSpinWait() {
		if (onSpinWaitHandle != null) {
			try {
				onSpinWaitHandle.invokeExact();
			} catch (Throwable throwable) {
				// This can't happen
			}
		}
	}
}
