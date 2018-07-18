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

/**
 * A request to the TDLib.
 */
public class Request {
    private long id;
    private TdApi.Function function;

    /**
     * Creates a request with eventId and function.
     * @param id Request identifier. Responses to TDLib requests will have the same id as the corresponding request. Updates from TDLib will have id == 0, incoming requests are thus disallowed to have id == 0.
     * @param function TDLib API function representing a request to TDLib.
     */
    public Request(long id, TdApi.Function function) {
        this.id = id;
        this.function = function;
    }

    /**
     * Get request identifier.
     * @return Request identifier. Responses to TDLib requests will have the same id as the corresponding request. Updates from TDLib will have id == 0, incoming requests are thus disallowed to have id == 0.
     */
    public long getId() {
        return this.id;
    }

    /**
     * Get TDLib API function.
     * @return TDLib API function representing a request to TDLib.
     */
    public TdApi.Function getFunction() {
        return this.function;
    }
}
