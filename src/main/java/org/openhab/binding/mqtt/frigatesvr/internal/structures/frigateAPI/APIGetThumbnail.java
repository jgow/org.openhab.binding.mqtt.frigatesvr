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

import static org.openhab.binding.mqtt.frigatesvr.internal.frigateSVRBindingConstants.MQTT_GETTHUMBNAIL_SUFFIX;

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

    public APIGetThumbnail(String label) {
        super("");
        this.label = label;
    }

    @Override
    public ResultStruct Process(APIHelper httpHelper, MqttBrokerConnection connection) {

        String call = "/api/" + cam + "/" + label + "/thumbnail.jpg";
        logger.info("posting: GET '{}'", call);
        // TODO ResultStruct rc = httpHelper.runGet(call);
        PublishResultWithImage(connection, topicPrefix, rc);
        return rc;
    }

    @Override
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
}
