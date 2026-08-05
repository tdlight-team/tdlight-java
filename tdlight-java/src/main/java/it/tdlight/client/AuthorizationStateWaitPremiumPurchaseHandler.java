package it.tdlight.client;

import it.tdlight.ExceptionHandler;
import it.tdlight.TelegramClient;
import it.tdlight.jni.TdApi.AuthorizationStateWaitPremiumPurchase;
import it.tdlight.jni.TdApi.Close;
import it.tdlight.jni.TdApi.Error;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;
import java.util.concurrent.atomic.AtomicBoolean;

final class AuthorizationStateWaitPremiumPurchaseHandler implements GenericUpdateHandler<UpdateAuthorizationState> {

	private final TelegramClient client;
	private final ExceptionHandler exceptionHandler;
	private final AtomicBoolean reported = new AtomicBoolean();

	AuthorizationStateWaitPremiumPurchaseHandler(TelegramClient client, ExceptionHandler exceptionHandler) {
		this.client = client;
		this.exceptionHandler = exceptionHandler;
	}

	@Override
	public void onUpdate(UpdateAuthorizationState update) {
		if (update.authorizationState.getConstructor() == AuthorizationStateWaitPremiumPurchase.CONSTRUCTOR
				&& reported.compareAndSet(false, true)) {
			UnsupportedOperationException failure = new UnsupportedOperationException(
					"Premium-purchase authorization isn't supported by SimpleTelegramClient. "
							+ "Use ClientFactory.create().createClient(), handle "
							+ "AuthorizationStateWaitPremiumPurchase directly, and close the factory afterwards."
			);
			try {
				client.send(new Close(), result -> {
					if (result instanceof Error) {
						failure.addSuppressed(new TelegramError((Error) result));
					}
				}, failure::addSuppressed);
			} catch (RuntimeException closeFailure) {
				failure.addSuppressed(closeFailure);
			}
			exceptionHandler.onException(failure);
		}
	}
}
