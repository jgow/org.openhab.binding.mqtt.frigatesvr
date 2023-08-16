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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRMiscHelper;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams.DASHStream;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams.HLSStream;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams.MJPEGStream;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams.StreamTypeBase;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRCommonConfiguration;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link frigateSVRServlet} is responsible for serving transcoded rtsp streams from Frigate
 * back to the local server, from whence it can easily be distributed to UI elements. Note that
 * care should be taken as to which camera re-stream from Frigate is used, as transcoding the
 * full high-resolution streams will cane the CPU and network. Better to use the detection
 * streams, as these have a lower framerate. It can also be used to stream the birdseye view
 * at the Frigate server level.
 *
 * @author Dr J Gow - Written from ground-up to handle requirements of FrigateSVR
 */
@NonNullByDefault
public class frigateSVRServlet extends HttpServlet {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final long serialVersionUID = -134658667574L;
    private static final Dictionary<Object, Object> initParameters = new Hashtable<>(Map.of("async-supported", "true"));
    private String pathServletBase = "";
    protected final HttpService httpService;
    private String whiteList = "DISABLE";
    private boolean isStarted = false;

    // we maintain an array of these. They are configured once at the start.

    private ArrayList<StreamTypeBase> streamTypes = new ArrayList<StreamTypeBase>();

    ///////////////////////////////////////////////////////////////////////////
    // constructor does not start the servlet, just notes the location of our
    // http service

    public frigateSVRServlet(HttpService httpService) {
        this.httpService = httpService;
    }

    ///////////////////////////////////////////////////////////////////////////
    // SetWhiteList
    //
    // Can be called at any time if the list is reconfigured - will apply to
    // new connections.

    public void SetWhitelist(String whiteList) {
        this.whiteList = whiteList;
    }

    ///////////////////////////////////////////////////////////////////////////
    // StartServer
    //
    // Initialize the server. We only do this once we have an onlined 'thing',
    // whether server or camera.

    public void StartServer(String pathServletBase, String pathReaderSuffix, String sourceURL, String ffPath,
            frigateSVRCommonConfiguration config) {

        this.pathServletBase = pathServletBase;

        logger.info("Starting server at {} for {}", pathServletBase, pathReaderSuffix);

        streamTypes.clear(); // start empty
        streamTypes.add(new MJPEGStream(pathServletBase, ffPath, sourceURL, pathReaderSuffix, config));
        streamTypes.add(new HLSStream(pathServletBase, ffPath, sourceURL, pathReaderSuffix, config));
        streamTypes.add(new DASHStream(pathServletBase, ffPath, sourceURL, pathReaderSuffix, config));

        // streamTypes.add(new HLSStream(pathServletBase, ffPath, sourceURL,
        // "-acodec copy -vcodec copy -movflags frag_keyframe+empty_moov -f mp4", "video/mp4",
        // pathReaderSuffix + ".mp4"));

        // Once we are running, we should not change streamTypes dynamically.
        // Should we need to, wrap the code that does so, together with get(), in some
        // form of semaphore/mutex. Otherwise getters and posters may choke.

        try {
            initParameters.put("servlet-name", pathServletBase);
            httpService.registerServlet(pathServletBase, this, initParameters, httpService.createDefaultHttpContext());
            this.isStarted = true;
        } catch (Exception e) {
            logger.warn("Registering servlet failed:{}", e.getMessage());
            this.isStarted = false;
        }

        // tell our streams that we are serving

        streamTypes.forEach(strm -> {
            strm.ServerReady();
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // StopServer
    //
    // Called if the 'thing' is offlined - it allows us to stop all streaming
    // if the Frigate server is shut down, or if we reconfigure the 'thing'

    public void StopServer() {
        logger.info("StopServer called: stopping streaming server");
        streamTypes.forEach(strm -> {
            strm.Cleanup();
        });
        if (isStarted) {
            try {
                logger.info("Stopping and unregistering server");
                httpService.unregister(pathServletBase);
                this.destroy();
                isStarted = false;
            } catch (IllegalArgumentException e) {
                logger.warn("Unregistration of servlet failed:{}", e.getMessage());
            }
        }
        // don't do this until the server has stopped, otherwise someone in
        // the middle of get() could cause concurrent access issues.

        streamTypes.clear();
    }

    ///////////////////////////////////////////////////////////////////////////
    // doPost
    //
    // this is the input point for streams from ffmpeg that are not
    // passed through files.

    @Override
    protected void doPost(@Nullable HttpServletRequest req, @Nullable HttpServletResponse resp) throws IOException {
        if (req == null || resp == null) {
            return;
        }
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            return;
        }
        String relPath = (@NonNull String) (frigateSVRMiscHelper.StripLeadingSlash(pathInfo));
        streamTypes.forEach(strm -> {
            if (strm.canPost(relPath)) {
                try {
                    strm.Poster(req, resp, relPath);
                } catch (IOException e) {
                    logger.warn("POST from ffmpeg failed: stream path: {}", strm.pathfromFF);
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // doGet
    //
    // Connections from UI elements wishing to display the streams from
    // the Frigate server or the cameras

    @Override
    protected void doGet(@Nullable HttpServletRequest req, @Nullable HttpServletResponse resp) throws IOException {
        if (req == null || resp == null) {
            return;
        }
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            return;
        }
        logger.info("GET received from {}/{}", req.getRemoteHost(), pathInfo);
        if (!whiteList.equals("DISABLE")) {
            String requestIP = "(" + req.getRemoteHost() + ")";
            if (!whiteList.contains(requestIP)) {
                logger.warn("{} was not in the whiteList and will be ignored.", requestIP);
                return;
            }
        }
        logger.debug("whitelist checks complete: {}", req.getRemoteHost());

        String relPath = (@NonNull String) (frigateSVRMiscHelper.StripLeadingSlash(pathInfo));
        streamTypes.forEach(strm -> {
            logger.info("getter: request: {} checking stream {}", relPath, strm.readerPath);
            if (strm.canAccept(relPath)) {
                try {
                    strm.Getter(req, resp, relPath);
                } catch (IOException e) {
                    logger.warn("getter failed for stream at path: {}", strm.pathfromFF);
                }
            }
        });
    }

    /////////////////////////////////////////////////////////////////
    // PokeMe
    //
    // Used by the keepalive mechanism. Here, we only need to
    // check our ffmpeg processes if we have open streams

    public void PokeMe() {
        streamTypes.forEach(strm -> {
            strm.PokeMe();
        });
    }
}
