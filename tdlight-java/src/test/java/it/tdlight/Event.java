package it.tdlight;

import it.tdlight.jni.TdApi;
import java.util.Objects;

public final class Event {

	private final int clientId;
	private final long eventId;
	private final TdApi.Object event;

	Event(int clientId, long eventId, TdApi.Object event) {
		this.clientId = clientId;
		this.eventId = eventId;
		this.event = event;
	}

	public int clientId() {
		return clientId;
	}

	public long eventId() {
		return eventId;
	}

	public TdApi.Object event() {
		return event;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		Event that = (Event) obj;
		return this.clientId == that.clientId && this.eventId == that.eventId && Objects.equals(this.event, that.event);
	}

	@Override
	public int hashCode() {
		return Objects.hash(clientId, eventId, event);
	}

	@Override
	public String toString() {
		return "Event[" + "clientId=" + clientId + ", " + "eventId=" + eventId + ", " + "event=" + event + ']';
	}
}
