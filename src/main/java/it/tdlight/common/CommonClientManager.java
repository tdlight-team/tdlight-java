package it.tdlight.common;

public abstract class CommonClientManager {

	private static InternalClientManager getClientManager(String implementationName) {
		// ClientManager is singleton:
		return InternalClientManager.get(implementationName);
	}

	protected static TelegramClient create(String implementationName,
			ResultHandler updateHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		InternalClient client = new InternalClient(getClientManager(implementationName),
				updateHandler,
				updateExceptionHandler,
				defaultExceptionHandler
		);
		return create(client);
	}

	protected static TelegramClient create(String implementationName,
			UpdatesHandler updatesHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		InternalClient client = new InternalClient(getClientManager(implementationName),
				updatesHandler,
				updateExceptionHandler,
				defaultExceptionHandler
		);
		return create(client);
	}

	private static TelegramClient create(InternalClient internalClient) {
		return internalClient;
	}
}
