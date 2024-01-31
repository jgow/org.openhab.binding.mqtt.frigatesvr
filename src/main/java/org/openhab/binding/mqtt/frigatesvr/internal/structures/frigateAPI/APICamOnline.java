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
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRServerState;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link APICamOnline} class contains mappings to the
 * Frigate SVR config block
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class APICamOnline extends APIBase {

    private final Logger logger = LoggerFactory.getLogger(APICamOnline.class);

    frigateSVRServerState svrState;

    public APICamOnline(frigateSVRServerState svrState) {
        super(MQTT_ONLINE_SUFFIX);
        this.svrState = svrState;
    }

    public ResultStruct ParseFromBits(String[] bits, @Nullable String payload) {

        ResultStruct rc = new ResultStruct();
        if (bits.length == 4) {
            this.cam = bits[2];
            logger.info("camera {} reports online", this.cam);
        } else {
            rc.message = "internal communication error";
        }
        return rc;
    }

    public ResultStruct Process(frigateSVRHTTPHelper httpHelper, MqttBrokerConnection connection, String topicPrefix,
            String[] bits, String payload) {

        logger.info("topic prefix in process {}", topicPrefix);
        // we force a re-publish of the status - TODO: improve.
        String topic = "frigateSVRALL/" + this.svrState.serverThingID + "/status";
        connection.publish(topic, this.svrState.GetJsonString().getBytes(), 1, false);
        logger.info("publishing status to {}", topic);
        return new ResultStruct(true, "ok");
    }

    public ResultStruct Validate() {
        // nothing to validate on the input side.
        return new ResultStruct(true, "ok");
    }

    protected String BuildTopicSuffix() {
        return "";
    }
}
