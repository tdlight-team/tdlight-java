package it.tdlight.client;

import java.util.function.Consumer;

public interface Authenticable {

	void getAuthenticationData(Consumer<AuthenticationData> result);
}
