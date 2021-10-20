package it.tdlight.common.internal;

import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.ResultHandler;
import it.tdlight.jni.TdApi;

public final class Handler<R extends TdApi.Object> {
	private final ResultHandler<R> resultHandler;
	private final ExceptionHandler exceptionHandler;

	public Handler(ResultHandler<R> resultHandler, ExceptionHandler exceptionHandler) {
		this.resultHandler = resultHandler;
		this.exceptionHandler = exceptionHandler;
	}

	public ResultHandler<R> getResultHandler() {
		return resultHandler;
	}

	public ExceptionHandler getExceptionHandler() {
		return exceptionHandler;
	}
}
