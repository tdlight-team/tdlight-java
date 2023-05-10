package it.tdlight;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Object;
import it.tdlight.jni.TdApi.Update;
import it.tdlight.util.CleanSupport;
import it.tdlight.util.CleanSupport.CleanableSupport;
import java.util.function.LongSupplier;

class AutoCleaningTelegramClient implements TelegramClient {

	private final TelegramClient client;
	private volatile CleanableSupport cleanable;

	AutoCleaningTelegramClient(InternalClientsState state) {
		this.client = new InternalClient(state, this::onClientRegistered);
	}

	public void onClientRegistered(int clientId, LongSupplier nextQueryIdSupplier) {
		Runnable shutDown = () -> NativeClientAccess.send(clientId, nextQueryIdSupplier.getAsLong(), new TdApi.Close());
		Thread shutdownHook = new Thread(shutDown);
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		cleanable = CleanSupport.register(this, shutDown);
	}

	@Override
	public void initialize(UpdatesHandler updatesHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		client.initialize(updatesHandler, updateExceptionHandler, defaultExceptionHandler);
	}

	@Override
	public void initialize(ResultHandler<Update> updateHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		client.initialize(updateHandler, updateExceptionHandler, defaultExceptionHandler);
	}

	@Override
	public <R extends Object> void send(Function<R> query,
			ResultHandler<R> resultHandler,
			ExceptionHandler exceptionHandler) {
		client.send(query, resultHandler, exceptionHandler);
	}

	@Override
	public <R extends Object> void send(Function<R> query, ResultHandler<R> resultHandler) {
		client.send(query, resultHandler);
	}

	@Override
	public <R extends Object> Object execute(Function<R> query) {
		return client.execute(query);
	}
}
