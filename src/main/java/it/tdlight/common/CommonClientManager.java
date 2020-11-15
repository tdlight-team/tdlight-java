package it.tdlight.common;

import it.tdlight.jni.TdApi;

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
		// Send a dummy request because @levlam is too lazy to fix race conditions in a better way
		internalClient.send(new TdApi.GetAuthorizationState(), (result) -> {}, ex -> {});
		return internalClient;
	}
}
