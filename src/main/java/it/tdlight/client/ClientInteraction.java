package it.tdlight.client;

import java.util.function.Consumer;

public interface ClientInteraction {

	void onParameterRequest(InputParameter parameter, ParameterInfo parameterInfo, Consumer<String> result);
}
