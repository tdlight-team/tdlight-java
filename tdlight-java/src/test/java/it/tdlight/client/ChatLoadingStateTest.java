package it.tdlight.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.tdlight.jni.TdApi;
import org.junit.jupiter.api.Test;

public class ChatLoadingStateTest {

	@Test
	public void marksInitialLoadAsVisibleAfterSuccess() {
		SimpleTestTelegramClient client = new SimpleTestTelegramClient(new TdApi.Ok());
		AuthorizationStateReadyLoadChats loader = new AuthorizationStateReadyLoadChats(client, new TdApi.ChatListMain());

		loader.onUpdate(readyUpdate());

		assertTrue(loader.isLoaded());
		assertTrue(client.getLastQuery() instanceof TdApi.LoadChats);
	}

	@Test
	public void treatsAlreadyLoadedResponseAsLoaded() {
		AuthorizationStateReadyLoadChats loader = new AuthorizationStateReadyLoadChats(
				new SimpleTestTelegramClient(new TdApi.Error(404, "Not Found")),
				new TdApi.ChatListArchive()
		);

		loader.onUpdate(readyUpdate());

		assertTrue(loader.isLoaded());
	}

	@Test
	public void leavesStateFalseAfterRequestFailure() {
		AuthorizationStateReadyLoadChats loader = new AuthorizationStateReadyLoadChats(
				new SimpleTestTelegramClient(new IllegalStateException("transport failed")),
				new TdApi.ChatListMain()
		);

		loader.onUpdate(readyUpdate());

		assertFalse(loader.isLoaded());
	}

	private static TdApi.UpdateAuthorizationState readyUpdate() {
		return new TdApi.UpdateAuthorizationState(new TdApi.AuthorizationStateReady());
	}
}
