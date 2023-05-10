package it.tdlight.util;

public class CleanSupport {

	public static CleanableSupport register(Object object, Runnable cleanAction) {
		return cleanAction::run;
	}


	public interface CleanableSupport {
		public void clean();
	}
}
