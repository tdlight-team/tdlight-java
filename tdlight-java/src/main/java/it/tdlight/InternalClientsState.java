package it.tdlight;

import io.atlassian.util.concurrent.CopyOnWriteMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;

public class InternalClientsState {
	static final int STATE_INITIAL = 0;
	static final int STATE_STARTING = 1;
	static final int STATE_STARTED = 2;
	static final int STATE_STOPPING = 3;
	static final int STATE_STOPPED = 4;
	private final AtomicInteger runState = new AtomicInteger();
	private final AtomicLong currentQueryId = new AtomicLong();
	private final Map<Integer, ClientEventsHandler> registeredClientEventHandlers = new HashMap<>();
	private final StampedLock eventsHandlingLock = new StampedLock();


	public long getNextQueryId() {
		return currentQueryId.updateAndGet(value -> (value == Long.MAX_VALUE ? 0 : value) + 1);
	}

	/**
	 * Before calling this method, lock using getEventsHandlingLock().writeLock()
	 */
	public void registerClient(int clientId, ClientEventsHandler internalClient) {
		boolean replaced = registeredClientEventHandlers.put(clientId, internalClient) != null;
		if (replaced) {
			throw new IllegalStateException("Client " + clientId + " already registered");
		}
	}

	/**
	 * Before calling this method, lock using getEventsHandlingLock().readLock()
	 */
	public ClientEventsHandler getClientEventsHandler(int clientId) {
		return registeredClientEventHandlers.get(clientId);
	}

	public StampedLock getEventsHandlingLock() {
		return eventsHandlingLock;
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

	/**
	 * Before calling this method, lock using getEventsHandlingLock().writeLock()
	 */
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
