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

import it.tdlight.jni.TdApi;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Identify the class by using the Constructor.
 */
@SuppressWarnings("rawtypes")
public final class ConstructorDetector {

	private static ConcurrentHashMap<Integer, Class> constructorHashMap;
	private static ConcurrentHashMap<Class, Integer> constructorHashMapInverse;

	private static void tryInit() {
		// Call this to load static methods and prevent errors during startup!
		try {
			Init.start();
		} catch (Throwable throwable) {
			throwable.printStackTrace();
		}
	}

	/**
	 * Initialize the ConstructorDetector, it is called from the Init class.
	 */
	static void init() {
		if (constructorHashMap != null) {
			return;
		}

		Class[] classes = TdApi.class.getDeclaredClasses();
		setConstructorHashMap(classes);
	}

	/**
	 * Identify the class.
	 *
	 * @param CONSTRUCTOR CONSTRUCTOR of the Tdlib API.
	 * @return The class related to CONSTRUCTOR.
	 */
	public static Class getClass(int CONSTRUCTOR) {
		tryInit();
		return constructorHashMap.getOrDefault(CONSTRUCTOR, null);
	}

	/**
	 * Identify the class.
	 *
	 * @param clazz class of the TDLib API.
	 * @return The CONSTRUCTOR.
	 */
	public static int getConstructor(Class<? extends TdApi.Object> clazz) {
		tryInit();
		return Objects.requireNonNull(constructorHashMapInverse.get(clazz));
	}

	private static void setConstructorHashMap(Class[] tdApiClasses) {
		constructorHashMap = new ConcurrentHashMap<>();
		constructorHashMapInverse = new ConcurrentHashMap<>();

		for (Class apiClass : tdApiClasses) {
			Field CONSTRUCTORField;
			int CONSTRUCTOR;

			try {
				CONSTRUCTORField = apiClass.getDeclaredField("CONSTRUCTOR");
			} catch (NoSuchFieldException e) {
				continue;
			}

			try {
				CONSTRUCTOR = CONSTRUCTORField.getInt(null);
			} catch (IllegalAccessException e) {
				continue;
			}

			constructorHashMap.put(CONSTRUCTOR, apiClass);
			constructorHashMapInverse.put(apiClass, CONSTRUCTOR);
		}
	}
}
