package it.tdlight.common.internal;

import it.tdlight.common.ReactiveTelegramClient;
import it.tdlight.common.TelegramClient;

public abstract class CommonClientManager {

	private static InternalClientManager getClientManager(String implementationName) {
		// ClientManager is singleton:
		return InternalClientManager.get(implementationName);
	}

	protected synchronized static TelegramClient create(String implementationName) {
		InternalClient client = new InternalClient(getClientManager(implementationName));
		return create(client);
	}

	protected synchronized static ReactiveTelegramClient createReactive(String implementationName) {
		InternalReactiveClient reactiveClient = new InternalReactiveClient(getClientManager(implementationName));
		return createReactive(reactiveClient);
	}

	private static TelegramClient create(InternalClient internalClient) {
		return internalClient;
	}

	private static ReactiveTelegramClient createReactive(InternalReactiveClient internalReactiveClient) {
		return internalReactiveClient;
	}
}
