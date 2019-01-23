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

package it.ernytech.tdbot;

import it.ernytech.tdlib.TdApi;

/**
 * Interface for easy interaction with TDLib for user.
 */
public class UserClient extends EasyClient<UserClient> {

    /**
     * Creates a new UserClient.
     * @param phoneNumber The phone number of user.
     */
    public UserClient(long phoneNumber) {
        super(easyClient -> easyClient.sendRaw(new TdApi.SetAuthenticationPhoneNumber(String.valueOf(phoneNumber), false, false)));
    }

    public UserClient(long phoneNumber, String firstName) {
        super(easyClient -> easyClient.sendRaw(new TdApi.SetAuthenticationPhoneNumber(String.valueOf(phoneNumber), false, false)));
        this.firstName = firstName;
    }

    public UserClient(long phoneNumber, String firstName, String lastName) {
        super(easyClient -> easyClient.sendRaw(new TdApi.SetAuthenticationPhoneNumber(String.valueOf(phoneNumber), false, false)));
        this.firstName = firstName;
        this.lastName = lastName;
    }
}
