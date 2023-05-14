package it.tdlight;

import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Object;
import it.tdlight.util.UnsupportedNativeLibraryException;
import it.tdlight.util.CleanSupport;
import it.tdlight.util.CleanSupport.CleanableSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TDLight client factory
 */
public class ClientFactory implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(ClientFactory.class);

	private static volatile ClientFactory COMMON;

	private final InternalClientsState state = new InternalClientsState() {
		@Override
		public void registerClient(int clientId, ClientEventsHandler internalClient) {
			startIfNeeded();
			super.registerClient(clientId, internalClient);
			responseReceiver.registerClient(clientId);
		}
	};

	private final ResponseReceiver responseReceiver = new NativeResponseReceiver(this::handleClientEvents);
	private volatile CleanableSupport cleanable;

	public static ClientFactory getCommonClientFactory() {
		ClientFactory common = COMMON;
		if (common == null) {
			synchronized (ClientFactory.class) {
				if (COMMON == null) {
					COMMON = new ClientFactory() {
						@Override
						public void close() {
							throw new UnsupportedOperationException("Common client factory can't be closed");
						}
					};
				}
				common = COMMON;
			}
		}
		return common;
	}

	public ClientFactory() {
		try {
			Init.init();
		} catch (UnsupportedNativeLibraryException e) {
			throw new RuntimeException("Can't load the client factory because TDLight can't be loaded", e);
		}
	}

	public TelegramClient createClient() {
		return new AutoCleaningTelegramClient(state);
	}

	public ReactiveTelegramClient createReactive() {
		return new InternalReactiveClient(state);
	}

	public void startIfNeeded() {
		if (state.shouldStartNow()) {
			try {
				Init.init();
				responseReceiver.start();
				this.cleanable = CleanSupport.register(this, () -> {
					try {
						this.responseReceiver.close();
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				});
				state.setStarted();
			} catch (Throwable ex) {
				state.setStopped();
				logger.error("Failed to start TDLight", ex);
			}
		}
	}

	private void handleClientEvents(int clientId,
			boolean isClosed,
			long[] clientEventIds,
			TdApi.Object[] clientEvents,
			int arrayOffset,
			int arrayLength) {
		var eventsHandlingLock = state.getEventsHandlingLock();
		boolean closeWriteLockAcquisitionFailed = false;
		var stamp = eventsHandlingLock.readLock();
		try {
			ClientEventsHandler handler = state.getClientEventsHandler(clientId);

			if (handler != null) {
				handler.handleEvents(isClosed, clientEventIds, clientEvents, arrayOffset, arrayLength);
			} else {
				java.util.List<DroppedEvent> droppedEvents = getEffectivelyDroppedEvents(clientEventIds,
						clientEvents,
						arrayOffset,
						arrayLength
				);

				if (!droppedEvents.isEmpty()) {
					logger.error("Unknown client id \"{}\"! {} events have been dropped!", clientId, droppedEvents.size());
					for (DroppedEvent droppedEvent : droppedEvents) {
						logger.error("The following event, with id \"{}\", has been dropped: {}",
								droppedEvent.id,
								droppedEvent.event
						);
					}
				}
			}

			if (isClosed) {
				var writeLockStamp = eventsHandlingLock.tryConvertToWriteLock(stamp);
				if (writeLockStamp != 0L) {
					stamp = writeLockStamp;
					removeClientEventHandlers(clientId);
				} else {
					closeWriteLockAcquisitionFailed = true;
				}
			}
		} finally {
			eventsHandlingLock.unlock(stamp);
		}

		if (closeWriteLockAcquisitionFailed) {
			stamp = eventsHandlingLock.writeLock();
			try {
				removeClientEventHandlers(clientId);
			} finally {
				eventsHandlingLock.unlockWrite(stamp);
			}
		}
	}

	/**
	 * Call this method only inside handleClientEvents!
	 * Ensure that state has the eventsHandlingLock locked in write mode
	 */
	private void removeClientEventHandlers(int clientId) {
		logger.debug("Removing Client {} from event handlers", clientId);
		state.removeClientEventHandlers(clientId);
		logger.debug("Removed Client {} from event handlers", clientId);
	}

	/**
	 * Get only events that have been dropped, ignoring synthetic errors related to the closure of a client
	 */
	private List<DroppedEvent> getEffectivelyDroppedEvents(long[] clientEventIds,
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

	protected void closeInternal() {
		if (state.shouldCloseNow()) {
			try {
				cleanable.clean();
			} catch (Throwable e) {
				logger.error("Failed to close", e);
			}
			this.state.setStopped();
		}
	}

	@Override
	public void close() {
		this.closeInternal();
	}

	private static final class DroppedEvent {

		private final long id;
		private final TdApi.Object event;

		private DroppedEvent(long id, Object event) {
			this.id = id;
			this.event = event;
		}
	}
}
