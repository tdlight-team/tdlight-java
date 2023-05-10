package it.tdlight.util;

import java.util.Collection;
import java.util.Map;

public class MapUtils {
	public static <T, U> void addAllKeys(Map<T, U> map, Collection<? extends T> keys, U placeholderValue) {
		keys.forEach(k -> map.put(k, placeholderValue));
	}
}
