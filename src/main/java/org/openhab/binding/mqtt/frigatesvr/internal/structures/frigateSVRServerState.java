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
package org.openhab.binding.mqtt.frigatesvr.internal.structures;

import java.util.ArrayList;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link mqtt.frigateSVRServerState} is a structure passed between a running server thing and
 * the supported cameras, providing status and configuration information
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRServerState {

    public String status = "offline"; // event id
    public String url = ""; // camera name
    public String rtspbase = "";
    public String clientID = "";
    public String topicPrefix = "";
    public ArrayList<String> Cameras = new ArrayList<String>();
    public String whitelist = "DISABLE";
    public String ffmpegPath = "/usr/bin/ffmpeg";
    public String serverThingID = "";
    public String URLChannelPrefix = "";
}
