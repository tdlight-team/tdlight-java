package it.tdlight.client;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Message;
import it.tdlight.jni.TdApi.Update;
import it.tdlight.jni.TdApi.UpdateMessageSendFailed;
import it.tdlight.jni.TdApi.UpdateMessageSendSucceeded;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;

class TemporaryMessageHandler implements GenericUpdateHandler<TdApi.Update> {

	private static final Logger LOG = LoggerFactory.getLogger(TemporaryMessageHandler.class);

	private final ConcurrentMap<TemporaryMessageURL, CompletableFuture<Message>> temporaryMessages;

	public TemporaryMessageHandler(ConcurrentMap<TemporaryMessageURL, CompletableFuture<Message>> temporaryMessages) {
		this.temporaryMessages = temporaryMessages;
	}

	@Override
	public void onUpdate(Update update) {
		switch (update.getConstructor()) {
			case TdApi.UpdateMessageSendSucceeded.CONSTRUCTOR: onUpdateSuccess(((TdApi.UpdateMessageSendSucceeded) update));
				break;
			case TdApi.UpdateMessageSendFailed.CONSTRUCTOR: onUpdateFailed(((TdApi.UpdateMessageSendFailed) update));
				break;
		}
	}

	private void onUpdateSuccess(UpdateMessageSendSucceeded updateMessageSendSucceeded) {
		TemporaryMessageURL tempUrl
				= new TemporaryMessageURL(updateMessageSendSucceeded.message.chatId, updateMessageSendSucceeded.oldMessageId);
		CompletableFuture<TdApi.Message> future = temporaryMessages.remove(tempUrl);
		if (future == null) {
			logNotHandled(tempUrl);
		} else {
			future.complete(updateMessageSendSucceeded.message);
		}
	}

	private void onUpdateFailed(UpdateMessageSendFailed updateMessageSendFailed) {
		TemporaryMessageURL tempUrl
				= new TemporaryMessageURL(updateMessageSendFailed.message.chatId, updateMessageSendFailed.oldMessageId);
		CompletableFuture<TdApi.Message> future = temporaryMessages.remove(tempUrl);
		if (future == null) {
			logNotHandled(tempUrl);
		} else {
			TdApi.Error error = new TdApi.Error(updateMessageSendFailed.errorCode, updateMessageSendFailed.errorMessage);
			future.completeExceptionally(new TelegramError(error));
		}
	}

	private void logNotHandled(TemporaryMessageURL tempUrl) {
		LOG.debug("The message {} is not being handled by the client", tempUrl);
	}
}
