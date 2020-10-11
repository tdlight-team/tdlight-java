package it.tdlight.jni;
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



import it.tdlight.jni.TdApi.Function;
import it.tdlight.jni.TdApi.Object;

public class NativeClient {
    protected static native long createNativeClient();
    protected static native void nativeClientSend(long nativeClientId, long eventId, Function function);
    protected static native int nativeClientReceive(long nativeClientId, long[] eventIds, Object[] events, double timeout);
    protected static native int nativeClientReceive(long nativeClientId, long[] eventIds, Object[] events, double timeout, boolean include_responses, boolean include_updates);
    protected static native Object nativeClientExecute(Function function);
    protected static native void destroyNativeClient(long nativeClientId);
}
