/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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

package org.openhab.binding.mqtt.frigatesvr.internal.structures;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.net.NetworkAddressService;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.http.HttpService;

/**
 * The {@link mqtt.frigateSVRServices} contains service objects passed down through the handler factory
 *
 * @author J Gow - Initial contribution
 */

@NonNullByDefault
public class frigateSVRServices {
    public final HttpService httpService;
    public final ConfigurationAdmin cfgAdmin;
    public final NetworkAddressService addressService;

    public frigateSVRServices(HttpService httpService, ConfigurationAdmin cfgAdmin,
            NetworkAddressService addressService) {
        this.httpService = httpService;
        this.cfgAdmin = cfgAdmin;
        this.addressService = addressService;
    }
}
