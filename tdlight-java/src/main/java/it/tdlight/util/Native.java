/*
 * Copyright (c) 2018. Ernesto Castellotti <erny.castell@gmail.com>
 * This file is part of JTdlib.
 *
 *     JTdlib is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     JTdlib is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with JTdlib.  If not, see <http://www.gnu.org/licenses/>.
 */
package it.tdlight.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.ByteOrder;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The class to load the libraries needed to run Tdlib
 */
public final class Native {

	/**
	 * Internal util
	 */
	public static void loadNativesInternal() throws UnsupportedNativeLibraryException {
		loadLibrary("tdlight");
	}

	private static final Logger logger = LoggerFactory.getLogger(Native.class);

	/**
	 * Load a native library
	 * @param libraryName Library name
	 * @throws UnsupportedNativeLibraryException The library can't be loaded
	 */
	private static void loadLibrary(String libraryName) throws UnsupportedNativeLibraryException {
		ClassLoader cl = Native.class.getClassLoader();
		String staticLibName = libraryName;
		List<String> sharedLibNames = getNormalizedArchitectures().map(suffix -> staticLibName + "." + suffix).collect(Collectors.toList());
		if (sharedLibNames.isEmpty()) {
			throw new IllegalStateException();
		}
		try {
			NativeLibraryLoader.loadFirstAvailable(cl, sharedLibNames.toArray(new String[0]));
		}	 catch (IllegalArgumentException | UnsatisfiedLinkError e1) {
			try {
				NativeLibraryLoader.load(staticLibName, cl);
				logger.debug("Failed to load {}", String.join(", ", sharedLibNames), e1);
			} catch (UnsatisfiedLinkError e2) {
				e1.addSuppressed(e2);
				throw new UnsupportedNativeLibraryException(e1);
			}
		}
	}

	private static Stream<String> getNormalizedArchitectures() {
		String os = getOs();
		String arch = getCpuArch();
		if (os.equals("unknown") || arch.equals("unknown")) {
			return getAllNormalizedArchitectures();
		}
		return getNormalizedArchitectures(os, arch);
	}

	private static Stream<String> getAllNormalizedArchitectures() {
		Set<String> all = new LinkedHashSet<>();
		for (String os : new String[]{"windows"}) {
			for (String arch : new String[]{"arm64", "amd64", "armhf", "i386", "s390x", "ppc64le"}) {
				getNormalizedArchitectures(os, arch).forEach(all::add);
			}
		}
		return all.stream();
	}

	private static Stream<String> getNormalizedArchitectures(String os, String arch) {
		switch (os) {
			case "linux": {
				return Stream.of("linux-" + arch + "-ssl1", "linux-" + arch + "-ssl3");
			}
			case "windows": {
				return Stream.of("windows-" + arch);
			}
			case "osx": {
				return Stream.of("osx-" + arch);
			}
			default: {
				throw new UnsupportedOperationException();
			}
		}
	}

	private static String getCpuArch() {
		String architecture = System.getProperty("os.arch").trim();
		switch (architecture) {
			case "amd64":
			case "x86_64":
				return "amd64";
			case "i386":
			case "x86":
			case "386":
			case "i686":
			case "686":
				return "i386";
			case "armv6":
			case "arm":
			case "armhf":
			case "aarch32":
			case "armv7":
			case "armv7l":
				return "armhf";
			case "arm64":
			case "aarch64":
			case "armv8":
			case "armv8l":
				return "arm64";
			case "s390x":
				return "s390x";
			case "powerpc":
			case "powerpc64":
			case "powerpc64le":
			case "powerpc64el":
			case "ppc":
			case "ppc64":
			case "ppc64le":
			case "ppc64el":
				if (ByteOrder
						.nativeOrder()
						.equals(ByteOrder.LITTLE_ENDIAN)) // Java always returns ppc64 for all 64-bit powerpc but
				{
					return "ppc64le";                                       // powerpc64le (our target) is very different, it uses this condition to accurately identify the architecture
				} else {
					return "unknown";
				}
			default:
				return "unknown";
		}
	}

	public static String getOs() {
		String os = System.getProperty("os.name").toLowerCase().trim();
		if (os.contains("linux")) {
			return "linux";
		}
		if (os.contains("windows")) {
			return "windows";
		}
		if (os.contains("mac")) {
			return "osx";
		}
		if (os.contains("darwin")) {
			return "osx";
		}
		return "unknown";
	}
}
