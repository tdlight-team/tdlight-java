package it.tdlight.client;

import it.tdlight.ResultHandler;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Object;
import it.tdlight.jni.TdApi.Update;
import org.slf4j.Logger;

public class SimpleUpdateHandler implements ResultHandler<Update> {

	private final GenericUpdateHandler<Update> handler;
	private final Logger logger;

	public SimpleUpdateHandler(GenericUpdateHandler<Update> handler, Logger logger) {
		this.handler = handler;
		this.logger = logger;
	}

	@Override
	public void onResult(Object update) {
		if (update instanceof TdApi.Update) {
			handler.onUpdate((TdApi.Update) update);
		} else {
			logger.warn("Unknown update type: {}", update);
		}
	}
}
