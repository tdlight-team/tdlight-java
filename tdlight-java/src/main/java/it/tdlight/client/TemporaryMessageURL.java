package it.tdlight.client;

import java.util.Objects;

final class TemporaryMessageURL {

	private final long chatId;
	private final long messageId;

	TemporaryMessageURL(long chatId, long messageId) {
		this.chatId = chatId;
		this.messageId = messageId;
	}

	public long chatId() {
		return chatId;
	}

	public long messageId() {
		return messageId;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		TemporaryMessageURL that = (TemporaryMessageURL) obj;
		return this.chatId == that.chatId && this.messageId == that.messageId;
	}

	@Override
	public int hashCode() {
		return Objects.hash(chatId, messageId);
	}

	@Override
	public String toString() {
		return "TemporaryMessageURL[" + "chatId=" + chatId + ", " + "messageId=" + messageId + ']';
	}


}
