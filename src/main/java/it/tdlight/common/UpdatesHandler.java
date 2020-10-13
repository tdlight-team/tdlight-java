package it.tdlight.common;

import it.tdlight.jni.TdApi.Object;
import java.util.List;

/**
 * Interface for handler for incoming updates from TDLib.
 */
public interface UpdatesHandler {

	/**
	 * Callback called on incoming update from TDLib.
	 *
	 * @param object Updates of type {@link it.tdlight.jni.TdApi.Update} about new events, or {@link it.tdlight.jni.TdApi.Error}.
	 */
	void onUpdates(List<Object> object);
}