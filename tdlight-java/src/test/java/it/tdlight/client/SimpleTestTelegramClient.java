package it.tdlight.client;

import it.tdlight.ExceptionHandler;
import it.tdlight.ResultHandler;
import it.tdlight.TelegramClient;
import it.tdlight.UpdatesHandler;
import it.tdlight.jni.TdApi;

final class SimpleTestTelegramClient implements TelegramClient {

	interface Responder {
		TdApi.Object respond(TdApi.Function<?> query) throws Throwable;
	}

	private final Responder responder;
	private final Throwable sendFailure;
	private TdApi.Function<?> lastQuery;

	SimpleTestTelegramClient(TdApi.Object response) {
		this(query -> response, null);
	}

	SimpleTestTelegramClient(Throwable sendFailure) {
		this(null, sendFailure);
	}

	private SimpleTestTelegramClient(Responder responder, Throwable sendFailure) {
		this.responder = responder;
		this.sendFailure = sendFailure;
	}

	@Override
	public void initialize(UpdatesHandler updatesHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
	}

	@Override
	public <R extends TdApi.Object> void send(TdApi.Function<R> query,
			ResultHandler<R> resultHandler,
			ExceptionHandler exceptionHandler) {
		lastQuery = query;
		if (sendFailure != null) {
			handleFailure(sendFailure, exceptionHandler);
			return;
		}
		try {
			TdApi.Object response = responder.respond(query);
			if (resultHandler != null) {
				resultHandler.onResult(response);
			}
		} catch (Throwable error) {
			handleFailure(error, exceptionHandler);
		}
	}

	@Override
	public <R extends TdApi.Object> TdApi.Object execute(TdApi.Function<R> query) {
		lastQuery = query;
		try {
			return responder.respond(query);
		} catch (Throwable error) {
			throw propagate(error);
		}
	}

	TdApi.Function<?> getLastQuery() {
		return lastQuery;
	}

	private static void handleFailure(Throwable error, ExceptionHandler exceptionHandler) {
		if (exceptionHandler != null) {
			exceptionHandler.onException(error);
		} else {
			throw propagate(error);
		}
	}

	private static RuntimeException propagate(Throwable error) {
		if (error instanceof RuntimeException) {
			return (RuntimeException) error;
		}
		if (error instanceof Error) {
			throw (Error) error;
		}
		return new RuntimeException(error);
	}
}
