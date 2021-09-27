package it.tdlight.client;

class AuthenticationDataQrCode implements AuthenticationData {

	@Override
	public boolean isQrCode() {
		return true;
	}

	@Override
	public boolean isBot() {
		return false;
	}

	@Override
	public long getUserPhoneNumber() {
		throw new UnsupportedOperationException("This is not a user");
	}

	@Override
	public String getBotToken() {
		throw new UnsupportedOperationException("This is not a bot");
	}
}
