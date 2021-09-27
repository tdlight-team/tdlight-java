package it.tdlight.client;

import it.tdlight.jni.TdApi.Object;
import it.tdlight.jni.TdApi.Update;

/**
 * Interface for incoming updates from TDLib.
 */
@FunctionalInterface
public interface GenericUpdateHandler<T extends Update> {

	/**
	 * Callback called on incoming update from TDLib.
	 *
	 * @param update Update of type TdApi.Update about new events.
	 */
	void onUpdate(T update);
}