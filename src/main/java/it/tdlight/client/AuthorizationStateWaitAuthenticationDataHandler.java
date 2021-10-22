package it.tdlight.client;

import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.TelegramClient;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.AuthorizationStateWaitPhoneNumber;
import it.tdlight.jni.TdApi.PhoneNumberAuthenticationSettings;
import it.tdlight.jni.TdApi.SetAuthenticationPhoneNumber;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;

final class AuthorizationStateWaitAuthenticationDataHandler implements GenericUpdateHandler<UpdateAuthorizationState> {

	private final TelegramClient client;
	private final Authenticable authenticable;
	private final ExceptionHandler exceptionHandler;

	public AuthorizationStateWaitAuthenticationDataHandler(TelegramClient client,
			Authenticable authenticable,
			ExceptionHandler exceptionHandler) {
		this.client = client;
		this.authenticable = authenticable;
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR) {
			AuthenticationData authenticationData = authenticable.getAuthenticationData();

			// Ask login parameters
			if (authenticationData instanceof ConsoleInteractiveAuthenticationData) {
				((ConsoleInteractiveAuthenticationData) authenticationData).askData();
			}

			if (authenticationData.isBot()) {
				String botToken = authenticationData.getBotToken();
				TdApi.CheckAuthenticationBotToken response = new TdApi.CheckAuthenticationBotToken(botToken);
				client.send(response, ok -> {
					if (ok.getConstructor() == Error.CONSTRUCTOR) {
						throw new TelegramError((Error) ok);
					}
				}, exceptionHandler);
			} else if (authenticationData.isQrCode()) {
				TdApi.RequestQrCodeAuthentication response = new TdApi.RequestQrCodeAuthentication();
				client.send(response, ok -> {
					if (ok.getConstructor() == Error.CONSTRUCTOR) {
						throw new TelegramError((Error) ok);
					}
				}, exceptionHandler);
			} else {
				PhoneNumberAuthenticationSettings phoneSettings = new PhoneNumberAuthenticationSettings(false, false, false);

				String phoneNumber = String.valueOf(authenticationData.getUserPhoneNumber());
				SetAuthenticationPhoneNumber response = new SetAuthenticationPhoneNumber(phoneNumber, phoneSettings);
				client.send(response, ok -> {
					if (ok.getConstructor() == Error.CONSTRUCTOR) {
						throw new TelegramError((Error) ok);
					}
				}, exceptionHandler);
			}
		}
	}
}
