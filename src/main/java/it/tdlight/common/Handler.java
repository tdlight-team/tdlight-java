package it.tdlight.common;

public class Handler {
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
