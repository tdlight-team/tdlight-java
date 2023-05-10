package it.tdlight;

import java.util.Map;
import java.util.function.LongSupplier;

interface ClientRegistrationEventHandler {

	void onClientRegistered(int clientId, LongSupplier nextQueryIdSupplier);
}
