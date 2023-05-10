package it.tdlight.client;

import it.tdlight.ClientFactory;
import it.tdlight.ExceptionHandler;
import it.tdlight.Init;
import it.tdlight.ResultHandler;
import it.tdlight.TelegramClient;
import it.tdlight.jni.TdApi.Update;
import it.tdlight.util.UnsupportedNativeLibraryException;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.ChatListArchive;
import it.tdlight.jni.TdApi.ChatListMain;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.User;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import org.jctools.maps.NonBlockingHashMap;
import org.jctools.maps.NonBlockingHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public final class SimpleTelegramClient implements Authenticable, MutableTelegramClient {

	public static final Logger LOG = LoggerFactory.getLogger(SimpleTelegramClient.class);

	static {
		try {
			Init.init();
		} catch (UnsupportedNativeLibraryException e) {
			throw new RuntimeException("Can't load native libraries", e);
		}
	}

	private final TelegramClient client;
	private ClientInteraction clientInteraction;
	private final TDLibSettings settings;
	private AuthenticationSupplier<?> authenticationData;

	private final Map<String, Set<CommandHandler>> commandHandlers = new NonBlockingHashMap<>();
	private final Set<ResultHandler<TdApi.Update>> updateHandlers = new NonBlockingHashSet<>();
	private final Set<ExceptionHandler> updateExceptionHandlers = new NonBlockingHashSet<>();
	private final Set<ExceptionHandler> defaultExceptionHandlers = new NonBlockingHashSet<>();

	private final AuthorizationStateReadyGetMe meGetter;
	private final AuthorizationStateReadyLoadChats mainChatsLoader;
	private final AuthorizationStateReadyLoadChats archivedChatsLoader;

	private final CountDownLatch closed = new CountDownLatch(1);

	SimpleTelegramClient(ClientFactory clientFactory,
			TDLibSettings settings,
			Map<String, Set<CommandHandler>> commandHandlers,
			Set<ResultHandler<Update>> updateHandlers,
			Set<ExceptionHandler> updateExceptionHandlers,
			Set<ExceptionHandler> defaultExceptionHandlers,
			ClientInteraction clientInteraction) {
		this.client = clientFactory.createClient();
		this.settings = Objects.requireNonNull(settings, "TDLight client settings are null");

		this.commandHandlers.putAll(commandHandlers);
		this.updateHandlers.addAll(updateHandlers);
		this.updateExceptionHandlers.addAll(updateExceptionHandlers);
		this.defaultExceptionHandlers.addAll(defaultExceptionHandlers);
		this.clientInteraction = clientInteraction != null ? clientInteraction
				: new ScannerClientInteraction(SequentialRequestsExecutor.getInstance(), this);


		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitTdlibParametersHandler(client, settings, this::handleDefaultException)
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitAuthenticationDataHandler(client, this, this::handleDefaultException)
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitRegistrationHandler(client,
						new SimpleTelegramClientInteraction(),
						this::handleDefaultException
				)
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitPasswordHandler(client,
						new SimpleTelegramClientInteraction(),
						this::handleDefaultException
				)
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitOtherDeviceConfirmationHandler(new SimpleTelegramClientInteraction(),
						this::handleDefaultException
				)
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitCodeHandler(client,
						new SimpleTelegramClientInteraction(),
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
	public AuthenticationSupplier<?> getAuthenticationSupplier() {
		return authenticationData;
	}

	@Override
	public void setClientInteraction(ClientInteraction clientInteraction) {
		this.clientInteraction = clientInteraction;
	}

	@Override
	public <T extends TdApi.Update> void addCommandHandler(String commandName, CommandHandler handler) {
		Set<CommandHandler> handlers = this.commandHandlers.computeIfAbsent(commandName, k -> new NonBlockingHashSet<>());
		handlers.add(handler);
	}

	@Override
	public <T extends TdApi.Update> void addUpdateHandler(Class<T> updateType, GenericUpdateHandler<T> handler) {
		this.updateHandlers.add(new SimpleResultHandler<>(updateType, handler));
	}

	@Override
	public void addUpdatesHandler(GenericUpdateHandler<TdApi.Update> handler) {
		this.updateHandlers.add(new SimpleUpdateHandler(handler, LOG));
	}

	/**
	 * Optional handler to handle errors received from TDLib
	 */
	@Override
	public void addUpdateExceptionHandler(ExceptionHandler updateExceptionHandler) {
		this.updateExceptionHandlers.add(updateExceptionHandler);
	}

	/**
	 * Optional handler to handle uncaught errors (when using send without an appropriate error handler)
	 */
	@Override
	public void addDefaultExceptionHandler(ExceptionHandler defaultExceptionHandlers) {
		this.defaultExceptionHandlers.add(defaultExceptionHandlers);
	}

	/**
	 * Start the client
	 */
	public void start(AuthenticationSupplier<?> authenticationData) {
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
	 * Sends a request to TDLib and get the result.
	 *
	 * @param function         The request to TDLib.
	 * @param resultHandler    Result handler. If it is null, nothing will be called.
	 * @throws NullPointerException if function is null.
	 */
	public <R extends TdApi.Object> void send(TdApi.Function<R> function, GenericResultHandler<R> resultHandler) {
		client.send(function, result -> resultHandler.onResult(Result.of(result)), this::handleResultHandlingException);
	}

	/**
	 * Sends a request to TDLib and get the result.
	 *
	 * @param function         The request to TDLib.
	 * @param resultHandler    Result handler. If it is null, nothing will be called.
	 * @param resultHandlerExceptionHandler Handle exceptions thrown inside the result handler.
	 *                                       If it is null, the default exception handler will be called.
	 * @throws NullPointerException if function is null.
	 */
	public <R extends TdApi.Object> void send(TdApi.Function<R> function, GenericResultHandler<R> resultHandler,
			ExceptionHandler resultHandlerExceptionHandler) {
		if (resultHandlerExceptionHandler == null) {
			resultHandlerExceptionHandler = this::handleResultHandlingException;
		}
		client.send(function, result -> resultHandler.onResult(Result.of(result)), resultHandlerExceptionHandler);
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

		public SimpleTelegramClientInteraction() {
		}

		@Override
		public CompletableFuture<String> onParameterRequest(InputParameter parameter, ParameterInfo parameterInfo) {
			try {
				return clientInteraction.onParameterRequest(parameter, parameterInfo);
			} catch (RejectedExecutionException | NullPointerException ex) {
				LOG.error("Failed to execute onParameterRequest. Returning an empty string", ex);
				return CompletableFuture.completedFuture("");
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
