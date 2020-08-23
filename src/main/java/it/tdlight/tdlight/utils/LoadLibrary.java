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

package it.tdlight.tdlight.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class to load the libraries needed to run Tdlib
 */
public class LoadLibrary {
	private static ConcurrentHashMap<String, Boolean> libraryLoaded = new ConcurrentHashMap<>();
	private static Path librariesPath = Paths.get(".JTDLibLibraries");
	private static final String libsVersion = LibraryVersion.VERSION;

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
	public static void load(String libname) throws Throwable {
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

	private static void loadLibrary(String libname) throws Throwable {
		Arch arch = getCpuArch();
		Os os = getOs();

		if (arch == Arch.unknown) {
			throw new CantLoadLibrary().initCause(new IllegalStateException("Arch: \"" + System.getProperty("os.arch") + "\" is unknown"));
		}

		if (os == Os.unknown) {
			throw new CantLoadLibrary().initCause(new IllegalStateException("Os: \"" + System.getProperty("os.name") + "\" is unknown"));
		}

		try {
			loadJarLibrary(libname, arch, os);
		} catch (IOException | CantLoadLibrary | UnsatisfiedLinkError e) {
			if (loadSysLibrary(libname)) {
				return;
			}
			throw new CantLoadLibrary().initCause(e);
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
						classForResource = it.tdlight.tdlight.linux.amd64.LoadLibrary.class;
						break;
					case aarch64:
						classForResource = it.tdlight.tdlight.linux.aarch64.LoadLibrary.class;
						break;
				}
				break;
			case osx:
				if (arch == Arch.amd64) {
					classForResource = it.tdlight.tdlight.osx.amd64.LoadLibrary.class;
				}
				break;
			case win:
				if (arch == Arch.amd64) {
					classForResource = it.tdlight.tdlight.win.amd64.LoadLibrary.class;
				}
				break;
		}
		if (classForResource == null) {
			throw new IOException("Native libraries for platform " + os + "-" + arch + " not found!");
		}
		String libPath = createPath("libs", os.name(), arch.name(), libname) + getExt(os);
		InputStream libInputStream = classForResource.getResourceAsStream(libPath);
		if (Files.notExists(tempFile)) {
			Files.copy(libInputStream, tempFile);
		}
		libInputStream.close();
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
				return Arch.i386;
			case "arm":
				return Arch.armhf;
			case "arm64":
			case "aarch64":
				return Arch.aarch64;
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
