package it.tdlight.client;

import it.tdlight.TelegramClient;
import it.tdlight.jni.TdApi.AuthorizationStateReady;
import it.tdlight.jni.TdApi.GetMe;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;
import it.tdlight.jni.TdApi.User;
import it.tdlight.jni.TdApi.Error;
import it.tdlight.jni.TdApi.UserTypeRegular;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class AuthorizationStateReadyGetMe implements GenericUpdateHandler<UpdateAuthorizationState> {

	private static final Logger logger = LoggerFactory.getLogger(AuthorizationStateReadyGetMe.class);

	private final TelegramClient client;
	private final CompletableFuture<Void> meReceived = new CompletableFuture<>();
	private final AtomicReference<User> me = new AtomicReference<>();
	private final AuthorizationStateReadyLoadChats mainChatsLoader;
	private final AuthorizationStateReadyLoadChats archivedChatsLoader;

	public AuthorizationStateReadyGetMe(TelegramClient client,
			AuthorizationStateReadyLoadChats mainChatsLoader,
			AuthorizationStateReadyLoadChats archivedChatsLoader) {
		this.client = client;
		this.mainChatsLoader = mainChatsLoader;
		this.archivedChatsLoader = archivedChatsLoader;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateReady.CONSTRUCTOR) {
			client.send(new GetMe(), result -> {
				if (result == null) {
					this.meReceived.completeExceptionally(new IllegalStateException("TdApi.GetMe returned null"));
					return;
				}
				if (result.getConstructor() == Error.CONSTRUCTOR) {
					this.meReceived.completeExceptionally(new TelegramError((Error) result));
					return;
				}
				if (!(result instanceof User)) {
					this.meReceived.completeExceptionally(new IllegalStateException(
							"TdApi.GetMe returned " + result.getClass().getName()
					));
					return;
				}
				User user = (User) result;
				this.me.set(user);
				this.meReceived.complete(null);
				if (user.type.getConstructor() == UserTypeRegular.CONSTRUCTOR) {
					mainChatsLoader.onUpdate(update);
					archivedChatsLoader.onUpdate(update);
				}
			}, error -> {
				this.meReceived.completeExceptionally(error);
				logger.warn("Failed to execute TdApi.GetMe()", error);
			});
		}
	}

	public User getMe() {
		return me.get();
	}

	public CompletableFuture<User> getMeAsync() {
		return meReceived.thenApply(v -> me.get());
	}
}
