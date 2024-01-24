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
public class APIGetLastFrame extends APIBase {

    private final Logger logger = LoggerFactory.getLogger(APIGetLastFrame.class);

    private String cam = "";

    public APIGetLastFrame() {
        super(MQTT_GETLASTFRAME_SUFFIX);
    }

    public APIGetLastFrame(@Nullable String payload) {
        super(MQTT_GETLASTFRAME_SUFFIX);
        this.payload = payload;
    }

    public ResultStruct ParseFromBits(String[] bits, @Nullable String payload) {
        ResultStruct rc = new ResultStruct();
        if (bits.length == 5) {
            this.cam = bits[2];

            // TODO: the payload will contain a JSON block with the query
            // arguments. We need to parse this out and use it to build the
            // query string.

            // this.payload = payload;

            // Why do we check the label again, since we already did it in the
            // cam handler? Well, some muppet may have poked us with this message
            // manually....

            rc = this.Validate();
        } else {
            rc.message = "internal communication error";
        }
        return rc;
    }

    public ResultStruct Process(frigateSVRHTTPHelper httpHelper, MqttBrokerConnection connection, String topicPrefix) {

        ResultStruct rc = new ResultStruct();
        logger.info("server: processing camera last frame request for {}", cam);
        String call = "/api/" + cam + "/latest.jpg";
        // ---------
        // TODO: parse out payload
        if (!payload.equals("")) {
            call += "?" + payload;
        }
        // TODO ---
        // --------
        rc = httpHelper.runGet(call);
        String camTopicPrefix = topicPrefix + "/" + cam + "/" + MQTT_CAMACTIONRESULT;
        logger.info("publishing state to {}", camTopicPrefix);
        if (rc.rc == true) {
            String imagePrefix = topicPrefix + "/" + cam + "/lastFrame";
            logger.info("publishing image to {}", imagePrefix);
            connection.publish(imagePrefix, rc.raw, 1, false);

            String errFormat = String.format("{\"success\":true,\"message\":\"%s\"}", rc.message);
            connection.publish(camTopicPrefix, errFormat.getBytes(), 1, false);
        } else {
            logger.error("failed call to GetLastFrame: rc message {}", rc.message);
            String errFormat = String.format("{\"success\":false,\"message\":\"%s\"}", rc.message);
            connection.publish(camTopicPrefix, errFormat.getBytes(), 1, false);
        }
        return rc;
    }

    @SuppressWarnings("null")
    public ResultStruct Validate() {
        ResultStruct rc = new ResultStruct();
        // We just extract our query string from the JSON payload, null is ok.
        try {
            if (payload != null) {
                JsonParser.parseString((@NonNull String) payload);
            }
            rc.rc = true;
            rc.message = "arguments valid";
        } catch (JsonSyntaxException e) {
            rc.message = e.toString();
        }
        return rc;
    }

    protected String BuildTopicSuffix() {
        return eventID;
    }
}
