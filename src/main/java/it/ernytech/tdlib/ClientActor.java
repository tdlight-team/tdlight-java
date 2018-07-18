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

import java.util.concurrent.locks.ReentrantLock;

/**
 * ClientActor class is an expansion of Client class, which allows you to receive updates via callback
 */
public class ClientActor {
    private final Client client = new Client();
    private TdCallback tdCallback;
    private int timeout;
    private final ReentrantLock executionLock = new ReentrantLock();

    /**
     * Creates a ClientActor using the specified callback.
     * @param tdCallback Callback for outgoing notifications from TDLib.
     */
    public ClientActor(TdCallback tdCallback) {
        this.tdCallback = tdCallback;
        this.timeout = 120;
        run();
    }

    /**
     * Creates a ClientActor using the specified callback
     * @param tdCallback Callback for outgoing notifications from TDLib.
     * @param timeout Maximum number of seconds allowed for this function to wait for new data, when the timeout ends, ClientActor requests a new update list.
     */
    public ClientActor(TdCallback tdCallback, int timeout) {
        this.tdCallback = tdCallback;
        this.timeout = timeout;
        run();
    }

    /**
     * Sends one request to TDLib. The answer will be received via callback.
     * @param request Request to TDLib.
     */
    public void request(Request request) {
        if (this.executionLock.isLocked()) {
            throw new IllegalStateException("ClientActor is destroyed");
        }

        this.client.send(request);
    }

    /**
     * Synchronously executes a TDLib request. Only a few requests can be executed synchronously. May be called from any thread.
     * @param request Request to the TDLib.
     * @return The request response.
     */
    public Response execute(Request request) {
        if (this.executionLock.isLocked()) {
            throw new IllegalStateException("ClientActor is destroyed");
        }

        return this.client.execute(request);
    }

    /**
     * Destroys the ClientActor and the TDLib instance.
     */
    public void destroyClientActor() {
        this.executionLock.lock();
        this.client.destroyClient();
        this.tdCallback.getCloseCallback().onClosed();
    }

    private void run() {
        new Thread(() -> {
            while (!this.executionLock.isLocked()) {
                var responseList = this.client.receive(this.timeout, 1000);

                if (responseList.size() < 1) {
                    continue;
                }

                for (Response response : responseList) {
                    if (response.getObject() == null) {
                        continue;
                    }


                    if (!TdApi.Error.class.isInstance(response.getObject())) {
                        this.tdCallback.getReceiveCallback().onResult(response);
                    } else {
                        this.tdCallback.getErrorCallback().onError(response);
                    }
                }
            }
        }).start();
    }
}
