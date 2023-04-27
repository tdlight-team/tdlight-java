package it.tdlight.client;

import java.util.concurrent.CompletableFuture;

class AuthenticationDataQrCode implements AuthenticationSupplier<AuthenticationDataQrCode>, AuthenticationData {

	@Override
	public boolean isQrCode() {
		return true;
	}

	@Override
	public boolean isBot() {
		return false;
	}

	@Override
	public String getUserPhoneNumber() {
		throw new UnsupportedOperationException("This is not a user");
	}

	@Override
	public String getBotToken() {
		throw new UnsupportedOperationException("This is not a bot");
	}

	@Override
	public CompletableFuture<AuthenticationDataQrCode> get() {
		return CompletableFuture.completedFuture(this);
	}
}
