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
package org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI;

import static org.openhab.binding.mqtt.frigatesvr.internal.frigateSVRBindingConstants.*;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRHTTPHelper;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link mqtt.frigateSVRConfiguration} class contains mappings to the
 * Frigate SVR config block
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class APIGetThumbnail extends APIBase {

    private String label = "";

    private final Logger logger = LoggerFactory.getLogger(APIGetThumbnail.class);

    public APIGetThumbnail() {
        super(MQTT_GETTHUMBNAIL_SUFFIX);
    }

    public APIGetThumbnail(String label) {
        super(MQTT_GETTHUMBNAIL_SUFFIX);
        this.label = label;
    }

    public ResultStruct ParseFromBits(String[] bits, @Nullable String payload) {
        ResultStruct rc = new ResultStruct();
        if (bits.length == 5) {
            this.cam = bits[2];
            this.label = bits[4];
            this.payload = ""; // payload is unused, only use label
            rc = this.Validate(); // in case the message was sent in from elsewhere
        } else {
            rc.message = "invalid topic string";
        }
        return rc;
    }

    public ResultStruct Process(frigateSVRHTTPHelper httpHelper, MqttBrokerConnection connection, String topicPrefix,
            String[] bits, String payload) {

        ResultStruct rc = ParseFromBits(bits, payload);
        if (rc.rc) {
            String call = "/api/" + cam + "/" + label + "/thumbnail.jpg";
            logger.info("posting: GET '{}'", call);
            rc = httpHelper.runGet(call);
        }
        PublishResultWithImage(connection, topicPrefix, rc);
        return rc;
    }

    @SuppressWarnings("null")
    public ResultStruct Validate() {
        ResultStruct rc = new ResultStruct();
        if (!label.isBlank() && !label.isEmpty() && label.matches("^[A-Za-z0-9]+$")) {
            // payload is not used
            rc.rc = true;
            rc.message = "arguments valid";
        } else {
            rc.message = "invalid event label";
        }
        return rc;
    }

    protected String BuildTopicSuffix() {
        return eventID + "/" + label;
    }
}
