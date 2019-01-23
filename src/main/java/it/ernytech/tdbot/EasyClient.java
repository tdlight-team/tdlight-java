/*
 * Copyright (c) 2018. Ernesto Castellotti <erny.castell@gmail.com>
 * This file is part of JTdlib.
 *
 *     JTdlib is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     JTdlib is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with JTdlib.  If not, see <http://www.gnu.org/licenses/>.
 */

package it.ernytech.tdbot;

import it.ernytech.tdlib.*;
import it.ernytech.tdlib.utils.CloseCallback;
import it.ernytech.tdlib.utils.ErrorCallback;
import it.ernytech.tdlib.utils.ReceiveCallback;

import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Interface for easy interaction with TDLib for bot, used to implement other clients such as BotClient and UserClient.
 */
public class EasyClient<T extends EasyClient> {
    private AuthorizationHandler authorizationHandler;
    private ClientActor clientActor;
    private ConcurrentHashMap<Long, TdCallback> handlers = new ConcurrentHashMap<>();
    private AtomicLong requestId = new AtomicLong(1);
    private ExecutorService executors = Executors.newFixedThreadPool(10);
    protected volatile boolean haveAuthorization = false;
    protected String firstName;
    protected String lastName;
    private volatile boolean haveClosed = false;

    @SuppressWarnings("unchecked")
    private final T thisAsT = (T) this;

    // tdlib parameters
    private TdApi.TdlibParameters parameters;
    private boolean useTestDc = false;
    private String databaseDirectory = "jtdlib-database";
    private String filesDirectory = "jtdlib-files";
    private boolean useFileDatabase = true;
    private boolean useChatInfoDatabase = true;
    private boolean useMessageDatabase = true;
    private static boolean useSecretChats = false; // JTDLib NEVER can use secret chats, so this parameter can't be edited by user
    private int apiId = 376588;
    private String apiHash = "2143fdfc2bbba3ec723228d2f81336c9";
    private String systemLanguageCode = "en";
    private String deviceModel = "JTDLib";
    private String systemVersion = "JTDLib";
    private String applicationVersion = "1.0";
    private boolean enableStorageOptimizer = false;
    private boolean ignoreFileNames = false;


    /**
     * Creates a new EasyClient.
     * @param authorizationHandler Callback to be implemented in the client to manage the authorization.
     */
    public EasyClient(AuthorizationHandler authorizationHandler) {
        this.authorizationHandler = authorizationHandler;
        this.handlers.put(0L, new TdCallback(response -> {}, error -> {}, () -> {}));
    }

