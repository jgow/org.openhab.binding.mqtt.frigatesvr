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

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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
public class APITriggerEvent extends APIBase {

    private String label = "";

    private final Logger logger = LoggerFactory.getLogger(APITriggerEvent.class);

    public APITriggerEvent(String label, @Nullable String eventParams) {
        super(eventParams);
        this.label = label;
    }

    @Override
    public ResultStruct Process(APIHelper apiHelper, frigateSVRActionProcessor ap) {
        return apiHelper.TriggerEvent(cam, label, payload);
    }

    @Override
    @SuppressWarnings("null")
    public ResultStruct Validate() {
        ResultStruct rc = new ResultStruct();
        if (!label.isBlank() && !label.isEmpty() && label.matches("^[A-Za-z0-9]+$")) {

            // our parameters are ok, check the JSON provided as payload is also valid. A null or blank
            // payload is ok. We don't use it, just check it is valid JSON before we fire it off. We let
            // Frigate do the content checking.

            rc.rc = CheckValidJSON(payload);
            if (!rc.rc) {
                logger.error("invalid payload {}", payload);
                rc.message = "invalid arguments";
            }
        } else {
            logger.error("invalid event label {}", label);
            rc.message = "invalid event label";
        }
        return rc;
    }
}
