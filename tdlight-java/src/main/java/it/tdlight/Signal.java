package it.tdlight;

import it.tdlight.jni.TdApi;
import java.util.Objects;
import java.util.StringJoiner;

public final class Signal {

	private final TdApi.Object item;
	private final Throwable ex;
	private final SignalType signalType;

	private Signal(SignalType signalType, TdApi.Object item, Throwable ex) {
		this.signalType = signalType;
		this.item = item;
		this.ex = ex;
	}

	public static Signal ofUpdateException(Throwable ex) {
		return new Signal(SignalType.EXCEPTION, null, Objects.requireNonNull(ex, "Exception is null"));
	}

	public static Signal ofUpdate(TdApi.Object item) {
		return new Signal(SignalType.UPDATE, Objects.requireNonNull(item, "Update is null"), null);
	}

	public static Signal ofClosed() {
		return new Signal(SignalType.CLOSE, null, null);
	}

	public boolean isUpdate() {
		return signalType == SignalType.UPDATE;
	}

	public boolean isException() {
		return signalType == SignalType.EXCEPTION;
	}

	public boolean isClosed() {
		return signalType == SignalType.CLOSE;
	}

	public boolean isNotClosed() {
		return signalType != SignalType.CLOSE;
	}

	public TdApi.Object getUpdate() {
		return Objects.requireNonNull(item, "This is not an update");
	}

	public Throwable getException() {
		return Objects.requireNonNull(ex, "This is not an exception");
	}

	public void getClosed() {
		if (signalType != SignalType.CLOSE) {
			throw new IllegalStateException("Expected signal type closed, but the type is " + signalType);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Signal signal = (Signal) o;
		return Objects.equals(item, signal.item) && Objects.equals(ex, signal.ex) && signalType == signal.signalType;
	}

	@Override
	public int hashCode() {
		return Objects.hash(item, ex, signalType);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", Signal.class.getSimpleName() + "[", "]")
				.add("item=" + item)
				.add("ex=" + ex)
				.add("signalType=" + signalType)
				.toString();
	}
}
