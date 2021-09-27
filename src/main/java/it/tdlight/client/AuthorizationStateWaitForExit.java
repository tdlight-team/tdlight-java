package it.tdlight.client;

import it.tdlight.common.TelegramClient;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.AuthorizationStateClosed;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;
import java.util.concurrent.CountDownLatch;

final class AuthorizationStateWaitForExit implements GenericUpdateHandler<TdApi.UpdateAuthorizationState> {

	private final CountDownLatch closed;

	public AuthorizationStateWaitForExit(CountDownLatch closed) {
		this.closed = closed;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateClosed.CONSTRUCTOR) {
			closed.countDown();
		}
	}
}
