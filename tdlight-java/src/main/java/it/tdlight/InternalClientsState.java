package it.tdlight;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.StampedLock;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class InternalClientsState {
	static final int STATE_INITIAL = 0;
	static final int STATE_STARTING = 1;
	static final int STATE_STARTED = 2;
	static final int STATE_STOPPING = 3;
	static final int STATE_STOPPED = 4;
	private final AtomicInteger runState = new AtomicInteger();
	private final AtomicLong currentQueryId = new AtomicLong();
	private final Map<Integer, ClientEventsHandler> registeredClientEventHandlers = new ConcurrentHashMap<>();
	private final Object lifecycleLock = new Object();
	private final IntSupplier clientCreator;
	private volatile Throwable terminalFailure;
	private final StampedLock eventsHandlingLock = new StampedLock();

	public InternalClientsState() {
		this(NativeClientAccess::create);
	}

	InternalClientsState(IntSupplier clientCreator) {
		this.clientCreator = clientCreator;
	}

	public long getNextQueryId() {
		return currentQueryId.updateAndGet(value -> (value == Long.MAX_VALUE ? 0 : value) + 1);
	}

	public void registerClient(int clientId, ClientEventsHandler internalClient) {
		synchronized (lifecycleLock) {
			long stamp = eventsHandlingLock.writeLock();
			try {
				startIfNeededLocked();
				registerClientLocked(clientId, internalClient);
			} finally {
				eventsHandlingLock.unlockWrite(stamp);
			}
		}
	}

	int createAndRegisterClient(ClientEventsHandler internalClient) {
		return createAndRegisterClient(internalClient, ignored -> {
		});
	}

	int createAndRegisterClient(ClientEventsHandler internalClient, IntConsumer clientCreated) {
		synchronized (lifecycleLock) {
			long stamp = eventsHandlingLock.writeLock();
			try {
				startIfNeededLocked();
				int clientId = clientCreator.getAsInt();
				try {
					clientCreated.accept(clientId);
					registerClientLocked(clientId, internalClient);
				} catch (RuntimeException | Error ex) {
					try {
						NativeClientAccess.send(clientId, getNextQueryId(), new it.tdlight.jni.TdApi.Close());
					} catch (Throwable closeFailure) {
						ex.addSuppressed(closeFailure);
					}
					throw ex;
				}
				return clientId;
			} finally {
				eventsHandlingLock.unlockWrite(stamp);
			}
		}
	}

	private void registerClientLocked(int clientId, ClientEventsHandler internalClient) {
		ClientEventsHandler replaced = registeredClientEventHandlers.putIfAbsent(clientId, internalClient);
		if (replaced != null) {
			throw new IllegalStateException("Client " + clientId + " already registered");
		}
		try {
			onClientRegistered(clientId);
		} catch (RuntimeException | Error ex) {
			registeredClientEventHandlers.remove(clientId, internalClient);
			throw ex;
		}
	}

	public ClientEventsHandler getClientEventsHandler(int clientId) {
		return registeredClientEventHandlers.get(clientId);
	}

	public StampedLock getEventsHandlingLock() {
		return eventsHandlingLock;
	}

	public boolean shouldStartNow() {
		synchronized (lifecycleLock) {
			return runState.compareAndSet(STATE_INITIAL, STATE_STARTING);
		}
	}

	public void setStopped() {
		synchronized (lifecycleLock) {
			runState.set(STATE_STOPPED);
		}
	}

	public void setStarted() {
		synchronized (lifecycleLock) {
			if (!runState.compareAndSet(STATE_STARTING, STATE_STARTED)) {
				throw new IllegalStateException();
			}
		}
	}

	public void removeClientEventHandlers(int clientId) {
		registeredClientEventHandlers.remove(clientId);
	}

	public void removeClientEventHandlers(int clientId, ClientEventsHandler expectedHandler) {
		registeredClientEventHandlers.remove(clientId, expectedHandler);
	}

	public boolean shouldCloseNow() {
		synchronized (lifecycleLock) {
			int currentState = runState.get();
			if (currentState == STATE_INITIAL) {
				runState.set(STATE_STOPPED);
				return false;
			}
			if (currentState == STATE_STARTED) {
				runState.set(STATE_STOPPING);
				return true;
			}
			return false;
		}
	}

	void ensureAcceptingClients() {
		synchronized (lifecycleLock) {
			int currentState = runState.get();
			if (currentState == STATE_STOPPING || currentState == STATE_STOPPED) {
				throw closedFactoryException();
			}
		}
	}

	private void startIfNeededLocked() {
		int currentState = runState.get();
		if (currentState == STATE_INITIAL) {
			runState.set(STATE_STARTING);
			try {
				onStart();
				runState.set(STATE_STARTED);
			} catch (RuntimeException | Error ex) {
				terminalFailure = ex;
				runState.set(STATE_STOPPED);
				throw ex;
			}
			return;
		}
		if (currentState != STATE_STARTED) {
			throw closedFactoryException();
		}
	}

	private IllegalStateException closedFactoryException() {
		Throwable failure = terminalFailure;
		if (failure != null) {
			return new IllegalStateException("Client factory is unavailable because startup failed", failure);
		}
		return new IllegalStateException("Client factory is closed");
	}

	protected void onStart() {
	}

	protected void onClientRegistered(int clientId) {
	}
}
