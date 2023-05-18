package it.tdlight.client;

import it.tdlight.ClientFactory;
import java.util.Objects;

public class SimpleTelegramClientFactory implements AutoCloseable {
	private final ClientFactory clientFactory;

	public SimpleTelegramClientFactory() {
		this(null);
	}

	public SimpleTelegramClientFactory(ClientFactory clientFactory) {
		this.clientFactory = Objects.requireNonNullElseGet(clientFactory, ClientFactory::acquireCommonClientFactory);
	}

	public SimpleTelegramClientBuilder builder(TDLibSettings clientSettings) {
		return new SimpleTelegramClientBuilder(clientFactory, clientSettings);
	}

	@Override
	public void close() {
		clientFactory.close();
	}
}
