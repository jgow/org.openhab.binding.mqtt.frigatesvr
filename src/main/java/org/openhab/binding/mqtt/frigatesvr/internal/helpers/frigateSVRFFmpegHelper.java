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
package org.openhab.binding.mqtt.frigatesvr.internal.helpers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link mqtt.frigateSVRFFmpegHelper} is a helper class allowing ffmpeg to re-stream
 * the H264 streams coming from Frigate to MJPEG, in order that they can be displayed on
 * openHAB UIs
 * 
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRFFmpegHelper {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private @Nullable Process process = null;
    private List<String> ffmpegargs = new ArrayList<String>();

    public frigateSVRFFmpegHelper() {
    }

    public void BuildFFMPEGCommand(String ffmpegLocation, String sourceURL, String destinationURL,
            String ffmpegCommands) {

        ffmpegargs.add(ffmpegLocation);
        String arglist = new String(" -rtsp_transport tcp -hide-banner -i");
        arglist += sourceURL + " " + ffmpegCommands;
        Collections.addAll(ffmpegargs, arglist.trim().split("\\s+"));
        ffmpegargs.add(destinationURL);
    }

    public void StartStream() {
        if (process != null) {
            try {
                process = Runtime.getRuntime().exec(ffmpegargs.toArray(new String[ffmpegargs.size()]));
            } catch (IOException e) {
                logger.error("Unable to start ffmpeg:");
                process = null;
            }
        }
    }

    public void StopStream() {
        if (process != null) {
            ((@Nullable Process) process).destroyForcibly();
            process = null;
        }
    }

    public boolean isRunning() {
        return (process == null) ? false : true;
    }
}
