package it.tdlight.common;

import it.tdlight.jni.TdApi.Error;

public class TDLibException extends RuntimeException {

	private final Error event;

	public TDLibException(Error event) {
		super(event.code + ": " + event.message);
		this.event = event;
	}

	public int getErrorCode() {
		return event.code;
	}

	public String getErrorMessage() {
		return event.message;
	}
}
