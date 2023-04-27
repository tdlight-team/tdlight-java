package it.tdlight;

import it.tdlight.jni.TdApi;
import java.time.Duration;
import org.reactivestreams.Publisher;

public interface ReactiveTelegramClient {

	/**
	 * Creates and registers the client
	 */
	void createAndRegisterClient();

	/**
	 * Sends a request to the TDLib.
	 *
	 * @param query   Object representing a query to the TDLib.
	 * @param timeout Response timeout
	 * @return a publisher that will emit exactly one item, or an error
	 * @throws NullPointerException if query is null.
	 */
	<R extends TdApi.Object> Publisher<TdApi.Object> send(TdApi.Function<R> query, Duration timeout);

	/**
	 * Synchronously executes a TDLib request. Only a few marked accordingly requests can be executed synchronously.
	 *
	 * @param query Object representing a query to the TDLib.
	 * @return request result or {@link TdApi.Error}.
	 * @throws NullPointerException if query is null.
	 */
	<R extends TdApi.Object> TdApi.Object execute(TdApi.Function<R> query);

	void setListener(SignalListener listener);

	/**
	 * Send close signal but don't remove the listener
	 */
	void cancel();

	/**
	 * Remove the listener
	 */
	void dispose();
}
