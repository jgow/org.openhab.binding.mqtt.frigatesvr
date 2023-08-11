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
package org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams;

//import static org.openhab.binding.ipcamera.internal.IpCameraBindingConstants.HLS_STARTUP_DELAY_MS;

import java.io.IOException;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.OpenStreams;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.StreamOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link MJPEGStream} encapsulates a served MJPEG stream
 *
 * @author Dr J Gow - initial contribution
 */
@NonNullByDefault
public class MJPEGStream extends StreamTypeBase {

    private final Logger logger = LoggerFactory.getLogger(MJPEGStream.class);
    protected OpenStreams streamList = new OpenStreams();

    public MJPEGStream(String baseURL, String ffBinary, String URLtoFF, String ffcmds, String readerPath) {
        super(baseURL, ffBinary, URLtoFF, ffcmds, readerPath);
        this.pathfromFF = "/frigate-in.jpg";
        String ffDestURL = new String("http://127.0.0.1:8080") + baseURL + pathfromFF;
        this.ffHelper.BuildFFMPEGCommand(ffBinary, URLtoFF, ffDestURL, ffcmds);
    }

    /////////////////////////////////////////////////////////////////////////
    // StopStreams
    //
    // Called by the servlet to ensure the stream is stopped and cleaned up.

    public void StopStreams() {
        this.streamList.closeAllStreams();
        super.StopStreams();
    }

    /////////////////////////////////////////////////////////////////////////
    // canPost
    //
    // Must return true if the post is valid for this stream type

    public boolean canPost(String pathInfo) {
        return (pathInfo.equals(this.pathfromFF)) ? true : false;
    }

    ////////////////////////////////////////////////////////////////////////
    // canAccept
    //
    // Must return true if the stream can accept the GET request.

    public boolean canAccept(String pathInfo) {
        return (pathInfo.equals(this.readerPath)) ? true : false;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Poster
    //
    // MJPEG streams are chunked into images - which ffmpeg will send us one
    // at a time. We pull these into a queue here.

    public void Poster(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
        ServletInputStream snapshotData = req.getInputStream();
        this.streamList.queueFrame(snapshotData.readAllBytes());
        snapshotData.close();
    }

    //////////////////////////////////////////////////////////////////////////
    // Getter
    //
    // We process this by dequeuing and sending each frame at a time.

    public void Getter(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {

        logger.info("getter processing request");

        StreamOutput output = new StreamOutput(resp, "video/x-motion-jpeg");
        if (this.streamList.isEmpty()) {
            logger.info("Starting ffmpeg stream");
            this.ffHelper.StartStream();
        } else {
            // make sure we are still running
            this.ffHelper.PokeMe();
        }
        this.streamList.addStream(output);
        do {
            try {
                output.sendFrame();
            } catch (InterruptedException | IOException e) {

                // Browser closed the stream:
                this.streamList.removeStream(output);

                // we must shut down the ffmpeg processes here if there are no more streams

                logger.debug("{} frigateSVR mjpeg reader streams remain open.", this.streamList.getNumberOfStreams());

                if (this.streamList.isEmpty()) {
                    this.ffHelper.StopStream();
                    logger.info("all mjpeg reader streams have stopped.");
                }

                return;
            }
        } while (!this.streamList.isEmpty());
    }
}
