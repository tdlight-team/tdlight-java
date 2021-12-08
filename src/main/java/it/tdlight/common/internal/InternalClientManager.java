package it.tdlight.common.internal;

import it.tdlight.common.ClientEventsHandler;
import it.tdlight.common.Init;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Object;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class InternalClientManager implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(InternalClientManager.class);
	private static final AtomicReference<InternalClientManager> INSTANCE = new AtomicReference<>(null);

	private final AtomicBoolean startCalled = new AtomicBoolean();
	private final AtomicBoolean closeCalled = new AtomicBoolean();

	private final String implementationName;
	private final ResponseReceiver responseReceiver;
	private final ConcurrentHashMap<Integer, ClientEventsHandler> registeredClientEventHandlers = new ConcurrentHashMap<>();
	private final AtomicLong currentQueryId = new AtomicLong();

	private InternalClientManager(String implementationName) {
		try {
			Init.start();
		} catch (Throwable ex) {
			ex.printStackTrace();
			System.exit(1);
		}
		this.implementationName = implementationName;
		responseReceiver = new NativeResponseReceiver(this::handleClientEvents);
	}

	/**
	 * @return true if started as a result of this call
	 */
	public boolean startIfNeeded() {
		if (closeCalled.get()) {
			return false;
		}
		if (startCalled.compareAndSet(false, true)) {
			responseReceiver.start();
			return true;
		} else {
			return false;
		}
	}

	public static InternalClientManager get(String implementationName) {
		InternalClientManager clientManager = INSTANCE.updateAndGet(val -> {
			if (val == null) {
				return new InternalClientManager(implementationName);
			}
			return val;
		});
		clientManager.startIfNeeded();
		return clientManager;
	}

	private void handleClientEvents(int clientId,
			boolean isClosed,
			long[] clientEventIds,
			TdApi.Object[] clientEvents,
			int arrayOffset,
			int arrayLength) {
		ClientEventsHandler handler = registeredClientEventHandlers.get(clientId);

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
			logger.trace("Removing Client {} from event handlers", clientId);
			registeredClientEventHandlers.remove(clientId);
			logger.trace("Removed Client {} from event handlers", clientId);
		}
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

	public void registerClient(int clientId, ClientEventsHandler internalClient) {
		boolean replaced = registeredClientEventHandlers.put(clientId, internalClient) != null;
		if (replaced) {
			throw new IllegalStateException("Client " + clientId + " already registered");
		}
		responseReceiver.registerClient(clientId);
	}

	public String getImplementationName() {
		return implementationName;
	}

	public long getNextQueryId() {
		return currentQueryId.updateAndGet(value -> (value >= Long.MAX_VALUE ? 0 : value) + 1);
	}

	@Override
	public void close() throws InterruptedException {
		if (startCalled.get()) {
			if (closeCalled.compareAndSet(false, true)) {
				responseReceiver.close();
			}
		} else {
			throw new IllegalStateException("Start not called");
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
}
