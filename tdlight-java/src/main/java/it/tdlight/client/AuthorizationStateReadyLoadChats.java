package it.tdlight.client;

import it.tdlight.TelegramClient;
import it.tdlight.jni.TdApi.AuthorizationStateReady;
import it.tdlight.jni.TdApi.ChatList;
import it.tdlight.jni.TdApi.Error;
import it.tdlight.jni.TdApi.LoadChats;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AuthorizationStateReadyLoadChats implements GenericUpdateHandler<UpdateAuthorizationState> {

	private static final Logger logger = LoggerFactory.getLogger(AuthorizationStateReadyLoadChats.class);

	private final TelegramClient client;
	private final ChatList chatList;

	private boolean loaded;

	public AuthorizationStateReadyLoadChats(TelegramClient client, ChatList chatList) {
		this.client = client;
		this.chatList = chatList;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateReady.CONSTRUCTOR) {
			client.send(new LoadChats(chatList, 2000), ok -> {
				if (ok.getConstructor() == Error.CONSTRUCTOR) {
					Error error = (Error) ok;
					if (error.code != 404) {
						throw new TelegramError((Error) ok);
					}
					logger.debug("All {} chats have already been loaded", chatList);
				} else {
					logger.debug("All chats have been loaded");
				}
			}, error -> logger.warn("Failed to execute TdApi.LoadChats()"));
		}
	}

	public boolean isLoaded() {
		return loaded;
	}
}
