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
package org.openhab.binding.mqtt.frigatesvr.internal.structures;

import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

/**
 * The {@link mqtt.frigateSVRServerState} is a structure passed between a running server thing and
 * the supported cameras, providing status and configuration information
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRServerState {

    @SerializedName("status")
    public String status = "offline"; // event id
    @SerializedName("url")
    public String url = ""; // camera name
    @SerializedName("rtspbase")
    public String rtspbase = "";
    @SerializedName("topicPrefix")
    public String topicPrefix = "";
    @SerializedName("Cameras")
    public ArrayList<String> Cameras = new ArrayList<String>();
    @SerializedName("whitelist")
    public String whitelist = "DISABLED";
    @SerializedName("ffmpegPath")
    public String ffmpegPath = "/usr/bin/ffmpeg";
    @SerializedName("serverThingID")
    public String serverThingID = "";

    public String GetJsonString() {
        return new GsonBuilder().create().toJson(this);
    }
}
