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

package it.tdlight.common;

import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.common.utils.CantLoadLibrary;
import it.tdlight.common.utils.LoadLibrary;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.LogStreamEmpty;
import it.tdlight.jni.TdApi.SetLogStream;
import it.tdlight.jni.TdApi.SetLogVerbosityLevel;
import it.tdlight.tdnative.NativeClient.LogMessageHandler;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Init class to successfully initialize Tdlib
 */
public final class Init {

	public static final Logger LOG = LoggerFactory.getLogger("it.tdlight.TDLight");

	private static volatile boolean started = false;

	/**
	 * Initialize Tdlib
	 *
	 * @throws CantLoadLibrary An exception that is thrown when the LoadLibrary class fails to load the library.
	 */
	public static void start() throws CantLoadLibrary {
		if (!started) {
			synchronized (Init.class) {
				if (!started) {
					LoadLibrary.load("tdjni");
					ConstructorDetector.init();
					try {
						NativeClientAccess.execute(new SetLogVerbosityLevel(2));
						Log.setLogMessageHandler(2, (verbosityLevel, message) -> {
							switch (verbosityLevel) {
								case -1:
								case 0:
								case 1:
									LOG.error(message);
									break;
								case 2:
									LOG.warn(message);
									break;
								case 3:
									LOG.info(message);
									break;
								case 4:
									LOG.debug(message);
									break;
								default:
									LOG.trace(message);
									break;
							}
						});
						NativeClientAccess.execute(new SetLogStream(new LogStreamEmpty()));
					} catch (Throwable ex) {
						LOG.error("Can't set verbosity level on startup", ex);
					}
					started = true;
				}
			}
		}
	}
}
