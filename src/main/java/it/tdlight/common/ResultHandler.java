package it.tdlight.common;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Object;

/**
 * Interface for handler for results of queries to TDLib and incoming updates from TDLib.
 */
@SuppressWarnings("unused")
public interface ResultHandler<R extends TdApi.Object> {

	/**
	 * Callback called on result of query to TDLib or incoming update from TDLib.
	 *
	 * @param object Result of type r, error of type TdApi.Error, or update of type TdApi.Update.
	 */
	void onResult(TdApi.Object object);
}