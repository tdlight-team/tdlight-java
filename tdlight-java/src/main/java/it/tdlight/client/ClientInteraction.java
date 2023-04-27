package it.tdlight.client;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface ClientInteraction {

	CompletableFuture<String> onParameterRequest(InputParameter parameter, ParameterInfo parameterInfo);
}
