package it.tdlight.client;

public interface AuthenticationData {

	boolean isQrCode();

	boolean isBot();

	String getUserPhoneNumber();

	String getBotToken();

	static AuthenticationData qrCode() {
		return new AuthenticationDataQrCode();
	}

	/**
	 * Deprecated, use {@link #user(String)} instead
	 */
	@Deprecated
	static AuthenticationData user(long userPhoneNumber) {
		return user(String.valueOf(userPhoneNumber));
	}

	static AuthenticationData user(String userPhoneNumber) {
		return new AuthenticationDataImpl(userPhoneNumber, null);
	}

	static AuthenticationData bot(String botToken) {
		return new AuthenticationDataImpl(null, botToken);
	}

	static ConsoleInteractiveAuthenticationData consoleLogin() {
		return new ConsoleInteractiveAuthenticationData();
	}
}
