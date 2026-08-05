package it.tdlight;

import static it.tdlight.util.TdApiObjectDescriptor.describe;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Object;
import it.tdlight.util.CleanSupport;
import it.tdlight.util.CleanSupport.CleanableSupport;
import it.tdlight.util.UnsupportedNativeLibraryException;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TDLight client factory
 */
class ClientFactoryImpl implements ClientFactory {

	private static final Logger logger = LoggerFactory.getLogger(ClientFactoryImpl.class);

	static volatile CommonClientFactory COMMON;

	private final FactoryLifecycle lifecycle;
	private final CleanableSupport cleanable;

	public ClientFactoryImpl() {
		this(true);
	}

	ClientFactoryImpl(boolean initializeNative) {
		if (initializeNative) {
			try {
				Init.init();
			} catch (UnsupportedNativeLibraryException e) {
				throw new RuntimeException("Can't load the client factory because TDLight can't be loaded", e);
			}
		}
		FactoryLifecycle lifecycle = new FactoryLifecycle();
		this.lifecycle = lifecycle;
		this.cleanable = CleanSupport.register(this, lifecycle::requestClose);
	}

	@Override
	public TelegramClient createClient() {
		lifecycle.state.ensureAcceptingClients();
		FactoryLease ownerLease = new FactoryLease(this);
		return new AutoCleaningTelegramClient(lifecycle.state, NativeClientAccess::send, ownerLease);
	}

	@Override
	public ReactiveTelegramClient createReactive() {
		lifecycle.state.ensureAcceptingClients();
		FactoryLease ownerLease = new FactoryLease(this);
		InternalReactiveClient client = new InternalReactiveClient(lifecycle.state,
				NativeClientAccess::send,
				ownerLease.weakReleaseCallback()
		);
		return new OwnedReactiveClient(client, ownerLease);
	}

	static void handleClientEvents(InternalClientsState state,
			int clientId,
			boolean isClosed,
			long[] clientEventIds,
			TdApi.Object[] clientEvents,
			int arrayOffset,
			int arrayLength) {
		ClientEventsHandler handler;
		StampedLock eventsHandlingLock = state.getEventsHandlingLock();
		long stamp = eventsHandlingLock.readLock();
		try {
			handler = state.getClientEventsHandler(clientId);
			if (isClosed && handler != null) {
				state.removeClientEventHandlers(clientId, handler);
			}
		} finally {
			eventsHandlingLock.unlockRead(stamp);
		}

		if (handler != null) {
			try {
				handler.handleEvents(isClosed, clientEventIds, clientEvents, arrayOffset, arrayLength);
			} finally {
				if (isClosed) {
					state.removeClientEventHandlers(clientId, handler);
				}
			}
			return;
		}

		java.util.List<DroppedEvent> droppedEvents = getEffectivelyDroppedEvents(clientEventIds,
				clientEvents,
				arrayOffset,
				arrayLength
		);

		if (!droppedEvents.isEmpty()) {
			logger.error("Unknown client id \"{}\"! {} events have been dropped!", clientId, droppedEvents.size());
			for (DroppedEvent droppedEvent : droppedEvents) {
				logger.error("Dropped event id \"{}\" has type {}",
						droppedEvent.id,
						describe(droppedEvent.event)
				);
			}
		}
	}

	/**
	 * Get only events that have been dropped, ignoring synthetic errors related to the closure of a client
	 */
	private static List<DroppedEvent> getEffectivelyDroppedEvents(long[] clientEventIds,
			TdApi.Object[] clientEvents,
			int arrayOffset,
			int arrayLength) {
		java.util.List<DroppedEvent> droppedEvents = new ArrayList<>(arrayLength);
		for (int i = arrayOffset; i < arrayOffset + arrayLength; i++) {
			long id = clientEventIds[i];
			TdApi.Object event = clientEvents[i];
			boolean mustPrintError = true;
			if (event instanceof TdApi.Error) {
				TdApi.Error errorEvent = (TdApi.Error) event;
				if (Objects.equals("Request aborted", errorEvent.message)) {
					mustPrintError = false;
				}
			}
			if (mustPrintError) {
				droppedEvents.add(new DroppedEvent(id, event));
			}
		}
		return droppedEvents;
	}

