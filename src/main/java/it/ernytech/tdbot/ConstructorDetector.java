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

package it.ernytech.tdbot;

import it.ernytech.tdlib.TdApi;

import java.lang.reflect.Field;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Identify the class by using the Constructor.
 */
public class ConstructorDetector {
    private static ConcurrentHashMap<Integer, Class> constructorHashMap;

    /**
     * Initialize the ConstructorDetector, it is called from the Init class.
     */
    public static void init() {
        if (constructorHashMap != null) {
            return;
        }

        Class[] classes = TdApi.class.getDeclaredClasses();
        setConstructorHashMap(classes);
    }

    /**
     * Identify the class.
     * @param CONSTRUCTOR CONSTRUCTOR of the Tdlib API.
     * @return The class related to CONSTRUCTOR.
     */
    public static Class getClass(int CONSTRUCTOR) {
        return constructorHashMap.getOrDefault(CONSTRUCTOR, null);
    }

    private static void setConstructorHashMap(Class[] tdApiClasses) {
        constructorHashMap = new ConcurrentHashMap<>();

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
        }
    }
}
