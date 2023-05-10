package it.tdlight;

import io.atlassian.util.concurrent.CopyOnWriteMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class InternalClientsState {
	static final int STATE_INITIAL = 0;
	static final int STATE_STARTING = 1;
	static final int STATE_STARTED = 2;
	static final int STATE_STOPPING = 3;
	static final int STATE_STOPPED = 4;
	private final AtomicInteger runState = new AtomicInteger();
	private final AtomicLong currentQueryId = new AtomicLong();
	private final Map<Integer, ClientEventsHandler> registeredClientEventHandlers = CopyOnWriteMap.newHashMap();


	public long getNextQueryId() {
		return currentQueryId.updateAndGet(value -> (value >= Long.MAX_VALUE ? 0 : value) + 1);
	}

	public void registerClient(int clientId, ClientEventsHandler internalClient) {
		boolean replaced = registeredClientEventHandlers.put(clientId, internalClient) != null;
		if (replaced) {
			throw new IllegalStateException("Client " + clientId + " already registered");
		}
	}

	public ClientEventsHandler getClientEventsHandler(int clientId) {
		return registeredClientEventHandlers.get(clientId);
	}

	public boolean shouldStartNow() {
		return runState.compareAndSet(STATE_INITIAL, STATE_STARTING);
	}

	public void setStopped() {
		runState.set(STATE_STOPPED);
	}

	public void setStarted() {
		if (!runState.compareAndSet(STATE_STARTING, STATE_STARTED)) {
			throw new IllegalStateException();
		}
	}

	public void removeClientEventHandlers(int clientId) {
		registeredClientEventHandlers.remove(clientId);
	}

	public boolean shouldCloseNow() {
		int prevS = runState.getAndUpdate(prev -> {
			if (prev == STATE_INITIAL) {
				return STATE_STOPPED;
			} else if (prev == STATE_STARTED) {
				return STATE_STOPPING;
			} else {
				return prev;
			}
		});
		return prevS == STATE_STARTED;
	}
}
