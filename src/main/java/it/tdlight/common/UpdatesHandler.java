package it.tdlight.common;

import it.tdlight.jni.TdApi.Update;
import java.util.List;

/**
 * Interface for handler for incoming updates from TDLib.
 */
public interface UpdatesHandler {

	/**
	 * Callback called on incoming update from TDLib.
	 *
	 * @param object Updates of type TdApi.Update about new events.
	 */
	void onUpdates(List<Update> object);
}