package it.tdlight.util;

import it.tdlight.jni.TdApi;

/**
 * Produces log-safe TDLib object metadata without invoking generated {@code toString()} methods.
 */
public final class TdApiObjectDescriptor {

	private TdApiObjectDescriptor() {
	}

	public static String describe(TdApi.Object object) {
		if (object == null) {
			return "null";
		}
		return object.getClass().getSimpleName() + "[constructor=" + object.getConstructor() + ']';
	}
}
