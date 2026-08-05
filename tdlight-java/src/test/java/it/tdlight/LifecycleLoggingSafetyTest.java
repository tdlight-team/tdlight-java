package it.tdlight;

import static it.tdlight.util.TdApiObjectDescriptor.describe;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.tdlight.jni.TdApi;
import org.junit.jupiter.api.Test;

public class LifecycleLoggingSafetyTest {

	@Test
	public void credentialFunctionDescriptorContainsOnlyTypeMetadata() {
		String secret = "123456:super-secret-bot-token";
		TdApi.CheckAuthenticationBotToken query = new TdApi.CheckAuthenticationBotToken(secret);

		String descriptor = describe(query);

		assertTrue(descriptor.startsWith("CheckAuthenticationBotToken[constructor="));
		assertFalse(descriptor.contains(secret));
		assertFalse(descriptor.contains("super-secret"));
	}
}
