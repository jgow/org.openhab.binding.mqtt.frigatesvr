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
public class APIGetLastFrame extends APIBase {

    private final Logger logger = LoggerFactory.getLogger(APIGetLastFrame.class);

    public APIGetLastFrame() {
        super(MQTT_GETLASTFRAME_SUFFIX);
    }

    public APIGetLastFrame(@Nullable String payload) {
        super(MQTT_GETLASTFRAME_SUFFIX);
        if (payload != null) {
            this.payload = payload;
        }
    }

    public ResultStruct ParseFromBits(String[] bits, String payload) {
        ResultStruct rc = new ResultStruct();
        if (bits.length == 4) {
            if (bits[3].equals(MQTT_GETLASTFRAME_SUFFIX)) {
                this.cam = bits[2];

                // The payload will contain a JSON block with the query
                // arguments. We need to grab this and later parse it into the
                // query string

                this.payload = payload;

                // Why do we check the label again, since we already did it in the
                // cam handler? Well, some muppet may have poked us with this message
                // manually....

                rc = this.Validate();

            } else {
                rc.message = "API handler/topic mismatch";
            }
        } else {
            rc.message = "internal communication error";
        }
        return rc;
    }

    public ResultStruct Process(frigateSVRHTTPHelper httpHelper, MqttBrokerConnection connection, String topicPrefix,
            String[] bits, String payload) {

        ResultStruct rc = ParseFromBits(bits, payload);

        if (rc.rc) {
            logger.info("server: processing camera last frame request for {}", cam);
            rc = ParseJSONQueryString(payload);
            if (rc.rc) {
                String call = "/api/" + cam + "/latest.jpg" + rc.message;
                rc = httpHelper.runGet(call);
            }
        }
        PublishResultWithImage(connection, topicPrefix, rc);
        return rc;
    }

    @SuppressWarnings("null")
    public ResultStruct Validate() {
        ResultStruct rc = new ResultStruct();
        // We just extract our query string from the JSON payload, null is ok.
        try {
            if (!payload.isEmpty()) {
                JsonParser.parseString(payload);
            }
            rc.rc = true;
            rc.message = "arguments valid";
        } catch (JsonSyntaxException e) {
            logger.info("parse failed {}", e.getMessage());
            rc.message = e.toString();
        }
        return rc;
    }

    protected String BuildTopicSuffix() {
        return eventID;
    }
}
