package it.tdlight.client;

import static it.tdlight.util.MapUtils.addAllKeys;

import io.atlassian.util.concurrent.CopyOnWriteMap;
import it.tdlight.ClientFactory;
import it.tdlight.ExceptionHandler;
import it.tdlight.Init;
import it.tdlight.ResultHandler;
import it.tdlight.TelegramClient;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.ChatListArchive;
import it.tdlight.jni.TdApi.ChatListMain;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.LoadChats;
import it.tdlight.jni.TdApi.LogOut;
import it.tdlight.jni.TdApi.Message;
import it.tdlight.jni.TdApi.Update;
import it.tdlight.jni.TdApi.User;
import it.tdlight.util.FutureSupport;
import it.tdlight.util.UnsupportedNativeLibraryException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unused")
public final class SimpleTelegramClient implements Authenticable, MutableTelegramClient, AutoCloseable {

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
	private final AuthenticationSupplier<?> authenticationData;

	private final CopyOnWriteMap<String, CopyOnWriteMap<CommandHandler, Void>> commandHandlers = CopyOnWriteMap.newHashMap();
	private final CopyOnWriteMap<ResultHandler<TdApi.Update>, Void> updateHandlers = CopyOnWriteMap.newHashMap();
	private final CopyOnWriteMap<ExceptionHandler, Void> updateExceptionHandlers = CopyOnWriteMap.newHashMap();
	private final CopyOnWriteMap<ExceptionHandler, Void> defaultExceptionHandlers = CopyOnWriteMap.newHashMap();

	private final AuthorizationStateReadyGetMe meGetter;
	private final AuthorizationStateReadyLoadChats mainChatsLoader;
	private final AuthorizationStateReadyLoadChats archivedChatsLoader;

	private final CompletableFuture<Void> ready = new CompletableFuture<>();
	private final CompletableFuture<Void> closed = new CompletableFuture<>();
	private final ConcurrentMap<TemporaryMessageURL, CompletableFuture<Message>> temporaryMessages = new ConcurrentHashMap<>();

