package it.tdlight.util;

import java.util.concurrent.atomic.AtomicBoolean;

public class CleanSupport {

	public static CleanableSupport register(Object object, Runnable cleanAction) {
		//noinspection removal
		return new CleanableSupport() {
			private final AtomicBoolean clean = new AtomicBoolean(false);
			@Override
			public void clean() {
				if (clean.compareAndSet(false, true)) {
					cleanAction.run();
				}
			}

			@Override
			protected void finalize() {
				this.clean();
			}
		};
	}


	public interface CleanableSupport {
		public void clean();
	}
}
