package it.tdlight;

import it.tdlight.ClientFactoryImpl.CommonClientFactory;

public interface ClientFactory extends AutoCloseable {

	/**
	 * Get the common client factory, start it if needed.
	 * Remember to call clientFactory.close() afterward to release it!
	 * @return Common client factory
	 */
	static ClientFactory acquireCommonClientFactory() {
		CommonClientFactory common = ClientFactoryImpl.COMMON;
		if (common == null) {
			synchronized (ClientFactory.class) {
				if (ClientFactoryImpl.COMMON == null) {
					ClientFactoryImpl.COMMON = new CommonClientFactory();
				}
				common = ClientFactoryImpl.COMMON;
			}
		}
		common.acquire();
		return common;
	}

	/**
	 * Create a new Client Factory
	 */
	static ClientFactory create() {
		return new ClientFactoryImpl();
	}

	TelegramClient createClient();

	ReactiveTelegramClient createReactive();

	@Override
	void close();
}
