/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.mqtt.frigatesvr.internal.helpers;

import java.net.DatagramSocket;
import java.net.InetAddress;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link mqtt.frigateSVRNetworkHelper} provides some useful information
 * relating to the host system
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRNetworkHelper {

    private String hostname = "";
    private String port = "8080";

    public frigateSVRNetworkHelper() {
        hostname = this.GetAccessName();
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetHost
    //
    // Returns the host, FQDN or otherwise (no port suffixes etc)

    public String GetHost() {
        return hostname;
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetPort
    //
    // Return the port the current OH instance is running on

    public String GetPort() {
        return port;
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetHostBaseURL
    //
    // Return the base URL to the running instance

    public String GetHostBaseURL() {
        return "http://" + hostname + ":" + port;
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetAccessName
    //
    // This is a brave attempt to get the FQDN or at least other name by
    // which the locally running host can be identified. If all fails, we
    // revert to 'localhost

    private String GetAccessName() {
        String host = "";
        try {
            host = InetAddress.getLocalHost().getCanonicalHostName();
        } catch (Exception e) {
            try {
                host = InetAddress.getLocalHost().getHostAddress();
            } catch (Exception f) {
                try {
                    // if all is lost, try this
                    // the IP does not need to be reachable
                    DatagramSocket socket = new DatagramSocket();
                    socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                    host = socket.getLocalAddress().getHostAddress();
                    socket.close();
                } catch (Exception g) {
                    // I give up
                    host = "localhost";
                }
            }
        }
        return host;
    }
}
