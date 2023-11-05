package it.tdlight.client;

import it.tdlight.ExceptionHandler;
import it.tdlight.TelegramClient;
import it.tdlight.jni.TdApi;
import java.util.Locale;

final class AuthorizationStateWaitEmailCodeHandler implements GenericUpdateHandler<TdApi.UpdateAuthorizationState> {

	private final TelegramClient client;
	private final ClientInteraction clientInteraction;
	private final ExceptionHandler exceptionHandler;

	public AuthorizationStateWaitEmailCodeHandler(TelegramClient client,
			ClientInteraction clientInteraction,
			ExceptionHandler exceptionHandler) {
		this.client = client;
		this.clientInteraction = clientInteraction;
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public void onUpdate(TdApi.UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == TdApi.AuthorizationStateWaitEmailCode.CONSTRUCTOR) {
			TdApi.AuthorizationStateWaitEmailCode authorizationState = (TdApi.AuthorizationStateWaitEmailCode) update.authorizationState;
			ParameterInfo parameterInfo = new ParameterInfoEmailCode(authorizationState.allowAppleId,
					authorizationState.allowGoogleId,
					authorizationState.codeInfo.emailAddressPattern,
					authorizationState.codeInfo.length,
					EmailAddressResetState.valueOf(authorizationState.emailAddressResetState.getClass().getSimpleName().substring("EmailAddressResetState".length()).toUpperCase(Locale.ROOT))
			);
			clientInteraction.onParameterRequest(InputParameter.ASK_EMAIL_CODE, parameterInfo).whenComplete((emailAddress, ex) -> {
				if (ex != null) {
					exceptionHandler.onException(ex);
					return;
				}
				sendEmailCode(emailAddress);
			});
		}
	}

	private void sendEmailCode(String code) {
		TdApi.CheckAuthenticationEmailCode response = new TdApi.CheckAuthenticationEmailCode(new TdApi.EmailAddressAuthenticationCode(code));
		client.send(response, ok -> {
			if (ok.getConstructor() == TdApi.Error.CONSTRUCTOR) {
				throw new TelegramError((TdApi.Error) ok);
			}
		}, exceptionHandler);
	}
}
