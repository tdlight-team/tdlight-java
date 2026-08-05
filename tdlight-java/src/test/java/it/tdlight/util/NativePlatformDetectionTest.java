package it.tdlight.util;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class NativePlatformDetectionTest {

	@Test
	public void normalizesArchitectureCase() throws Exception {
		List<String> names = normalizedArchitectures("Linux", "AMD64");

		assertEquals("linux_amd64_clang_ssl1", names.get(0));
		assertTrue(names.contains("linux_amd64_gnu_ssl3"));
	}

	@Test
	public void unknownPlatformFallbackCoversEverySupportedOperatingSystem() throws Exception {
		List<String> names = normalizedArchitectures("Unknown OS", "unknown-architecture");

		assertTrue(names.contains("linux_amd64_gnu_ssl3"));
		assertTrue(names.contains("windows_amd64"));
		assertTrue(names.contains("macos_arm64"));
	}

	@Test
	public void detectsMacOsArm64() throws Exception {
		assertEquals(java.util.Collections.singletonList("macos_arm64"),
				normalizedArchitectures("Mac OS X", "aarch64"));
	}

	@SuppressWarnings("unchecked")
	private static List<String> normalizedArchitectures(String osName, String osArch) throws Exception {
		synchronized (NativePlatformDetectionTest.class) {
			String previousOsName = System.getProperty("os.name");
			String previousOsArch = System.getProperty("os.arch");
			try {
				System.setProperty("os.name", osName);
				System.setProperty("os.arch", osArch);
				Method method = Native.class.getDeclaredMethod("getNormalizedArchitectures");
				method.setAccessible(true);
				return ((Stream<String>) method.invoke(null)).collect(toList());
			} finally {
				restoreProperty("os.name", previousOsName);
				restoreProperty("os.arch", previousOsArch);
			}
		}
	}

	private static void restoreProperty(String name, String value) {
		if (value == null) {
			System.clearProperty(name);
		} else {
			System.setProperty(name, value);
		}
	}
}
