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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRFFmpegHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link StreamType} encapsulates each individual type of stream
 *
 * @author Dr J Gow - initial contribution
 */
@NonNullByDefault
public class StreamTypeBase {

    private final Logger logger = LoggerFactory.getLogger(StreamTypeBase.class);
    protected frigateSVRFFmpegHelper ffHelper = new frigateSVRFFmpegHelper();
    public String pathfromFF = "";
    public String readerPath = "";

    public StreamTypeBase(String baseURL, String ffBinary, String URLtoFF, String ffcmds, String readerPath) {
        this.readerPath = readerPath;
    }

    /////////////////////////////////////////////////////////////////////////
    // StopStreams
    //
    // Called by the servlet to ensure the stream is stopped and cleaned up.

    public void StopStreams() {
        this.ffHelper.StopStream();
    }

    /////////////////////////////////////////////////////////////////////////
    // PokeMe
    //
    // Automatically called from the keepalive

    public void PokeMe() {
        this.ffHelper.PokeMe();
    }

    /////////////////////////////////////////////////////////////////////////
    // canPost
    //
    // Must return true if the post is valid for this stream type

    public boolean canPost(String pathInfo) {
        return false;
    }

    ////////////////////////////////////////////////////////////////////////
    // canAccept
    //
    // Must return true if the stream can accept the GET request.

    public boolean canAccept(String pathInfo) {
        return false;
    }

    ////////////////////////////////////////////////////////////////////////
    // Poster
    //
    // Callback in response to a valid POST response sent to this endpoint

    public void Poster(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
        // ServletInputStream snapshotData = req.getInputStream();
        // do {
        // this.streamList.queueFrame(snapshotData.readNBytes(8192));
        // } while (!snapshotData.isFinished());
        // snapshotData.close();
    }

    //////////////////////////////////////////////////////////////////////////
    // Getter
    //
    // Callback in response to a GET

    public void Getter(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
    }

    /////////////////////////////////////////////////////////////////////////
    // SendFile
    //
    // Send a file in response.

    protected void SendFile(HttpServletResponse response, String filename, String contentType) throws IOException {
        logger.info("serving file {}", filename);
        File file = new File(filename);
        if (!file.exists()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            logger.info("file {} not found", filename);
            return;
        }
        response.setBufferSize((int) file.length());
        response.setContentType(contentType);
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
