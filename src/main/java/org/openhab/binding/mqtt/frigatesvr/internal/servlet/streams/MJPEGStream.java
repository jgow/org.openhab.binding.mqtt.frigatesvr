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

import java.io.IOException;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRCommonConfiguration;
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
    private boolean postFlag = false;

    public MJPEGStream(String baseURL, String ffBinary, String URLtoFF, String readerPath,
            frigateSVRCommonConfiguration config) {
        super(baseURL, ffBinary, URLtoFF, readerPath, config);
        this.startOnLoad = config.ffMJPEGStartProducerOnLoad;

        this.pathfromFF = "frigate-in.jpg";
        String ffDestURL = new String("http://127.0.0.1:8080") + baseURL + "/" + pathfromFF;
        // no WD prefix here
        this.ffHelper.BuildFFMPEGCommand(ffBinary, URLtoFF, ffDestURL, config.ffMJPEGTranscodeCommands, null);
    }

    /////////////////////////////////////////////////////////////////////////
    // CheckStarted
    //
    // This member function is overloaded per stream type to return true
    // as soon as output has been generated.

    public boolean CheckStarted() {
        return postFlag;
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
    // at a time. We pull these into a queue here. If we have no output
    // streams to send to, the images simply get binned. This way even for
    // these stream types, we can leave the ffmpeg process running in the
    // background.

    public void Poster(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
        ServletInputStream snapshotData = req.getInputStream();
        this.streamList.queueFrame(snapshotData.readAllBytes());
        snapshotData.close();
        postFlag = true;
    }

    //////////////////////////////////////////////////////////////////////////
    // Getter
    //
    // We process this by dequeuing and sending each frame at a time.

    public void Getter(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {

        logger.info("getter processing request");

        StreamOutput output = new StreamOutput(resp, "video/x-motion-jpeg");

        synchronized (this) {
            if (!this.isStreamRunning) {
                this.postFlag = false;
                // If we fail to start the stream, just return 'not found'
                this.StartStreams();
                if (!this.isStreamRunning) {
                    logger.warn("failed to start ffmpeg stream");
                    resp.sendError(HttpServletResponse.SC_NOT_FOUND);
                    return;
                }
            }
        }

        // otherwise we have frames being posted
        // The synchronized fifo and StreamOutput objects come from ipcamera - they
        // work well.

        this.streamList.addStream(output);
        do {
            hitCount++;
            try {
                output.sendFrame();
            } catch (InterruptedException | IOException e) {

                // Browser closed the stream:
                this.streamList.removeStream(output);

                // we must shut down the ffmpeg processes here if there are no more streams

                logger.debug("{} frigateSVR mjpeg reader streams remain open.", this.streamList.getNumberOfStreams());

                if (this.streamList.isEmpty()) {
                    this.StopStreams();
                    logger.info("all MJPEG reader streams have stopped.");
                }

                return;
            }
        } while (!this.streamList.isEmpty());
    }
}
