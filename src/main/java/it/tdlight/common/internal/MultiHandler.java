package it.tdlight.common.internal;

import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.UpdatesHandler;

public final class MultiHandler {

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
