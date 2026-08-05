package it.tdlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.AuthorizationStateWaitPremiumPurchase;
import it.tdlight.jni.TdApi.Close;
import it.tdlight.jni.TdApi.Ok;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

public class AuthorizationStateWaitPremiumPurchaseHandlerTest {

	@Test
	public void reportsUnsupportedPremiumPurchaseWithoutWaitingForInput() {
		AtomicInteger failureCount = new AtomicInteger();
		AtomicReference<Throwable> failure = new AtomicReference<>();
		SimpleTestTelegramClient client = new SimpleTestTelegramClient(new Ok());
		AuthorizationStateWaitPremiumPurchaseHandler handler = new AuthorizationStateWaitPremiumPurchaseHandler(client, error -> {
			failureCount.incrementAndGet();
			failure.set(error);
		});
		UpdateAuthorizationState update = new UpdateAuthorizationState(
				new AuthorizationStateWaitPremiumPurchase("premium_product", 30, "support@example.com", "Support")
		);

		handler.onUpdate(update);
		handler.onUpdate(update);

		assertEquals(1, failureCount.get());
		assertTrue(failure.get() instanceof UnsupportedOperationException);
		assertTrue(failure.get().getMessage().contains("ClientFactory.create().createClient()"));
		assertEquals(Close.CONSTRUCTOR, client.getLastQuery().getConstructor());
	}

	@Test
	public void retainsRejectedCloseAsDiagnosticContext() {
		AtomicReference<Throwable> failure = new AtomicReference<>();
		SimpleTestTelegramClient client = new SimpleTestTelegramClient(new TdApi.Error(500, "close rejected"));
		AuthorizationStateWaitPremiumPurchaseHandler handler =
				new AuthorizationStateWaitPremiumPurchaseHandler(client, failure::set);

		handler.onUpdate(new UpdateAuthorizationState(
				new AuthorizationStateWaitPremiumPurchase("premium_product", 30, "support@example.com", "Support")
		));

		assertEquals(1, failure.get().getSuppressed().length);
		assertInstanceOf(TelegramError.class, failure.get().getSuppressed()[0]);
	}
}
