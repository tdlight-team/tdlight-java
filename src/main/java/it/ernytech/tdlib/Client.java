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

package it.ernytech.tdlib;

import it.ernytech.tdlib.utils.Init;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Interface for interaction with TDLib.
 */
public class Client {
    private final long clientId;
    private final ReentrantLock receiveLock = new ReentrantLock();
    private final ReentrantLock executionLock = new ReentrantLock();
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
    public void send(Request request) {
        if (this.executionLock.isLocked()) {
            throw new IllegalStateException("ClientActor is destroyed");
        }

        nativeClientSend(this.clientId, request.getId(), request.getFunction());
    }

    /**
     * Receives incoming updates and request responses from TDLib. May be called from any thread, but shouldn't be called simultaneously from two different threads.
     * @param timeout Maximum number of seconds allowed for this function to wait for new data.
     * @param eventSize Maximum number of events allowed in list.
     * @return An incoming update or request response list. The object returned in the response may be an empty list if the timeout expires.
     */
    public List<Response> receive(double timeout, int eventSize) {
        if (this.executionLock.isLocked()) {
            throw new IllegalStateException("ClientActor is destroyed");
        }

        var responseList = new ArrayList<Response>();
        var eventIds = new long[eventSize];
        var events = new TdApi.Object[eventSize];

        if (this.receiveLock.isLocked()) {
            throw new IllegalThreadStateException("Thread: " + Thread.currentThread().getName() + " trying receive incoming updates but shouldn't be called simultaneously from two different threads!");
        }

        this.receiveLock.lock();
        var resultSize = nativeClientReceive(this.clientId, eventIds, events, timeout);
        this.receiveLock.unlock();

        for (int i = 0; i < resultSize; i++) {
            responseList.add(new Response(eventIds[i], events[i]));
        }

        return responseList;
    }

    /**
     * Receives incoming updates and request responses from TDLib. May be called from any thread, but shouldn't be called simultaneously from two different threads.
     * @param timeout Maximum number of seconds allowed for this function to wait for new data.
     * @return An incoming update or request response. The object returned in the response may be a nullptr if the timeout expires.
     */
    public Response receive(double timeout) {
        if (this.executionLock.isLocked()) {
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
    public Response execute(Request request) {
        if (this.executionLock.isLocked()) {
            throw new IllegalStateException("ClientActor is destroyed");
        }

        TdApi.Object object = nativeClientExecute(request.getFunction());
        return new Response(0, object);
    }

    /**
     * Destroys the client and TDLib instance.
     */
    public void destroyClient() {
        this.executionLock.lock();
        destroyNativeClient(this.clientId);
    }
}
