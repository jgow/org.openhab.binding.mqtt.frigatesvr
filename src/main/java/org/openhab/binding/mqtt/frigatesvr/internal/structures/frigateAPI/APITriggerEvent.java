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
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRHTTPHelper;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

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

    public APITriggerEvent() {
        super(MQTT_EVTTRIGGER_SUFFIX);
    }

    public APITriggerEvent(String label, @Nullable String payload) {
        super(MQTT_EVTTRIGGER_SUFFIX);
        this.label = label;
    }

    public ResultStruct ParseFromBits(String[] bits, String payload) {
        ResultStruct rc = new ResultStruct();
        if (bits.length == 5) {
            this.cam = bits[2];
            this.label = bits[4];
            this.payload = payload;

            // Why do we check the label again, since we already did it in the
            // cam handler? Well, some muppet may have poked us with this message
            // manually....

            rc = this.Validate();
        } else {
            rc.message = "internal communication error";
        }
        return rc;
    }

    public ResultStruct Process(frigateSVRHTTPHelper httpHelper, MqttBrokerConnection connection, String topicPrefix,
            String[] bits, String payload) {

        ResultStruct rc = ParseFromBits(bits, payload);
        if (rc.rc) {
            String call = "/api/events/" + cam + "/" + label + "/create";
            logger.info("posting: POST '{}'", call);
            rc = httpHelper.runPost(call, payload);
        }
        PublishResult(connection, topicPrefix, rc);
        return rc;
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

    protected String BuildTopicSuffix() {
        return eventID + "/" + label;
    }
}
