package it.tdlight;

import it.tdlight.client.TelegramError;
import it.tdlight.jni.TdApi;
import java.util.concurrent.CompletableFuture;

public interface TelegramClient {

	/**
	 * Initialize the client synchronously.
	 *
	 * @param updatesHandler          Handler in which the updates are received
	 * @param updateExceptionHandler  Handler in which the errors from updates are received
	 * @param defaultExceptionHandler Handler that receives exceptions triggered in a handler
	 */
	void initialize(UpdatesHandler updatesHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler);

	/**
	 * Initialize the client synchronously.
	 *
	 * @param updateHandler           Handler in which the updates are received
	 * @param updateExceptionHandler  Handler in which the errors from updates are received
	 * @param defaultExceptionHandler Handler that receives exceptions triggered in a handler
	 */
	default void initialize(ResultHandler<TdApi.Update> updateHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		this.initialize((UpdatesHandler) updates -> updates.forEach(updateHandler::onResult),
				updateExceptionHandler,
				defaultExceptionHandler
		);
	}

	/**
	 * Sends a request to the TDLib.
	 *
	 * @param query            Object representing a query to the TDLib.
	 * @param resultHandler    Result handler with onResult method which will be called with result of the query or with
	 *                         TdApi.Error as parameter. If it is null, nothing will be called.
	 * @param exceptionHandler Exception handler with onException method which will be called on exception thrown from
	 *                         resultHandler. If it is null, then defaultExceptionHandler will be called.
	 * @throws NullPointerException if query is null.
	 */
	<R extends TdApi.Object> void send(TdApi.Function<R> query,
			ResultHandler<R> resultHandler,
			ExceptionHandler exceptionHandler);

	/**
	 * Sends a request to the TDLib with an empty ExceptionHandler.
	 *
	 * @param query         Object representing a query to the TDLib.
	 * @param resultHandler Result handler with onResult method which will be called with result of the query or with
	 *                      TdApi.Error as parameter. If it is null, then defaultExceptionHandler will be called.
	 * @throws NullPointerException if query is null.
	 */
	default <R extends TdApi.Object> void send(TdApi.Function<R> query, ResultHandler<R> resultHandler) {
		send(query, resultHandler, null);
	}

	/**
	 * Synchronously executes a TDLib request. Only a few marked accordingly requests can be executed synchronously.
	 *
	 * @param query Object representing a query to the TDLib.
	 * @return request result or {@link TdApi.Error}.
	 * @throws NullPointerException if query is null.
	 */
	<R extends TdApi.Object> TdApi.Object execute(TdApi.Function<R> query);

	/**
	 * Sends a request to the TDLib and returns a {@link CompletableFuture} that will be completed
	 * with the result or completed exceptionally with a {@link TelegramError} if TDLib returns an error.
	 *
	 * <p>The returned future has no timeout by default. To avoid hanging indefinitely on a
	 * non-responsive request, use {@link CompletableFuture#orTimeout(long, java.util.concurrent.TimeUnit)}
	 * on the returned future.</p>
	 *
	 * @param query Object representing a query to the TDLib.
	 * @param <R>   The expected result type.
	 * @return a {@link CompletableFuture} that completes with the result or fails with {@link TelegramError}.
	 * @throws NullPointerException if query is null.
	 */
	default <R extends TdApi.Object> CompletableFuture<R> sendAsync(TdApi.Function<R> query) {
		CompletableFuture<R> future = new CompletableFuture<>();
		send(query, result -> {
			if (result.getConstructor() == TdApi.Error.CONSTRUCTOR) {
				future.completeExceptionally(new TelegramError((TdApi.Error) result));
			} else {
				@SuppressWarnings("unchecked")
				R r = (R) result;
				future.complete(r);
			}
		});
		return future;
	}
}
