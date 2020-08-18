package it.tdlight.tdlight;

import java.io.IOException;
import java.util.List;

public interface TelegramClient {

	void send(Request request);

	List<Response> receive(double timeout, int eventSize);

	Response receive(double timeout);

	Response execute(Request request);

	void destroyClient();

	void initializeClient() throws IOException;

	boolean isDestroyed();
}
