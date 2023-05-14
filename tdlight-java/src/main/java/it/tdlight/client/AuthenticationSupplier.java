package it.tdlight.client;

import java.util.concurrent.CompletableFuture;

public interface AuthenticationSupplier<T extends AuthenticationData> {

	/**
	 * User used in Telegram Test Servers
	 * @param value any number from 0001 to 9999
	 */
	static SimpleAuthenticationSupplier<?> testUser(int value) {
		if (value < 1) {
			throw new IllegalArgumentException("value must be greater than 0");
		}
		if (value > 9999) {
			throw new IllegalArgumentException("value must be lower than 10000");
		}
		return new AuthenticationDataImpl("999664" + value, null, true);
	}

	CompletableFuture<T> get();

	static SimpleAuthenticationSupplier<?> qrCode() {
		return new AuthenticationDataQrCode();
	}

	/**
	 * Deprecated, use {@link #user(String)} instead
	 */
	@Deprecated
	static SimpleAuthenticationSupplier<?> user(long userPhoneNumber) {
		return user(String.valueOf(userPhoneNumber));
	}

	static SimpleAuthenticationSupplier<?> user(String userPhoneNumber) {
		return new AuthenticationDataImpl(userPhoneNumber, null, false);
	}

	static SimpleAuthenticationSupplier<?> bot(String botToken) {
		return new AuthenticationDataImpl(null, botToken, false);
	}

	static ConsoleInteractiveAuthenticationData consoleLogin() {
		return new ConsoleInteractiveAuthenticationData();
	}
}
