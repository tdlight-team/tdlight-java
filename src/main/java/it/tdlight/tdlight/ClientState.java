package it.tdlight.tdlight;

import java.util.StringJoiner;

public class ClientState {

	private boolean hasClientId;
	private long clientId;
	private boolean initialized;
	private boolean readyToReceive;
	private boolean readyToSend;

	private ClientState(boolean hasClientId, long clientId, boolean initialized, boolean readyToReceive, boolean readyToSend) {
		this.hasClientId = hasClientId;
		this.clientId = clientId;
		this.initialized = initialized;
		this.readyToReceive = readyToReceive;
		this.readyToSend = readyToSend;
	}

	public static ClientState of(boolean hasClientId, long clientId, boolean initialized, boolean readyToReceive, boolean readyToSend) {
		return new ClientState(hasClientId, clientId, initialized, readyToReceive, readyToSend);
	}

	public boolean hasClientId() {
		return hasClientId;
	}

	public long getClientId() {
		return clientId;
	}

	public boolean isInitialized() {
		return initialized;
	}

	public boolean isReadyToReceive() {
		return readyToReceive;
	}

	public boolean isReadyToSend() {
		return readyToSend;
	}

	public ClientState setHasClientId(boolean hasClientId) {
		this.hasClientId = hasClientId;
		return this;
	}

	public ClientState setClientId(long clientId) {
		this.clientId = clientId;
		return this;
	}

	public ClientState setInitialized(boolean initialized) {
		this.initialized = initialized;
		return this;
	}

	public ClientState setReadyToReceive(boolean readyToReceive) {
		this.readyToReceive = readyToReceive;
		return this;
	}

	public ClientState setReadyToSend(boolean readyToSend) {
		this.readyToSend = readyToSend;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		ClientState that = (ClientState) o;

		if (hasClientId != that.hasClientId) {
			return false;
		}
		if (clientId != that.clientId) {
			return false;
		}
		if (initialized != that.initialized) {
			return false;
		}
		if (readyToReceive != that.readyToReceive) {
			return false;
		}
		return readyToSend == that.readyToSend;
	}

	@Override
	public int hashCode() {
		int result = (hasClientId ? 1 : 0);
		result = 31 * result + (int) (clientId ^ (clientId >>> 32));
		result = 31 * result + (initialized ? 1 : 0);
		result = 31 * result + (readyToReceive ? 1 : 0);
		result = 31 * result + (readyToSend ? 1 : 0);
		return result;
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", ClientState.class.getSimpleName() + "[", "]")
				.add("hasClientId=" + hasClientId)
				.add("clientId=" + clientId)
				.add("initialized=" + initialized)
				.add("readyToReceive=" + readyToReceive)
				.add("readyToSend=" + readyToSend)
				.toString();
	}
}
