package it.tdlight.client;

import static java.util.concurrent.CompletableFuture.completedFuture;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@SuppressWarnings("unused")
final class AuthenticationDataImpl implements SimpleAuthenticationSupplier<AuthenticationDataImpl> {

	private final String userPhoneNumber;
	private final String botToken;

	AuthenticationDataImpl(String userPhoneNumber, String botToken) {
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

	@Override
	public boolean isQrCode() {
		return false;
	}

	@Override
	public boolean isBot() {
		return botToken != null;
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
		if (userPhoneNumber != null) {
			return userPhoneNumber;
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
		AuthenticationDataImpl that = (AuthenticationDataImpl) o;
		return Objects.equals(userPhoneNumber, that.userPhoneNumber) && Objects.equals(botToken, that.botToken);
	}

	@Override
	public int hashCode() {
		return Objects.hash(userPhoneNumber, botToken);
	}

	@Override
	public CompletableFuture<AuthenticationDataImpl> get() {
		return completedFuture(this);
	}
}
