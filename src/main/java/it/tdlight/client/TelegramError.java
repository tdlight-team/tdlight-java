package it.tdlight.client;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Error;
import java.util.Objects;
import java.util.StringJoiner;

public final class TelegramError extends RuntimeException {

	private final int code;
	private final String message;

	public TelegramError(Error error) {
		super(error.code + ": " + error.message);
		this.code = error.code;
		this.message = error.message;
	}

	public TelegramError(Error error, Throwable cause) {
		super(error.code + ": " + error.message, cause);
		this.code = error.code;
		this.message = error.message;
	}

	public TelegramError(Throwable cause) {
		super(cause);
		this.code = 500;
		if (cause.getMessage() != null) {
			this.message = cause.getMessage();
		} else {
			this.message = "Error";
		}
	}

	public int getErrorCode() {
		return code;
	}

	public String getErrorMessage() {
		return message;
	}

	public Error getError() {
		return new TdApi.Error(code, message);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		TelegramError that = (TelegramError) o;
		return code == that.code && Objects.equals(message, that.message);
	}

	@Override
	public int hashCode() {
		return Objects.hash(code, message);
	}
}
