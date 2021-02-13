package it.tdlight.common;

import it.tdlight.jni.TdApi;
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
			logger.error("Unknown client id \"{}\"! {} events have been dropped!", clientId, clientEvents.length);
			for (int i = 0; i < clientEvents.length; i++) {
				long id = clientEventIds[i];
				TdApi.Object event = clientEvents[i];
				logger.error("The following event, with id \"{}\", has been dropped: {}", id, event);
			}
		}

		if (isClosed) {
			registeredClientEventHandlers.remove(clientId);
		}
	}

	public void registerClient(int clientId, ClientEventsHandler internalClient) {
		responseReceiver.registerClient(clientId);
		boolean replaced = registeredClientEventHandlers.put(clientId, internalClient) != null;
		if (replaced) {
			throw new IllegalStateException("Client " + clientId + " already registered");
		}
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
