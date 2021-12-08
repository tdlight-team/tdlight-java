package it.tdlight.common.internal;

import it.tdlight.jni.TdApi;
import java.util.List;
import java.util.Objects;

public final class Result {

	private final int clientId;
	private final boolean isClosed;
	private final long[] clientEventIds;
	private final TdApi.Object[] clientEvents;
	private final int arrayOffset;
	private final int arrayLength;

	public Result(int clientId,
			boolean isClosed,
			long[] clientEventIds,
			TdApi.Object[] clientEvents,
			int arrayOffset,
			int arrayLength) {
		this.clientId = clientId;
		this.isClosed = isClosed;
		this.clientEventIds = clientEventIds;
		this.clientEvents = clientEvents;
		this.arrayOffset = arrayOffset;
		this.arrayLength = arrayLength;
	}

	public int clientId() {
		return clientId;
	}

	public boolean isClosed() {
		return isClosed;
	}

	public long[] clientEventIds() {
		return clientEventIds;
	}

	public TdApi.Object[] clientEvents() {
		return clientEvents;
	}

	public int arrayOffset() {
		return arrayOffset;
	}

	public int arrayLength() {
		return arrayLength;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		Result that = (Result) obj;
		return this.clientId == that.clientId && this.isClosed == that.isClosed && Objects.equals(this.clientEventIds,
				that.clientEventIds
		) && Objects.equals(this.clientEvents, that.clientEvents) && this.arrayOffset == that.arrayOffset
				&& this.arrayLength == that.arrayLength;
	}

	@Override
	public int hashCode() {
		return Objects.hash(clientId, isClosed, clientEventIds, clientEvents, arrayOffset, arrayLength);
	}

	@Override
	public String toString() {
		return "Result[" + "clientId=" + clientId + ", " + "isClosed=" + isClosed + ", " + "clientEventIds="
				+ clientEventIds + ", " + "clientEvents=" + clientEvents + ", " + "arrayOffset=" + arrayOffset + ", "
				+ "arrayLength=" + arrayLength + ']';
	}
}
