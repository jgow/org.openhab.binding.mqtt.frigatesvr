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

import org.eclipse.jdt.annotation.NonNull;
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
    private boolean isStarted = false;

    public frigateSVRFFmpegHelper() {
    }

    ////////////////////////////////////////////////////////////
    // BuildFFMPEGCommand
    //
    // Construct a command string for the FFMPEG process

    public void BuildFFMPEGCommand(String ffmpegLocation, String sourceURL, String destinationURL,
            String ffmpegCommands) {

        ffmpegargs.clear();
        ffmpegargs.add(ffmpegLocation);
        String arglist = new String(" -rtsp_transport tcp -hide_banner -i ");
        arglist += sourceURL + " " + ffmpegCommands;
        Collections.addAll(ffmpegargs, arglist.trim().split("\\s+"));
        ffmpegargs.add(destinationURL);
        ffmpegargs.forEach(n -> {
            logger.info("FFMPEG command: {}", n);
        });
    }

    /////////////////////////////////////////////////////////////
    // StartStream
    //
    // Start the ffmpeg process.

    public void StartStream() {
        if (process == null) {
            try {
                logger.info("ffmpeg stream starting");
                process = Runtime.getRuntime().exec(ffmpegargs.toArray(new String[ffmpegargs.size()]));
                isStarted = true;
            } catch (IOException e) {
                logger.error("Unable to start ffmpeg:");
                process = null;
                isStarted = false;
                return;
            }
        }
    }

    /////////////////////////////////////////////////////////////
    // StopStream
    //
    // Stops the ffmpeg process, effectively by killing it.

    public void StopStream() {
        if (process != null) {
            ((@NonNull Process) process).destroyForcibly();
            isStarted = false;
            process = null;
        }
    }

    ////////////////////////////////////////////////////////////
    // PokeMe
    //
    // Used by the keepalive mechanism - if we are meant to have
    // a running ffmpeg process and it has crashed/been killed,
    // then attempt to restart us

    public void PokeMe() {
        do {
            if (!isStarted) {
                break;
            }
            if (process != null) {
                if (((@NonNull Process) process).isAlive()) {
                    break;
                }
            }
            // we must restart
            process = null;
            StartStream();
        } while (false);
    }

    ////////////////////////////////////////////////////////
    // isRunning
    //
    // Returns true if the ffmpeg process is still running

    public boolean isRunning() {
        return (process == null) ? false : (((@NonNull Process) process).isAlive()) ? true : false;
    }
}
