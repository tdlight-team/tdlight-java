package it.tdlight.client;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.tdlight.jni.TdApi;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

public class AuthorizationStateReadyGetMeTest {

	@Test
	public void completesExceptionallyWhenTdlibReturnsAnError() {
		SimpleTestTelegramClient client = new SimpleTestTelegramClient(new TdApi.Error(401, "Unauthorized"));
		AuthorizationStateReadyGetMe handler = newHandler(client);

		handler.onUpdate(readyUpdate());

		CompletionException failure = assertThrows(CompletionException.class, () -> handler.getMeAsync().join());
		assertTrue(failure.getCause() instanceof TelegramError);
	}

	@Test
	public void completesExceptionallyWhenSendingFails() {
		IllegalStateException cause = new IllegalStateException("transport failed");
		AuthorizationStateReadyGetMe handler = newHandler(new SimpleTestTelegramClient(cause));

		handler.onUpdate(readyUpdate());

		CompletionException failure = assertThrows(CompletionException.class, () -> handler.getMeAsync().join());
		assertSame(cause, failure.getCause());
	}

	@Test
	public void completesExceptionallyWhenTdlibReturnsNull() {
		AuthorizationStateReadyGetMe handler = newHandler(new SimpleTestTelegramClient((TdApi.Object) null));

		handler.onUpdate(readyUpdate());

		CompletionException failure = assertThrows(CompletionException.class, () -> handler.getMeAsync().join());
		assertTrue(failure.getCause() instanceof IllegalStateException);
	}

	private static AuthorizationStateReadyGetMe newHandler(SimpleTestTelegramClient client) {
		return new AuthorizationStateReadyGetMe(
				client,
				new AuthorizationStateReadyLoadChats(client, new TdApi.ChatListMain()),
				new AuthorizationStateReadyLoadChats(client, new TdApi.ChatListArchive())
		);
	}

	private static TdApi.UpdateAuthorizationState readyUpdate() {
		return new TdApi.UpdateAuthorizationState(new TdApi.AuthorizationStateReady());
	}
}
