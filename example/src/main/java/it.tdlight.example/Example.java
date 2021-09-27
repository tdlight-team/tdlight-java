//
// Copyright Aliaksei Levin (levlam@telegram.org), Arseny Smirnov (arseny30@gmail.com) 2014-2020
//
// Distributed under the Boost Software License, Version 1.0. (See accompanying
// file LICENSE_1_0.txt or copy at http://www.boost.org/LICENSE_1_0.txt)
//
package it.tdlight.example;

import it.tdlight.client.APIToken;
import it.tdlight.client.Authenticable;
import it.tdlight.client.AuthenticationData;
import it.tdlight.client.CommandHandler;
import it.tdlight.client.Result;
import it.tdlight.client.SimpleTelegramClient;
import it.tdlight.client.TDLibSettings;
import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.Init;
import it.tdlight.common.ResultHandler;
import it.tdlight.common.TelegramClient;
import it.tdlight.common.utils.CantLoadLibrary;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.AuthorizationState;
import it.tdlight.jni.TdApi.Chat;
import it.tdlight.jni.TdApi.MessageContent;
import it.tdlight.jni.TdApi.MessageSender;
import it.tdlight.jni.TdApi.MessageSenderUser;
import it.tdlight.jni.TdApi.MessageText;
import it.tdlight.jni.TdApi.UpdateAuthorizationState;
import it.tdlight.jni.TdApi.UpdateNewMessage;
import it.tdlight.tdlight.ClientManager;
import java.io.BufferedReader;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Example class for TDLight Java
 *
 * The documentation of the TDLight functions can be found here:
 * https://tdlight-team.github.io/tdlight-docs
 */
public final class Example {

	/**
	 * Admin user id, used by the stop command example
	 */
	private static final long ADMIN_ID = 667900586;

	public static void main(String[] args) throws CantLoadLibrary, InterruptedException {
		// Initialize TDLight native libraries
		Init.start();

		// Obtain the API token
		APIToken apiToken = APIToken.example();

		// Configure the client
		TDLibSettings settings = TDLibSettings.create(apiToken);

		// Create a client
		SimpleTelegramClient client = new SimpleTelegramClient(settings);

		// Configure the authentication info
		AuthenticationData authenticationData = AuthenticationData.consoleLogin();

		// Add an example update handler that prints when the bot is started
		client.addUpdateHandler(UpdateAuthorizationState.class, update -> printStatus(update.authorizationState));

		// Add an example command handler that stops the bot
		client.addCommandHandler("stop", new StopCommandHandler(client));

		// Add an example update handler that prints every received message
		client.addUpdateHandler(UpdateNewMessage.class, update -> {
			// Get the message content
			MessageContent messageContent = update.message.content;

			// Get the message text
			String text;
			if (messageContent instanceof MessageText) {
				// Get the text of the text message
				text = ((MessageText) messageContent).text.text;
			} else {
				// We handle only text messages, the other messages will be printed as their type
				text = "(" + messageContent.getClass().getSimpleName() + ")";
			}

			// Get the chat title
			client.send(new TdApi.GetChat(update.message.chatId), (Result<Chat> chatIdResult) -> {
				// Get the chat response
				Chat chat = chatIdResult.get();
				// Get the chat name
				String chatName = chat.title;

				// Print the message
				System.out.println("Received new message from chat " + chatName + ": " + text);
			});
		});

		// Start the client
		client.start(authenticationData);

		// Wait for exit
		client.waitForExit();
	}

	/**
	 * Print the bot status
	 */
	private static void printStatus(AuthorizationState authorizationState) {
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
	private static boolean isAdmin(MessageSender sender) {
		if (sender instanceof MessageSenderUser) {
			MessageSenderUser senderUser = (MessageSenderUser) sender;
			return senderUser.userId == ADMIN_ID;
		}
		return false;
	}

	/**
	 * Close the bot if the /stop command is sent by the administrator
	 */
	private static class StopCommandHandler implements CommandHandler {

		private final SimpleTelegramClient client;

		public StopCommandHandler(SimpleTelegramClient client) {
			this.client = client;
		}

		@Override
		public void onCommand(Chat chat, MessageSender commandSender, String arguments) {
			// Check if the sender is the admin
			if (isAdmin(commandSender)) {
				// Stop the client
				System.out.println("Received stop command. closing...");
				client.sendClose();
			}
		}
	}
}