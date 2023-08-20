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
package org.openhab.binding.mqtt.frigatesvr.internal.servlet;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link StreamType} encapsulates each individual type of stream
 *
 * @author Dr J Gow - initial contribution
 */
@NonNullByDefault
public class HTTPHandler {

    private final Logger logger = LoggerFactory.getLogger(HTTPHandler.class);
    protected boolean isStreamRunning = false;
    protected int hitCount = 0;
    public String pathfromFF = "";
    protected boolean startOnLoad = true;

    @SuppressWarnings("serial")
    private static final Map<String, String> mimeExt = new HashMap<String, String>() {
        {
            put("mpd", "application/dash+xml");
            put("mp4", "video/mp4");
            put("m4v", "video");
            put("m4s", "video/iso.segment");
            put("m4a", "audio/mp4");
            put("m3u8", "application/x-mpegURL");
            put("ts", "video/MP2T");
        }
    };

    public HTTPHandler() {
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

    public void ServerReady(String serverBase) {
    }

    /////////////////////////////////////////////////////////////////////////
    // Cleanup
    //
    // Called by the servlet to remove stream environments.

    public synchronized void Cleanup() {
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
    // canDelete
    //
    // Must return true if the stream can accept the GET request.

    public boolean canDelete(String pathInfo) {
        return false;
    }

    ////////////////////////////////////////////////////////////////////////
    // Poster
    //
    // Callback in response to a valid POST response sent to this endpoint

    public void Poster(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    //////////////////////////////////////////////////////////////////////////
    // Getter
    //
    // Callback in response to a GET

    public void Getter(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
    }

    /////////////////////////////////////////////////////////////////////////
    // Deleter
    //
    // Callback in response to DELETE

    public void Deleter(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND);
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
        // data over and over. This should not matter if we do this for other
        // results as well.

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
