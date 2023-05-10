package it.tdlight;

import java.util.function.LongSupplier;

public interface ClientRegistrationEventHandler {
	void onClientRegistered(int clientId, LongSupplier nextQueryIdSupplier);
}
