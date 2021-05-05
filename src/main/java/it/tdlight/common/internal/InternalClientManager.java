package it.tdlight.common.internal;

import it.tdlight.common.ClientEventsHandler;
import it.tdlight.common.Init;
import it.tdlight.jni.TdApi;
import it.tdlight.jni.TdApi.Object;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InternalClientManager implements AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(InternalClientManager.class);
	private static final AtomicReference<InternalClientManager> INSTANCE = new AtomicReference<>(null);

	private final String implementationName;
	private final ResponseReceiver responseReceiver = new ResponseReceiver(this::handleClientEvents);
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
	}

	public static InternalClientManager get(String implementationName) {
		return INSTANCE.updateAndGet(val -> val == null ? new InternalClientManager(implementationName) : val);
	}

	private void handleClientEvents(int clientId, boolean isClosed, long[] clientEventIds, TdApi.Object[] clientEvents) {
		ClientEventsHandler handler = registeredClientEventHandlers.get(clientId);

		if (handler != null) {
 			handler.handleEvents(isClosed, clientEventIds, clientEvents);
		} else {
			java.util.List<Entry<Long, TdApi.Object>> droppedEvents = getEffectivelyDroppedEvents(clientEventIds, clientEvents);

			if (!droppedEvents.isEmpty()) {
				logger.error("Unknown client id \"{}\"! {} events have been dropped!", clientId, droppedEvents.size());
				for (Entry<Long, Object> droppedEvent : droppedEvents) {
					logger.error("The following event, with id \"{}\", has been dropped: {}",
							droppedEvent.getKey(),
							droppedEvent.getValue());
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
	private List<Entry<Long, TdApi.Object>> getEffectivelyDroppedEvents(long[] clientEventIds, TdApi.Object[] clientEvents) {
		java.util.List<Entry<Long, TdApi.Object>> droppedEvents = new ArrayList<>(clientEvents.length);
		for (int i = 0; i < clientEvents.length; i++) {
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
				droppedEvents.add(Map.entry(id, event));
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
		responseReceiver.close();
	}
}
