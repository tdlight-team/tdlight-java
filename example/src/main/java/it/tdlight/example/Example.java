package it.tdlight.example;

import it.tdlight.client.*;
import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.CommandHandler;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.TDLibSettings;
import it.tdlight.Init;
import it.tdlight.jni.TdApi.AuthorizationState;
import it.tdlight.jni.TdApi.Chat;
import it.tdlight.jni.TdApi.MessageContent;
import it.tdlight.jni.TdApi;
import it.tdlight.util.UnsupportedNativeLibraryException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Example class for TDLight Java
 * <p>
 * <a href="https://tdlight-team.github.io/tdlight-docs">The documentation of the TDLight functions can be found here</a>
 */
public final class Example {

	/**
	 * Admin user id, used by the stop command example
	 */
	private static final TdApi.MessageSender ADMIN_ID = new TdApi.MessageSenderUser(667900586);

	private static SimpleTelegramClient client;

	public static void main(String[] args) throws UnsupportedNativeLibraryException, InterruptedException {
		// Initialize TDLight native libraries
		Init.init();

		// Create the client factory
		try (SimpleTelegramClientFactory clientFactory = new SimpleTelegramClientFactory()) {

			// Obtain the API token
			//
			// var apiToken = new APIToken(your-api-id-here, "your-api-hash-here");
			//
			APIToken apiToken = APIToken.example();


			// Configure the client
			TDLibSettings settings = TDLibSettings.create(apiToken);
			// This is an example, remove this line to use the real telegram datacenters!
			settings.setUseTestDatacenter(true);

			// Configure the session directory
			Path sessionPath = Paths.get("example-tdlight-session");
			settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
			settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));

			// Prepare a new client builder
			SimpleTelegramClientBuilder clientBuilder = clientFactory.builder(settings);

			// Configure the authentication info
			SimpleAuthenticationSupplier<?> authenticationData = AuthenticationSupplier.testUser(7381); // Replace with AuthenticationSupplier.consoleLogin(), or .user(xxx), or .bot(xxx);

			// Add an example update handler that prints when the bot is started
			clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, Example::onUpdateAuthorizationState);

			// Add an example update handler that prints every received message
			clientBuilder.addUpdateHandler(TdApi.UpdateNewMessage.class, Example::onUpdateNewMessage);

			// Add an example command handler that stops the bot
			clientBuilder.addCommandHandler("stop", new StopCommandHandler());

			// Create and start the client
			client = clientBuilder.build(authenticationData);

			// Wait for exit
			client.waitForExit();
		}
	}

	/**
	 * Print new messages received via updateNewMessage
	 */
	private static void onUpdateNewMessage(TdApi.UpdateNewMessage update) {
		// Get the message content
		MessageContent messageContent = update.message.content;

		// Get the message text
		String text;
		if (messageContent instanceof TdApi.MessageText messageText) {
			// Get the text of the text message
			text = messageText.text.text;
		} else {
			// We handle only text messages, the other messages will be printed as their type
			text = String.format("(%s)", messageContent.getClass().getSimpleName());
		}

		// Get the chat title
		client.send(new TdApi.GetChat(update.message.chatId), chatIdResult -> {
			// Get the chat response
			Chat chat = chatIdResult.get();
			// Get the chat name
			String chatName = chat.title;

			// Print the message
			System.out.printf("Received new message from chat %s: %s%n", chatName, text);
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
		AuthorizationState authorizationState = update.authorizationState;
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