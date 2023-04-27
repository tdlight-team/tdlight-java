package it.tdlight.client;

import it.tdlight.utils.ScannerUtils;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public final class ConsoleInteractiveAuthenticationData implements AuthenticationSupplier<AuthenticationData> {

	private static final class State implements AuthenticationData {
		final boolean isQr;
		final boolean isBot;
		final String botToken;
		final String phoneNumber;

		State(boolean isQr, boolean isBot, String botToken, String phoneNumber) {
			this.isQr = isQr;
			this.isBot = isBot;
			this.botToken = botToken;
			this.phoneNumber = phoneNumber;
		}

		@Override
		public boolean isQrCode() {
			return isQr;
		}

		@Override
		public boolean isBot() {
			return isBot;
		}

		@Override
		public String getUserPhoneNumber() {
			if (isBot || isQr) {
				throw new UnsupportedOperationException("This is not a user");
			}
			return phoneNumber;
		}

		@Override
		public String getBotToken() {
			if (!isBot || isQr) {
				throw new UnsupportedOperationException("This is not a bot");
			}
			return botToken;
		}
	}

	private final AtomicReference<CompletableFuture<AuthenticationData>> state = new AtomicReference<>();

	ConsoleInteractiveAuthenticationData() {

	}

	public CompletableFuture<AuthenticationData> askData() {
		return get();
	}

	public boolean isInitialized() {
		CompletableFuture<AuthenticationData> cf = state.get();
		return cf != null && cf.isDone();
	}

	@Override
	public CompletableFuture<AuthenticationData> get() {
		CompletableFuture<AuthenticationData> cf = new CompletableFuture<AuthenticationData>();
		if (state.compareAndSet(null, cf)) {
			SequentialRequestsExecutor.getInstance().execute(() -> {
				try {
					String choice;

					// Choose login type
					String mode;
					do {
						String response = ScannerUtils.askParameter("login",
								"Do you want to login using a bot [token], a [phone] number, or a [qr] code? [token/phone/qr]");
						if (response != null) {
							choice = response.trim().toLowerCase(Locale.ROOT);
							switch (choice) {
								case "phone":
									mode = "PHONE";
									break;
								case "token":
									mode = "TOKEN";
									break;
								case "qr":
									mode = "QR";
									break;
								default:
									mode = null;
									break;
							}
						} else {
							mode = null;
						}
					} while (mode == null);

					if ("TOKEN".equals(mode)) {
						String token;
						do {
							token = ScannerUtils.askParameter("login", "Please type the bot token");
						} while (token.length() < 5 || !token.contains(":"));

						cf.complete(new State(false, true, token, null));
					} else if ("PHONE".equals(mode)) {
						String phoneNumber;
						do {
							phoneNumber = ScannerUtils.askParameter("login", "Please type your phone number");
						} while (phoneNumber.length() < 3);

						cf.complete(new State(false, false, null, phoneNumber));
					} else {
						cf.complete(new State(true, false, null, null));
					}
				} catch (Throwable ex) {
					cf.completeExceptionally(ex);
					throw ex;
				}
			});
			return cf;
		} else {
			return state.get();
		}
	}
}
