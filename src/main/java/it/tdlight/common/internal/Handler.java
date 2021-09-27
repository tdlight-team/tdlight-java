package it.tdlight.common.internal;

import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.ResultHandler;

public final class Handler {
	private final ResultHandler resultHandler;
	private final ExceptionHandler exceptionHandler;

	public Handler(ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
		this.resultHandler = resultHandler;
		this.exceptionHandler = exceptionHandler;
	}

	public ResultHandler getResultHandler() {
		return resultHandler;
	}

	public ExceptionHandler getExceptionHandler() {
		return exceptionHandler;
	}
}
