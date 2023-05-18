package it.tdlight.client;

import it.tdlight.ClientFactory;

public class SimpleTelegramClientFactory implements AutoCloseable {
	private final ClientFactory clientFactory;

	public SimpleTelegramClientFactory() {
		this(null);
	}

	public SimpleTelegramClientFactory(ClientFactory clientFactory) {
		if (clientFactory == null) {
			 this.clientFactory = ClientFactory.acquireCommonClientFactory();
		} else {
			this.clientFactory = clientFactory;
		}
	}

	public SimpleTelegramClientBuilder builder(TDLibSettings clientSettings) {
		return new SimpleTelegramClientBuilder(clientFactory, clientSettings);
	}

	@Override
	public void close() {
		clientFactory.close();
	}
}
