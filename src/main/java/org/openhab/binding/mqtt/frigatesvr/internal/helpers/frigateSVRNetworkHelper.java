/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link mqtt.frigateSVRNetworkHelper} provides some useful information
 * relating to the host system
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRNetworkHelper {

    private final Logger logger = LoggerFactory.getLogger(frigateSVRNetworkHelper.class);
    private frigateSVRServices services;

    public frigateSVRNetworkHelper(frigateSVRServices services) {
        this.services = services;
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetProtocol
    //
    // The protocol would be set up in config; we do need to determine if
    // https is required from the config? Todo.

    public String GetProtocol() {
        String protocol = "";
        if (System.getProperty("org.osgi.service.http.port.secure") != null) {
            protocol = "https";
        } else {
            if (System.getProperty("org.osgi.service.http.port") != null) {
                protocol = "http";
            }
        }
        return protocol;
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetHost
    //
    // Returns the host, FQDN or otherwise (no port suffixes etc)

    public String GetHost() {
        return this.GetAccessName();
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetPort
    //
    // Return the port the current OH instance is running on

    public String GetPort() {
        @Nullable
        String port;
        port = System.getProperty("org.osgi.service.http.port.secure");
        if (port == null) {
            logger.info("Secure web service port not found, looking for insecure");
            port = System.getProperty("org.osgi.service.http.port");
            if (port == null) {
                logger.error("unable to determine secure and insecure web server ports");
                port = "";
            }
        }
        return port;
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetHostBaseURL
    //
    // Return the base URL to the running instance

    public String GetHostBaseURL() {
        String protocol = GetProtocol();
        String port = GetPort();
        String url = "";
        if (!protocol.isBlank() && !port.isBlank()) {
            url = protocol + "://" + GetHost() + ":" + port;
        }
        return url;
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetAccessName
    //
    // This is a brave attempt to get the FQDN or at least other name by
    // which the locally running host can be identified. If all fails, we
    // revert to 'localhost

    private String GetAccessName() {

        String host = "";
        logger.info("attempting to obtain host name");
        try {
            host = InetAddress.getByName(services.addressService.getPrimaryIpv4HostAddress()).getCanonicalHostName();
        } catch (Exception e) {
            try {
                logger.error("trying addressService with getHostAddress");
                host = InetAddress.getByName(services.addressService.getPrimaryIpv4HostAddress()).getHostAddress();
            } catch (Exception f) {
                try {
                    logger.error("trying getLocalHost with getCanonicalHostName");
                    host = InetAddress.getLocalHost().getCanonicalHostName();
                } catch (Exception g) {
                    try {
                        logger.error("failed; trying getLocalHost and getHostAddress");
                        host = InetAddress.getLocalHost().getHostAddress();
                    } catch (Exception h) {
                        try {
                            logger.error("failed; trying local connection");
                            // if all is lost, try this
                            // the IP does not need to be reachable
                            DatagramSocket socket = new DatagramSocket();
                            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
                            host = socket.getLocalAddress().getHostAddress();
                            socket.close();
                        } catch (Exception i) {
                            // I give up
                            logger.error(
                                    "all methods failed; unable to determine forwarder host name - using <unknown>");
                            host = "<unknown>";
                        }
                    }
                }
            }
        }
        logger.info("have forwarder hostname {}", host);
        return host;
    }
}
