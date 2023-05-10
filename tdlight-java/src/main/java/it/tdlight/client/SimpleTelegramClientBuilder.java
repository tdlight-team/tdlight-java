package it.tdlight.client;

import it.tdlight.ClientFactory;
import it.tdlight.ExceptionHandler;
import it.tdlight.ResultHandler;
import it.tdlight.jni.TdApi.Update;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SimpleTelegramClientBuilder implements MutableTelegramClient {

	private static final Logger LOG = LoggerFactory.getLogger(SimpleTelegramClientBuilder.class);
	private final ClientFactory clientManager;
	private final TDLibSettings clientSettings;
	private ClientInteraction clientInteraction;

	private final Map<String, Set<CommandHandler>> commandHandlers = new HashMap<>();
	private final Set<ResultHandler<Update>> updateHandlers = new HashSet<>();
	private final Set<ExceptionHandler> updateExceptionHandlers = new HashSet<>();
	private final Set<ExceptionHandler> defaultExceptionHandlers = new HashSet<>();

	SimpleTelegramClientBuilder(ClientFactory clientManager, TDLibSettings clientSettings) {
		this.clientManager = clientManager;
		this.clientSettings = clientSettings;
	}

	@Override
	public void setClientInteraction(ClientInteraction clientInteraction) {
		this.clientInteraction = clientInteraction;
	}

	@Override
	public <T extends Update> void addCommandHandler(String commandName, CommandHandler handler) {
		commandHandlers.computeIfAbsent(commandName, k -> new HashSet<>()).add(handler);
	}

	@Override
	public <T extends Update> void addUpdateHandler(Class<T> updateType, GenericUpdateHandler<T> handler) {
		this.updateHandlers.add(new SimpleResultHandler<>(updateType, handler));
	}

	@Override
	public void addUpdatesHandler(GenericUpdateHandler<Update> handler) {
		this.updateHandlers.add(new SimpleUpdateHandler(handler, LOG));
	}

	@Override
	public void addUpdateExceptionHandler(ExceptionHandler updateExceptionHandler) {
		this.updateExceptionHandlers.add(updateExceptionHandler);
	}

	@Override
	public void addDefaultExceptionHandler(ExceptionHandler defaultExceptionHandlers) {
		this.defaultExceptionHandlers.add(defaultExceptionHandlers);
	}

	/**
	 * Build and start the client
	 * @return Telegram client
	 */
	public SimpleTelegramClient build(AuthenticationSupplier<?> authenticationData) {
		return new SimpleTelegramClient(clientManager,
				clientSettings,
				commandHandlers,
				updateHandlers,
				updateExceptionHandlers,
				defaultExceptionHandlers,
				clientInteraction,
				authenticationData
		);
	}

}
