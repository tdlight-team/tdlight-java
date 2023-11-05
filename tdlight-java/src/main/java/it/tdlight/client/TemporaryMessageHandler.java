package it.tdlight.client;

import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

class TemporaryMessageHandler implements GenericUpdateHandler<TdApi.Update> {

	private static final Logger LOG = LoggerFactory.getLogger(TemporaryMessageHandler.class);

	private final ConcurrentMap<TemporaryMessageURL, CompletableFuture<TdApi.Message>> temporaryMessages;

	public TemporaryMessageHandler(ConcurrentMap<TemporaryMessageURL, CompletableFuture<TdApi.Message>> temporaryMessages) {
		this.temporaryMessages = temporaryMessages;
	}

	@Override
	public void onUpdate(TdApi.Update update) {
		switch (update.getConstructor()) {
			case TdApi.UpdateMessageSendSucceeded.CONSTRUCTOR: onUpdateSuccess(((TdApi.UpdateMessageSendSucceeded) update));
				break;
			case TdApi.UpdateMessageSendFailed.CONSTRUCTOR: onUpdateFailed(((TdApi.UpdateMessageSendFailed) update));
				break;
		}
	}

	private void onUpdateSuccess(TdApi.UpdateMessageSendSucceeded updateMessageSendSucceeded) {
		TemporaryMessageURL tempUrl
				= new TemporaryMessageURL(updateMessageSendSucceeded.message.chatId, updateMessageSendSucceeded.oldMessageId);
		CompletableFuture<TdApi.Message> future = temporaryMessages.remove(tempUrl);
		if (future == null) {
			logNotHandled(tempUrl);
		} else {
			future.complete(updateMessageSendSucceeded.message);
		}
	}

	private void onUpdateFailed(TdApi.UpdateMessageSendFailed updateMessageSendFailed) {
		TemporaryMessageURL tempUrl
				= new TemporaryMessageURL(updateMessageSendFailed.message.chatId, updateMessageSendFailed.oldMessageId);
		CompletableFuture<TdApi.Message> future = temporaryMessages.remove(tempUrl);
		if (future == null) {
			logNotHandled(tempUrl);
		} else {
			TdApi.Error error = updateMessageSendFailed.error;
			future.completeExceptionally(new TelegramError(error));
		}
	}

	private void logNotHandled(TemporaryMessageURL tempUrl) {
		LOG.debug("The message {} is not being handled by the client", tempUrl);
	}
}
