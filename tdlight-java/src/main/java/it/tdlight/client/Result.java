package it.tdlight.client;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Error;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

public final class Result<T extends TdApi.Object> {

	private final T object;
	private final Error error;
	private final TelegramError telegramError;

	private Result(T object, TdApi.Error error, TelegramError telegramError) {
		this.object = object;
		this.error = error;
		this.telegramError = telegramError;
	}

	public static <T extends TdApi.Object> Result<T> of(TdApi.Object response) {
		if (response instanceof TdApi.Error) {
			return new Result<>(null, (TdApi.Error) response, null);
		} else {
			//noinspection unchecked
			return new Result<>((T) response, null, null);
		}
	}

	public static <T extends TdApi.Object> Result<T> ofError(Throwable response) {
		return new Result<>(null, null, new TelegramError(response));
	}

	public T get() {
		if (error != null) {
			throw new TelegramError(error);
		} else if (telegramError != null) {
			throw telegramError;
		}
		return Objects.requireNonNull(object);
	}

	public boolean isError() {
		return error != null || telegramError != null;
	}

	public TdApi.Error getError() {
		if (telegramError != null) {
			return telegramError.getError();
		}
		return Objects.requireNonNull(error);
	}

	public Optional<TdApi.Error> error() {
		if (error != null) {
			return Optional.of(error);
		} else if (telegramError != null) {
			return Optional.of(telegramError.getError());
		} else {
			return Optional.empty();
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Result<?> result = (Result<?>) o;
		return Objects.equals(object, result.object) && Objects.equals(error, result.error) && Objects.equals(telegramError,
				result.telegramError
		);
	}

	@Override
	public int hashCode() {
		return Objects.hash(object, error, telegramError);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", Result.class.getSimpleName() + "[", "]")
				.add("object=" + object)
				.add("error=" + error)
				.add("telegramError=" + telegramError)
				.toString();
	}
}
