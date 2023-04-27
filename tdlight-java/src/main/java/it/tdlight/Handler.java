package it.tdlight;

import it.tdlight.ExceptionHandler;
import it.tdlight.ResultHandler;
import it.tdlight.jni.TdApi;

final class Handler<R extends TdApi.Object> {

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
