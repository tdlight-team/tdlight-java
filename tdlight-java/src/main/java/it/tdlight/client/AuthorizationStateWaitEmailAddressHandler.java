package it.tdlight.client;

import it.tdlight.ExceptionHandler;
import it.tdlight.TelegramClient;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.AuthorizationStateWaitEmailAddress;
import it.tdlight.jni.TdApi.CheckAuthenticationCode;
import it.tdlight.jni.TdApi.CheckAuthenticationEmailCode;
import it.tdlight.jni.TdApi.SetAuthenticationEmailAddress;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;

final class AuthorizationStateWaitEmailAddressHandler implements GenericUpdateHandler<UpdateAuthorizationState> {

	private final TelegramClient client;
	private final ClientInteraction clientInteraction;
	private final ExceptionHandler exceptionHandler;

	public AuthorizationStateWaitEmailAddressHandler(TelegramClient client,
			ClientInteraction clientInteraction,
			ExceptionHandler exceptionHandler) {
		this.client = client;
		this.clientInteraction = clientInteraction;
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateWaitEmailAddress.CONSTRUCTOR) {
			AuthorizationStateWaitEmailAddress authorizationState = (AuthorizationStateWaitEmailAddress) update.authorizationState;
			ParameterInfo parameterInfo = new ParameterInfoEmailAddress(authorizationState.allowAppleId, authorizationState.allowGoogleId);
			clientInteraction.onParameterRequest(InputParameter.ASK_EMAIL_ADDRESS, parameterInfo).whenComplete((emailAddress, ex) -> {
				if (ex != null) {
					exceptionHandler.onException(ex);
					return;
				}
				sendEmailAddress(emailAddress);
			});
		}
	}

	private void sendEmailAddress(String emailAddress) {
		SetAuthenticationEmailAddress response = new SetAuthenticationEmailAddress(emailAddress);
		client.send(response, ok -> {
			if (ok.getConstructor() == TdApi.Error.CONSTRUCTOR) {
				throw new TelegramError((TdApi.Error) ok);
			}
		}, exceptionHandler);
	}
}
