package it.tdlight.common;

public abstract class CommonClient {

	protected abstract String getImplementationName();


	private InternalClientManager getClientManager() {
		// ClientManager is singleton:
		return InternalClientManager.get(getImplementationName());
	}

	public TelegramClient create(ResultHandler updateHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		InternalClient client = new InternalClient(getClientManager(),
				updateHandler,
				updateExceptionHandler,
				defaultExceptionHandler
		);
		return create(client);
	}

	public TelegramClient create(UpdatesHandler updatesHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		InternalClient client = new InternalClient(getClientManager(),
				updatesHandler,
				updateExceptionHandler,
				defaultExceptionHandler
		);
		return create(client);
	}

	private TelegramClient create(InternalClient internalClient) {
		return internalClient;
	}
}
