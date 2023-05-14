package it.tdlight.client;

import it.tdlight.ExceptionHandler;
import it.tdlight.TelegramClient;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.AuthorizationStateWaitCode;
import it.tdlight.jni.TdApi.CheckAuthenticationCode;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;

final class AuthorizationStateWaitCodeHandler implements GenericUpdateHandler<UpdateAuthorizationState> {

	private final TelegramClient client;
	private final ClientInteraction clientInteraction;
	private final String testCode;
	private final ExceptionHandler exceptionHandler;

	public AuthorizationStateWaitCodeHandler(TelegramClient client,
			ClientInteraction clientInteraction,
			String testCode,
			ExceptionHandler exceptionHandler) {
		this.client = client;
		this.clientInteraction = clientInteraction;
		this.testCode = testCode;
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateWaitCode.CONSTRUCTOR) {
			AuthorizationStateWaitCode authorizationState = (AuthorizationStateWaitCode) update.authorizationState;
			if (testCode != null) {
				sendCode(testCode);
			} else {
				ParameterInfo parameterInfo = new ParameterInfoCode(authorizationState.codeInfo.phoneNumber,
						authorizationState.codeInfo.nextType,
						authorizationState.codeInfo.timeout,
						authorizationState.codeInfo.type
				);
				clientInteraction.onParameterRequest(InputParameter.ASK_CODE, parameterInfo).whenComplete((code, ex) -> {
					if (ex != null) {
						exceptionHandler.onException(ex);
						return;
					}
					sendCode(code);
				});
			}
		}
	}

	private void sendCode(String code) {
		CheckAuthenticationCode response = new CheckAuthenticationCode(code);
		client.send(response, ok -> {
			if (ok.getConstructor() == TdApi.Error.CONSTRUCTOR) {
				throw new TelegramError((TdApi.Error) ok);
			}
		}, exceptionHandler);
	}
}
