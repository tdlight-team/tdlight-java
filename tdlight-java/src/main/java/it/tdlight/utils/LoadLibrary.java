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
package it.tdlight.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The class to load the libraries needed to run Tdlib
 */
public final class LoadLibrary {

	private static final Set<String> LIBRARY_LOADED = new ConcurrentHashMap<String, Boolean>().keySet(true);
	private static final String LIBS_VERSION =
			LibraryVersion.IMPLEMENTATION_NAME + "-" + LibraryVersion.VERSION + "-" + LibraryVersion.NATIVES_VERSION;

	/**
	 * Load a library installed in the system (priority choice) or a library included in the jar.
	 *
	 * @param libname The name of the library.
	 * @throws CantLoadLibrary An exception that is thrown when the LoadLibrary class fails to load the library.
	 */
	public static void load(String libname) throws CantLoadLibrary {
		if (libname == null || libname.trim().isEmpty()) {
			throw new IllegalArgumentException();
		}

		if (LIBRARY_LOADED.contains(libname)) return;
		synchronized (LoadLibrary.class) {
			if (LIBRARY_LOADED.contains(libname)) return;

			String libraryCachePathString = System.getProperty("it.tdlight.libraryCachePath");
			Path libraryCachePath = libraryCachePathString != null ? Paths.get(libraryCachePathString) : null;
			loadLibrary(libname, libraryCachePath);
			LIBRARY_LOADED.add(libname);
		}
	}

	/**
	 * Load a native library
	 * @param libraryName Library name
	 * @param libraryCachePath optional, path in which the library will be extracted
	 * @throws CantLoadLibrary The library can't be loaded
	 */
	private static void loadLibrary(String libraryName, Path libraryCachePath) throws CantLoadLibrary {
		if (libraryCachePath == null) {
			libraryCachePath = Paths.get(System.getProperty("user.home")).resolve(".cache").resolve("tdlight-jni-cache");
		}
		try {
			loadJarLibrary(libraryName, libraryCachePath);
		} catch (CantLoadLibrary | UnsatisfiedLinkError e) {
			if (loadSysLibrary(libraryName)) {
				return;
			}
			throw new CantLoadLibrary(e);
		}
	}

	private static boolean loadSysLibrary(String libname) {
		try {
			System.loadLibrary(libname);
		} catch (UnsatisfiedLinkError e) {
			return false;
		}

		return true;
	}

	private static void loadJarLibrary(String libraryName, Path libraryCachePath) throws CantLoadLibrary {
		Path tempPath;
		try {
			tempPath = libraryCachePath.resolve("version-" + LIBS_VERSION).resolve(libraryName);
			if (Files.notExists(tempPath)) {
				tempPath = Files.createDirectories(tempPath);
			}
		} catch (IOException e) {
			throw new CantLoadLibrary("Can't create temporary files", e);
		}

		ClassLoader classForResource = LoadLibrary.class.getClassLoader();
		List<String> normalizedArchs = getNormalizedArchitectures().collect(Collectors.toList());
		Exception lastEx = null;
		loadAny: for (String normalizedArch : normalizedArchs) {
			Path tempFile = tempPath.resolve(libraryName + "." + normalizedArch);
			InputStream libInputStream;
			try {
				libInputStream = Objects.requireNonNull(classForResource.getResourceAsStream("META-INF/tdlight-jni/lib" + libraryName + "." + normalizedArch));
				if (Files.notExists(tempFile)) {
					try {
						Files.copy(libInputStream, tempFile);
					} catch (IOException e) {
						throw new CantLoadLibrary("Can't copy native libraries into temporary files", e);
					}
				}
				try {
					libInputStream.close();
				} catch (IOException e) {
					throw new CantLoadLibrary("Can't load the native libraries", e);
				}
				System.load(tempFile.toAbsolutePath().toString());
				lastEx = null;
				break loadAny;
			} catch (Throwable e) {
				lastEx = new CantLoadLibrary(e);
			}
		}
		if (lastEx != null) {
			throw new CantLoadLibrary("Native libraries for platforms "
					+ String.join(", ", normalizedArchs) + " not found!", lastEx);
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
				return Stream.of("linux-" + arch + "-ssl1.so", "linux-" + arch + "-ssl3.so");
			}
			case "windows": {
				return Stream.of("windows-" + arch + ".dll");
			}
			case "osx": {
				return Stream.of("osx-" + arch + ".dylib");
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
