package it.ernytech.tdlib;
/*
 * Copyright (c) 2018. Ernesto Castellotti <erny.castell@gmail.com>
 * This file is part of JTdlib.
 *
 *     JTdlib is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU Lesser General Public License as published by
 *     the Free Software Foundation, either version 3 of the License.
 *
 *     JTdlib is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU Lesser General Public License
 *     along with JTdlib.  If not, see <http://www.gnu.org/licenses/>.
 */



import it.ernytech.tdlib.utils.Init;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.StampedLock;

/**
 * Interface for interaction with TDLib.
 */
public class Client implements TelegramClient {
    private long clientId;
    private final ReentrantLock receiveLock = new ReentrantLock();
    private final StampedLock executionLock = new StampedLock();
    private volatile Long stampedLockValue = 1L;
    private static native long createNativeClient();
    private static native void nativeClientSend(long nativeClientId, long eventId, TdApi.Function function);
    private static native int nativeClientReceive(long nativeClientId, long[] eventIds, TdApi.Object[] events, double timeout);
    private static native TdApi.Object nativeClientExecute(TdApi.Function function);
    private static native void destroyNativeClient(long nativeClientId);

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

    /**
     * Sends request to TDLib. May be called from any thread.
     * @param request Request to TDLib.
     */
    @Override
    public void send(Request request) {
        if (this.executionLock.isWriteLocked()) {
            throw new IllegalStateException("ClientActor is destroyed");
        }

        nativeClientSend(this.clientId, request.getId(), request.getFunction());
    }

    private long[] eventIds;
    private TdApi.Object[] events;

    /**
     * Receives incoming updates and request responses from TDLib. May be called from any thread, but shouldn't be called simultaneously from two different threads.
     * @param timeout Maximum number of seconds allowed for this function to wait for new records.
     * @param eventSize Maximum number of events allowed in list.
     * @return An incoming update or request response list. The object returned in the response may be an empty list if the timeout expires.
     */
    @Override
    public List<Response> receive(double timeout, int eventSize) {
        if (this.executionLock.isWriteLocked()) {
            throw new IllegalStateException("ClientActor is destroyed");
        }

        var responseList = new ArrayList<Response>();
        if (eventIds == null) {
            eventIds = new long[eventSize];
            events = new TdApi.Object[eventSize];
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
            resultSize = nativeClientReceive(this.clientId, eventIds, events, timeout);
        } finally {
            this.receiveLock.unlock();
        }

        for (int i = 0; i < resultSize; i++) {
            responseList.add(new Response(eventIds[i], events[i]));
        }

        return responseList;
    }

    /**
     * Receives incoming updates and request responses from TDLib. May be called from any thread, but shouldn't be called simultaneously from two different threads.
     * @param timeout Maximum number of seconds allowed for this function to wait for new records.
     * @return An incoming update or request response. The object returned in the response may be a nullptr if the timeout expires.
     */
    @Override
    public Response receive(double timeout) {
        if (this.executionLock.isWriteLocked()) {
            throw new IllegalStateException("ClientActor is destroyed");
        }

        var responseList = receive(timeout, 1);

        if (responseList.size() < 1) {
            return null;
        }

        return responseList.get(0);
    }

    /**
     * Synchronously executes TDLib requests. Only a few requests can be executed synchronously. May be called from any thread.
     * @param request Request to the TDLib.
     * @return The request response.
     */
    @Override
    public Response execute(Request request) {
        if (this.executionLock.isWriteLocked()) {
            throw new IllegalStateException("ClientActor is destroyed");
        }

        TdApi.Object object = nativeClientExecute(request.getFunction());
        return new Response(0, object);
    }

    /**
     * Destroys the client and TDLib instance.
     */
    @Override
    public void destroyClient() {
        stampedLockValue = this.executionLock.tryWriteLock();
        destroyNativeClient(this.clientId);
    }

    /**
     * Destroys the client and TDLib instance.
     */
    @Override
    public void initializeClient() {
        this.executionLock.tryUnlockWrite();
        stampedLockValue = null;
        this.clientId = createNativeClient();
    }

	@Override
  public boolean isDestroyed() {
    	return this.executionLock.isWriteLocked();
	}
}
