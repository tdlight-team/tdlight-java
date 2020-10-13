package it.tdlight.tdlight;

import it.tdlight.common.CommonClientManager;
import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.ResultHandler;
import it.tdlight.common.TelegramClient;
import it.tdlight.common.UpdatesHandler;

/**
 * Interface for interaction with TDLight.
 */
public class ClientManager extends CommonClientManager {

	private static final String implementationName = "tdlight";

	public static TelegramClient create(
			ResultHandler updateHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		return CommonClientManager.create(implementationName, updateHandler, updateExceptionHandler, defaultExceptionHandler);
	}

	public static TelegramClient create(
			UpdatesHandler updatesHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		return CommonClientManager.create(implementationName, updatesHandler, updateExceptionHandler, defaultExceptionHandler);
	}
}
