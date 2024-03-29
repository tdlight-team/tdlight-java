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

/**
 * An exception that is thrown when a native library can't be loaded.
 */
public final class UnsupportedNativeLibraryException extends Exception {

	/**
	 * Creates a new UnsupportedNativeLibraryException.
	 */
	UnsupportedNativeLibraryException() {
		super("Failed to load TDLight native libraries");
	}

	public UnsupportedNativeLibraryException(String message) {
		super(message);
	}

	public UnsupportedNativeLibraryException(String message, Exception cause) {
		super(message, cause);
	}

	public UnsupportedNativeLibraryException(Throwable cause) {
		super("Failed to load TDLight native libraries", cause);
	}
}
