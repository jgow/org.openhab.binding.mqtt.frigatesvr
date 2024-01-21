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

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * The {@link mqtt.frigateSVRConfiguration} class contains mappings to the
 * Frigate SVR config block
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class APITriggerEvent extends APIBase {

    @Expose
    @SerializedName("label")
    @Nullable
    private String label = null;

    public APITriggerEvent(String label, @Nullable String payload) {
        super(MQTT_EVTTRIGGER_SUFFIX);
        this.payload = payload;
    }

    @SuppressWarnings("null")
    public ResultStruct Validate() {
        ResultStruct rc = new ResultStruct();
        if (label != null) {
            if (!label.isBlank() && !label.isEmpty() && label.matches("^[A-Za-z0-9]+$")) {
                // our parameters are ok, check the JSON provided as payload is also valid. A null or blank
                // payload is ok. We don't use it, just check it is valid JSON before we fire it off. We let
                // Frigate do the content checking.
                try {
                    if (payload != null) {
                        JsonParser.parseString((@NonNull String) payload);
                    }
                    rc.rc = true;
                    rc.message = "arguments valid";
                } catch (JsonSyntaxException e) {
                    rc.message = e.toString();
                }
            } else {
                rc.message = "invalid event label";
            }
        } else {
            rc.message = "event label null";
        }
        return rc;
    }

    protected String BuildURL() {
        return "";
    }

    protected String BuildTopicSuffix() {
        return eventID + "/" + label;
    }
}
