package it.tdlight.client;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Chat;
import it.tdlight.jni.TdApi.User;

public interface CommandHandler {

	void onCommand(Chat chat, TdApi.MessageSender commandSender, String arguments);
}
