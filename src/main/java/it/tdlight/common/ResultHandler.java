package it.tdlight.common;

import it.tdlight.jni.TdApi.Object;

/**
 * Interface for handler for results of queries to TDLib and incoming updates from TDLib.
 */
public interface ResultHandler {

	/**
	 * Callback called on result of query to TDLib or incoming update from TDLib.
	 *
	 * @param object Result of query or update of type TdApi.Update about new events.
	 */
	void onResult(Object object);
}