	public void closeInternal() {
		try {
			// The cleaner action only initiates closure so the shared finalizer thread can never block here.
			cleanable.clean();
			lifecycle.awaitClosed();
		} catch (Throwable e) {
			logger.error("Failed to close", e);
		}
	}

	@Override
	public void close() {
		this.closeInternal();
	}

	private static final class FactoryLifecycle {

		private final InternalClientsState state;
		private final ResponseReceiver responseReceiver;

		private FactoryLifecycle() {
			InternalClientsState newState = new InternalClientsState() {
				@Override
				protected void onStart() {
					responseReceiver.start();
				}

				@Override
				protected void onClientRegistered(int clientId) {
					responseReceiver.registerClient(clientId);
				}
			};
			this.state = newState;
			this.responseReceiver = new NativeResponseReceiver((clientId,
					isClosed,
					clientEventIds,
					clientEvents,
					arrayOffset,
					arrayLength) -> handleClientEvents(newState,
					clientId,
					isClosed,
					clientEventIds,
					clientEvents,
					arrayOffset,
					arrayLength
			), newState::getNextQueryId);
		}

		private void requestClose() {
			state.shouldCloseNow();
			responseReceiver.requestClose();
		}

		private void awaitClosed() {
			try {
				responseReceiver.awaitClose();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted while closing TDLight response receiver", ex);
			} finally {
				state.setStopped();
			}
		}
	}

	static final class FactoryLease {

		private volatile ClientFactoryImpl owner;

		FactoryLease(ClientFactoryImpl owner) {
			this.owner = Objects.requireNonNull(owner, "Factory owner is null");
		}

		Runnable weakReleaseCallback() {
			WeakReference<FactoryLease> leaseReference = new WeakReference<>(this);
			return () -> {
				FactoryLease lease = leaseReference.get();
				if (lease != null) {
					lease.owner = null;
				}
			};
		}
	}

	private static final class OwnedReactiveClient implements ReactiveTelegramClient {

		private final InternalReactiveClient client;
		// Keeps the creating factory alive while this public client is live.
		@SuppressWarnings("unused")
		private final FactoryLease ownerLease;

		private OwnedReactiveClient(InternalReactiveClient client, FactoryLease ownerLease) {
			this.client = client;
			this.ownerLease = ownerLease;
		}

		@Override
		public void createAndRegisterClient() {
			client.createAndRegisterClient();
		}

		@Override
		public <R extends TdApi.Object> Publisher<TdApi.Object> send(TdApi.Function<R> query, Duration timeout) {
			return new LeaseRetainingPublisher(client.send(query, timeout), ownerLease);
		}

		@Override
		public <R extends TdApi.Object> TdApi.Object execute(TdApi.Function<R> query) {
			return client.execute(query);
		}

		@Override
		public void setListener(SignalListener listener) {
			client.setListener(listener);
		}

		@Override
		public void cancel() {
			client.cancel();
		}

		@Override
		public void dispose() {
			client.dispose();
		}
	}

	private static final class LeaseRetainingPublisher implements Publisher<TdApi.Object> {

		private final Publisher<TdApi.Object> publisher;
		// A cold publisher can be subscribed more than once, so it retains the factory for its own lifetime.
		@SuppressWarnings("unused")
		private final FactoryLease ownerLease;

		private LeaseRetainingPublisher(Publisher<TdApi.Object> publisher, FactoryLease ownerLease) {
			this.publisher = Objects.requireNonNull(publisher, "Publisher is null");
			this.ownerLease = Objects.requireNonNull(ownerLease, "Factory owner lease is null");
		}

		@Override
		public void subscribe(Subscriber<? super TdApi.Object> subscriber) {
			Objects.requireNonNull(subscriber, "Subscriber is null");
			OperationLease operationLease = new OperationLease(ownerLease);
			try {
				publisher.subscribe(new LeaseRetainingSubscriber(subscriber, operationLease));
			} catch (RuntimeException | java.lang.Error ex) {
				operationLease.release();
				throw ex;
			}
		}
	}

