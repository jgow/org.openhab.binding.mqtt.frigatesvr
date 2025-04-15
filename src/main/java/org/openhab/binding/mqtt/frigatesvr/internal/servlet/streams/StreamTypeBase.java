/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.HTTPHandler;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRCommonConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link StreamType} encapsulates each individual type of stream
 *
 * @author Dr J Gow - initial contribution
 */
@NonNullByDefault
public class StreamTypeBase extends HTTPHandler {

    private final Logger logger = LoggerFactory.getLogger(StreamTypeBase.class);
    protected FFmpegManager ffHelper = new FFmpegManager();
    protected boolean isStreamRunning = false;
    protected int hitCount = 0;
    public String pathfromFF = "";
    public String readerPath = "";
    private int keepalive_delay = 2;
    protected frigateSVRCommonConfiguration config;
    protected boolean startOnLoad = true;
    protected String serverBase = "";

    @SuppressWarnings("serial")
    private static final Map<String, String> mimeExt = new HashMap<String, String>() {
        {
            put("mpd", "application/dash+xml");
            put("mp4", "video/mp4");
            put("m4v", "video/mp4");
            put("m4s", "video/iso.segment");
            put("m4a", "audio/mp4");
            put("m3u8", "application/vnd.apple.mpegurl");
            put("ts", "video/MP2T");
        }
    };

    public StreamTypeBase(String readerPath, String ffBinary, String URLtoFF, frigateSVRCommonConfiguration config) {
        this.readerPath = readerPath;
        this.config = config;
        this.keepalive_delay = config.ffKeepalivesBeforeExit;
    }

    /////////////////////////////////////////////////////////////////////////
    // GetMime
    //
    // Return the mime type for a file with a given extension, or unknown

    private static String GetMime(String fn) {
        String ext = fn.substring(fn.lastIndexOf('.') + 1);
        if (!ext.equals("")) {
            if (mimeExt.containsKey(ext)) {
                return (@NonNull String) (mimeExt.get(ext));
            }
        }
        return "application/octet-stream";
    }

    /////////////////////////////////////////////////////////////////////////
    // ServerReady
    //
    // Called when the server is ready - we can use this to start streams
    // if the stream producer start on demand is disabled.

    @Override
    public void ServerReady(String serverBase) {
        this.serverBase = serverBase;
        if (this.startOnLoad) {
            StartStreams();
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // StartStreams
    //
    // To start the streams, if we are not already running. This could come
    // in on multiple contexts - so use a context lock

    public synchronized void StartStreams() {

        // if our ffmpeg process is already running, we need
        // do nothing.

        if (!this.ffHelper.isRunning() && !isStreamRunning) {

            // otherwise we need to start it and wait for it to start

            this.ffHelper.StartStream();

            // Right then: now wait until ffmpeg has started spitting output
            // and that it is available, otherwise we may get browser error
            // messages. Need to have this online before the browser timeouts
            // This can take some time. While this code doesn't actually block,
            // it could sit in the loop 30 seconds or more in order to get
            // ffmpeg started.
            // To check, we use stream-specific presence of output from ffmpeg,
            // together with a valid frame count.
            // The check for actual output is stream type specific.

            int count = 0;
            do {
                logger.info("waiting 1000ms for stream to appear");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                String s = this.ffHelper.GetStats("frame");
                int frm = 0;
                if (!s.isBlank()) {
                    try {
                        frm = Integer.valueOf(s);
                    } catch (NumberFormatException e) {
                        frm = 0;
                    }
                }

                if (this.CheckStarted() && ((!s.equals("")) && frm >= this.config.ffMinFramesToStart)) {
                    logger.info("ffmpeg stream confirmed started; frame count {} fps {}", s,
                            this.ffHelper.GetStats("fps"));

                    // guarantees we always wait one timeout interval
                    // once the stream is marked 'running'

                    hitCount = 1;
                    isStreamRunning = true;

                    break;
                } else {
                    logger.info("waiting for ffmpeg; frame count {} fps {} checkstarted {} minFrames {}", s,
                            this.ffHelper.GetStats("fps"), (this.CheckStarted()) ? "true" : "false",
                            this.config.ffMinFramesToStart);
                    if (count++ == 30) {
                        logger.warn("ffmpeg start failed");
                        this.StopStreams();
                        break;
                    }
                }
            } while (true);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // CheckStarted
    //
    // This member function is overloaded per stream type to return true
    // as soon as output has been generated.

    public boolean CheckStarted() {
        return false;
    }

    /////////////////////////////////////////////////////////////////////////
    // StopStreams
    //
    // Called by the servlet to ensure the stream is stopped and cleaned up.

    public synchronized void StopStreams() {
        logger.info("StopStreams called");
        isStreamRunning = false;
        this.ffHelper.StopStream();
    }

    /////////////////////////////////////////////////////////////////////////
    // Cleanup
    //
    // Called by the servlet to remove stream environments.

    public synchronized void Cleanup() {
        this.StopStreams();
        this.ffHelper.Cleanup();
    }

    /////////////////////////////////////////////////////////////////////////
    // PokeMe
    //
    // Automatically called from the keepalive. Verify the hitcount. If we get
    // no hits between keepalives, then shut down the stream. However, it can
    // take a while for the ffmpeg producer to start loading files, so
    // do not check the hit count unless the ffmpeg process has written
    // the playlist.

    public synchronized void PokeMe() {
        this.ffHelper.PokeMe();
        if ((this.isStreamRunning == true) && !startOnLoad) {
            if (--keepalive_delay == 0) {
                logger.info("stream is running ({})", this.getClass().getSimpleName());
                // no-one has requested the stream between now and the last
                // keepalive. Assume we're not wanted, so go and eat worms.
                if (hitCount == 0) {
                    logger.info("no further requestors; shutting down stream");
                    StopStreams();
                } else {
                    logger.info("hitcount = {}, stream continuing", hitCount);
                }
                keepalive_delay = config.ffKeepalivesBeforeExit;
            }
        }
        hitCount = 0;
    }

    /////////////////////////////////////////////////////////////////////////
    // SendFile
    //
    // Send a file in response.

    protected void SendFile(HttpServletResponse response, String filename, String contentType) throws IOException {

        String mimeType;
        if (contentType.equals("")) {
            mimeType = GetMime(filename);
        } else {
            mimeType = contentType;
        }

        logger.debug("serving file {} content type {}", filename, mimeType);

        File file = new File(filename);
        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            logger.info("file {} not found", filename);
            return;
        }

        response.setBufferSize((int) file.length());
        response.setContentType(mimeType);

        // Ensure headers are set to inform the client
        // that files should not be cached. Otherwise we will get old stream
        // data over and over in the case of the multipart stuff.

        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Expose-Headers", "*");
        response.setHeader("Content-Length", String.valueOf(file.length()));
        response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");

        FileInputStream fs = new FileInputStream(file);
        fs.transferTo(response.getOutputStream());
        fs.close();
    }
}
