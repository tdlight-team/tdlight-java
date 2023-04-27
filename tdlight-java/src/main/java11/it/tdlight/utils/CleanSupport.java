package it.tdlight.utils;

import java.lang.ref.Cleaner;

public class CleanSupport {

	private static final Cleaner cleaner = Cleaner.create();

	public static CleanableSupport register(Object object, Runnable cleanAction) {
		var c = cleaner.register(object, cleanAction);
		return c::clean;
	}

	public interface CleanableSupport {
		public void clean();
	}
}
