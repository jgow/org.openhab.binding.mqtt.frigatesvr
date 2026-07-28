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
public class APIGetRecordingSummary extends APIBase {

    private final Logger logger = LoggerFactory.getLogger(APIGetRecordingSummary.class);

    public APIGetRecordingSummary() {
        super(""); // no payload
    }

    @Override
    public ResultStruct Process(APIHelper apiHelper, frigateSVRActionProcessor ap) {
        return apiHelper.GetRecordingSummary(cam, payload);
    }

    @Override
    public ResultStruct Validate() {
        // nothing to validate on the input side.
        return new ResultStruct(true, "ok");
    }
}
