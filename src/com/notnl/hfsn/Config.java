/*
 * Copyright (C) 2017 noti0na1 <i@notnl.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.notnl.hfsn;

import io.netty.util.internal.SystemPropertyUtil;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 *
 * @author noti0na1 <i@notnl.com>
 */
public class Config {

    public static final boolean SSL = SystemPropertyUtil.getBoolean("ssl", true);

    public static final int PORT = SystemPropertyUtil.getInt("port", SSL ? 443 : 80);

    public static final boolean REDIRECT_TO_HTTPS = SystemPropertyUtil.getBoolean("redrict", true);

    public static final String HOSTNAME;

    public static final String DIR = SystemPropertyUtil.get("dir", "");

    public static final String CERTIFICATE = SystemPropertyUtil.get("certificate", "");

    public static final String PRIVATE_KEY = SystemPropertyUtil.get("privateKey", "");

    public static final String RESOURCE = "resource";

    public static final String SERVER = "HFSN 1.6";

    static {
        String localHostname;
        try {
            localHostname = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ex) {
            System.err.println(ex);
            localHostname = "localhost";
        }
        HOSTNAME = SystemPropertyUtil.get("hostname", localHostname);
    }
}
