package it.tdlight.tdlib;

import it.tdlight.common.internal.CommonClientManager;
import it.tdlight.common.ReactiveTelegramClient;
import it.tdlight.common.TelegramClient;

/**
 * Interface for interaction with TDLib.
 */
public class ClientManager extends CommonClientManager {

	private static final String implementationName = "tdlib";

	public static TelegramClient create() {
		return CommonClientManager.create(implementationName);
	}

	public static ReactiveTelegramClient createReactive() {
		return CommonClientManager.createReactive(implementationName);
	}
}
