package it.tdlight.tdlight;

import it.tdlight.common.CommonClientManager;
import it.tdlight.common.TelegramClient;

/**
 * Interface for interaction with TDLight.
 */
public class ClientManager extends CommonClientManager {

	private static final String implementationName = "tdlight";

	public static TelegramClient create() {
		return CommonClientManager.create(implementationName);
	}
}