	SimpleTelegramClient(ClientFactory clientFactory,
			TDLibSettings settings,
			Map<String, Set<CommandHandler>> commandHandlers,
			Set<ResultHandler<Update>> updateHandlers,
			Set<ExceptionHandler> updateExceptionHandlers,
			Set<ExceptionHandler> defaultExceptionHandlers,
			ClientInteraction clientInteraction,
			AuthenticationSupplier<?> authenticationData) {
		this.client = clientFactory.createClient();
		this.settings = Objects.requireNonNull(settings, "TDLight client settings are null");

		commandHandlers.forEach((k, v) -> {
			CopyOnWriteMap<CommandHandler, Void> mapped = CopyOnWriteMap.newHashMap();
			addAllKeys(mapped, v, null);
			this.commandHandlers.put(k, mapped);
		});
		addAllKeys(this.updateHandlers, updateHandlers, null);
		addAllKeys(this.updateExceptionHandlers, updateExceptionHandlers, null);
		addAllKeys(this.defaultExceptionHandlers, defaultExceptionHandlers, null);
		this.clientInteraction = clientInteraction != null ? clientInteraction
				: new ScannerClientInteraction(SequentialRequestsExecutor.getInstance(), this);


		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitTdlibParametersHandler(client, settings, this::handleDefaultException)
		);
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				new AuthorizationStateWaitAuthenticationDataHandler(client, this, this::handleDefaultException)
		);
		SimpleTelegramClientInteraction updateHandlerInteraction = new SimpleTelegramClientInteraction();
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class, new AuthorizationStateWaitRegistrationHandler(client,
				updateHandlerInteraction,
				this::handleDefaultException
		));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class, new AuthorizationStateWaitPasswordHandler(client,
				updateHandlerInteraction,
				this::handleDefaultException
		));
		this.addUpdateHandler(
				TdApi.UpdateAuthorizationState.class, new AuthorizationStateWaitOtherDeviceConfirmationHandler(
						updateHandlerInteraction,
						this::handleDefaultException
				));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class, new AuthorizationStateWaitCodeHandler(client,
				updateHandlerInteraction,
				getTestCode(authenticationData),
				this::handleDefaultException
		));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class, new AuthorizationStateWaitEmailAddressHandler(client,
				updateHandlerInteraction,
				this::handleDefaultException
		));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class, new AuthorizationStateWaitEmailCodeHandler(client,
				updateHandlerInteraction,
				this::handleDefaultException
		));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class, new AuthorizationStateWaitReady(this::onReady));
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class, new AuthorizationStateWaitForExit(this::onCloseUpdate));
		this.mainChatsLoader = new AuthorizationStateReadyLoadChats(client, new ChatListMain());
		this.archivedChatsLoader = new AuthorizationStateReadyLoadChats(client, new ChatListArchive());
		this.addUpdateHandler(TdApi.UpdateAuthorizationState.class,
				this.meGetter = new AuthorizationStateReadyGetMe(client, mainChatsLoader, archivedChatsLoader));
		this.addUpdateHandler(TdApi.UpdateNewMessage.class, new CommandsHandler(client, this.commandHandlers, this::getMe));
		TemporaryMessageHandler temporaryMessageHandler = new TemporaryMessageHandler(this.temporaryMessages);
		this.addUpdateHandler(TdApi.UpdateMessageSendSucceeded.class, temporaryMessageHandler);
		this.addUpdateHandler(TdApi.UpdateMessageSendFailed.class, temporaryMessageHandler);
		this.authenticationData = authenticationData;
		createDirectories();
		client.initialize(this::handleUpdate, this::handleUpdateException, this::handleDefaultException);
	}

	private String getTestCode(AuthenticationSupplier<?> authenticationData) {
		if (authenticationData instanceof AuthenticationDataImpl) {
			if (!((AuthenticationDataImpl) authenticationData).isBot()
					&& ((AuthenticationDataImpl) authenticationData).isTest()) {
				String phoneNumber = ((AuthenticationDataImpl) authenticationData).getUserPhoneNumber();
				String loginCodeChar = phoneNumber.substring(5, 6);
				StringBuilder sb = new StringBuilder();
				//noinspection StringRepeatCanBeUsed
				for (int i = 0; i < 5; i++) {
					sb.append(loginCodeChar);
				}
				return sb.toString();
			}
		}
		return null;
	}

	private void handleUpdate(TdApi.Object update) {
		boolean handled = false;
		for (ResultHandler<TdApi.Update> updateHandler : updateHandlers.keySet()) {
			updateHandler.onResult(update);
			handled = true;
		}
		if (!handled) {
			LOG.warn("An update was not handled, please use addUpdateHandler(handler) before starting the client!");
		}
	}

	private void handleUpdateException(Throwable ex) {
		boolean handled = false;
		for (ExceptionHandler updateExceptionHandler : updateExceptionHandlers.keySet()) {
			updateExceptionHandler.onException(ex);
			handled = true;
		}
		if (!handled) {
			LOG.warn("Error received from Telegram!", ex);
		}
	}

	private void handleDefaultException(Throwable ex) {
		boolean handled = false;
		for (ExceptionHandler exceptionHandler : defaultExceptionHandlers.keySet()) {
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
		CopyOnWriteMap<CommandHandler, Void> handlers = this.commandHandlers.computeIfAbsent(commandName, k -> CopyOnWriteMap.newHashMap());
		handlers.put(handler, null);
	}

	@Override
	public <T extends TdApi.Update> void addUpdateHandler(Class<T> updateType, GenericUpdateHandler<? super T> handler) {
		this.updateHandlers.put(new SimpleResultHandler<>(updateType, handler), null);
	}

	@Override
	public void addUpdatesHandler(GenericUpdateHandler<TdApi.Update> handler) {
		this.updateHandlers.put(new SimpleUpdateHandler(handler, LOG), null);
	}

	/**
	 * Optional handler to handle errors received from TDLib
	 */
	@Override
	public void addUpdateExceptionHandler(ExceptionHandler updateExceptionHandler) {
		this.updateExceptionHandlers.put(updateExceptionHandler, null);
	}

	/**
	 * Optional handler to handle uncaught errors (when using send without an appropriate error handler)
	 */
	@Override
	public void addDefaultExceptionHandler(ExceptionHandler defaultExceptionHandlers) {
		this.defaultExceptionHandlers.put(defaultExceptionHandlers, null);
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

	private <R extends TdApi.Object> boolean shouldWaitForReadiness(Function<R> function) {
		switch (function.getConstructor()) {
			case TdApi.Close.CONSTRUCTOR:
			case TdApi.SetLogVerbosityLevel.CONSTRUCTOR:
			case TdApi.SetLogTagVerbosityLevel.CONSTRUCTOR:
			case TdApi.SetLogStream.CONSTRUCTOR:
			case TdApi.SetNetworkType.CONSTRUCTOR:
			case TdApi.SetOption.CONSTRUCTOR:
			case TdApi.GetOption.CONSTRUCTOR:
			case TdApi.GetAuthorizationState.CONSTRUCTOR:
			case TdApi.GetCurrentState.CONSTRUCTOR:
			case TdApi.GetLogStream.CONSTRUCTOR:
			case TdApi.GetLogTags.CONSTRUCTOR:
			case TdApi.GetLogVerbosityLevel.CONSTRUCTOR:
			case TdApi.GetLogTagVerbosityLevel.CONSTRUCTOR:
			case TdApi.GetNetworkStatistics.CONSTRUCTOR:
			case TdApi.AddNetworkStatistics.CONSTRUCTOR:
			case TdApi.ResetNetworkStatistics.CONSTRUCTOR:
			case TdApi.AddProxy.CONSTRUCTOR:
			case TdApi.DisableProxy.CONSTRUCTOR:
			case TdApi.EditProxy.CONSTRUCTOR:
			case TdApi.RemoveProxy.CONSTRUCTOR:
			case TdApi.PingProxy.CONSTRUCTOR:
			case TdApi.TestProxy.CONSTRUCTOR:
			case TdApi.EnableProxy.CONSTRUCTOR:
			case TdApi.GetProxyLink.CONSTRUCTOR:
			case TdApi.GetProxies.CONSTRUCTOR:
			case TdApi.LogOut.CONSTRUCTOR:
			case TdApi.SetTdlibParameters.CONSTRUCTOR:
			case TdApi.CheckAuthenticationCode.CONSTRUCTOR:
			case TdApi.CheckAuthenticationBotToken.CONSTRUCTOR:
			case TdApi.CheckAuthenticationEmailCode.CONSTRUCTOR:
			case TdApi.CheckAuthenticationPassword.CONSTRUCTOR:
			case TdApi.CheckAuthenticationPasswordRecoveryCode.CONSTRUCTOR:
			case TdApi.SetAuthenticationPhoneNumber.CONSTRUCTOR:
			case TdApi.GetCountries.CONSTRUCTOR:
			case TdApi.GetCountryCode.CONSTRUCTOR:
			case TdApi.GetDatabaseStatistics.CONSTRUCTOR:
			case TdApi.GetDeepLinkInfo.CONSTRUCTOR:
			case TdApi.ParseMarkdown.CONSTRUCTOR:
			case TdApi.ParseTextEntities.CONSTRUCTOR:
			case TdApi.GetJsonString.CONSTRUCTOR:
			case TdApi.GetJsonValue.CONSTRUCTOR:
			case TdApi.GetLoginUrl.CONSTRUCTOR:
			case TdApi.GetLoginUrlInfo.CONSTRUCTOR:
			case TdApi.GetMarkdownText.CONSTRUCTOR:
			case TdApi.GetMemoryStatistics.CONSTRUCTOR:
			case TdApi.GetRecoveryEmailAddress.CONSTRUCTOR:
			case TdApi.GetStorageStatistics.CONSTRUCTOR:
			case TdApi.GetStorageStatisticsFast.CONSTRUCTOR:
				return false;
		}
		return true;
	}

	/**
	 * Sends a request to TDLib and get the result.
	 *
	 * @param function         The request to TDLib.
	 * @param resultHandler    Result handler. If it is null, nothing will be called.
	 * @throws NullPointerException if function is null.
	 */
	public <R extends TdApi.Object> void send(TdApi.Function<R> function, GenericResultHandler<R> resultHandler) {
		this.send(function, resultHandler, null);
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
		if (shouldWaitForReadiness(function)) {
			ready.whenComplete((ignored, error) -> {
				if (error != null) {
					resultHandler.onResult(Result.ofError(error));
				} else {
					this.sendUnsafe(function, resultHandler, resultHandlerExceptionHandler);
				}
			});
		} else {
			this.sendUnsafe(function, resultHandler, resultHandlerExceptionHandler);
		}
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
	public <R extends TdApi.Object> void sendUnsafe(TdApi.Function<R> function, GenericResultHandler<R> resultHandler,
			ExceptionHandler resultHandlerExceptionHandler) {
		if (resultHandlerExceptionHandler == null) {
			resultHandlerExceptionHandler = this::handleResultHandlingException;
		}
		client.send(function, result -> resultHandler.onResult(Result.of(result)), resultHandlerExceptionHandler);
	}

	/**
	 * Sends a request to TDLib and get the result.
	 *
	 * @param function         The request to TDLib.
	 * @throws NullPointerException if function is null.
	 */
	public <R extends TdApi.Object> CompletableFuture<R> send(TdApi.Function<R> function) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		if (shouldWaitForReadiness(function)) {
			return ready.thenCompose(r -> this.sendUnsafe(function));
		} else {
			return this.sendUnsafe(function);
		}
	}

	/**
	 * Sends a request to TDLib and get the result.
	 *
	 * @param function         The request to TDLib.
	 * @throws NullPointerException if function is null.
	 */
	@SuppressWarnings("unchecked")
	public <R extends TdApi.Object> CompletableFuture<R> sendUnsafe(TdApi.Function<R> function) {
		CompletableFuture<R> future = new CompletableFuture<>();
		client.send(function, result -> {
			if (result instanceof TdApi.Error) {
				future.completeExceptionally(new TelegramError((TdApi.Error) result));
			} else {
				future.complete((R) result);
			}
		}, future::completeExceptionally);
		return future;
	}

	/**
	 * Sends a message
	 *
	 * @param function The request to TDLib.
	 * @param wait     Wait until the message has been sent.
	 *                  If false, the returned message will not represent the real message, but it will be a temporary message.
	 * @throws NullPointerException if function is null.
	 */
	public CompletableFuture<TdApi.Message> sendMessage(TdApi.SendMessage function, boolean wait) {
		CompletableFuture<TdApi.Message> sendRequest = this.send(function);
		if (wait) {
			return sendRequest.thenCompose(msg -> {
				CompletableFuture<TdApi.Message> future = new CompletableFuture<>();
				CompletableFuture<?> prev = temporaryMessages.put(new TemporaryMessageURL(msg.chatId, msg.id), future);
				if (prev != null) {
					prev.completeExceptionally(new IllegalStateException("Another temporary message has the same id"));
				}
				return future;
			});
		} else {
			return sendRequest;
		}
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
	public CompletableFuture<Void> closeAsync() {
		return this.send(new TdApi.Close()).thenCompose(x -> this.waitForExitAsync());
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
		try {
			closed.get();
		} catch (ExecutionException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Wait until TDLight is closed
	 */
	public CompletableFuture<Void> waitForExitAsync() {
		return FutureSupport.copy(closed);
	}

	private void onReady() {
		this.ready.complete(null);
	}

	private void onCloseUpdate() {
		this.ready.completeExceptionally(new TelegramError(new TdApi.Error(400, "Client closed")));
		this.closed.complete(null);
		this.temporaryMessages.clear();
	}

	@Override
	public void close() throws Exception {
		this.closeAsync().get(90, TimeUnit.SECONDS);
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

	public CompletableFuture<User> getMeAsync() {
		return meGetter.getMeAsync();
	}

	/**
	 * Loads more chats from the main chat list. The loaded chats and their positions in the chat list will be sent through updates. Chats are sorted by the pair (chat.position.order, chat.id) in descending order. Returns a 404 error if all chats have been loaded.
	 *
	 **/
	public CompletableFuture<Void> loadChatListMainAsync() {
		return send(new LoadChats(new ChatListMain(), 2000)).thenAccept(ok -> {});
	}

	/**
	 * Closes the TDLib instance after a proper logout. Requires an available network connection. All local data will be destroyed. After the logout completes, updateAuthorizationState with authorizationStateClosed will be sent.
	 *
	 **/
	public CompletableFuture<Void> logOutAsync() {
		return send(new LogOut()).thenAccept(ok -> {});
	}

	public boolean isMainChatsListLoaded() {
		return mainChatsLoader.isLoaded();
	}

	public boolean isArchivedChatsListLoaded() {
		return archivedChatsLoader.isLoaded();
	}
}
