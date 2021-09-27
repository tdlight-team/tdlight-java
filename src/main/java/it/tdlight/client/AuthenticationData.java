package it.tdlight.client;

public interface AuthenticationData {

	boolean isBot();

	long getUserPhoneNumber();

	String getBotToken();

	static AuthenticationData user(long userPhoneNumber) {
		return new AuthenticationDataImpl(userPhoneNumber, null);
	}

	static AuthenticationData bot(String botToken) {
		return new AuthenticationDataImpl(null, botToken);
	}

	static AuthenticationData consoleLogin() {
		return new ConsoleInteractiveAuthenticationData();
	}
}
