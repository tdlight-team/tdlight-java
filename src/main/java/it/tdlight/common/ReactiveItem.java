package it.tdlight.common;

import it.tdlight.jni.TdApi;
import java.util.Objects;

public final class ReactiveItem {

	private final TdApi.Object item;
	private final Throwable ex;
	private final boolean updateException;

	private ReactiveItem(Throwable ex, boolean updateException) {
		this.item = null;
		this.ex = Objects.requireNonNull(ex);
		this.updateException = updateException;
	}

	private ReactiveItem(TdApi.Object item) {
		this.item = Objects.requireNonNull(item);
		this.ex = null;
		this.updateException = false;
	}

	public static ReactiveItem ofUpdateException(Throwable ex) {
		return new ReactiveItem(ex, true);
	}

	public static ReactiveItem ofHandleException(Throwable ex) {
		return new ReactiveItem(ex, false);
	}

	public static ReactiveItem ofUpdate(TdApi.Object item) {
		return new ReactiveItem(item);
	}

	public boolean isUpdateException() {
		return ex != null && updateException;
	}

	public boolean isHandleException() {
		return ex != null && !updateException;
	}

	public boolean isUpdate() {
		return ex == null;
	}

	public TdApi.Object getUpdate() {
		return Objects.requireNonNull(item, "This is not an update");
	}

	public Throwable getUpdateException() {
		if (!updateException) {
			throw new IllegalStateException("This is not an update exception");
		}
		return Objects.requireNonNull(ex, "This is not an update exception");
	}

	public Throwable getHandleException() {
		if (updateException) {
			throw new IllegalStateException("This is not an handle exception");
		}
		return Objects.requireNonNull(ex, "This is not an handle exception");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		ReactiveItem that = (ReactiveItem) o;
		return updateException == that.updateException && Objects.equals(item, that.item) && Objects.equals(ex, that.ex);
	}

	@Override
	public int hashCode() {
		return Objects.hash(item, ex, updateException);
	}

	@Override
	public String toString() {
		if (ex != null) {
			if (updateException) {
				return "UpdateException(" + ex + ")";
			} else {
				return "HandleException(" + ex + ")";
			}
		} else {
			return "" + item;
		}
	}
}
