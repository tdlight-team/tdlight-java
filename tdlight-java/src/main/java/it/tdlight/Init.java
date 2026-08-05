/*
 * Copyright (c) 2018. Emily Castellotti <info@emy.sh>
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

package it.tdlight;

import it.tdlight.jni.TdApi.LogStreamEmpty;
import it.tdlight.jni.TdApi.SetLogStream;
import it.tdlight.jni.TdApi.SetLogVerbosityLevel;
import it.tdlight.util.Native;
import it.tdlight.util.UnsupportedNativeLibraryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Initialize TDLight
 */
public final class Init {

	public static final Logger LOG = LoggerFactory.getLogger("it.tdlight.TDLight");

	private static final Initializer INITIALIZER = new Initializer();

	/**
	 * Initialize TDLight.
	 * This method is idempotent.
	 *
	 * @throws UnsupportedNativeLibraryException An exception that is thrown when the LoadLibrary class fails to load the library.
	 */
	public static void init() throws UnsupportedNativeLibraryException {
		INITIALIZER.initialize(Init::initialize);
	}

	private static void initialize() throws UnsupportedNativeLibraryException {
		Native.loadNativesInternal();
		ConstructorDetector.init();
		NativeClientAccess.execute(new SetLogVerbosityLevel(3));
		NativeClientAccess.setLogMessageHandler(3, new Slf4JLogMessageHandler());
		NativeClientAccess.execute(new SetLogStream(new LogStreamEmpty()));
	}

	@FunctionalInterface
	interface InitializationAction {

		void run() throws Throwable;
	}

	/**
	 * Coordinates initialization without publishing success before initialization has actually completed.
	 */
	static final class Initializer {

		private boolean initializing;
		private boolean initialized;

		synchronized void initialize(InitializationAction action) throws UnsupportedNativeLibraryException {
			if (initialized) {
				return;
			}
			if (initializing) {
				throw new IllegalStateException("Recursive TDLight initialization");
			}
			initializing = true;
			try {
				action.run();
			} catch (Throwable ex) {
				rethrow(ex);
			} finally {
				initializing = false;
			}
			initialized = true;
		}

		private static void rethrow(Throwable failure) throws UnsupportedNativeLibraryException {
			if (failure instanceof UnsupportedNativeLibraryException) {
				throw (UnsupportedNativeLibraryException) failure;
			}
			if (failure instanceof RuntimeException) {
				throw (RuntimeException) failure;
			}
			if (failure instanceof Error) {
				throw (Error) failure;
			}
			throw new IllegalStateException("TDLight initialization failed", failure);
		}
	}
}
