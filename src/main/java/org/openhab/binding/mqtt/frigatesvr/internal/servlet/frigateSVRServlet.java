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

//import static org.openhab.binding.ipcamera.internal.IpCameraBindingConstants.HLS_STARTUP_DELAY_MS;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRFFmpegHelper;
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
 * @author Matthew Skinner - Initial contribution (to ipcamera binding)
 * @author Dr J Gow - imported from ipcamera to frigateSVR, substantially modified to suit
 *         requirements of Frigate.
 */
@NonNullByDefault
public class frigateSVRServlet extends HttpServlet {

    private Logger logger = LoggerFactory.getLogger(this.getClass());
    private static final long serialVersionUID = -134658667574L;
    private static final Dictionary<Object, Object> initParameters = new Hashtable<>(Map.of("async-supported", "true"));
    private String pathServletBase = "";
    private String pathReaderSuffix = "";
    protected final HttpService httpService;
    private frigateSVRFFmpegHelper ffmpegHelper = new frigateSVRFFmpegHelper();
    private String whiteList = "DISABLE";
    private boolean isStarted = false;

    public OpenStreams openStreams = new OpenStreams();

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

    public void StartServer(String pathServletBase, String pathReaderSuffix, String ffmpegPath, String sourceURL,
            String ffmpegDestinationURL, String ffmpegCommands) {
        this.pathServletBase = pathServletBase;
        this.pathReaderSuffix = pathReaderSuffix;
        logger.info("Starting server at {} for {}", pathServletBase, pathReaderSuffix);
        this.ffmpegHelper.BuildFFMPEGCommand(ffmpegPath, sourceURL, ffmpegDestinationURL, ffmpegCommands);
        try {
            initParameters.put("servlet-name", pathServletBase);
            httpService.registerServlet(pathServletBase, this, initParameters, httpService.createDefaultHttpContext());
            this.isStarted = true;
        } catch (Exception e) {
            logger.warn("Registering servlet failed:{}", e.getMessage());
            this.isStarted = false;
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // StopServer
    //
    // Called if the 'thing' is offlined - it allows us to stop all streaming
    // if the Frigate server is shut down, or if we reconfigure the 'thing'

    public void StopServer() {
        openStreams.closeAllStreams();
        ffmpegHelper.StopStream();
        if (isStarted) {
            try {
                httpService.unregister(pathServletBase);
                this.destroy();
                isStarted = false;
            } catch (IllegalArgumentException e) {
                logger.warn("Unregistration of servlet failed:{}", e.getMessage());
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // doPost
    //
    // ffmpeg sends us the transcoded rtsp stream here, as a sequence of .jpg.

    @Override
    protected void doPost(@Nullable HttpServletRequest req, @Nullable HttpServletResponse resp) throws IOException {
        if (req == null || resp == null) {
            return;
        }
        String pathInfo = req.getPathInfo();
        if (pathInfo == null) {
            return;
        }
        if (pathInfo.equals("/frigate-in.jpg")) {
            ServletInputStream snapshotData = req.getInputStream();
            openStreams.queueFrame(snapshotData.readAllBytes());
            snapshotData.close();
        }
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
        logger.info("GET: received from {}/{}", req.getRemoteHost(), pathInfo);
        if (!whiteList.equals("DISABLE")) {
            String requestIP = "(" + req.getRemoteHost() + ")";
            if (!whiteList.contains(requestIP)) {
                logger.warn("The request made from {} was not in the whiteList and will be ignored.", requestIP);
                return;
            }
        }
        logger.debug("whitelist checks complete: {}", req.getRemoteHost());

        // From Frigate, we only export mjpeg. If you want anything different, this can come directly
        // from the Frigate backend. This is only for convenience

        if (pathInfo.equals(pathReaderSuffix)) {
            StreamOutput output = new StreamOutput(resp);
            if (openStreams.isEmpty()) {
                logger.info("Starting ffmpeg stream");
                ffmpegHelper.StartStream();
            } else {
                // make sure we are still running
                ffmpegHelper.PokeMe();
            }
            openStreams.addStream(output);
            do {
                try {
                    output.sendFrame();
                } catch (InterruptedException | IOException e) {

                    // Browser closed the stream:
                    openStreams.removeStream(output);

                    // we must shut down the ffmpeg processes here if there are no more streams

                    logger.debug("{} frigateSVR mjpeg reader streams remain open.", openStreams.getNumberOfStreams());

                    if (openStreams.isEmpty()) {
                        ffmpegHelper.StopStream();
                        logger.info("all mjpeg reader streams have stopped.");
                    }

                    return;
                }
            } while (!openStreams.isEmpty());
        }
    }

    /////////////////////////////////////////////////////////////////
    // PokeMe
    //
    // Used by the keepalive mechanism. Here, we only need to
    // check our ffmpeg processes if we have open streams

    public void PokeMe() {

        if (!openStreams.isEmpty()) {
            ffmpegHelper.PokeMe();
        }
    }
}
