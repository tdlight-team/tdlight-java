package it.tdlight;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Object;
import it.tdlight.jni.TdApi.Update;
import it.tdlight.util.CleanSupport;
import it.tdlight.util.CleanSupport.CleanableSupport;
import java.util.Map;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

class AutoCleaningTelegramClient implements TelegramClient {
	private final InternalClient client;
	private volatile CleanableSupport cleanable;

	AutoCleaningTelegramClient(InternalClientsState state) {
		this.client = new InternalClient(state, this::onClientRegistered);
	}

	public void onClientRegistered(int clientId, LongSupplier nextQueryIdSupplier) {
		Runnable shutDown = () -> {
			Logger logger = LoggerFactory.getLogger(TelegramClient.class);
			logger.debug(MarkerFactory.getMarker("TG"), "The client is being shut down automatically");
			long reqId = nextQueryIdSupplier.getAsLong();
			NativeClientAccess.send(clientId, reqId, new TdApi.Close());
		};
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
