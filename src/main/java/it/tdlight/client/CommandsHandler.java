package it.tdlight.client;

import it.tdlight.common.TelegramClient;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Chat;
import it.tdlight.jni.TdApi.Message;
import it.tdlight.jni.TdApi.MessageText;
import it.tdlight.jni.TdApi.UpdateNewMessage;
import it.tdlight.jni.TdApi.User;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class CommandsHandler implements GenericUpdateHandler<UpdateNewMessage> {

	private static final Logger logger = LoggerFactory.getLogger(CommandsHandler.class);

	private final TelegramClient client;
	private final Map<String, Set<CommandHandler>> commandHandlers;
	private final AtomicReference<User> me;

	public CommandsHandler(TelegramClient client,
			Map<String, Set<CommandHandler>> commandHandlers,
			AtomicReference<User> me) {
		this.client = client;
		this.commandHandlers = commandHandlers;
		this.me = me;
	}

	@Override
	public void onUpdate(UpdateNewMessage update) {
		if (update.getConstructor() == UpdateNewMessage.CONSTRUCTOR) {
			Message message = update.message;
			if (message.forwardInfo == null && !message.isChannelPost && (message.authorSignature == null
					|| message.authorSignature.isEmpty()) && message.content.getConstructor() == MessageText.CONSTRUCTOR) {
				MessageText messageText = (MessageText) message.content;
				String text = messageText.text.text;
				if (text.startsWith("/")) {
					String[] parts = text.split(" ", 2);
					if (parts.length == 1) {
						parts = new String[]{parts[0], ""};
					}
					if (parts.length == 2) {
						String currentUnsplittedCommandName = parts[0].substring(1);
						String arguments = parts[1].trim();
						String[] commandParts = currentUnsplittedCommandName.split("@", 2);
						String currentCommandName = null;
						boolean correctTarget = false;
						if (commandParts.length == 2) {
							String myUsername = this.getMyUsername().orElse(null);
							if (myUsername != null) {
								currentCommandName = commandParts[0].trim();
								String currentTargetUsername = commandParts[1];
								if (myUsername.equalsIgnoreCase(currentTargetUsername)) {
									correctTarget = true;
								}
							}
						} else if (commandParts.length == 1) {
							currentCommandName = commandParts[0].trim();
							correctTarget = true;
						}

						if (correctTarget) {
							String commandName = currentCommandName;
							Set<CommandHandler> handlers = commandHandlers.getOrDefault(currentCommandName, Collections.emptySet());

							for (CommandHandler handler : handlers) {
								client.send(new TdApi.GetChat(message.chatId), response -> {
									if (response.getConstructor() == TdApi.Error.CONSTRUCTOR) {
										throw new TelegramError((TdApi.Error) response);
									}
									handler.onCommand((Chat) response, message.sender, arguments);
								}, error -> logger.warn("Error when handling the command {}", commandName, error));
							}
						}
					}
				}
			}
		}
	}

	private Optional<String> getMyUsername() {
		User user = this.me.get();
		if (user == null || user.username == null || user.username.isEmpty()) {
			return Optional.empty();
		} else {
			return Optional.of(user.username);
		}
	}
}
