package it.tdlight.tdnative;

public class ObjectsUtils {
	/**
	 * Returns the first argument if it is non-{@code null} and
	 * otherwise returns the non-{@code null} second argument.
	 *
	 * @param obj an object
	 * @param defaultObj a non-{@code null} object to return if the first argument
	 *                   is {@code null}
	 * @param <T> the type of the reference
	 * @return the first argument if it is non-{@code null} and
	 *        otherwise the second argument if it is non-{@code null}
	 * @throws NullPointerException if both {@code obj} is null and
	 *        {@code defaultObj} is {@code null}
	 * @since 9
	 */
	public static <T> T requireNonNullElse(T obj, T defaultObj) {
		return (obj != null) ? obj : requireNonNull(defaultObj, "defaultObj");
	}

	/**
	 * Checks that the specified object reference is not {@code null}. This
	 * method is designed primarily for doing parameter validation in methods
	 * and constructors, as demonstrated below:
	 * <blockquote><pre>
	 * public Foo(Bar bar) {
	 *     this.bar = Objects.requireNonNull(bar);
	 * }
	 * </pre></blockquote>
	 *
	 * @param obj the object reference to check for nullity
	 * @param <T> the type of the reference
	 * @return {@code obj} if not {@code null}
	 * @throws NullPointerException if {@code obj} is {@code null}
	 */
	public static <T> T requireNonNull(T obj) {
		if (obj == null)
			throw new NullPointerException();
		return obj;
	}

	/**
	 * Checks that the specified object reference is not {@code null} and
	 * throws a customized {@link NullPointerException} if it is. This method
	 * is designed primarily for doing parameter validation in methods and
	 * constructors with multiple parameters, as demonstrated below:
	 * <blockquote><pre>
	 * public Foo(Bar bar, Baz baz) {
	 *     this.bar = Objects.requireNonNull(bar, "bar must not be null");
	 *     this.baz = Objects.requireNonNull(baz, "baz must not be null");
	 * }
	 * </pre></blockquote>
	 *
	 * @param obj     the object reference to check for nullity
	 * @param message detail message to be used in the event that a {@code
	 *                NullPointerException} is thrown
	 * @param <T> the type of the reference
	 * @return {@code obj} if not {@code null}
	 * @throws NullPointerException if {@code obj} is {@code null}
	 */
	public static <T> T requireNonNull(T obj, String message) {
		if (obj == null)
			throw new NullPointerException(message);
		return obj;
	}
}
