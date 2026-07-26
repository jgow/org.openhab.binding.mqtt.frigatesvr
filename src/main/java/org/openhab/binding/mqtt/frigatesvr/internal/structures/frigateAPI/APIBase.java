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

import static org.openhab.binding.mqtt.frigatesvr.internal.frigateSVRBindingConstants.*;

import java.util.Iterator;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRHTTPHelper;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APIHelper;
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

    public APIBase(String payload) {
    	this.payload=payload;
    }
        
    public void SetCamera(String camera) {
    	this.cam=camera;
    }
    
    public void SetPayload(String payload) {
        this.payload = payload;
    }

    public String getPayload() {
        return this.payload;
    }

    public abstract ResultStruct Process(APIHelper apiHelper, 
    		                             MqttBrokerConnection connection);

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

    protected void PublishResultWithImage(MqttBrokerConnection conn, String topicPrefix, ResultStruct rc) {

        // A bit different. Here the raw data from the Frigate API contains an image,
        // which we must post to a different endpoint. This will only be necessary if the
        // result is successful. If successful, the message just contains the ok string
        // from the HTTP call.

        if (rc.rc) {
            if (rc.raw.length > 0) {
                // the return is an image - we post this to the camera's image channel
                String imagePrefix = topicPrefix + "/" + MQTT_CAMIMAGERESULT;
                logger.debug("publishing image to {}", imagePrefix);
                conn.publish(imagePrefix, rc.raw, 1, false);
            }
        } else {
            logger.error("{}", rc.message);
        }

        // In this case, rc.raw will contain an image, we only need to post the
        // message to the result block

        String camTopicPrefix = topicPrefix + "/" + MQTT_CAMACTIONRESULT;
        String errFormat = String.format("{\"success\":%s,\"message\":\"%s\"}", (rc.rc) ? "true" : "false", rc.message);
        logger.debug("server - publishing result block to {}", camTopicPrefix);
        conn.publish(camTopicPrefix, errFormat.getBytes(), 1, false);
    }
    
    ///////////////////////////////////////////////////////////////////////////
    /// CheckValidJSON
    /// 
    /// Check if a string contains valid JSON
    
    protected boolean CheckValidJSON(String json) {
    	boolean rc=false;
    	try {
            JsonParser.parseString(json);
            rc = true;
        } catch (JsonSyntaxException e) {
        	logger.error("invalid JSON: {}", json);
        }
    	return rc;
    }
    
}
