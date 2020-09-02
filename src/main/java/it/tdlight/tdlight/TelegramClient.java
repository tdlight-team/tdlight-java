package it.tdlight.tdlight;

import java.io.IOException;
import java.util.List;

public interface TelegramClient {

	/**
	 * Sends request to TDLib. May be called from any thread.
	 * @param request Request to TDLib.
	 */
	void send(Request request);

	/**
	 * Receives incoming updates and request responses from TDLib. May be called from any thread, but shouldn't be called simultaneously from two different threads.
	 * @param timeout Maximum number of seconds allowed for this function to wait for new records.
	 * @param eventSize Maximum number of events allowed in list.
	 * @return An incoming update or request response list. The object returned in the response may be an empty list if the timeout expires.
	 */
	default List<Response> receive(double timeout, int eventSize) {
		return receive(timeout, eventSize, true, true);
	}

	/**
	 * Receives incoming updates and/or request responses from TDLib. May be called from any thread, but shouldn't be called simultaneously from two different threads.
	 * @param timeout Maximum number of seconds allowed for this function to wait for new records.
	 * @param eventSize Maximum number of events allowed in list.
	 * @param receiveResponses True to receive responses.
	 * @param receiveUpdates True to receive updates from TDLib.
	 * @return An incoming update or request response list. The object returned in the response may be an empty list if the timeout expires.
	 */
	List<Response> receive(double timeout, int eventSize, boolean receiveResponses, boolean receiveUpdates);

	/**
	 * Receives incoming updates and request responses from TDLib. May be called from any thread, but
	 * shouldn't be called simultaneously from two different threads.
	 *
	 * @param timeout Maximum number of seconds allowed for this function to wait for new records.
	 * @return An incoming update or request response. The object returned in the response may be a
	 * nullptr if the timeout expires.
	 */
	default Response receive(double timeout) {
		return receive(timeout, true, true);
	}

	/**
	 * Receives incoming updates and request responses from TDLib. May be called from any thread, but
	 * shouldn't be called simultaneously from two different threads.
	 *
	 * @param timeout Maximum number of seconds allowed for this function to wait for new records.
	 * @param receiveResponses True to receive responses.
	 * @param receiveUpdates True to receive updates from TDLib.
	 * @return An incoming update or request response. The object returned in the response may be a
	 * nullptr if the timeout expires.
	 */
	Response receive(double timeout, boolean receiveResponses, boolean receiveUpdates);

	/**
	 * Synchronously executes TDLib requests. Only a few requests can be executed synchronously. May be called from any thread.
	 * @param request Request to the TDLib.
	 * @return The request response.
	 */
	Response execute(Request request);

	/**
	 * Destroys the client and TDLib instance.
	 */
	void destroyClient();

	/**
	 * Initializes the client and TDLib instance.
	 */
	void initializeClient() throws IOException;
}
