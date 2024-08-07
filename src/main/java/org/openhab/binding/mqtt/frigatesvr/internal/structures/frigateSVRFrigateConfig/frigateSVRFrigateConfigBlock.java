/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRFrigateConfig;

import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * The {@link mqtt.frigateSVRConfiguration} class contains mappings to the
 * Frigate SVR config block
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRFrigateConfigBlock {

    private final Logger logger = LoggerFactory.getLogger(frigateSVRFrigateConfigBlock.class);

    @Expose
    @SerializedName("mqtt")
    public frigateSVRFrigateMQTTBlock mqtt = new frigateSVRFrigateMQTTBlock();
    @Expose
    @SerializedName("birdseye")
    public frigateSVRFrigateBirdseyeBlock birdseye = new frigateSVRFrigateBirdseyeBlock();
    @Expose
    @SerializedName("cameras")
    public JsonObject cameras = new JsonObject();

    //////////////////////////////////////////////////////////////////
    // GetCameraList
    //
    // Returns a list of camera names from the configuration. Will
    // return an empty list if the handler is offline or not
    // initialized.

    public ArrayList<String> GetCameraList() {
        ArrayList<String> cameraList = new ArrayList<String>();
        if (cameras.keySet().size() > 0) {
            cameras.keySet().forEach(key -> {
                logger.debug("Adding camera :{}", key);
                cameraList.add(key);
            });
        } else {
            logger.info("no cameras in Frigate config");
        }
        return cameraList;
    }
}
