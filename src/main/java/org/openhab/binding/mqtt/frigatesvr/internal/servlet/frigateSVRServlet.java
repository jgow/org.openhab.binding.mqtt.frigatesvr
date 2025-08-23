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
package org.openhab.binding.mqtt.frigatesvr.internal.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRMiscHelper;
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
 * @author Dr J Gow - Initial contribution
 */

@NonNullByDefault
@SuppressWarnings("serial")
public class frigateSVRServlet extends HttpServlet {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final Dictionary<Object, Object> initParameters = new Hashtable<>(Map.of("async-supported", "true"));
    private String pathServletBase = "";
    protected final HttpService httpService;
    private String whiteList = "DISABLE";
    private boolean isStarted = false;
    private static ReentrantLock svrMutex = new ReentrantLock();

    // we maintain an array of these. They are configured once at the start.

    private ArrayList<HTTPHandler> handlers = new ArrayList<HTTPHandler>();

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

    public synchronized void StartServer(String pathServletBase, ArrayList<HTTPHandler> handlers) {

        this.pathServletBase = pathServletBase;

        logger.debug("Starting server at {}", pathServletBase);

        this.handlers.clear(); // start empty
        this.handlers = handlers;

        // The svrMutex serves a different purpose despite use of 'synchronized'. For some reason,
        // on occasion, if two threads in two different 'things' try to create servlets at the
        // same time despite them being at different locations, the creation chokes.
        // I do not have the inclination to grovel through the source code to find out why.
        // The static mutex ensures that we complete one servlet creation at a time, providing we
        // are in the same context. A likely possibility is when camera 'things' and server 'things'
        // come online together. A side-effect is to delay startup if the streams are configured to be
        // running on startup.

        svrMutex.lock();
        try {
            initParameters.put("servlet-name", pathServletBase);
            httpService.registerServlet(pathServletBase, this, initParameters, httpService.createDefaultHttpContext());
            logger.debug("streaming servlet started");
            this.isStarted = true;
        } catch (Exception e) {
            logger.warn("Registering servlet failed:{}", e.getMessage());
            this.isStarted = false;
            // if we haven't started for whatever reason, stop our streams.
        }
        svrMutex.unlock();

        // if we have succeeded, then start the streams. It actually takes some time for the servlet
        // to actually start, so we can overlap this with the stream start

        if (this.isStarted) {

            // tell our streams that we are serving. We may get async GET requests
            // as soon as the server is up.

            this.handlers.forEach(strm -> {
                strm.ServerReady(pathServletBase);
            });

        } else {
            this.handlers.clear();
        }

        // Once we are running, we should not change handlers dynamically.
        // Should we need to, wrap the code that does so, together with get(), in some
        // form of semaphore/mutex. Otherwise getters and posters may choke.
    }

    ///////////////////////////////////////////////////////////////////////////
    // StopServer
    //
    // Called if the 'thing' is offlined - it allows us to stop all streaming
    // if the Frigate server is shut down, or if we reconfigure the 'thing'

    public synchronized void StopServer() {
        logger.debug("StopServer called: stopping streaming server");

        // serialize threads that destroy servlets

        svrMutex.lock();
        if (isStarted) {
            try {
                logger.debug("Stopping and unregistering server");
                httpService.unregister(pathServletBase);
                isStarted = false;
            } catch (IllegalArgumentException e) {
                logger.warn("Unregistration of servlet failed:{}", e.getMessage());
            }
        }
        svrMutex.unlock();

        // don't do this until the server has stopped, otherwise someone in
        // the middle of get() could cause concurrent access issues.

        handlers.forEach(strm -> {
            strm.Cleanup();
        });
        handlers.clear();

        // close us out

        this.destroy();
    }

    ///////////////////////////////////////////////////////////////////////////
    // doPost
    //
    // this is the input point for streams from ffmpeg that are not
    // passed through files.

    @SuppressWarnings("null")
    @Override
    protected void doPost(@Nullable HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (CheckRequest(req, resp) == true) {
            String relPath = frigateSVRMiscHelper.StripLeadingSlash(req.getPathInfo());
            handlers.forEach(strm -> {
                if (strm.canPost(relPath)) {
                    try {
                        strm.Poster(req, resp, relPath);
                    } catch (IOException e) {
                        logger.warn("POST from ffmpeg failed: stream path: {}", strm.pathfromFF);
                    }
                }
            });
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // doGet
    //
    // Connections from UI elements wishing to display the streams from
    // the Frigate server or the cameras

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        if (CheckRequest(req, resp) == true) {

            String relPath = frigateSVRMiscHelper.StripLeadingSlash((req.getPathInfo()));
            handlers.forEach(strm -> {
                if (strm.canAccept(relPath)) {
                    try {
                        strm.Getter(req, resp, relPath);
                    } catch (IOException e) {
                        logger.warn("getter failed for stream at path: {}", strm.pathfromFF);
                    }
                }
            });
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // doDelete
    //
    // Handle the DELETE method - used for the Frigate API forwarder

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (CheckRequest(req, resp) == true) {

            String relPath = frigateSVRMiscHelper.StripLeadingSlash((req.getPathInfo()));
            handlers.forEach(strm -> {
                if (strm.canDelete(relPath)) {
                    try {
                        strm.Deleter(req, resp, relPath);
                    } catch (IOException e) {
                        logger.warn("deleter failed for stream at path: {}", strm.pathfromFF);
                    }
                }
            });
        }
    }

    /////////////////////////////////////////////////////////////////
    // CheckRequest
    //
    // Deals with the common functionality to check validity of
    // requests

    private boolean CheckRequest(@Nullable HttpServletRequest req, @Nullable HttpServletResponse resp)
            throws IOException {
        boolean valid = false;
        do {
            if (req == null || resp == null) {
                break;
            }
            if (req.getPathInfo() == null) {
                break;
            }
            // this whitelist methodology is the same as from 'ipcamera' - it
            // seems sensible to keep this consistent. However, we always make
            // certain that our localhost is in the whitelist, otherwise the
            // forwarder would screw up.

            if (!whiteList.equals("DISABLE")) {
                String requestIP = "(" + req.getRemoteHost() + ")(127.0.0.1)";
                if (!whiteList.contains(requestIP)) {
                    logger.warn("{} was not in the whitelist and will be ignored.", requestIP);
                    break;
                }
            }
            logger.debug("validity checks complete: {}", req.getRemoteHost());
            valid = true;
        } while (false);
        return valid;
    }

    /////////////////////////////////////////////////////////////////
    // PokeMe
    //
    // Used by the keepalive mechanism. Here, we only need to
    // check our ffmpeg processes if we have open streams

    public void PokeMe() {
        handlers.forEach(strm -> {
            strm.PokeMe();
        });
    }
}
