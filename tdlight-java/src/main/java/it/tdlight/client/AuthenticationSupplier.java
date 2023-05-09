package it.tdlight.client;

import java.util.concurrent.CompletableFuture;

public interface AuthenticationSupplier<T extends AuthenticationData> {

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
		return new AuthenticationDataImpl(userPhoneNumber, null);
	}

	static SimpleAuthenticationSupplier<?> bot(String botToken) {
		return new AuthenticationDataImpl(null, botToken);
	}

	static ConsoleInteractiveAuthenticationData consoleLogin() {
		return new ConsoleInteractiveAuthenticationData();
	}
}
