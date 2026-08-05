package it.tdlight.client;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.tdlight.jni.TdApi;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.Test;

public class TemporaryMessageHandlerTest {

	@Test
	public void closeFailsAndRemovesEveryPendingMessage() {
		ConcurrentMap<TemporaryMessageURL, CompletableFuture<TdApi.Message>> pending = new ConcurrentHashMap<>();
		TemporaryMessageHandler handler = new TemporaryMessageHandler(pending);
		CompletableFuture<TdApi.Message> first = handler.register(new TemporaryMessageURL(1, -1));
		CompletableFuture<TdApi.Message> second = handler.register(new TemporaryMessageURL(1, -2));
		IllegalStateException cause = new IllegalStateException("client closed");

		handler.close(cause);

		assertFailure(first, cause);
		assertFailure(second, cause);
		assertTrue(pending.isEmpty());
	}

	@Test
	public void registrationAfterCloseFailsImmediatelyWithoutLeakingAMapEntry() {
		ConcurrentMap<TemporaryMessageURL, CompletableFuture<TdApi.Message>> pending = new ConcurrentHashMap<>();
		TemporaryMessageHandler handler = new TemporaryMessageHandler(pending);
		IllegalStateException cause = new IllegalStateException("client closed");
		handler.close(cause);

		CompletableFuture<TdApi.Message> future = handler.register(new TemporaryMessageURL(1, -1));

		assertFailure(future, cause);
		assertTrue(pending.isEmpty());
	}

	@Test
	public void cancellingARegisteredMessageRemovesItsMapEntry() {
		ConcurrentMap<TemporaryMessageURL, CompletableFuture<TdApi.Message>> pending = new ConcurrentHashMap<>();
		TemporaryMessageHandler handler = new TemporaryMessageHandler(pending);
		CompletableFuture<TdApi.Message> future = handler.register(new TemporaryMessageURL(1, -1));

		future.cancel(false);

		assertTrue(pending.isEmpty());
	}

	@Test
	public void cancellingTheWaitBeforeSendCompletesCancelsTheSendRequest() {
		TemporaryMessageHandler handler = new TemporaryMessageHandler(new ConcurrentHashMap<>());
		CompletableFuture<TdApi.Message> sendRequest = new CompletableFuture<>();
		CompletableFuture<TdApi.Message> result = handler.waitForSentMessage(sendRequest);

		result.cancel(false);

		assertTrue(sendRequest.isCancelled());
	}

	@Test
	public void nullSendResultFailsTheWaitInsteadOfLeavingItPending() {
		ConcurrentMap<TemporaryMessageURL, CompletableFuture<TdApi.Message>> pending = new ConcurrentHashMap<>();
		TemporaryMessageHandler handler = new TemporaryMessageHandler(pending);
		CompletableFuture<TdApi.Message> sendRequest = CompletableFuture.completedFuture(null);

		CompletableFuture<TdApi.Message> result = handler.waitForSentMessage(sendRequest);

		CompletionException failure = assertThrows(CompletionException.class, result::join);
		assertTrue(failure.getCause() instanceof IllegalStateException);
		assertTrue(pending.isEmpty());
	}

	@Test
	public void cancellingTheWaitAfterRegistrationRemovesTheMapEntry() {
		ConcurrentMap<TemporaryMessageURL, CompletableFuture<TdApi.Message>> pending = new ConcurrentHashMap<>();
		TemporaryMessageHandler handler = new TemporaryMessageHandler(pending);
		CompletableFuture<TdApi.Message> sendRequest = new CompletableFuture<>();
		CompletableFuture<TdApi.Message> result = handler.waitForSentMessage(sendRequest);
		TdApi.Message temporaryMessage = new TdApi.Message();
		temporaryMessage.chatId = 1;
		temporaryMessage.id = -1;
		sendRequest.complete(temporaryMessage);

		result.cancel(false);

		assertTrue(pending.isEmpty());
	}

	private static void assertFailure(CompletableFuture<?> future, Throwable cause) {
		CompletionException failure = assertThrows(CompletionException.class, future::join);
		assertSame(cause, failure.getCause());
	}
}
