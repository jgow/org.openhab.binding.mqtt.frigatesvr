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
package org.openhab.binding.mqtt.frigatesvr.internal.discovery;

import static org.openhab.binding.mqtt.frigatesvr.internal.frigateSVRBindingConstants.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mqtt.frigatesvr.internal.handlers.frigateSVRServerHandler;
import org.openhab.core.config.discovery.AbstractDiscoveryService;
import org.openhab.core.config.discovery.DiscoveryResult;
import org.openhab.core.config.discovery.DiscoveryResultBuilder;
import org.openhab.core.thing.ThingUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link frigateSVRCameraDiscovery} discovers cameras exported by a specific frigateSVR server.
 *
 * @author Dr J Gow - Initial contribution
 */

@NonNullByDefault
public class frigateSVRCameraDiscoveryService extends AbstractDiscoveryService {
    private final Logger logger = LoggerFactory.getLogger(frigateSVRCameraDiscoveryService.class);
    private final frigateSVRServerHandler serverHandler;

    public frigateSVRCameraDiscoveryService(frigateSVRServerHandler serverHandler) {
        super(SUPPORTED_THING_TYPES_UIDS, 10, false);
        this.serverHandler = serverHandler;
    }

    @Override
    protected void startScan() {
        // Get the list of cameras from the server object. It will be an empty
        // list if the camera is offline.
        ArrayList<String> cameras = serverHandler.GetCameraList();
        String serverURL = serverHandler.GetBaseURL();

        // We now have the properties we need to configure each cam for discovery

        if (!cameras.isEmpty()) {
            cameras.forEach(cam -> {

                // grab the MQTT bridge ID - the new devices will need this

                ThingUID bridgeID = serverHandler.getThing().getUID();

                if (bridgeID != null) {
                    // build a new ThingID for the discovered cameras

                    String camUIDstring = cam + "-" + serverHandler.GetHostAndPort();
                    ThingUID newThing = new ThingUID(THING_TYPE_CAMERA, bridgeID, camUIDstring);

                    // build the config block

                    String unique = String.format("%s@%s", cam, serverURL);
                    Map<String, Object> properties = new HashMap<>();
                    properties.put(CONF_ID_SERVERID, serverHandler.getThing().getUID().getAsString());
                    properties.put(CONF_ID_CAMNAME, cam);
                    properties.put(CONF_ID_UNIQUE, unique);

                    // build the new camera
                    //
                    // Now for some reason I just could not get the substitution "@text/discovery.label.prefix.camera"
                    // to actually substitute: the name just kept appearing as '@text/discovery.label.prefix.camera'.
                    // Hence the hardcoded string

                    DiscoveryResult result = DiscoveryResultBuilder.create(newThing).withThingType(THING_TYPE_CAMERA)
                            .withProperties(properties).withBridge(bridgeID).withRepresentationProperty(CONF_ID_UNIQUE)
                            .withLabel("Camera" + " : " + cam).build();

                    // post it

                    thingDiscovered(result);
                }
            });

        } else {
            logger.debug("discovery: No cameras found");
        }
    }

    @Override
    protected synchronized void stopScan() {
        super.stopScan();
        removeOlderResults(getTimestampOfLastScan());
    }
}
