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
import it.tdlight.jni.TdApi.ChatListArchive;
import it.tdlight.jni.TdApi.ChatListMain;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.User;
import it.tdlight.tdnative.NativeClient;
import it.tdlight.tdnative.NativeClient.LogMessageHandler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public final class SimpleTelegramClient implements Authenticable {

	public static final Logger LOG = LoggerFactory.getLogger(SimpleTelegramClient.class);
	public static ExecutorService blockingExecutor = Executors.newSingleThreadExecutor();

	static {
		try {
			Init.start();
		} catch (CantLoadLibrary e) {
			throw new RuntimeException("Can't load native libraries", e);
		}
	}

	private final TelegramClient client;
	private ClientInteraction clientInteraction = new ScannerClientInteraction(blockingExecutor, this);
	private final TDLibSettings settings;
	private AuthenticationData authenticationData;

	private final Map<String, Set<CommandHandler>> commandHandlers = new ConcurrentHashMap<>();
	private final Set<ResultHandler<TdApi.Update>> updateHandlers = new ConcurrentHashMap<ResultHandler<TdApi.Update>, Object>().keySet(
			new Object());
	private final Set<ExceptionHandler> updateExceptionHandlers = new ConcurrentHashMap<ExceptionHandler, Object>().keySet(
			new Object());
	private final Set<ExceptionHandler> defaultExceptionHandlers = new ConcurrentHashMap<ExceptionHandler, Object>().keySet(
			new Object());

	private final AuthorizationStateReadyGetMe meGetter;
	private final AuthorizationStateReadyLoadChats mainChatsLoader;
	private final AuthorizationStateReadyLoadChats archivedChatsLoader;

	private final CountDownLatch closed = new CountDownLatch(1);

	public SimpleTelegramClient(TDLibSettings settings) {
		this.client = CommonClientManager.create(LibraryVersion.IMPLEMENTATION_NAME);
		this.settings = settings;
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitTdlibParametersHandler(client, settings, this::handleDefaultException)
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitAuthenticationDataHandler(client, this, this::handleDefaultException)
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitRegistrationHandler(client,
						new SimpleTelegramClientInteraction(blockingExecutor),
						this::handleDefaultException
				)
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitPasswordHandler(client,
						new SimpleTelegramClientInteraction(blockingExecutor),
						this::handleDefaultException
				)
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitOtherDeviceConfirmationHandler(new SimpleTelegramClientInteraction(blockingExecutor))
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitCodeHandler(client,
						new SimpleTelegramClientInteraction(blockingExecutor),
						this::handleDefaultException
				)
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class, new AuthorizationStateWaitForExit(this.closed));
		this.mainChatsLoader = new AuthorizationStateReadyLoadChats(client, new ChatListMain());
		this.archivedChatsLoader = new AuthorizationStateReadyLoadChats(client, new ChatListArchive());
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				this.meGetter = new AuthorizationStateReadyGetMe(client, mainChatsLoader, archivedChatsLoader));
		this.addUpdateHandler(TdApi.UpdateNewMessage.class, new CommandsHandler(client, this.commandHandlers, this::getMe));
	}

	private void handleUpdate(TdApi.Object update) {
		boolean handled = false;
		for (ResultHandler<TdApi.Update> updateHandler : updateHandlers) {
			updateHandler.onResult(update);
			handled = true;
		}
		if (!handled) {
			LOG.warn("An update was not handled, please use addUpdateHandler(handler) before starting the client!");
		}
	}

	private void handleUpdateException(Throwable ex) {
		boolean handled = false;
		for (ExceptionHandler updateExceptionHandler : updateExceptionHandlers) {
			updateExceptionHandler.onException(ex);
			handled = true;
		}
		if (!handled) {
			LOG.warn("Error received from Telegram!", ex);
		}
	}

	private void handleDefaultException(Throwable ex) {
		boolean handled = false;
		for (ExceptionHandler exceptionHandler : defaultExceptionHandlers) {
			exceptionHandler.onException(ex);
			handled = true;
		}
		if (!handled) {
			LOG.warn("Unhandled exception!", ex);
		}
	}

	private void handleResultHandlingException(Throwable ex) {
		LOG.error("Failed to handle the request result", ex);
	}

	@Override
	public void getAuthenticationData(Consumer<AuthenticationData> result) {
		if (authenticationData instanceof ConsoleInteractiveAuthenticationData) {
			ConsoleInteractiveAuthenticationData consoleInteractiveAuthenticationData
					= (ConsoleInteractiveAuthenticationData) authenticationData;
			try {
				blockingExecutor.execute(() -> {
						consoleInteractiveAuthenticationData.askData();
						result.accept(consoleInteractiveAuthenticationData);
				});
			} catch (RejectedExecutionException | NullPointerException ex) {
				LOG.error("Failed to execute askData. Returning an empty string", ex);
				result.accept(consoleInteractiveAuthenticationData);
			}
		} else {
			result.accept(authenticationData);
		}
	}

	public void setClientInteraction(ClientInteraction clientInteraction) {
		this.clientInteraction = clientInteraction;
	}

	public <T extends TdApi.Update> void addCommandHandler(String commandName, CommandHandler handler) {
		Set<CommandHandler> handlers = this.commandHandlers.computeIfAbsent(commandName,
				k -> new ConcurrentHashMap<CommandHandler, Object>().keySet(new Object())
		);
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
				LOG.warn("Unknown update type: {}", update);
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
	public <R extends TdApi.Object> void send(TdApi.Function<R> function, GenericResultHandler<R> resultHandler) {
		client.send(function, result -> resultHandler.onResult(Result.of(result)), this::handleResultHandlingException);
	}

	/**
	 * Send a function and get the result
	 */
	public <R extends TdApi.Object> void send(TdApi.Function<R> function, GenericResultHandler<R> resultHandler, ExceptionHandler exceptionHandler) {
		client.send(function, result -> resultHandler.onResult(Result.of(result)), exceptionHandler);
	}

	/**
	 * Execute a synchronous function.
	 * <strong>Please note that only some functions can be executed using this method.</strong>
	 * If you want to execute a function please use {@link #send(Function, GenericResultHandler)}!
	 */
	public <R extends TdApi.Object> Result<R> execute(TdApi.Function<R> function) {
		return Result.of(client.execute(function));
	}

	/**
	 * Send the close signal but don't wait
	 */
	public void sendClose() {
		client.send(new TdApi.Close(), ok -> {
			if (ok.getConstructor() == TdApi.Error.CONSTRUCTOR) {
				throw new TelegramError((TdApi.Error) ok);
			}
		});
	}

	/**
	 * Send the close signal and wait for exit
	 */
	public void closeAndWait() throws InterruptedException {
		client.send(new TdApi.Close(), ok -> {
			if (ok.getConstructor() == TdApi.Error.CONSTRUCTOR) {
				throw new TelegramError((TdApi.Error) ok);
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

		private final ExecutorService blockingExecutor;

		public SimpleTelegramClientInteraction(ExecutorService blockingExecutor) {
			this.blockingExecutor = blockingExecutor;
		}

		@Override
		public void onParameterRequest(InputParameter parameter, ParameterInfo parameterInfo, Consumer<String> result) {
			try {
				blockingExecutor.execute(() -> clientInteraction.onParameterRequest(parameter, parameterInfo, result));
			} catch (RejectedExecutionException | NullPointerException ex) {
				LOG.error("Failed to execute onParameterRequest. Returning an empty string", ex);
				result.accept("");
			}
		}
	}

	public User getMe() {
		return meGetter.getMe();
	}

	public boolean isMainChatsListLoaded() {
		return mainChatsLoader.isLoaded();
	}

	public boolean isArchivedChatsListLoaded() {
		return archivedChatsLoader.isLoaded();
	}
}
