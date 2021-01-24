package it.tdlight.tdlib;

import it.tdlight.common.CommonClientManager;
import it.tdlight.common.TelegramClient;

/**
 * Interface for interaction with TDLib.
 */
public class ClientManager extends CommonClientManager {

	private static final String implementationName = "tdlib";

	public static TelegramClient create() {
		return CommonClientManager.create(implementationName);
	}
}
