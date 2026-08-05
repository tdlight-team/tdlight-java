package it.tdlight;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import it.tdlight.jni.TdApi;
import org.junit.jupiter.api.Test;

public final class TdApiEqualityTest {

	@Test
	public void generatedObjectsUseValueEqualityForStringsAndArrays() {
		TdApi.FormattedText leftText = new TdApi.FormattedText(new String("same"), new TdApi.TextEntity[0]);
		TdApi.FormattedText rightText = new TdApi.FormattedText(new String("same"), new TdApi.TextEntity[0]);

		assertEquals(leftText, rightText);
		assertEquals(leftText.hashCode(), rightText.hashCode());

		TdApi.CallServerTypeTelegramReflector leftBytes =
				new TdApi.CallServerTypeTelegramReflector(new byte[] {1, 2}, true);
		TdApi.CallServerTypeTelegramReflector rightBytes =
				new TdApi.CallServerTypeTelegramReflector(new byte[] {1, 2}, true);
		TdApi.CallServerTypeTelegramReflector differentBytes =
				new TdApi.CallServerTypeTelegramReflector(new byte[] {1, 3}, true);

		assertEquals(leftBytes, rightBytes);
		assertEquals(leftBytes.hashCode(), rightBytes.hashCode());
		assertNotEquals(leftBytes, differentBytes);

		TdApi.Location positiveZero = new TdApi.Location(+0.0d, 1.0d, 2.0d);
		TdApi.Location negativeZero = new TdApi.Location(-0.0d, 1.0d, 2.0d);
		assertNotEquals(positiveZero, negativeZero);
		assertNotEquals(
				new TdApi.Location(1.0d, 2.0d, 3.0d).hashCode(),
				new TdApi.Location(1.0d, 2.0d, 4.0d).hashCode(),
				"Every primitive field must contribute to the generated hash"
		);

		TdApi.Location canonicalNaN = new TdApi.Location(Double.NaN, 1.0d, 2.0d);
		TdApi.Location alternateNaN = new TdApi.Location(
				Double.longBitsToDouble(0x7ff8000000000001L), 1.0d, 2.0d
		);
		assertEquals(canonicalNaN, alternateNaN);
		assertEquals(canonicalNaN.hashCode(), alternateNaN.hashCode());
	}
}
