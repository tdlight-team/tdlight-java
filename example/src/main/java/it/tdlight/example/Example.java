package it.tdlight.example;

import it.tdlight.Init;
import it.tdlight.Log;
import it.tdlight.Slf4JLogMessageHandler;
import it.tdlight.client.APIToken;
import it.tdlight.client.AuthenticationSupplier;
import it.tdlight.client.SimpleAuthenticationSupplier;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.SimpleTelegramClientBuilder;
import it.tdlight.client.SimpleTelegramClientFactory;
import it.tdlight.client.TDLibSettings;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.AuthorizationState;
import it.tdlight.jni.TdApi.FormattedText;
import it.tdlight.jni.TdApi.InputMessageText;
import it.tdlight.jni.TdApi.Message;
import it.tdlight.jni.TdApi.MessageContent;
import it.tdlight.jni.TdApi.MessageSenderUser;
import it.tdlight.jni.TdApi.SendMessage;
import it.tdlight.jni.TdApi.TextEntity;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * Example class for TDLight Java
 * <p>
 * <a href="https://tdlight-team.github.io/tdlight-docs">The documentation of the TDLight functions can be found here</a>
 */
public final class Example {

	public static void main(String[] args) throws Exception {
		long adminId = Integer.getInteger("it.tdlight.example.adminid", 667900586);

		// Initialize TDLight native libraries
		Init.init();

		// Set the log level
		Log.setLogMessageHandler(1, new Slf4JLogMessageHandler());

		// Create the client factory (You can create more than one client,
		// BUT only a single instance of ClientFactory is allowed globally!
		// You must reuse it if you want to create more than one client!)
		try (SimpleTelegramClientFactory clientFactory = new SimpleTelegramClientFactory()) {
			// Obtain the API token
			//
			// var apiToken = new APIToken(your-api-id-here, "your-api-hash-here");
			//
			APIToken apiToken = APIToken.example();


			// Configure the client
			TDLibSettings settings = TDLibSettings.create(apiToken);

			// Configure the session directory.
			// After you authenticate into a session, the authentication will be skipped from the next restart!
			// If you want to ensure to match the authentication supplier user/bot with your session user/bot,
			//   you can name your session directory after your user id, for example: "tdlib-session-id12345"
			Path sessionPath = Paths.get("example-tdlight-session");
			settings.setDatabaseDirectoryPath(sessionPath.resolve("data"));
			settings.setDownloadedFilesDirectoryPath(sessionPath.resolve("downloads"));

			// Prepare a new client builder
			SimpleTelegramClientBuilder clientBuilder = clientFactory.builder(settings);

			// Configure the authentication info
			// Replace with AuthenticationSupplier.consoleLogin(), or .user(xxx), or .bot(xxx);
			SimpleAuthenticationSupplier<?> authenticationData = AuthenticationSupplier.testUser(7381);
			// This is an example, remove this line to use the real telegram datacenters!
			settings.setUseTestDatacenter(true);

			// Create and start the client
			try (var app = new ExampleApp(clientBuilder, authenticationData, adminId)) {
				// Get me
				TdApi.User me = app.getClient().getMeAsync().get(1, TimeUnit.MINUTES);

				// Send a test message
				var req = new SendMessage();
				req.chatId = me.id;
				var txt = new InputMessageText();
				txt.text = new FormattedText("TDLight test", new TextEntity[0]);
				req.inputMessageContent = txt;
				Message result = app.getClient().sendMessage(req, true).get(1, TimeUnit.MINUTES);
				System.out.println("Sent message:" + result);
			}
		}
	}

	public static class ExampleApp implements AutoCloseable {

		private final SimpleTelegramClient client;

		/**
		 * Admin user id, used by the stop command example
		 */
		private final long adminId;

		public ExampleApp(SimpleTelegramClientBuilder clientBuilder,
				SimpleAuthenticationSupplier<?> authenticationData,
				long adminId) {
			this.adminId = adminId;

			// Add an example update handler that prints when the bot is started
			clientBuilder.addUpdateHandler(TdApi.UpdateAuthorizationState.class, this::onUpdateAuthorizationState);

			// Add an example command handler that stops the bot
			clientBuilder.addCommandHandler("stop", this::onStopCommand);

			// Add an example update handler that prints every received message
			clientBuilder.addUpdateHandler(TdApi.UpdateNewMessage.class, this::onUpdateNewMessage);

			// Build the client
			this.client = clientBuilder.build(authenticationData);
		}

		@Override
		public void close() throws Exception {
			client.close();
		}

		public SimpleTelegramClient getClient() {
			return client;
		}

		/**
		 * Print the bot status
		 */
		private void onUpdateAuthorizationState(TdApi.UpdateAuthorizationState update) {
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
		 * Print new messages received via updateNewMessage
		 */
		private void onUpdateNewMessage(TdApi.UpdateNewMessage update) {
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

			long chatId = update.message.chatId;

			// Get the chat title
			client.send(new TdApi.GetChat(chatId))
					// Use the async completion handler, to avoid blocking the TDLib response thread accidentally
					.whenCompleteAsync((chatIdResult, error) -> {
						if (error != null) {
							// Print error
							System.err.printf("Can't get chat title of chat %s%n", chatId);
							error.printStackTrace(System.err);
						} else {
							// Get the chat name
							String title = chatIdResult.title;
							// Print the message
							System.out.printf("Received new message from chat %s (%s): %s%n", title, chatId, text);
						}
					});
		}

		/**
		 * Close the bot if the /stop command is sent by the administrator
		 */
		private void onStopCommand(TdApi.Chat chat, TdApi.MessageSender commandSender, String arguments) {
			// Check if the sender is the admin
			if (isAdmin(commandSender)) {
				// Stop the client
				System.out.println("Received stop command. closing...");
				client.sendClose();
			}
		}

		/**
		 * Check if the command sender is admin
		 */
		public boolean isAdmin(TdApi.MessageSender sender) {
			if (sender instanceof MessageSenderUser messageSenderUser) {
				return messageSenderUser.userId == adminId;
			} else {
				return false;
			}
		}

	}
}