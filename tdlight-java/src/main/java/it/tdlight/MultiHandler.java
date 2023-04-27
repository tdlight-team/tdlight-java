package it.tdlight;

import it.tdlight.ExceptionHandler;
import it.tdlight.UpdatesHandler;

final class MultiHandler {

	private final UpdatesHandler updatesHandler;
	private final ExceptionHandler exceptionHandler;

	public MultiHandler(UpdatesHandler updatesHandler, ExceptionHandler exceptionHandler) {
		this.updatesHandler = updatesHandler;
		this.exceptionHandler = exceptionHandler;
	}

	public UpdatesHandler getUpdatesHandler() {
		return updatesHandler;
	}

	public ExceptionHandler getExceptionHandler() {
		return exceptionHandler;
	}
}
