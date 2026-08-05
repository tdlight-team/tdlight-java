package it.tdlight.client;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
final class AuthenticationDataImpl implements SimpleAuthenticationSupplier<AuthenticationDataImpl> {

	private final String userPhoneNumber;
	private final String botToken;
	private final boolean test;
	/**
	 * Safe string representation of the bot token
	 */
	private final String botTokenId;

	AuthenticationDataImpl(String userPhoneNumber, String botToken, boolean test) {
		if ((userPhoneNumber == null) == (botToken == null)) {
			throw new IllegalArgumentException("Please use either a bot token or a phone number");
		}
		if (botToken != null) {
			if (botToken.length() < 5 || botToken.length() > 200) {
				throw new IllegalArgumentException("Bot token is invalid");
			}
		}
		this.userPhoneNumber = userPhoneNumber;
		this.botToken = botToken;
		this.test = test;
		if (botToken != null) {
			botTokenId = safeBotId(botToken);
		} else {
			botTokenId = "";
		}
	}

	static String safeBotId(String botToken) {
		int separator = botToken.indexOf(':');
		String candidateId = separator > 0 ? botToken.substring(0, separator) : "";
		return isDecimalBotId(candidateId) ? candidateId : "[REDACTED]";
	}

	private static boolean isDecimalBotId(String value) {
		if (value.isEmpty() || value.length() > 19) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character < '0' || character > '9') {
				return false;
			}
		}
		try {
			return Long.parseLong(value) > 0;
		} catch (NumberFormatException ignored) {
			return false;
		}
	}

	@Override
	public boolean isQrCode() {
		return false;
	}

	@Override
	public boolean isBot() {
		return botToken != null;
	}

	public boolean isTest() {
		return test;
	}

	@Override
	public String getUserPhoneNumber() {
		if (userPhoneNumber == null) {
			throw new UnsupportedOperationException("This is not a user");
		}
		return userPhoneNumber;
	}

	@Override
	public String getBotToken() {
		if (botToken == null) {
			throw new UnsupportedOperationException("This is not a bot");
		}
		return botToken;
	}

	@Override
	public String toString() {
		String value;
		if (userPhoneNumber != null) {
			value = userPhoneNumber;
		} else  {
			value = botTokenId;
		}
		if (test) {
			return value + " (test)";
		} else {
			return value;
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
		AuthenticationDataImpl that = (AuthenticationDataImpl) o;
		return Objects.equals(userPhoneNumber, that.userPhoneNumber) && Objects.equals(botToken, that.botToken)
				&& Objects.equals(test, that.test);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userPhoneNumber, botToken, test);
	}

	@Override
	public CompletableFuture<AuthenticationDataImpl> get() {
		return completedFuture(this);
	}
}
