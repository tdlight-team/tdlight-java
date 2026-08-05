package it.tdlight;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Object;
import it.tdlight.jni.TdApi.Update;
import it.tdlight.util.CleanSupport;
import it.tdlight.util.CleanSupport.CleanableSupport;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

class AutoCleaningTelegramClient implements TelegramClient {
	private final InternalClient client;
	// Keeps the creating factory alive while this public client is live.
	private final ClientFactoryImpl.FactoryLease ownerLease;
	// This is intentionally owned only by the wrapper, so its finalizer runs when the wrapper becomes unreachable.
	private final CleanableSupport cleanable;

	AutoCleaningTelegramClient(InternalClientsState state) {
		this(state, NativeClientAccess::send, null);
	}

	AutoCleaningTelegramClient(InternalClientsState state, InternalClient.ClientSender clientSender) {
		this(state, clientSender, null);
	}

	AutoCleaningTelegramClient(InternalClientsState state,
			InternalClient.ClientSender clientSender,
			ClientFactoryImpl.FactoryLease ownerLease) {
		CleanupState cleanupState = new CleanupState(clientSender);
		Runnable releaseOwner = ownerLease != null ? ownerLease.weakReleaseCallback() : () -> {};
		Runnable clientClosed = () -> {
			try {
				cleanupState.onClientClosed();
			} finally {
				releaseOwner.run();
			}
		};
		this.ownerLease = ownerLease;
		this.cleanable = CleanSupport.register(this, cleanupState::clean);
		this.client = new InternalClient(state,
				cleanupState::onClientRegistered,
				clientSender,
				clientClosed
		);
	}

	private static final class CleanupState {

		private final InternalClient.ClientSender clientSender;
		private boolean closed;
		private boolean closeInFlight;
		private boolean closeSent;
		private volatile int clientId;
		private volatile LongSupplier nextQueryIdSupplier;
		private volatile Thread shutdownHook;

		private CleanupState(InternalClient.ClientSender clientSender) {
			this.clientSender = clientSender;
		}

		private void onClientRegistered(int clientId, LongSupplier nextQueryIdSupplier) {
			this.clientId = clientId;
			this.nextQueryIdSupplier = nextQueryIdSupplier;
			Thread shutdownHook = new Thread(this::clean, "TDLight client shutdown " + clientId);
			this.shutdownHook = shutdownHook;
			Runtime.getRuntime().addShutdownHook(shutdownHook);
		}

		private synchronized void clean() {
			LongSupplier currentNextQueryIdSupplier = nextQueryIdSupplier;
			if (closed || closeSent || closeInFlight || currentNextQueryIdSupplier == null) {
				return;
			}
			closeInFlight = true;
			try {
				Logger logger = LoggerFactory.getLogger(TelegramClient.class);
				logger.debug(MarkerFactory.getMarker("TG"), "The client is being shut down automatically");
				long reqId = currentNextQueryIdSupplier.getAsLong();
				clientSender.send(clientId, reqId, new TdApi.Close());
				closeSent = true;
			} finally {
				closeInFlight = false;
			}
		}

		private void onClientClosed() {
			synchronized (this) {
				closed = true;
			}
			Thread currentShutdownHook = shutdownHook;
			if (currentShutdownHook != null) {
				try {
					Runtime.getRuntime().removeShutdownHook(currentShutdownHook);
				} catch (IllegalStateException | SecurityException ignored) {
				}
			}
		}
	}

	@Override
	public void initialize(UpdatesHandler updatesHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		client.initialize(updatesHandler, updateExceptionHandler, defaultExceptionHandler);
	}

	@Override
	public void initialize(ResultHandler<Update> updateHandler,
			ExceptionHandler updateExceptionHandler,
			ExceptionHandler defaultExceptionHandler) {
		client.initialize(updateHandler, updateExceptionHandler, defaultExceptionHandler);
	}

	@Override
	public <R extends Object> void send(Function<R> query,
			ResultHandler<R> resultHandler,
			ExceptionHandler exceptionHandler) {
		client.send(query, resultHandler, exceptionHandler);
	}

	@Override
	public <R extends Object> void send(Function<R> query, ResultHandler<R> resultHandler) {
		client.send(query, resultHandler);
	}

	@Override
	public <R extends Object> Object execute(Function<R> query) {
		return client.execute(query);
	}
}