    public EasyClient(AuthorizationHandler authorizationHandler, boolean logoutAtShutdown) {
        this.authorizationHandler = authorizationHandler;
        this.handlers.put(0L, new TdCallback(response -> {}, error -> {}, () -> {}));

        if (logoutAtShutdown) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::close));
        }
    }

    public T useTestDc(boolean useTestDc) {
        this.useTestDc = useTestDc;
        return thisAsT;
    }

    public T databaseDirectory(String databaseDirectory) {
        this.databaseDirectory = databaseDirectory;
        return thisAsT;
    }

    public T filesDirectory(String filesDirectory) {
        this.filesDirectory = filesDirectory;
        return thisAsT;
    }

    public T useChatInfoDatabase(boolean useChatInfoDatabase) {
        this.useChatInfoDatabase = useChatInfoDatabase;
        return thisAsT;
    }

    public T useMessageDatabase (boolean useMessageDatabase ) {
        this.useMessageDatabase  = useMessageDatabase ;
        return thisAsT;
    }

    public EasyClient apiId (int apiId) {
        this.apiId = apiId;
        return thisAsT;
    }

    public T apiHash (String apiHash) {
        this.apiHash = apiHash;
        return thisAsT;
    }

    public T systemLanguageCode (String systemLanguageCode) {
        this.systemLanguageCode = systemLanguageCode;
        return thisAsT;
    }

    public T deviceModel (String deviceModel) {
        this.deviceModel = deviceModel;
        return thisAsT;
    }

    public T systemVersion (String systemVersion) {
        this.systemVersion = systemVersion;
        return thisAsT;
    }

    public T applicationVersion (String applicationVersion) {
        this.applicationVersion = applicationVersion;
        return thisAsT;
    }

    public T enableStorageOptimizer (boolean enableStorageOptimizer) {
        this.enableStorageOptimizer = enableStorageOptimizer;
        return thisAsT;
    }

    public T ignoreFileNames (boolean ignoreFileNames) {
        this.ignoreFileNames = ignoreFileNames;
        return thisAsT;
    }

    public T parameters (TdApi.TdlibParameters parameters) {
        this.parameters = parameters;
        return thisAsT;
    }

    public T create() {
        if (this.parameters == null) {
            this.parameters = new TdApi.TdlibParameters();
            this.parameters.useTestDc = this.useTestDc;
            this.parameters.databaseDirectory = this.databaseDirectory;
            this.parameters.filesDirectory = this.filesDirectory;
            this.parameters.useFileDatabase = this.useFileDatabase;
            this.parameters.useChatInfoDatabase = this.useChatInfoDatabase;
            this.parameters.useMessageDatabase = this.useMessageDatabase;
            this.parameters.useSecretChats = this.useSecretChats;
            this.parameters.apiId = this.apiId;
            this.parameters.apiHash = this.apiHash;
            this.parameters.systemLanguageCode = this.systemLanguageCode;
            this.parameters.deviceModel = this.deviceModel;
            this.parameters.systemVersion = this.systemVersion;
            this.parameters.applicationVersion = this.applicationVersion;
            this.parameters.enableStorageOptimizer = this.enableStorageOptimizer;
            this.parameters.ignoreFileNames = this.ignoreFileNames;
        }

        Log.setVerbosityLevel(1);
        inizializeClient();
        return thisAsT;
    }

    public void close() {
        send(new TdApi.LogOut());
        this.executors.shutdown();
        this.haveClosed = true;
    }

    /**
     * Send a request to tdlib without waiting for the result.
     * @param function TDLib API function representing a request to TDLib.
     * @return Request identifier. Responses to TDLib requests will have the same id as the corresponding request.
     */
    public long send(TdApi.Function function) {
        var requestId = this.requestId.getAndIncrement();
        this.executors.execute(() -> {
            while (true) {
                if (this.haveAuthorization) {
                    break;
                }
            }

            this.clientActor.request(new Request(requestId, function));
        });
        return requestId;
    }

    /**
     * Send a request to tdlib and send the response to the callbacks.
     * @param function TDLib API function representing a request to TDLib.
     * @param receiveCallback Interface of callback for receive incoming update or request response.
     * @param errorCallback Interface of callback for receive incoming error response.
     */
    public void execute(TdApi.Function function, ReceiveCallback receiveCallback, ErrorCallback errorCallback) {
        var requestId = send(function);
        this.handlers.put(requestId, new TdCallback(receiveCallback, errorCallback));
    }

    /**
     * Send a request to tdlib, wait for the result and return the response.
     * @param function TDLib API function representing a request to TDLib.
     * @return A response to a request.
     */
    public Response execute(TdApi.Function function) {
        var responseAtomicReference = new AtomicReference<Response>();
        var executedAtomicBoolean = new AtomicBoolean(false);

        execute(function, response -> {
            responseAtomicReference.set(response);
            executedAtomicBoolean.set(true);
        }, error -> {
            responseAtomicReference.set(error);
            executedAtomicBoolean.set(true);
        });

        while (true) {
            if (executedAtomicBoolean.get()) {
                return responseAtomicReference.get();
            }
        }
    }

    /**
     * Set the default callback for general updates (no response to requests).
     * @param receiveCallback Interface of callback for receive incoming update or request response.
     */
    public void setUpdateCallback(ReceiveCallback receiveCallback) {
        ErrorCallback errorCallback = null;
        CloseCallback closeCallback = null;

        if (this.handlers.containsKey(0L)) {
            var tdCallback = this.handlers.get(0L);

            if (tdCallback.getErrorCallback() != null) {
                errorCallback = tdCallback.getErrorCallback();
            }

            if (tdCallback.getCloseCallback() != null) {
                closeCallback = tdCallback.getCloseCallback();
            }
        }

        this.handlers.put(0L, new TdCallback(receiveCallback, errorCallback, closeCallback));
    }

    /**
     * Set the default callback for general error updates (no response to requests).
     * @param errorCallback Interface of callback for receive incoming error response.
     */
    public void setErrorCallback(ErrorCallback errorCallback) {
        ReceiveCallback receiveCallback = null;
        CloseCallback closeCallback = null;

        if (this.handlers.containsKey(0L)) {
            var tdCallback = this.handlers.get(0L);

            if (tdCallback.getReceiveCallback() != null) {
                receiveCallback = tdCallback.getReceiveCallback();
            }

            if (tdCallback.getCloseCallback() != null) {
                closeCallback = tdCallback.getCloseCallback();
            }
        }

        this.handlers.put(0L, new TdCallback(receiveCallback, errorCallback, closeCallback));
    }

    /**
     * Set the default callback for Tdlib closing notification.
     * @param closeCallback Interface of callback for receive notification of closing Tdlib.
     */
    public void setCloseCallback(CloseCallback closeCallback) {
        ReceiveCallback receiveCallback = null;
        ErrorCallback errorCallback = null;

        if (this.handlers.containsKey(0L)) {
            var tdCallback = this.handlers.get(0L);

            if (tdCallback.getReceiveCallback() != null) {
                receiveCallback = tdCallback.getReceiveCallback();
            }

            if (tdCallback.getErrorCallback() != null) {
                errorCallback = tdCallback.getErrorCallback();
            }
        }

        this.handlers.put(0L, new TdCallback(receiveCallback, errorCallback, closeCallback));
    }

    /**
     * Destroys the client and TDLib instance.
     */
    public void destroyBotClient() {
        this.clientActor.destroyClientActor();
        Log.setVerbosityLevel(5);
    }

    private void onResult(Response response) {
        System.out.println(response.getObject());
        if (response.getObject().getConstructor() == TdApi.UpdateAuthorizationState.CONSTRUCTOR) {
            TdApi.UpdateAuthorizationState updateAuthorizationState = (TdApi.UpdateAuthorizationState) response.getObject();
            authorizationHandler(updateAuthorizationState.authorizationState);
            return;
        }

        if (response.getId() == 0) {
            this.handlers.get(0L).getReceiveCallback().onResult(response);
        } else {
            var tdCallback = this.handlers.remove(response.getId());

            if (tdCallback == null) {
                return;
            }

            var receiveCallback = tdCallback.getReceiveCallback();
            receiveCallback.onResult(response);
        }
    }

    private void onError(Response error) {
        TdApi.Error tdError = (TdApi.Error) error.getObject();

        if (tdError.message.equals("PHONE_CODE_INVALID")) {
            authorizationHandler(new TdApi.AuthorizationStateWaitCode());
        }

        if (tdError.message.equals("PASSWORD_HASH_INVALID")) {
            authorizationHandler(new TdApi.AuthorizationStateWaitPassword());
        }

        if (tdError.message.equals("PHONE_NUMBER_INVALID")) {
            throw new IllegalArgumentException("Phone number is invalid!");
        }

        if (tdError.message.equals("ACCESS_TOKEN_INVALID")) {
            throw new IllegalArgumentException("Bot token is invalid!");
        }

        if (error.getId() == 0) {
            this.handlers.get(0L).getErrorCallback().onError(error);
        } else {
            var tdCallback = this.handlers.remove(error.getId());

            if (tdCallback == null) {
                return;
            }

            tdCallback.getErrorCallback().onError(error);
        }
    }

    private void onClosed() {
        this.handlers.get(0L).getCloseCallback().onClosed();
    }

    private void inizializeClient() {
        this.clientActor = new ClientActor(new TdCallback(this::onResult, this::onError, this::onClosed));
    }

    public void sendRaw(TdApi.Function function) {
        var requestId = this.requestId.getAndIncrement();
        this.clientActor.request(new Request(requestId, function));
    }

    /**
     * Manages the authorization state updates.
     * @param authorizationState The status of the authorization.
     */
    protected void authorizationHandler(TdApi.AuthorizationState authorizationState) {
        switch (authorizationState.getConstructor()) {
            case TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR : {
                sendRaw(new TdApi.SetTdlibParameters(this.parameters));
                break;
            }

            case TdApi.AuthorizationStateWaitEncryptionKey.CONSTRUCTOR: {
                sendRaw(new TdApi.CheckDatabaseEncryptionKey());
                break;
            }

            case TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR: {
                this.authorizationHandler.onAuthorizationStateWaitPhoneNumber(this);
                break;
            }

            case TdApi.AuthorizationStateWaitCode.CONSTRUCTOR: {
                var scanner = new Scanner(System.in);
		TdApi.AuthorizationStateWaitCode authorizationStateWaitCode = (TdApi.AuthorizationStateWaitCode) authorizationState;
		TdApi.CheckAuthenticationCode authCodeReply = new TdApi.CheckAuthenticationCode();
		System.out.print("Insert your code: ");
		authCodeReply.code = scanner.nextLine();

		if (!authorizationStateWaitCode.isRegistered) {
		    authCodeReply.firstName = this.firstName;
		    if (this.lastName != null) {
                        authCodeReply.lastName = this.lastName;
                    }
                }

                sendRaw(authCodeReply);
                System.out.println();
                break;
            }

            case TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR: {
                var scanner = new Scanner(System.in);
                System.out.println("Password authorization");
                System.out.println("Password hint: " + ((TdApi.AuthorizationStateWaitPassword) authorizationState).passwordHint);
                System.out.print("Insert your password: ");
                sendRaw(new TdApi.CheckAuthenticationPassword(scanner.nextLine()));
                System.out.println();
                break;
            }

            case TdApi.AuthorizationStateReady.CONSTRUCTOR: {
                this.haveAuthorization = true;
                break;
            }
            case TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR: {
                this.haveAuthorization = false;
                break;
            }
            case TdApi.AuthorizationStateClosing.CONSTRUCTOR: {
                this.haveAuthorization = false;
                break;
            }
            case TdApi.AuthorizationStateClosed.CONSTRUCTOR: {
                if(this.haveClosed) {
                    this.destroyBotClient();
                } else {
                    this.destroyBotClient();
                    inizializeClient();
                }
                break;
            }

            default: {
                throw new IllegalStateException("Unsupported authorization state:\n" + authorizationState);
            }
        }
    }
}

