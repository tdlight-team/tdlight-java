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

package it.ernytech.tdlib.utils;

import java.io.IOException;
import java.nio.file.*;
import java.nio.ByteOrder;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The class to load the libraries needed to run Tdlib
 */
public class LoadLibrary {
    private static ConcurrentHashMap<String, Boolean> libraryLoaded = new ConcurrentHashMap<>();

    /**
     * Load a library installed in the system (priority choice) or a library included in the jar.
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
        if (loadSysLibrary(libname)) {
            return;
        }

        var arch = getCpuArch();
        var os = getOs();

        if (arch == Arch.unknown) {
            throw new CantLoadLibrary().initCause(new IllegalStateException("Arch: \"" + System.getProperty("os.arch") + "\" is unknown"));
        }

        if (os == Os.unknown) {
            throw new CantLoadLibrary().initCause(new IllegalStateException("Os: \"" + System.getProperty("os.name") + "\" is unknown"));
        }

        try {
            loadJarLibrary(libname, arch, os);
        } catch (IOException | CantLoadLibrary | UnsatisfiedLinkError e) {
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
        Path tempPath = Files.createTempDirectory(libname);
        deleteOnExit(tempPath);
        Path tempFile = Paths.get(tempPath.toString(), libname + getExt(os));
        deleteOnExit(tempPath);
        var libInputStream = LoadLibrary.class.getResourceAsStream(createPath("libs", os.name(), arch.name(), libname) + getExt(os));
        Files.copy(libInputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
        System.load(tempFile.toFile().getAbsolutePath());
    }


    private static Arch getCpuArch() {
        var arch = System.getProperty("os.arch").trim();

        switch (arch) {
            case "amd64" : {
                return Arch.amd64;
            }

            case "x86_64" : {
                return Arch.amd64;
            }

            case "i386" : {
                return Arch.i386;
            }

            case "x86" : {
                return Arch.i386;
            }
            
            case "arm" : {
                return Arch.armhf;
            }

            case "aarch64" : {
                return Arch.aarch64;
            }

            case "ppc64" : {
		if (!ByteOrder.nativeOrder().equals(ByteOrder.BIG_ENDIAN)) { // Java always returns ppc64 for all 64-bit powerpc but 
                    return Arch.ppc64el;                                     // powerpc64le (our target) is very different, it uses  
		}                                                            // little-endian unlike powerpc64, so we just check 
            }                                                                // this condition to accurately identify the architecture

            default : {
                return Arch.unknown;
            }
        }
    }

    public static Os getOs() {
        var os = System.getProperty("os.name").toLowerCase().trim();

        if (os.contains("linux")) {
            return Os.linux;
        }

        if (os.contains("windows")) {
            return Os.win;
        }

        if (os.contains("mac")) {
            return Os.mac;
        }

        if (os.contains("darwin")) {
            return Os.mac;
        }

        return Os.unknown;
    }

    private static String getExt(Os os) {
        switch (os) {
            case linux: {
                return ".so";
            }

            case win: {
                return ".dll";
            }

            case mac: {
                return ".so";
            }

            default: {
                return ".so";
            }
        }
    }

    private static void deleteOnExit(Path path) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                Files.delete(path);
            } catch (IOException ignored) {
            }
        }));
    }

    private static String createPath(String... path) {
        var stringBuilder = new StringBuilder();
        stringBuilder.append("/");

        for (int i = 0; i < path.length; i++) {
            stringBuilder.append(path[i]);

            if (i < path.length -1) {
                stringBuilder.append("/");
            }
        }

        return stringBuilder.toString();
    }
}
