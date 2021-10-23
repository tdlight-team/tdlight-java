package it.tdlight.example;

import it.tdlight.client.*;
import it.tdlight.client.AuthenticationData;
import it.tdlight.client.CommandHandler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.TDLibSettings;
import it.tdlight.common.Init;
import it.tdlight.common.utils.CantLoadLibrary;
import it.tdlight.jni.TdApi;
import java.nio.file.Paths;

/**
 * Example class for TDLight Java
 * <p>
 * The documentation of the TDLight functions can be found here: https://tdlight-team.github.io/tdlight-docs
 */
public final class Example {

	/**
	 * Admin user id, used by the stop command example
	 */
	private static final TdApi.MessageSender ADMIN_ID = new TdApi.MessageSenderUser(667900586);

	private static SimpleTelegramClient client;

	public static void main(String[] args) throws CantLoadLibrary, InterruptedException {
		// Initialize TDLight native libraries
		Init.start();

		// Obtain the API token
		var apiToken = APIToken.example();

		// Configure the client
		var settings = TDLibSettings.create(apiToken);

		// Configure the session directory
		var sessionPath = Paths.get("example-tdlight-session");
		settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
		settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));

		// Create a client
		client = new SimpleTelegramClient(settings);

		// Configure the authentication info
		var authenticationData = AuthenticationData.consoleLogin();

		// Add an example update handler that prints when the bot is started
		client.addUpdateHandler(TdApi.UpdateAuthorizationState.class, Example::onUpdateAuthorizationState);

		// Add an example update handler that prints every received message
		client.addUpdateHandler(TdApi.UpdateNewMessage.class, Example::onUpdateNewMessage);

		// Add an example command handler that stops the bot
		client.addCommandHandler("stop", new StopCommandHandler());

		// Start the client
		client.start(authenticationData);

		// Wait for exit
		client.waitForExit();
	}

	private static void onUpdateNewMessage(TdApi.UpdateNewMessage update) {
		// Get the message content
		var messageContent = update.message.content;

		// Get the message text
		String text;
		if (messageContent instanceof TdApi.MessageText messageText) {
			// Get the text of the text message
			text = messageText.text.text;
		} else {
			// We handle only text messages, the other messages will be printed as their type
			text = "(" + messageContent.getClass().getSimpleName() + ")";
		}

		// Get the chat title
		client.send(new TdApi.GetChat(update.message.chatId), chatIdResult -> {
			// Get the chat response
			var chat = chatIdResult.get();
			// Get the chat name
			var chatName = chat.title;

			// Print the message
			System.out.println("Received new message from chat " + chatName + ": " + text);
		});
	}

	/**
	 * Close the bot if the /stop command is sent by the administrator
	 */
	private static class StopCommandHandler implements CommandHandler {

		@Override
		public void onCommand(TdApi.Chat chat, TdApi.MessageSender commandSender, String arguments) {
			// Check if the sender is the admin
			if (isAdmin(commandSender)) {
				// Stop the client
				System.out.println("Received stop command. closing...");
				client.sendClose();
			}
		}
	}

	/**
	 * Print the bot status
	 */
	private static void onUpdateAuthorizationState(TdApi.UpdateAuthorizationState update) {
		var authorizationState = update.authorizationState;
		if (authorizationState instanceof TdApi.AuthorizationStateReady) {
			System.out.println("Logged in");
		} else if (authorizationState instanceof TdApi.AuthorizationStateClosing) {
			System.out.println("Closing...");
		} else if (authorizationState instanceof TdApi.AuthorizationStateClosed) {
			System.out.println("Closed");
		} else if (authorizationState instanceof TdApi.AuthorizationStateLoggingOut) {
			System.out.println("Logging out...");
		}
	}

	/**
	 * Check if the command sender is admin
	 */
	private static boolean isAdmin(TdApi.MessageSender sender) {
		return sender.equals(ADMIN_ID);
	}
}