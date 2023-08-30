package it.tdlight.client;

import it.tdlight.ExceptionHandler;
import it.tdlight.jni.TdApi;

public interface MutableTelegramClient {

	void setClientInteraction(ClientInteraction clientInteraction);

	<T extends TdApi.Update> void addCommandHandler(String commandName, CommandHandler handler);

	<T extends TdApi.Update> void addUpdateHandler(Class<T> updateType, GenericUpdateHandler<? super T> handler);

	void addUpdatesHandler(GenericUpdateHandler<TdApi.Update> handler);

	void addUpdateExceptionHandler(ExceptionHandler updateExceptionHandler);

	void addDefaultExceptionHandler(ExceptionHandler defaultExceptionHandlers);
}
