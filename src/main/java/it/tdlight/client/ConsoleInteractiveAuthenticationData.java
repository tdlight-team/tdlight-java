package it.tdlight.client;

import it.tdlight.common.utils.ScannerUtils;
import java.util.Locale;

public final class ConsoleInteractiveAuthenticationData implements AuthenticationData {

	private static final Object LOCK = new Object();

	private boolean initialized = false;
	private boolean isQr;
	private boolean isBot;
	private String botToken;
	private long phoneNumber;

	ConsoleInteractiveAuthenticationData() {

	}

	public void askData() {
		initializeIfNeeded();
	}

	public boolean isInitialized() {
		return initialized;
	}

	@Override
	public boolean isQrCode() {
		initializeIfNeeded();
		return isQr;
	}

	@Override
	public boolean isBot() {
		initializeIfNeeded();
		return isBot;
	}

	@Override
	public long getUserPhoneNumber() {
		initializeIfNeeded();
		if (isBot || isQr) {
			throw new UnsupportedOperationException("This is not a user");
		}
		return phoneNumber;
	}

	@Override
	public String getBotToken() {
		initializeIfNeeded();
		if (!isBot || isQr) {
			throw new UnsupportedOperationException("This is not a bot");
		}
		return botToken;
	}

	private void initializeIfNeeded() {
		if (initialized) {
			return;
		}

		synchronized (LOCK) {
			if (initialized) {
				return;
			}

			String choice;

			// Choose login type
			String mode;
			do {
				var response = ScannerUtils.askParameter("login",
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

				this.isBot = true;
				this.phoneNumber = -1;
				this.botToken = token;
				this.isQr = false;
			} else if ("PHONE".equals(mode)) {
				String phoneNumber;
				do {
					phoneNumber = ScannerUtils.askParameter("login", "Please type your phone number");
				} while (phoneNumber.length() < 3);

				long phoneNumberLong = Long.parseLong(phoneNumber.replaceAll("\\D", ""));

				this.isBot = false;
				this.phoneNumber = phoneNumberLong;
				this.botToken = null;
				this.isQr = false;
			} else {

				this.isBot = false;
				this.phoneNumber = -1;
				this.botToken = null;
				this.isQr = true;
			}

			initialized = true;
		}
	}
}
