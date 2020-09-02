package it.tdlight.tdlight;

import it.tdlight.tdlib.TdApi.Object;
import it.tdlight.tdlib.NativeClient;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

/**
 * Interface for interaction with TDLib.
 */
public class Client extends NativeClient implements TelegramClient {
	private long clientId;
	private final ReentrantLock receiveLock = new ReentrantLock();
	private final StampedLock executionLock = new StampedLock();
	private volatile Long stampedLockValue = 1L;

	/**
	 * Creates a new TDLib client.
	 */
	public Client() {}

	@Override
	public void send(Request request) {
		if (this.executionLock.isWriteLocked()) {
			throw new IllegalStateException("ClientActor is destroyed");
		}

		nativeClientSend(this.clientId, request.getId(), request.getFunction());
	}

	private long[] eventIds;
	private Object[] events;

	@Override
	public List<Response> receive(double timeout, int eventSize, boolean receiveResponses, boolean receiveUpdates) {
		if (this.executionLock.isWriteLocked()) {
			throw new IllegalStateException("ClientActor is destroyed");
		}

		ArrayList<Response> responseList = new ArrayList<>();
		if (eventIds == null) {
			eventIds = new long[eventSize];
			events = new Object[eventSize];
		} else if (eventIds.length != eventSize) {
			throw new IllegalArgumentException("EventSize can't change! Previous value = " + eventIds.length + " New value = " + eventSize);
		} else {
			Arrays.fill(eventIds, 0);
			Arrays.fill(events, null);
		}

		if (this.receiveLock.isLocked()) {
			throw new IllegalThreadStateException("Thread: " + Thread.currentThread().getName() + " trying receive incoming updates but shouldn't be called simultaneously from two different threads!");
		}

		int resultSize;
		this.receiveLock.lock();
		try {
			resultSize = nativeClientReceive(this.clientId, eventIds, events, timeout, receiveResponses, receiveUpdates);
		} finally {
			this.receiveLock.unlock();
		}

		for (int i = 0; i < resultSize; i++) {
			responseList.add(new Response(eventIds[i], events[i]));
		}

		return responseList;
	}

	@Override
	public Response receive(double timeout, boolean receiveResponses, boolean receiveUpdates) {
		if (this.executionLock.isWriteLocked()) {
			throw new IllegalStateException("ClientActor is destroyed");
		}

		List<Response> responseList = receive(timeout, 1, receiveResponses, receiveUpdates);

		if (responseList.size() < 1) {
			return null;
		}

		return responseList.get(0);
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
		try {
			Init.start();
		} catch (Throwable throwable) {
			throwable.printStackTrace();
			System.exit(1);
		}
		this.clientId = createNativeClient();
	}
}
