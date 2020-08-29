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

package it.tdlight.tdlight;

import it.tdlight.tdlight.utils.CantLoadLibrary;
import it.tdlight.tdlight.utils.LoadLibrary;
import it.tdlight.tdlight.utils.Os;

/**
 * Init class to successfully initialize Tdlib
 */
public class Init {

    private static boolean started = false;

    /**
     * Initialize Tdlib
     *
     * @throws CantLoadLibrary An exception that is thrown when the LoadLibrary class fails to load the library.
     */
    public synchronized static void start() throws CantLoadLibrary {
        if (!started) {
            Os os = LoadLibrary.getOs();

            if (os == Os.win) {
                LoadLibrary.load("libcrypto-1_1-x64");
                LoadLibrary.load("libssl-1_1-x64");
                LoadLibrary.load("zlib1");
            }

            LoadLibrary.load("tdjni");
            ConstructorDetector.init();
            started = true;
        }
    }
}
