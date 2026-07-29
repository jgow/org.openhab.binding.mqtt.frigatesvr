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
package org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI;

import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mqtt.frigatesvr.internal.handlers.frigateSVRActionProcessor;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link mqtt.frigateSVRConfiguration} class contains mappings to the
 * Frigate SVR config block
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class APIPtz extends APIBase {

    private final Logger logger = LoggerFactory.getLogger(APIPtz.class);

    private CameraPTZCaps PTZCaps;

    public APIPtz(String dir, CameraPTZCaps caps) {
        super(dir);
        this.PTZCaps = caps;
    }

    @Override
    public ResultStruct Process(APIHelper apiHelper, frigateSVRActionProcessor ap) {
        // This is an MQTT action - no return value.
        ap.SendMQTTCommand("ptz", this.payload);
        return new ResultStruct(true, "ok");
    }

    @Override
    @SuppressWarnings("null")
    public ResultStruct Validate() {
        ResultStruct rc = new ResultStruct();
        List<String> pm = List.of("MOVE_UP", "MOVE_DOWN", "MOVE_LEFT", "MOVE_RIGHT");
        List<String> pz = List.of("ZOOM_IN", "ZOOM_OUT");
        do {

            if (this.payload.isBlank() || this.payload.isEmpty()) {
                rc.rc = false;
                rc.message = "empty parameter";
                break;
            }
            if (this.payload.equals("STOP")) {
                rc.rc = true;
                rc.message = "ok";
                break;
            }
            if (Arrays.asList(this.PTZCaps.features).contains("pt")) {
                if (pm.contains(this.payload)) {
                    rc.rc = true;
                    rc.message = "ok";
                    break;
                }
            }
            if (Arrays.asList(this.PTZCaps.features).contains("zoom")) {
                if (pz.contains(this.payload)) {
                    rc.rc = true;
                    rc.message = "ok";
                    break;
                }
            }
            String[] bits = this.payload.split("_");
            if (bits.length == 2) {
                if (bits[0].equals("preset")) {
                    if (Arrays.asList(this.PTZCaps.presets).contains(bits[1])) {
                        rc.rc = true;
                        rc.message = "ok";
                        break;
                    }
                }
            }
            rc.rc = false;
            rc.message = "invalid parameter " + param;
            logger.error("invalid parameter {}", this.param);

        } while (false);
        return rc;
    }
}
