package it.tdlight.client;

import it.tdlight.common.TelegramClient;
import it.tdlight.jni.TdApi.AuthorizationStateReady;
import it.tdlight.jni.TdApi.GetMe;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;
import it.tdlight.jni.TdApi.User;
import it.tdlight.jni.TdApi.Error;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AuthorizationStateReadyGetMe implements GenericUpdateHandler<UpdateAuthorizationState> {

	private static final Logger logger = LoggerFactory.getLogger(AuthorizationStateReadyGetMe.class);

	private final TelegramClient client;
	private final AtomicReference<User> me;

	public AuthorizationStateReadyGetMe(TelegramClient client, AtomicReference<User> me) {
		this.client = client;
		this.me = me;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateReady.CONSTRUCTOR) {
			client.send(new GetMe(), me -> {
				if (me.getConstructor() == Error.CONSTRUCTOR) {
					throw new TelegramError((Error) me);
				}
				this.me.set((User) me);
			}, error -> logger.warn("Failed to execute TdApi.GetMe()"));
		}
	}
}
