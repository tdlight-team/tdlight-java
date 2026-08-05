package it.tdlight.client;

import it.tdlight.jni.TdApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

class TemporaryMessageHandler implements GenericUpdateHandler<TdApi.Update> {

	private static final Logger LOG = LoggerFactory.getLogger(TemporaryMessageHandler.class);

	private final ConcurrentMap<TemporaryMessageURL, CompletableFuture<TdApi.Message>> temporaryMessages;
	private final Object lifecycleLock = new Object();
	private volatile Throwable closeCause;

	public TemporaryMessageHandler(ConcurrentMap<TemporaryMessageURL, CompletableFuture<TdApi.Message>> temporaryMessages) {
		this.temporaryMessages = temporaryMessages;
	}

	CompletableFuture<TdApi.Message> register(TemporaryMessageURL temporaryMessage) {
		CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
		CompletableFuture<TdApi.Message> previous;
		synchronized (lifecycleLock) {
			if (closeCause != null) {
				future.completeExceptionally(closeCause);
				return future;
			}
			previous = temporaryMessages.put(temporaryMessage, future);
		}
		if (previous != null) {
			previous.completeExceptionally(new IllegalStateException("Another temporary message has the same id"));
		}
		future.whenComplete((result, error) -> remove(temporaryMessage, future));
		return future;
	}

	CompletableFuture<TdApi.Message> waitForSentMessage(CompletableFuture<TdApi.Message> sendRequest) {
		CompletableFuture<TdApi.Message> result = new CompletableFuture<>();
		AtomicReference<CompletableFuture<TdApi.Message>> pendingMessage = new AtomicReference<>();
		sendRequest.whenComplete((temporaryMessage, sendError) -> {
			if (sendError != null) {
				result.completeExceptionally(sendError);
				return;
			}
			if (result.isDone()) {
				return;
			}
			if (temporaryMessage == null) {
				result.completeExceptionally(new IllegalStateException("TdApi.SendMessage returned null"));
				return;
			}
			CompletableFuture<TdApi.Message> pending;
			try {
				pending = register(new TemporaryMessageURL(temporaryMessage.chatId, temporaryMessage.id));
			} catch (RuntimeException ex) {
				result.completeExceptionally(ex);
				return;
			}
			pendingMessage.set(pending);
			if (result.isDone()) {
				pending.cancel(false);
				return;
			}
			pending.whenComplete((message, pendingError) -> {
				if (pendingError != null) {
					result.completeExceptionally(pendingError);
				} else {
					result.complete(message);
				}
			});
		});
		result.whenComplete((message, error) -> {
			if (!sendRequest.isDone()) {
				sendRequest.cancel(false);
			}
			CompletableFuture<TdApi.Message> pending = pendingMessage.get();
			if (pending != null && !pending.isDone()) {
				pending.cancel(false);
			}
		});
		return result;
	}

	void close(Throwable cause) {
		List<CompletableFuture<TdApi.Message>> pending;
		synchronized (lifecycleLock) {
			if (closeCause != null) {
				return;
			}
			closeCause = Objects.requireNonNull(cause, "cause");
			pending = new ArrayList<>(temporaryMessages.values());
			temporaryMessages.clear();
		}
		for (CompletableFuture<TdApi.Message> future : pending) {
			future.completeExceptionally(cause);
		}
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
		CompletableFuture<TdApi.Message> future = remove(tempUrl);
		if (future == null) {
			logNotHandled(tempUrl);
		} else {
			future.complete(updateMessageSendSucceeded.message);
		}
	}

	private void onUpdateFailed(TdApi.UpdateMessageSendFailed updateMessageSendFailed) {
		TemporaryMessageURL tempUrl
				= new TemporaryMessageURL(updateMessageSendFailed.message.chatId, updateMessageSendFailed.oldMessageId);
		CompletableFuture<TdApi.Message> future = remove(tempUrl);
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

	private CompletableFuture<TdApi.Message> remove(TemporaryMessageURL temporaryMessage) {
		synchronized (lifecycleLock) {
			return temporaryMessages.remove(temporaryMessage);
		}
	}

	private void remove(TemporaryMessageURL temporaryMessage, CompletableFuture<TdApi.Message> future) {
		synchronized (lifecycleLock) {
			temporaryMessages.remove(temporaryMessage, future);
		}
	}
}
