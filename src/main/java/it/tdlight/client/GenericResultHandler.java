package it.tdlight.client;

import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface for incoming responses from TDLib.
 */
@FunctionalInterface
public interface GenericResultHandler<T extends TdApi.Object> {

	/**
	 * Callback called when TDLib responds.
	 */
	void onResult(Result<T> result);
}