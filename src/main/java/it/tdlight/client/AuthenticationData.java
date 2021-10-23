package it.tdlight.client;

public interface AuthenticationData {

	boolean isQrCode();

	boolean isBot();

	long getUserPhoneNumber();

	String getBotToken();

	static AuthenticationData qrCode() {
		return new AuthenticationDataQrCode();
	}

	static AuthenticationData user(long userPhoneNumber) {
		return new AuthenticationDataImpl(userPhoneNumber, null);
	}

	static AuthenticationData bot(String botToken) {
		return new AuthenticationDataImpl(null, botToken);
	}

	static ConsoleInteractiveAuthenticationData consoleLogin() {
		return new ConsoleInteractiveAuthenticationData();
	}
}
