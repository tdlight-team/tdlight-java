package it.tdlight.common;

/**
 * Interface for handler of exceptions thrown while invoking ResultHandler.
 * By default, all such exceptions are ignored.
 * All exceptions thrown from ExceptionHandler are ignored.
 */
public interface ExceptionHandler {

	/**
	 * Callback called on exceptions thrown while invoking ResultHandler.
	 *
	 * @param e Exception thrown by ResultHandler.
	 */
	void onException(Throwable e);
}