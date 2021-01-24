package it.tdlight.common;

public abstract class CommonClientManager {

	private static InternalClientManager getClientManager(String implementationName) {
		// ClientManager is singleton:
		return InternalClientManager.get(implementationName);
	}

	protected static TelegramClient create(String implementationName) {
		InternalClient client = new InternalClient(getClientManager(implementationName));
		return create(client);
	}

	private static TelegramClient create(InternalClient internalClient) {
		return internalClient;
	}
}
