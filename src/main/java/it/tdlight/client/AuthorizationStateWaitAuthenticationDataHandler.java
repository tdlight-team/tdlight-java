package it.tdlight.client;

		import it.tdlight.common.ExceptionHandler;
		import it.tdlight.common.TelegramClient;
		import it.tdlight.common.utils.ScannerUtils;
		import it.tdlight.jni.TdApi;
		import it.tdlight.jni.TdApi.AuthorizationStateWaitEncryptionKey;
		import it.tdlight.jni.TdApi.AuthorizationStateWaitPhoneNumber;
		import it.tdlight.jni.TdApi.CheckDatabaseEncryptionKey;
		import it.tdlight.jni.TdApi.Error;
		import it.tdlight.jni.TdApi.PhoneNumberAuthenticationSettings;
		import it.tdlight.jni.TdApi.SetAuthenticationPhoneNumber;
		import it.tdlight.jni.TdApi.UpdateAuthorizationState;
		import org.slf4j.Logger;
		import org.slf4j.LoggerFactory;

final class AuthorizationStateWaitAuthenticationDataHandler implements GenericUpdateHandler<UpdateAuthorizationState> {

	private static final Logger logger = LoggerFactory.getLogger(AuthorizationStateWaitAuthenticationDataHandler.class);

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
			if (authenticationData.isBot()) {
				String botToken = authenticationData.getBotToken();
				TdApi.CheckAuthenticationBotToken response = new TdApi.CheckAuthenticationBotToken(botToken);
				client.send(response, ok -> {
					if (ok.getConstructor() == Error.CONSTRUCTOR) {
						throw new TelegramError((Error) ok);
					}
				}, ex -> {
					logger.error("Failed to set TDLight phone number or bot token!", ex);
					exceptionHandler.onException(ex);
				});
			} else {
				PhoneNumberAuthenticationSettings phoneSettings = new PhoneNumberAuthenticationSettings(false, false, false);

				String phoneNumber = String.valueOf(authenticationData.getUserPhoneNumber());
				SetAuthenticationPhoneNumber response = new SetAuthenticationPhoneNumber(phoneNumber, phoneSettings);
				client.send(response, ok -> {
					if (ok.getConstructor() == Error.CONSTRUCTOR) {
						throw new TelegramError((Error) ok);
					}
				}, ex -> {
					logger.error("Failed to set TDLight phone number!", ex);
					exceptionHandler.onException(ex);
				});
			}
		}
	}
}
