package it.tdlight.client;

public interface AuthenticationData {

	boolean isQrCode();

	boolean isBot();

	String getUserPhoneNumber();

	String getBotToken();
}
