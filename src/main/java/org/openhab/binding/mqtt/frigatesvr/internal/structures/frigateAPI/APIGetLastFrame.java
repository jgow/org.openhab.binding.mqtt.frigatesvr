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

import static org.openhab.binding.mqtt.frigatesvr.internal.frigateSVRBindingConstants.MQTT_GETLASTFRAME_SUFFIX;

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


    public APIGetLastFrame(@Nullable String payload) {
        super(payload);
    }

    @Override
    public ResultStruct Process(APIHelper httpHelper, MqttBrokerConnection connection) {

    	logger.debug("server: processing camera last frame request for {}", cam);
    	rc = ParseJSONQueryString(payload);
    	if (rc.rc) {
    		String call = "/api/" + cam + "/latest.jpg" + rc.message;
    		// rc = httpHelper.runGet(call);
    	}
        PublishResultWithImage(connection, topicPrefix, rc);
        return rc;
    }

    @Override
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
            logger.debug("parse failed {}", e.getMessage());
            rc.message = e.toString();
        }
        return rc;
    }

}
