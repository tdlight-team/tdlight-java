package it.tdlight.client;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Chat;

public interface CommandHandler {

	void onCommand(Chat chat, TdApi.MessageSender commandSender, String arguments);
}
