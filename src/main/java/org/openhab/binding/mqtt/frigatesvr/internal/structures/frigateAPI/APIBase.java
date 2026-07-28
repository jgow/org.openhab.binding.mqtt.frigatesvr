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

import java.util.Iterator;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.handlers.frigateSVRActionProcessor;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

/**
 * The {@link mqtt.frigateSVRConfiguration} class contains mappings to the
 * Frigate SVR config block
 *
 * @author J Gow - Initial String camTopicPrefix = topicPrefix + "/" + cam + "/" + MQTT_CAMACTIONRESULT;
 *         contribution
 */
@NonNullByDefault
public abstract class APIBase {

    protected String param = "";
    protected String payload = "";
    protected String cam = "";

    private final Logger logger = LoggerFactory.getLogger(APIBase.class);

    // Constructors

    public APIBase(@Nullable String payload) {
        if (payload == null) {
            this.payload = "";
        } else {
            this.payload = payload;
        }
    }

    public void SetCamera(String camera) {
        this.cam = camera;
    }

    public void SetPayload(String payload) {
        this.payload = payload;
    }

    public String getPayload() {
        return this.payload;
    }

    public abstract ResultStruct Process(APIHelper apiHelper, frigateSVRActionProcessor connection);

    public abstract ResultStruct Validate();

    public ResultStruct ParseJSONQueryString(String json) {
        ResultStruct rc = new ResultStruct();
        String msg = new String("?");
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            Iterator<String> members = o.keySet().iterator();
            while (members.hasNext()) {
                String q = members.next();
                try {
                    JsonElement v = o.get(q);
                    msg += q + "=" + v.getAsString();
                } catch (Exception e) {
                    logger.debug("ignoring query element {}", q);
                }
                if (members.hasNext()) {
                    msg += "&";
                }
            }
            rc.message = msg;
            rc.rc = true;
        } catch (Exception e) {
            rc.message = "bad JSON query structure";
        }
        return rc;
    }

    ///////////////////////////////////////////////////////////////////////////
    /// CheckValidJSON
    ///
    /// Check if a string contains valid JSON

    protected boolean CheckValidJSON(String json) {
        boolean rc = false;
        try {
            JsonParser.parseString(json);
            rc = true;
        } catch (JsonSyntaxException e) {
            logger.error("invalid JSON: {}", json);
        }
        return rc;
    }
}
