package it.tdlight.client;

import it.tdlight.jni.TdApi;

/**
 * Interface for incoming responses from TDLib.
 */
@FunctionalInterface
public interface GenericResultHandler<T extends TdApi.Object> {

	/**
	 * Callback called when TDLib responds.
	 */
	void onResult(Result<T> result);

	default void onResult(TdApi.Object result) {
		onResult(Result.of(result));
	}

	default void onErrorResult(Throwable exception) {
		onResult(Result.ofError(exception));
	}
}