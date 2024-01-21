/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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

import static org.openhab.binding.mqtt.frigatesvr.internal.frigateSVRBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;

/**
 * The {@link mqtt.frigateSVRConfiguration} class contains mappings to the
 * Frigate SVR config block
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class APIGetRecordingSummary extends APIBase {

    public APIGetRecordingSummary() {
        super(MQTT_GETRECORDINGSUMMARY_SUFFIX);
    }

    public ResultStruct Validate() {
        // nothing to validate on the input side.
        return new ResultStruct(true, "ok");
    }

    protected String BuildURL() {
        return "";
    }

    protected String BuildTopicSuffix() {
        return eventID;
    }
}
