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
 * Interface for easy interaction with TDLib for bot.
 */
public class BotClient extends EasyClient {

    /**
     * Creates a new BotClient
     * @param botToken The bot token generated with t.me/BotFather
     */
    public BotClient(String botToken) {
        super(easyClient -> easyClient.sendRaw(new TdApi.CheckAuthenticationBotToken(botToken)));
    }
}
