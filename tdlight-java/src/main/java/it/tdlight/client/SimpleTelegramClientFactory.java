package it.tdlight.client;

import it.tdlight.ClientFactory;

public class SimpleTelegramClientFactory implements AutoCloseable {
	private final ClientFactory clientFactory;
	private final boolean commonClientFactory;

	public SimpleTelegramClientFactory() {
		this(null);
	}

	public SimpleTelegramClientFactory(ClientFactory clientFactory) {
		if (clientFactory == null) {
			 this.clientFactory = ClientFactory.getCommonClientFactory();
			 this.commonClientFactory = true;
		} else {
			this.clientFactory = clientFactory;
			this.commonClientFactory = false;
		}
	}

	public SimpleTelegramClientBuilder builder(TDLibSettings clientSettings) {
		return new SimpleTelegramClientBuilder(clientFactory, clientSettings);
	}

	@Override
	public void close() {
		if (!commonClientFactory) {
			clientFactory.close();
		}
	}
}
