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

package it.tdlight.common.utils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class to load the libraries needed to run Tdlib
 */
public final class LoadLibrary {

	private static final ConcurrentHashMap<String, Boolean> libraryLoaded = new ConcurrentHashMap<>();
	private static final Path librariesPath = Paths.get(".cache");
	private static final String libsVersion =
			LibraryVersion.IMPLEMENTATION_NAME + "-" + LibraryVersion.VERSION + "-" + LibraryVersion.NATIVES_VERSION;

	static {
		if (Files.notExists(librariesPath)) {
			try {
				Files.createDirectories(librariesPath);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

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

		if (libraryLoaded.containsKey(libname)) {
			if (libraryLoaded.get(libname)) {
				return;
			}
		}

		loadLibrary(libname);
		libraryLoaded.put(libname, true);
	}

	private static void loadLibrary(String libname) throws CantLoadLibrary {
		Arch arch = getCpuArch();
		Os os = getOs();

		if (arch == Arch.UNKNOWN) {
			throw new CantLoadLibrary("Arch: \"" + System.getProperty("os.arch") + "\" is unknown");
		}

		if (os == Os.UNKNOWN) {
			throw new CantLoadLibrary("Os: \"" + System.getProperty("os.name") + "\" is unknown");
		}

		try {
			loadJarLibrary(libname, arch, os);
		} catch (CantLoadLibrary | UnsatisfiedLinkError e) {
			if (loadSysLibrary(libname)) {
				return;
			}
			throw (CantLoadLibrary) new CantLoadLibrary().initCause(e);
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

	private static String removeLastPackageParts(String clazz, int count, String className) {
		List<String> parts = new ArrayList<>(Arrays.asList(clazz.split("\\.")));
		parts.remove(parts.size() - 1);
		for (int i = 0; i < count; i++) {
			parts.remove(parts.size() - 1);
		}
		StringJoiner joiner = new StringJoiner(".");
		for (String part : parts) {
			joiner.add(part);
		}
		joiner.add(className);
		return joiner.toString();
	}

	private static void loadJarLibrary(String libname, Arch arch, Os os) throws CantLoadLibrary {
		Path tempPath;
		try {
			tempPath = Files.createDirectories(librariesPath.resolve("version-" + libsVersion).resolve(libname));
		} catch (IOException e) {
			throw new CantLoadLibrary("Can't create temporary files", e);
		}
		Path tempFile = Paths.get(tempPath.toString(), libname + getExt(os));
		Class<?> classForResource = null;
		switch (os) {
			case LINUX:
				switch (arch) {
					case AMD64:
						classForResource = tryLoadLibraryVersionClass(LibraryVersion.LINUX_AMD64_CLASS, os, arch);
						break;
					case I386:
						classForResource = tryLoadLibraryVersionClass(LibraryVersion.LINUX_X86_CLASS, os, arch);
						break;
					case AARCH64:
						classForResource = tryLoadLibraryVersionClass(LibraryVersion.LINUX_AARCH64_CLASS, os, arch);
						break;
					case ARMHF:
						classForResource = tryLoadLibraryVersionClass(LibraryVersion.LINUX_ARMHF_CLASS, os, arch);
						break;
					case S390X:
						classForResource = tryLoadLibraryVersionClass(LibraryVersion.LINUX_S390X_CLASS, os, arch);
						break;
					case PPC64LE:
						classForResource = tryLoadLibraryVersionClass(LibraryVersion.LINUX_PPC64LE_CLASS, os, arch);
						break;
				}
				break;
			case OSX:
				if (arch == Arch.AMD64) {
					classForResource = tryLoadLibraryVersionClass(LibraryVersion.OSX_AMD64_CLASS, os, arch);
				}
				break;
			case WINDOWS:
				switch (arch) {
					case AMD64:
						classForResource = tryLoadLibraryVersionClass(LibraryVersion.WINDOWS_AMD64_CLASS, os, arch);
						break;
					case I386:
						break;
				}
				break;
		}
		if (classForResource == null) {
			throw new CantLoadLibrary("Native libraries for platform " + os + "-" + arch + " not found!"
					+ " Required version: " + getRequiredVersionName(os, arch));
		}
		InputStream libInputStream;
		try {
			libInputStream = Objects.requireNonNull((InputStream) classForResource
					.getDeclaredMethod("getLibraryAsStream")
					.invoke(InputStream.class));
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | NullPointerException e) {
			throw new CantLoadLibrary("Native libraries for platform " + os + "-" + arch + " not found!", e);
		}
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
		System.load(tempFile.toFile().getAbsolutePath());
	}

	private static Class<?> tryLoadLibraryVersionClass(String classForResource, Os os, Arch arch) throws CantLoadLibrary {
		try {
			return Class.forName(classForResource);
		} catch (ClassNotFoundException e1) {
			// exact library not found

			//Check if a wrong version is installed
			try {
				Class<?> foundAnotherVersion = Class.forName(removeFromVersion(classForResource));
				throw new CantLoadLibrary("Can't load the native libraries."
						+ " A different version of the native libraries was found."
						+ " Please make sure that you installed the correct one."
						+ " Required version: " + getRequiredVersionName(os, arch), e1);
			} catch (ClassNotFoundException e2) {
				// not found arch

				//Check if a wrong arch is installed
				try {
					Class<?> foundAnotherArch = Class.forName(removeFromArch(classForResource));
					throw new CantLoadLibrary("Can't load the native libraries."
							+ " A different architecture of the native libraries was found."
							+ " Please make sure that you installed the correct one."
							+ " Required version: " + getRequiredVersionName(os, arch), e1);
				} catch (ClassNotFoundException e3) {
					// not found os

					//Check if a wrong os is installed
					try {
						Class<?> foundAnotherOs = Class.forName(removeFromOs(classForResource));
						throw new CantLoadLibrary("Can't load the native libraries."
								+ " A different OS of the native libraries was found."
								+ " Please make sure that you installed the correct one."
								+ " Required version: " + getRequiredVersionName(os, arch), e1);
					} catch (ClassNotFoundException e4) {
						// not found anything

						// No library was found, return
						return null;
					}
				}
			}
		}
	}

	private static String getRequiredVersionName(Os os, Arch arch) {
		return LibraryVersion.IMPLEMENTATION_NAME + " " + os + " " + arch + " 4.0." + LibraryVersion.NATIVES_VERSION;
	}

	private static String removeFromVersion(String libraryVersionClass) {
		return removeLastPackageParts(libraryVersionClass, 1, "AnyVersion");
	}

	private static String removeFromArch(String libraryVersionClass) {
		return removeLastPackageParts(libraryVersionClass, 2, "AnyArch");
	}

	private static String removeFromOs(String libraryVersionClass) {
		return removeLastPackageParts(libraryVersionClass, 3, "AnyOs");
	}

	private static Arch getCpuArch() {
		String architecture = System.getProperty("os.arch").trim();
		switch (architecture) {
			case "amd64":
			case "x86_64":
				return Arch.AMD64;
			case "i386":
			case "x86":
			case "386":
			case "i686":
			case "686":
				return Arch.I386;
			case "armv6":
			case "arm":
			case "armhf":
			case "aarch32":
			case "armv7":
			case "armv7l":
				return Arch.ARMHF;
			case "arm64":
			case "aarch64":
			case "armv8":
			case "armv8l":
				return Arch.AARCH64;
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
					return Arch.PPC64LE;                                       // powerpc64le (our target) is very different, it uses this condition to accurately identify the architecture
				} else {
					return Arch.UNKNOWN;
				}
			default:
				return Arch.UNKNOWN;
		}
	}

	public static Os getOs() {
		String os = System.getProperty("os.name").toLowerCase().trim();
		if (os.contains("linux")) {
			return Os.LINUX;
		}
		if (os.contains("windows")) {
			return Os.WINDOWS;
		}
		if (os.contains("mac")) {
			return Os.OSX;
		}
		if (os.contains("darwin")) {
			return Os.OSX;
		}
		return Os.UNKNOWN;
	}

	private static String getExt(Os os) {
		switch (os) {
			case WINDOWS:
				return ".dll";
			case OSX:
				return ".dylib";
			case LINUX:
			case UNKNOWN:
			default:
				return ".so";
		}
	}
}
