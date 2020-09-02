package it.tdlight.tdlight;

import it.tdlight.tdlib.NativeClient;
import it.tdlight.tdlib.TdApi.Object;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

/**
 * Interface for interaction with TDLib.
 */
public class Client extends NativeClient implements TelegramClient {
	private long clientId;
	private final ReentrantLock receiveResponsesLock = new ReentrantLock();
	private final ReentrantLock receiveUpdatesLock = new ReentrantLock();
	private final StampedLock executionLock = new StampedLock();
	private volatile Long stampedLockValue;

	/**
	 * Creates a new TDLib client.
	 */
	public Client() {
		try {
			Init.start();
		} catch (Throwable throwable) {
			throwable.printStackTrace();
			System.exit(1);
		}
		this.clientId = createNativeClient();
	}

	@Override
	public void send(Request request) {
		if (this.executionLock.isWriteLocked()) {
			throw new IllegalStateException("ClientActor is destroyed");
		}

		nativeClientSend(this.clientId, request.getId(), request.getFunction());
	}

	private final long[][] eventIds = new long[8][];
	private final Object[][] events = new Object[8][];

	@Override
	public List<Response> receive(double timeout, int eventsSize, boolean receiveResponses, boolean receiveUpdates) {
		return Arrays.asList(this.receive(timeout, eventsSize, receiveResponses, receiveUpdates, false));
	}

	private Response[] receive(double timeout, int eventsSize, boolean receiveResponses, boolean receiveUpdates, boolean singleResponse) {
		if (this.executionLock.isWriteLocked()) {
			throw new IllegalStateException("ClientActor is destroyed");
		}
		int group = (singleResponse ? 0b100 : 0b000) | (receiveResponses ? 0b010 : 0b000) | (receiveUpdates ? 0b001 : 0b000);

		if (eventIds[group] == null) {
			eventIds[group] = new long[eventsSize];
		} else {
			Arrays.fill(eventIds[group], 0);
		}
		if (events[group] == null) {
			events[group] = new Object[eventsSize];
		} else {
			Arrays.fill(events[group], null);
		}
		if (eventIds[group].length != eventsSize || events[group].length != eventsSize) {
			throw new IllegalArgumentException("EventSize can't change over time!"
					+ " Previous: " + eventIds[group].length + " Current: " + eventsSize);
		}

		if (receiveResponses && this.receiveResponsesLock.isLocked()) {
			throw new IllegalThreadStateException("Thread: " + Thread.currentThread().getName() + " trying receive incoming responses but shouldn't be called simultaneously from two different threads!");
		}

		if (receiveUpdates && this.receiveUpdatesLock.isLocked()) {
			throw new IllegalThreadStateException("Thread: " + Thread.currentThread().getName() + " trying receive incoming updates but shouldn't be called simultaneously from two different threads!");
		}

		int resultSize;
		if (receiveResponses) this.receiveResponsesLock.lock();
		try {
			if (receiveUpdates) this.receiveUpdatesLock.lock();
			try {
				resultSize = nativeClientReceive(this.clientId, eventIds[group], events[group], timeout, receiveResponses, receiveUpdates);
			} finally {
				if (receiveUpdates) this.receiveUpdatesLock.unlock();
			}
		} finally {
			if (receiveResponses) this.receiveResponsesLock.unlock();
		}

		Response[] responses = new Response[resultSize];

		for (int i = 0; i < resultSize; i++) {
			responses[i] = new Response(eventIds[group][i], events[group][i]);
		}

		return responses;
	}

	@Override
	public Response receive(double timeout, boolean receiveResponses, boolean receiveUpdates) {
		if (this.executionLock.isWriteLocked()) {
			throw new IllegalStateException("ClientActor is destroyed");
		}

		Response[] responses = receive(timeout, 1, receiveResponses, receiveUpdates, true);

		if (responses.length != 0) {
			return responses[0];
		}

		return null;
	}

	@Override
	public Response execute(Request request) {
		if (this.executionLock.isWriteLocked()) {
			throw new IllegalStateException("ClientActor is destroyed");
		}

		Object object = nativeClientExecute(request.getFunction());
		return new Response(0, object);
	}

	@Override
	public void destroyClient() {
		stampedLockValue = this.executionLock.tryWriteLock();
		destroyNativeClient(this.clientId);
	}

	@Override
	public void initializeClient() {
		this.executionLock.tryUnlockWrite();
		stampedLockValue = null;
		this.clientId = createNativeClient();
	}
}
