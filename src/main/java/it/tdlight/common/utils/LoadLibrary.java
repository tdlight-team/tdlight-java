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
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.lang.reflect.InvocationTargetException;
import it.tdlight.jni.ObjectsUtils;

/**
 * The class to load the libraries needed to run Tdlib
 */
public class LoadLibrary {
	private static final ConcurrentHashMap<String, Boolean> libraryLoaded = new ConcurrentHashMap<>();
	private static final Path librariesPath = Paths.get(".cache");
	private static final String libsVersion = LibraryVersion.IMPLEMENTATION_NAME
			+ "-" + LibraryVersion.VERSION + "-" + LibraryVersion.NATIVES_VERSION;

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

		if (arch == Arch.unknown) {
			throw (CantLoadLibrary) new CantLoadLibrary().initCause(new IllegalStateException("Arch: \"" + System.getProperty("os.arch") + "\" is unknown"));
		}

		if (os == Os.unknown) {
			throw (CantLoadLibrary) new CantLoadLibrary().initCause(new IllegalStateException("Os: \"" + System.getProperty("os.name") + "\" is unknown"));
		}

		try {
			loadJarLibrary(libname, arch, os);
		} catch (IOException | CantLoadLibrary | UnsatisfiedLinkError e) {
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

	private static void loadJarLibrary(String libname, Arch arch, Os os) throws IOException, CantLoadLibrary {
		Path tempPath = Files.createDirectories(librariesPath.resolve("version-" + libsVersion).resolve(libname));
		Path tempFile = Paths.get(tempPath.toString(), libname + getExt(os));
		Class<?> classForResource = null;
		switch (os) {
			case linux:
				switch (arch) {
					case amd64:
						try {
							classForResource = Class.forName(LibraryVersion.LINUX_AMD64_CLASS);
						} catch (ClassNotFoundException e) {
							// not found
						}
						break;
					case x86:
						try {
							classForResource = Class.forName(LibraryVersion.LINUX_X86_CLASS);
						} catch (ClassNotFoundException e) {
							// not found
						}
						break;
					case aarch64:
						try {
							classForResource = Class.forName(LibraryVersion.LINUX_AARCH64_CLASS);
						} catch (ClassNotFoundException e) {
							// not found
						}
						break;
					case armv7:
						try {
							classForResource = Class.forName(LibraryVersion.LINUX_ARMV7_CLASS);
						} catch (ClassNotFoundException e) {
							// not found
						}
						break;
					case armv6:
						try {
							classForResource = Class.forName(LibraryVersion.LINUX_ARMV6_CLASS);
						} catch (ClassNotFoundException e) {
							// not found
						}
						break;
					case ppc64le:
						try {
							classForResource = Class.forName(LibraryVersion.LINUX_PPC64LE_CLASS);
						} catch (ClassNotFoundException e) {
							// not found
						}
						break;
				}
				break;
			case osx:
				if (arch == Arch.amd64) {
					try {
						classForResource = Class.forName(LibraryVersion.OSX_AMD64_CLASS);
					} catch (ClassNotFoundException e) {
						// not found
					}
				}
				break;
			case win:
				switch (arch) {
					case amd64:
						try {
							classForResource = Class.forName(LibraryVersion.WINDOWS_AMD64_CLASS);
						} catch (ClassNotFoundException e) {
							// not found
						}
						break;
					case x86:
						break;
				}
				break;
		}
		if (classForResource == null) {
			throw new IOException("Native libraries for platform " + os + "-" + arch + " not found!");
		}
		InputStream libInputStream;
		try {
			libInputStream = ObjectsUtils.requireNonNull((InputStream) classForResource.getDeclaredMethod("getLibraryAsStream").invoke(InputStream.class));
		} catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | NullPointerException e) {
			throw new IOException("Native libraries for platform " + os + "-" + arch + " not found!", e);
		}
		if (Files.notExists(tempFile)) {
			Files.copy(libInputStream, tempFile);
		}
		if (libInputStream != null) libInputStream.close();
		System.load(tempFile.toFile().getAbsolutePath());
	}


	private static Arch getCpuArch() {
		String architecture = System.getProperty("os.arch").trim();
		switch (architecture) {
			case "amd64":
			case "x86_64":
				return Arch.amd64;
			case "i386":
			case "x86":
				return Arch.x86;
			case "armv6":
				return Arch.armv6;
			case "arm":
			case "aarch32":
			case "armv7":
			case "armv7l":
				return Arch.armv7;
			case "arm64":
			case "aarch64":
				return Arch.aarch64;
			case "powerpc":
			case "ppc64":
				if (ByteOrder.nativeOrder().equals(ByteOrder.LITTLE_ENDIAN)) // Java always returns ppc64 for all 64-bit powerpc but
					return Arch.ppc64le;                                       // powerpc64le (our target) is very different, it uses this condition to accurately identify the architecture
				else
					return Arch.unknown;
			default:
				return Arch.unknown;
		}
	}

	public static Os getOs() {
		String os = System.getProperty("os.name").toLowerCase().trim();
		if (os.contains("linux"))
			return Os.linux;
		if (os.contains("windows"))
			return Os.win;
		if (os.contains("mac"))
			return Os.osx;
		if (os.contains("darwin"))
			return Os.osx;
		return Os.unknown;
	}

	private static String getExt(Os os) {
		switch (os) {
			case win:
				return ".dll";
			case osx:
				return ".dylib";
			case linux:
			case unknown:
			default:
				return ".so";
		}
	}

	private static String createPath(String... path) {
		StringBuilder stringBuilder = new StringBuilder();
		stringBuilder.append("/");

		for (int i = 0; i < path.length; i++) {
			stringBuilder.append(path[i]);

			if (i < path.length - 1) {
				stringBuilder.append("/");
			}
		}

		return stringBuilder.toString();
	}
}
