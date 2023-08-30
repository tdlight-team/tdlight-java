package it.tdlight.client;

import it.tdlight.jni.TdApi.AuthorizationStateReady;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;

final class AuthorizationStateWaitReady implements GenericUpdateHandler<UpdateAuthorizationState> {

	private final Runnable setReady;

	public AuthorizationStateWaitReady(Runnable setReady) {
		this.setReady = setReady;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateReady.CONSTRUCTOR) {
			setReady.run();
		}
	}
}
