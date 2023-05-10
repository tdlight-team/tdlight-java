package it.tdlight.client;

import it.tdlight.util.ScannerUtils;
import it.tdlight.jni.TdApi.TermsOfService;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ScannerClientInteraction implements ClientInteraction {

	private static final Logger LOG = LoggerFactory.getLogger(ScannerClientInteraction.class);

	private final Executor blockingExecutor;
	private final Authenticable authenticable;

	public ScannerClientInteraction(Executor blockingExecutor, Authenticable authenticable) {
		this.blockingExecutor = blockingExecutor;
		this.authenticable = authenticable;
	}

	@Override
	public CompletableFuture<String> onParameterRequest(InputParameter parameter, ParameterInfo parameterInfo) {
		AuthenticationSupplier<?> authSupplier = authenticable.getAuthenticationSupplier();
		AuthenticationData authData = getAuthDataNowOrNull(authSupplier);
		return CompletableFuture.supplyAsync(() -> {
			String who;
			boolean useRealWho = authData != null;
			if (!useRealWho) {
				who = "login";
			} else if (authData.isQrCode()) {
				who = "QR login";
			} else if (authData.isBot()) {
				who = authData.getBotToken().split(":", 2)[0];
			} else {
				who = "+" + authData.getUserPhoneNumber();
			}
			String question;
			boolean trim = false;
			switch (parameter) {
				case ASK_FIRST_NAME:
					question = "Enter first name";
					trim = true;
					break;
				case ASK_LAST_NAME:
					question = "Enter last name";
					trim = true;
					break;
				case ASK_CODE:
					question = "Enter authentication code";
					ParameterInfoCode codeInfo = ((ParameterInfoCode) parameterInfo);
					question += "\n\tPhone number: " + codeInfo.getPhoneNumber();
					question += "\n\tTimeout: " + codeInfo.getTimeout() + " seconds";
					question += "\n\tCode type: " + codeInfo.getType().getClass().getSimpleName()
							.replace("AuthenticationCodeType", "");
					if (codeInfo.getNextType() != null) {
						question += "\n\tNext code type: " + codeInfo
								.getNextType()
								.getClass()
								.getSimpleName()
								.replace("AuthenticationCodeType", "");
					}
					trim = true;
					break;
				case ASK_PASSWORD:
					question = "Enter your password";
					String passwordMessage = "Password authorization:";
					String hint = ((ParameterInfoPasswordHint) parameterInfo).getHint();
					if (hint != null && !hint.isEmpty()) {
						passwordMessage += "\n\tHint: " + hint;
					}
					boolean hasRecoveryEmailAddress = ((ParameterInfoPasswordHint) parameterInfo)
							.hasRecoveryEmailAddress();
					passwordMessage += "\n\tHas recovery email: " + hasRecoveryEmailAddress;
					String recoveryEmailAddressPattern = ((ParameterInfoPasswordHint) parameterInfo)
							.getRecoveryEmailAddressPattern();
					if (recoveryEmailAddressPattern != null && !recoveryEmailAddressPattern.isEmpty()) {
						passwordMessage += "\n\tRecovery email address pattern: " + recoveryEmailAddressPattern;
					}
					System.out.println(passwordMessage);
					break;
				case NOTIFY_LINK:
					String link = ((ParameterInfoNotifyLink) parameterInfo).getLink();
					System.out.println("Please confirm this login link on another device: " + link);
					System.out.println();
					try {
						System.out.println(QrCodeTerminal.getQr(link));
						System.out.println();
					} catch (NoClassDefFoundError ex) {
						LOG.warn("QR code library is missing!"
								+ " Please add the following dependency to your project: com.google.zxing:core");
					}
					return "";
				case TERMS_OF_SERVICE:
					TermsOfService tos = ((ParameterInfoTermsOfService) parameterInfo).getTermsOfService();
					question = "Terms of service:\n\t" + tos.text.text;
					if (tos.minUserAge > 0) {
						question += "\n\tMinimum user age: " + tos.minUserAge;
					}
					if (tos.showPopup) {
						question += "\nPlease press enter.";
						trim = true;
					} else {
						System.out.println(question);
						return "";
					}
					break;
				default:
					question = parameter.toString();
					break;
			}
			String result = ScannerUtils.askParameter(who, question);
			if (trim) {
				return result.trim();
			} else {
				return Objects.requireNonNull(result);
			}
		}, blockingExecutor);
	}

	private AuthenticationData getAuthDataNowOrNull(AuthenticationSupplier<?> authSupplier) {
		try {
			return authSupplier.get().getNow(null);
		} catch (Throwable ignored) {
			return null;
		}
	}
}
