package it.tdlight.client;

import java.util.Objects;
import java.util.StringJoiner;

@SuppressWarnings("unused")
public final class AuthenticationData {
	private final Long userPhoneNumber;
	private final String botToken;

	private AuthenticationData(Long userPhoneNumber, String botToken) {
		if ((userPhoneNumber == null) == (botToken == null)) {
			throw new IllegalArgumentException("Please use either a bot token or a phone number");
		}
		if (botToken != null) {
			if (botToken.length() < 5 || botToken.length() > 200) {
				throw new IllegalArgumentException("Bot token is invalid: " + botToken);
			}
		}
		this.userPhoneNumber = userPhoneNumber;
		this.botToken = botToken;
	}

	public static AuthenticationData user(long userPhoneNumber) {
		return new AuthenticationData(userPhoneNumber, null);
	}

	public static AuthenticationData bot(String botToken) {
		return new AuthenticationData(null, botToken);
	}

	public boolean isBot() {
		return botToken != null;
	}

	public Long getUserPhoneNumber() {
		if (userPhoneNumber == null) {
			throw new UnsupportedOperationException("This is not a user");
		}
		return userPhoneNumber;
	}

	public String getBotToken() {
		if (botToken == null) {
			throw new UnsupportedOperationException("This is not a bot");
		}
		return botToken;
	}

	@Override
	public String toString() {
		if (userPhoneNumber != null) {
			return "+" + userPhoneNumber;
		} else {
			return "\"" + botToken + "\"";
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
		AuthenticationData that = (AuthenticationData) o;
		return Objects.equals(userPhoneNumber, that.userPhoneNumber) && Objects.equals(botToken, that.botToken);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userPhoneNumber, botToken);
	}
}
