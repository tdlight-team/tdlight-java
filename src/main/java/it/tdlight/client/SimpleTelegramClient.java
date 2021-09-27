package it.tdlight.client;

import it.tdlight.common.ConstructorDetector;
import it.tdlight.common.ExceptionHandler;
import it.tdlight.common.Init;
import it.tdlight.common.Log;
import it.tdlight.common.ResultHandler;
import it.tdlight.common.TelegramClient;
import it.tdlight.common.internal.CommonClientManager;
import it.tdlight.common.utils.CantLoadLibrary;
import it.tdlight.common.utils.LibraryVersion;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Chat;
import it.tdlight.jni.TdApi.Error;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Message;
import it.tdlight.jni.TdApi.MessageText;
import it.tdlight.jni.TdApi.UpdateNewMessage;
import it.tdlight.jni.TdApi.User;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public final class SimpleTelegramClient implements Authenticable {

	private static final Logger logger = LoggerFactory.getLogger(SimpleTelegramClient.class);

	static {
		try {
			Init.start();
		} catch (CantLoadLibrary e) {
			throw new RuntimeException("Can't load native libraries", e);
		}
		try {
			//noinspection deprecation
			Log.setVerbosityLevel(1);
		} catch (Throwable ex) {
			logger.warn("Can't set verbosity level", ex);
		}
	}

	private final TelegramClient client;
	private ClientInteraction clientInteraction = new ScannerClientInteraction(this);
	private final TDLibSettings settings;
	private AuthenticationData authenticationData;

	private final Map<String, Set<CommandHandler>> commandHandlers = new ConcurrentHashMap<>();
	private final Set<ResultHandler> updateHandlers = new ConcurrentHashMap<ResultHandler, Object>()
			.keySet(new Object());
	private final Set<ExceptionHandler> updateExceptionHandlers = new ConcurrentHashMap<ExceptionHandler, Object>()
			.keySet(new Object());
	private final Set<ExceptionHandler> defaultExceptionHandlers = new ConcurrentHashMap<ExceptionHandler, Object>()
			.keySet(new Object());

	private final CountDownLatch closed = new CountDownLatch(1);

	public SimpleTelegramClient(TDLibSettings settings) {
		this.client = CommonClientManager.create(LibraryVersion.IMPLEMENTATION_NAME);
		this.settings = settings;
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitTdlibParametersHandler(client, settings, this::handleDefaultException));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitEncryptionKeyHandler(client, this::handleDefaultException));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitAuthenticationDataHandler(client, this,
						this::handleDefaultException));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitRegistrationHandler(client, new SimpleTelegramClientInteraction(),
						this::handleDefaultException));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitPasswordHandler(client, new SimpleTelegramClientInteraction(),
						this::handleDefaultException));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitOtherDeviceConfirmationHandler(client, new SimpleTelegramClientInteraction(),
						this::handleDefaultException));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitCodeHandler(client, new SimpleTelegramClientInteraction(),
						this::handleDefaultException));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class, new AuthorizationStateWaitForExit(this.closed));
		AtomicReference<User> me = new AtomicReference<>();
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class, new AuthorizationStateReadyGetMe(client, me));
		this.addUpdateHandler(TdApi.UpdateNewMessage.class, new CommandsHandler(client, this.commandHandlers, me));
	}

	private void handleUpdate(TdApi.Object update) {
		boolean handled = false;
		for (ResultHandler updateHandler : updateHandlers) {
			updateHandler.onResult(update);
			handled = true;
		}
		if (!handled) {
			logger.warn("An update was not handled, please use addUpdateHandler(handler) before starting the client!");
		}
	}

	private void handleUpdateException(Throwable ex) {
		boolean handled = false;
		for (ExceptionHandler updateExceptionHandler : updateExceptionHandlers) {
			updateExceptionHandler.onException(ex);
			handled = true;
		}
		if (!handled) {
			logger.warn("Error received from Telegram!", ex);
		}
	}

	private void handleDefaultException(Throwable ex) {
		boolean handled = false;
		for (ExceptionHandler exceptionHandler : defaultExceptionHandlers) {
			exceptionHandler.onException(ex);
			handled = true;
		}
		if (!handled) {
			logger.warn("Unhandled exception!", ex);
		}
	}

	@Override
	public AuthenticationData getAuthenticationData() {
		return authenticationData;
	}

	public void setClientInteraction(ClientInteraction clientInteraction) {
		this.clientInteraction = clientInteraction;
	}

	public <T extends TdApi.Update> void addCommandHandler(String commandName, CommandHandler handler) {
		Set<CommandHandler> handlers = this.commandHandlers
				.computeIfAbsent(commandName, k -> new ConcurrentHashMap<CommandHandler, Object>().keySet(new Object()));
		handlers.add(handler);
	}

	@SuppressWarnings("unchecked")
	public <T extends TdApi.Update> void addUpdateHandler(Class<T> updateType, GenericUpdateHandler<T> handler) {
		int updateConstructor = ConstructorDetector.getConstructor(updateType);
		this.updateHandlers.add(update -> {
			if (update.getConstructor() == updateConstructor) {
				handler.onUpdate((T) update);
			}
		});
	}

	public void addUpdatesHandler(GenericUpdateHandler<TdApi.Update> handler) {
		this.updateHandlers.add(update -> {
			if (update instanceof TdApi.Update) {
				handler.onUpdate((TdApi.Update) update);
			} else {
				logger.warn("Unknown update type: {}", update);
			}
		});
	}

	/**
	 * Optional handler to handle errors received from TDLib
	 */
	public void addUpdateExceptionHandler(ExceptionHandler updateExceptionHandler) {
		this.updateExceptionHandlers.add(updateExceptionHandler);
	}

	/**
	 * Optional handler to handle uncaught errors (when using send without an appropriate error handler)
	 */
	public void addDefaultExceptionHandler(ExceptionHandler defaultExceptionHandlers) {
		this.defaultExceptionHandlers.add(defaultExceptionHandlers);
	}

	/**
	 * Start the client
	 */
	public void start(AuthenticationData authenticationData) {
		this.authenticationData = authenticationData;
		createDirectories();
		client.initialize(this::handleUpdate, this::handleUpdateException, this::handleDefaultException);
	}

	private void createDirectories() {
		try {
			if (Files.notExists(settings.getDatabaseDirectoryPath())) {
				Files.createDirectories(settings.getDatabaseDirectoryPath());
			}
			if (Files.notExists(settings.getDownloadedFilesDirectoryPath())) {
				Files.createDirectories(settings.getDownloadedFilesDirectoryPath());
			}
		} catch (IOException ex) {
			throw new UncheckedIOException("Can't create TDLight directories", ex);
		}
	}

	/**
	 * Send a function and get the result
	 */
	public <T extends TdApi.Object> void send(TdApi.Function function, GenericResultHandler<T> resultHandler) {
		client.send(function, resultHandler::onResult, resultHandler::onErrorResult);
	}

	/**
	 * Execute a synchronous function.
	 * <strong>Please note that only some functions can be executed using this method.</strong>
	 * If you want to execute a function please use {@link #send(Function, GenericResultHandler)}!
	 */
	public <T extends TdApi.Object> Result<T> execute(TdApi.Function function) {
		return Result.of(client.execute(function));
	}

	/**
	 * Send the close signal but don't wait
	 */
	public void sendClose() {
		client.send(new TdApi.Close(), ok -> {
			if (ok.getConstructor() == Error.CONSTRUCTOR) {
				throw new TelegramError((Error) ok);
			}
		});
	}

	/**
	 * Send the close signal and wait for exit
	 */
	public void closeAndWait() throws InterruptedException {
		client.send(new TdApi.Close(), ok -> {
			if (ok.getConstructor() == Error.CONSTRUCTOR) {
				throw new TelegramError((Error) ok);
			}
		});
		this.waitForExit();
	}

	/**
	 * Wait until TDLight is closed
	 */
	public void waitForExit() throws InterruptedException {
		closed.await();
	}

	private final class SimpleTelegramClientInteraction implements ClientInteraction {

		@Override
		public String onParameterRequest(InputParameter parameter, ParameterInfo parameterInfo) {
			return clientInteraction.onParameterRequest(parameter, parameterInfo);
		}
	}
}
