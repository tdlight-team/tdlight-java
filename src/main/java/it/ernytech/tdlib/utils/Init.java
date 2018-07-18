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

import it.ernytech.tdbot.ConstructorDetector;

/**
 * Init class to successfully initialize Tdlib
 */
public class Init {
    /**
     * Initialize Tdlib
     * @throws CantLoadLibrary An exception that is thrown when the LoadLibrary class fails to load the library.
     */
    public static void start() throws CantLoadLibrary {
        var os = LoadLibrary.getOs();

        if (os == Os.win) {
            LoadLibrary.load("libeay32");
            LoadLibrary.load("ssleay32");
            LoadLibrary.load("zlib1");
        }

        LoadLibrary.load("tdjni");
        ConstructorDetector.init();
    }
}
