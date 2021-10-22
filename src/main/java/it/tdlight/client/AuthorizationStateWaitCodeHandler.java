package it.tdlight.client;

import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.TelegramClient;
import it.tdlight.jni.TdApi.AuthorizationStateWaitCode;
import it.tdlight.jni.TdApi.CheckAuthenticationCode;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;

final class AuthorizationStateWaitCodeHandler implements GenericUpdateHandler<UpdateAuthorizationState> {

	private final TelegramClient client;
	private final ClientInteraction clientInteraction;
	private final ExceptionHandler exceptionHandler;

	public AuthorizationStateWaitCodeHandler(TelegramClient client,
			ClientInteraction clientInteraction,
			ExceptionHandler exceptionHandler) {
		this.client = client;
		this.clientInteraction = clientInteraction;
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateWaitCode.CONSTRUCTOR) {
			AuthorizationStateWaitCode authorizationState = (AuthorizationStateWaitCode) update.authorizationState;
			ParameterInfo parameterInfo = new ParameterInfoCode(authorizationState.codeInfo.phoneNumber,
					authorizationState.codeInfo.nextType,
					authorizationState.codeInfo.timeout,
					authorizationState.codeInfo.type
			);
			String code = clientInteraction.onParameterRequest(InputParameter.ASK_CODE, parameterInfo);
			CheckAuthenticationCode response = new CheckAuthenticationCode(code);
			client.send(response, ok -> {
				if (ok.getConstructor() == Error.CONSTRUCTOR) {
					throw new TelegramError((Error) ok);
				}
			}, exceptionHandler);
		}
	}
}
