package it.tdlight.client;

import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.TelegramClient;
import it.tdlight.jni.TdApi.AuthorizationStateWaitOtherDeviceConfirmation;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;

final class AuthorizationStateWaitOtherDeviceConfirmationHandler
		implements GenericUpdateHandler<UpdateAuthorizationState> {

	private final TelegramClient client;
	private final ClientInteraction clientInteraction;
	private final ExceptionHandler exceptionHandler;

	public AuthorizationStateWaitOtherDeviceConfirmationHandler(TelegramClient client,
			ClientInteraction clientInteraction,
			ExceptionHandler exceptionHandler) {
		this.client = client;
		this.clientInteraction = clientInteraction;
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR) {
			AuthorizationStateWaitOtherDeviceConfirmation authorizationState =
					(AuthorizationStateWaitOtherDeviceConfirmation) update.authorizationState;
			ParameterInfo parameterInfo = new ParameterInfoNotifyLink(authorizationState.link);
			clientInteraction.onParameterRequest(InputParameter.NOTIFY_LINK, parameterInfo);
		}
	}
}