	private static final class LeaseRetainingSubscriber implements Subscriber<TdApi.Object> {

		private final Subscriber<? super TdApi.Object> subscriber;
		private final OperationLease operationLease;

		private LeaseRetainingSubscriber(Subscriber<? super TdApi.Object> subscriber, OperationLease operationLease) {
			this.subscriber = subscriber;
			this.operationLease = operationLease;
		}

		@Override
		public void onSubscribe(Subscription subscription) {
			try {
				subscriber.onSubscribe(new LeaseRetainingSubscription(subscription, operationLease));
			} catch (RuntimeException | java.lang.Error ex) {
				operationLease.release();
				throw ex;
			}
		}

		@Override
		public void onNext(TdApi.Object item) {
			try {
				subscriber.onNext(item);
			} catch (RuntimeException | java.lang.Error ex) {
				operationLease.release();
				throw ex;
			}
		}

		@Override
		public void onError(Throwable throwable) {
			try {
				subscriber.onError(throwable);
			} finally {
				operationLease.release();
			}
		}

		@Override
		public void onComplete() {
			try {
				subscriber.onComplete();
			} finally {
				operationLease.release();
			}
		}
	}

	private static final class LeaseRetainingSubscription implements Subscription {

		private final Subscription subscription;
		private final OperationLease operationLease;

		private LeaseRetainingSubscription(Subscription subscription, OperationLease operationLease) {
			this.subscription = Objects.requireNonNull(subscription, "Subscription is null");
			this.operationLease = operationLease;
		}

		@Override
		public void request(long count) {
			try {
				subscription.request(count);
			} catch (RuntimeException | java.lang.Error ex) {
				operationLease.release();
				throw ex;
			}
		}

		@Override
		public void cancel() {
			try {
				subscription.cancel();
			} finally {
				operationLease.release();
			}
		}
	}

	private static final class OperationLease {

		@SuppressWarnings("unused")
		private volatile FactoryLease ownerLease;

		private OperationLease(FactoryLease ownerLease) {
			this.ownerLease = ownerLease;
		}

		private void release() {
			ownerLease = null;
		}
	}

	private static final class DroppedEvent {

		private final long id;
		private final TdApi.Object event;

		private DroppedEvent(long id, Object event) {
			this.id = id;
			this.event = event;
		}
	}

	static class CommonClientFactory implements ClientFactory {

		private final Supplier<? extends ClientFactory> clientFactorySupplier;
		private int references;
		private ClientFactory clientFactory;

		CommonClientFactory() {
			this(ClientFactoryImpl::new);
		}

		CommonClientFactory(Supplier<? extends ClientFactory> clientFactorySupplier) {
			this.clientFactorySupplier = Objects.requireNonNull(clientFactorySupplier, "Client factory supplier is null");
		}

		void acquire() {
			synchronized (this) {
				if (clientFactory == null) {
					clientFactory = Objects.requireNonNull(clientFactorySupplier.get(),
							"Client factory supplier returned null");
				}
				references++;
			}
		}

		private ClientFactory getClientFactory() {
			ClientFactory clientFactory;
			synchronized (this) {
				clientFactory = this.clientFactory;
				if (clientFactory == null) {
					throw new IllegalStateException("Common client factory is closed");
				}
			}
			return clientFactory;
		}

		@Override
		public TelegramClient createClient() {
			return getClientFactory().createClient();
		}

		@Override
		public ReactiveTelegramClient createReactive() {
			return getClientFactory().createReactive();
		}

		@Override
		public void close() {
			ClientFactory clientFactoryToClose;
			synchronized (this) {
				if (references <= 0) {
					throw new IllegalStateException("Common client factory is not acquired");
				}
				references--;
				if (references == 0) {
					clientFactoryToClose = this.clientFactory;
					this.clientFactory = null;
				} else {
					clientFactoryToClose = null;
				}
			}
			if (clientFactoryToClose != null) {
				clientFactoryToClose.close();
			}
		}
	}
}
