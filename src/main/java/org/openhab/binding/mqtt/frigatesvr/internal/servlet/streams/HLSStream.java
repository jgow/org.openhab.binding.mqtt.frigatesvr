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
import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRCommonConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link HLSStream} encapsulates a served HLS stream
 *
 * @author Dr J Gow - initial contribution
 */
@NonNullByDefault
public class HLSStream extends StreamTypeBase {

    private final Logger logger = LoggerFactory.getLogger(HLSStream.class);

    public HLSStream(String readerPath, String ffBinary, String URLtoFF, frigateSVRCommonConfiguration config) {
        super(readerPath, ffBinary, URLtoFF, config);

        String fmtCmds = " -f hls -hls_flags delete_segments -hls_time 4 -hls_list_size 6";
        this.pathfromFF = readerPath + ".m3u8";
        logger.debug("stream entry point set to {}", this.pathfromFF);

        this.startOnLoad = config.ffHLSStartProducerOnLoad;

        // use PWD as prefix for now

        this.ffHelper.BuildFFMPEGCommand(ffBinary, URLtoFF, this.pathfromFF, config.ffHLSTranscodeCommands + fmtCmds,
                config.ffTempDir);
    }

    /////////////////////////////////////////////////////////////////////////
    // CheckStarted
    //
    // This member function is overloaded per stream type to return true
    // as soon as output has been generated.

    @Override
    public boolean CheckStarted() {
        File f = new File(this.ffHelper.GetDestinationPath() + "/" + this.pathfromFF);
        return (f.exists() && f.isFile());
    }

    ///////////////////////////////////////////////////////////////////////
    // canAccept
    //
    // HLS streams will request the playlist (xxxx.m3u8) and the transport
    // streams (xxxx.ts). We thus limit any get requests to files of this
    // type - and ideally only accept requests for the .ts after the .m3u8
    // has been accessed at least once (not yet implemented except where
    // stream is started dynamically

    @Override
    public boolean canAccept(String pathInfo) {
        String pattern = this.readerPath + "((\\d+\\.ts)|(\\.m3u8))";
        logger.debug("Pattern to match: |{}| against |{}|", pattern, pathInfo);
        return (pathInfo.matches(pattern)) ? true : false;
    }

    /////////////////////////////////////////////////////////////////////////
    // Getter
    //
    // If we get here, we must be asking for an .m3u8 or a .ts, and on this
    // path. We don't just send any old file. The request path has already
    // been verified.
    //
    // We need to start the ffmpeg helper if it is not already
    // started. It will not know if the stream is no longer required;
    // the keepalives will shut it down if hitCount is zero between two
    // keepalives. With the ffmpeg options we have, we won't fill up the
    // HD as it will keep wrapping, but unless the option is set to start
    // streams at server start, then we can stop the producer if there is
    // no-one listening. Use the regular keepalive to check the hitcount
    // if the stream is set to start dynamically.
    //
    // We also need to mutex access to the stream start if not
    // already started. If we are not running, then multiple clients need
    // to wait until either we are running, or have errored out in the
    // ffmpeg starting sequence The 'StartStreams' member function has
    // serialized access, and will do nothing if the ffmpeg producer
    // is already running

    @Override
    public void Getter(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {

        logger.debug("processing HLS get request");

        // a hit is either the playlist or a ts request

        hitCount++;

        // note when we get here, pathInfo is trimmed and has any leading
        // slashes chopped off. We have also verified that this call
        // is for us (via the regex in canAccept we only allow requests
        // for an .m3u8 or a .ts (and the latter only if we are running)

        if (pathInfo.equals(this.readerPath + ".m3u8")) {

            // someone wants the playlist..

            StartStreams();
        }

        // Then just serve the file. It will be either the m3u8 or a
        // numbered .ts. It will respond with error to the client
        // if the file not found. However, we send only if the stream
        // is running by this point.

        if (this.isStreamRunning) {
            this.SendFile(resp, this.ffHelper.GetDestinationPath() + "/" + pathInfo, "");
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
