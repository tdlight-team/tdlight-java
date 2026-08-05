package it.tdlight.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class CredentialRedactionTest {

	@Test
	public void apiHashIsRedactedFromStringRepresentations() {
		String apiHash = "secret-api-hash";

		String rendered = new APIToken(1234, apiHash).toString();

		assertFalse(rendered.contains(apiHash));
		assertTrue(rendered.contains("[REDACTED]"));
	}

	@Test
	public void botTokenSecretIsRedactedFromStringRepresentations() {
		String botToken = "12345:secret-bot-token";

		String rendered = AuthenticationSupplier.bot(botToken).toString();

		assertFalse(rendered.contains("secret-bot-token"));
		assertTrue(rendered.contains("12345"));
	}

	@Test
	public void botTokenWithoutAnIdSeparatorIsFullyRedacted() {
		String botToken = "secret-bot-token";

		String rendered = AuthenticationSupplier.bot(botToken).toString();

		assertFalse(rendered.contains(botToken));
		assertTrue(rendered.contains("[REDACTED]"));
	}

	@Test
	public void malformedBotTokenPrefixIsFullyRedacted() {
		String botToken = "secret-prefix:rest";

		String rendered = AuthenticationSupplier.bot(botToken).toString();

		assertFalse(rendered.contains("secret-prefix"));
		assertTrue(rendered.contains("[REDACTED]"));
		assertEquals("[REDACTED]", AuthenticationDataImpl.safeBotId(botToken));
	}

	@Test
	public void oversizedNumericBotTokenPrefixIsFullyRedacted() {
		String numericSecret = "1234567890123456789012345678901234567890";
		String botToken = numericSecret + ":rest";

		String rendered = AuthenticationSupplier.bot(botToken).toString();

		assertFalse(rendered.contains(numericSecret));
		assertTrue(rendered.contains("[REDACTED]"));
		assertEquals("[REDACTED]", AuthenticationDataImpl.safeBotId(botToken));
	}

	@Test
	public void invalidBotTokenIsNotEchoedInValidationErrors() {
		String botToken = "bad";

		IllegalArgumentException failure = assertThrows(
				IllegalArgumentException.class,
				() -> AuthenticationSupplier.bot(botToken)
		);

		assertFalse(failure.getMessage().contains(botToken));
	}
}
