package it.tdlight.tdlight;

import it.tdlight.common.ReactiveTelegramClient;
import it.tdlight.common.TelegramClient;
import it.tdlight.common.internal.CommonClientManager;

/**
 * Interface for interaction with TDLight.
 */
public class ClientManager extends CommonClientManager {

	private static final String implementationName = "tdlight";

	public static TelegramClient create() {
		return CommonClientManager.create(implementationName);
	}

	public static ReactiveTelegramClient createReactive() {
		return CommonClientManager.createReactive(implementationName);
	}
}
