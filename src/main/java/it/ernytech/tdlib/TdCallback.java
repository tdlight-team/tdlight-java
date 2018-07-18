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

import it.ernytech.tdlib.utils.CloseCallback;
import it.ernytech.tdlib.utils.ErrorCallback;
import it.ernytech.tdlib.utils.ReceiveCallback;

/**
 * Interface of callback for interaction with TDLib.
 */
public class TdCallback {
    private ReceiveCallback receiveCallback;
    private ErrorCallback errorCallback;
    private CloseCallback closeCallback;

    /**
     * Creates a new TdCallback.
     * @param receiveCallback Interface of callback for receive incoming update or request response.
     * @param errorCallback Interface of callback for receive incoming error response.
     * @param closeCallback Interface of callback for receive notification of closing Tdlib.
     */
    public TdCallback(ReceiveCallback receiveCallback, ErrorCallback errorCallback, CloseCallback closeCallback) {
        this.receiveCallback = receiveCallback;
        this.errorCallback = errorCallback;
        this.closeCallback = closeCallback;
    }

    /**
     * Creates a new TdCallback.
     * @param receiveCallback Interface of callback for receive incoming update or request response.
     */
    public TdCallback(ReceiveCallback receiveCallback) {
        this.receiveCallback = receiveCallback;
    }

    /**
     * Creates a new TdCallback.
     * @param errorCallback Interface of callback for receive incoming error response.
     */
    public TdCallback(ErrorCallback errorCallback) {
        this.errorCallback = errorCallback;
    }

    /**
     * Creates a new TdCallback.
     * @param closeCallback Interface of callback for receive notification of closing Tdlib.
     */
    public TdCallback(CloseCallback closeCallback) {
        this.closeCallback = closeCallback;
    }

    /**
     * Creates a new TdCallback.
     * @param receiveCallback Interface of callback for receive incoming update or request response.
     * @param errorCallback Interface of callback for receive incoming error response.
     */
    public TdCallback(ReceiveCallback receiveCallback, ErrorCallback errorCallback) {
        this.receiveCallback = receiveCallback;
        this.errorCallback = errorCallback;
    }

    /**
     * Creates a new TdCallback.
     * @param errorCallback Interface of callback for receive incoming error response.
     * @param closeCallback Interface of callback for receive notification of closing Tdlib.
     */
    public TdCallback(ErrorCallback errorCallback, CloseCallback closeCallback) {
        this.errorCallback = errorCallback;
        this.closeCallback = closeCallback;
    }

    /**
     * Creates a new TdCallback.
     * @param receiveCallback Interface of callback for receive incoming update or request response.
     * @param closeCallback Interface of callback for receive notification of closing Tdlib.
     */
    public TdCallback(ReceiveCallback receiveCallback, CloseCallback closeCallback) {
        this.receiveCallback = receiveCallback;
        this.closeCallback = closeCallback;
    }

    /**
     * Get ReceiveCallback.
     * @return This method return ReceiveCallback or "null callback" (a callback that receives data from tdlib but does not perform any operation) if is null.
            */
    public ReceiveCallback getReceiveCallback() {
        if (this.receiveCallback == null) {
            return response -> {};
        }

        return this.receiveCallback;
    }

    /**
     * Get ErrorCallback.
     * @return This method return ErrorCallback or "null callback" (a callback that receives data from tdlib but does not perform any operation) if is null.
     */
    public ErrorCallback getErrorCallback() {
        if (this.errorCallback == null) {
            return error -> {};
        }

        return this.errorCallback;
    }

    /**
     * Get CloseCallback.
     * @return This method return CloseCallback or "null callback" (a callback that receives data from tdlib but does not perform any operation) if is null.
     */
    public CloseCallback getCloseCallback() {
        if (this.closeCallback == null) {
            return () -> {};
        }

        return this.closeCallback;
    }
}
