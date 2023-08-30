package it.tdlight.client;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.AuthorizationStateClosed;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;
import java.util.concurrent.CountDownLatch;

final class AuthorizationStateWaitForExit implements GenericUpdateHandler<TdApi.UpdateAuthorizationState> {

	private final Runnable setClosed;

	public AuthorizationStateWaitForExit(Runnable setClosed) {
		this.setClosed = setClosed;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateClosed.CONSTRUCTOR) {
			setClosed.run();
		}
	}
}
