package it.tdlight.common;

import it.tdlight.jni.TdApi.Object;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class InternalClientManager implements AutoCloseable {

	private static final AtomicReference<InternalClientManager> INSTANCE = new AtomicReference<>(null);

	private final String implementationName;
	private final ResponseReceiver responseReceiver = new ResponseReceiver(this::handleClientEvents);
	private final Int2ObjectMap<ClientEventsHandler> clientEventsHandlerMap = new Int2ObjectOpenHashMap<>();
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

	private void handleClientEvents(int clientId, boolean isClosed, long[] clientEventIds, Object[] clientEvents) {
		ClientEventsHandler handler = clientEventsHandlerMap.get(clientId);

		if (handler != null) {
			handler.handleEvents(isClosed, clientEventIds, clientEvents);
		} else {
			System.err.println("Unknown client id " + clientId + ", " + clientEvents.length + " events have been dropped!");
		}

		if (isClosed) {
			clientEventsHandlerMap.remove(clientId);
		}
	}

	public void registerClient(ClientEventsHandler client) {
		this.clientEventsHandlerMap.put(client.getClientId(), client);
	}

	public String getImplementationName() {
		return implementationName;
	}

	public long getNextQueryId() {
		return currentQueryId.getAndUpdate(value -> (value >= Long.MAX_VALUE ? 0 : value) + 1);
	}

	@Override
	public void close() throws InterruptedException {
		responseReceiver.close();
	}
}
