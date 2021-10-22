package it.tdlight.client;

import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.TelegramClient;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.AuthorizationStateWaitEncryptionKey;
import it.tdlight.jni.TdApi.CheckDatabaseEncryptionKey;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;

final class AuthorizationStateWaitEncryptionKeyHandler implements GenericUpdateHandler<UpdateAuthorizationState> {

	private final TelegramClient client;
	private final ExceptionHandler exceptionHandler;

	public AuthorizationStateWaitEncryptionKeyHandler(TelegramClient client, ExceptionHandler exceptionHandler) {
		this.client = client;
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateWaitEncryptionKey.CONSTRUCTOR) {
			client.send(new CheckDatabaseEncryptionKey(), ok -> {
				if (ok.getConstructor() == TdApi.Error.CONSTRUCTOR) {
					throw new TelegramError((TdApi.Error) ok);
				}
			}, exceptionHandler);
		}
	}
}
