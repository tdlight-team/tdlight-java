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

package it.tdlight.common;

import it.tdlight.jni.TdApi;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * A response to a request, or an incoming update from TDLib.
 */
@SuppressWarnings("unused")
public final class Response {

	private final long id;
	private final TdApi.Object object;

	/**
	 * Creates a response with eventId and object, do not create answers explicitly! you must receive the reply through a
	 * client.
	 *
	 * @param id     TDLib request identifier, which corresponds to the response or 0 for incoming updates from TDLib.
	 * @param object TDLib API object representing a response to a TDLib request or an incoming update.
	 */
	public Response(long id, TdApi.Object object) {
		this.id = id;
		this.object = object;
	}

	/**
	 * Get TDLib request identifier.
	 *
	 * @return TDLib request identifier, which corresponds to the response or 0 for incoming updates from TDLib.
	 */
	public long getId() {
		return this.id;
	}

	/**
	 * Get TDLib API object.
	 *
	 * @return TDLib API object representing a response to a TDLib request or an incoming update.
	 */
	public TdApi.Object getObject() {
		return this.object;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		Response response = (Response) o;
		return id == response.id && Objects.equals(object, response.object);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, object);
	}

	@Override
	public String toString() {
		return new StringJoiner(", ", Response.class.getSimpleName() + "[", "]").add("object=" + object).toString();
	}
}
