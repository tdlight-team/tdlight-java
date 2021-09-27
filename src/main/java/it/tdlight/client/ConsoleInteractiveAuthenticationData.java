package it.tdlight.client;

import it.tdlight.common.utils.ScannerUtils;
import java.util.Locale;
import java.util.stream.Collector;

final class ConsoleInteractiveAuthenticationData implements AuthenticationData {

	private static final Object LOCK = new Object();

	private boolean initialized = false;
	private boolean isBot;
	private String botToken;
	private long phoneNumber;

	public ConsoleInteractiveAuthenticationData() {

	}

	public void askData() {
		initializeIfNeeded();
	}

	public boolean isInitialized() {
		return initialized;
	}

	@Override
	public boolean isBot() {
		initializeIfNeeded();
		return isBot;
	}

	@Override
	public long getUserPhoneNumber() {
		initializeIfNeeded();
		if (isBot) {
			throw new UnsupportedOperationException("This is not a user");
		}
		return phoneNumber;
	}

	@Override
	public String getBotToken() {
		initializeIfNeeded();
		if (!isBot) {
			throw new UnsupportedOperationException("This is not a bot");
		}
		return botToken;
	}

	private void initializeIfNeeded() {
		if (initialized) return;

		synchronized (LOCK) {
			if (initialized) return;

			String choice;

			// Choose login type
			Boolean useBotToken;
			do {
				choice = ScannerUtils
						.askParameter("login", "Do you want to login using a bot [token] or a [phone] number? [token/phone]")
						.trim()
						.toLowerCase(Locale.ROOT);
				if ("phone".equals(choice)) {
					useBotToken = false;
				} else if ("token".equals(choice)) {
					useBotToken = true;
				} else {
					useBotToken = null;
				}
			} while (useBotToken == null);

			if (useBotToken) {
				String token;
				do {
					token = ScannerUtils.askParameter("login", "Please type the bot token");
				} while (token.length() < 5 || !token.contains(":"));

				this.isBot = true;
				this.phoneNumber = -1;
				this.botToken = token;
			} else {
				String phoneNumber;
				do {
					phoneNumber = ScannerUtils.askParameter("login", "Please type your phone number");
				} while (phoneNumber.length() < 3);

				long phoneNumberLong = Long.parseLong(phoneNumber.chars().filter(Character::isDigit).boxed().collect(Collector.of(
						StringBuilder::new,
						StringBuilder::append,
						StringBuilder::append,
						StringBuilder::toString)));

				this.isBot = false;
				this.phoneNumber = phoneNumberLong;
				this.botToken = null;
			}

			initialized = true;
		}
	}
}
