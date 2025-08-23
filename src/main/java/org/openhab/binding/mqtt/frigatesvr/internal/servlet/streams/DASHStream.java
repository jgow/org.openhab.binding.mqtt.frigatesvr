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
 * The {@link DASHStream} encapsulates a served DASH-MPD stream
 *
 * @author Dr J Gow - initial contribution
 */
@NonNullByDefault
public class DASHStream extends StreamTypeBase {

    private final Logger logger = LoggerFactory.getLogger(DASHStream.class);

    public DASHStream(String readerPath, String ffBinary, String URLtoFF, frigateSVRCommonConfiguration config) {
        super(readerPath, ffBinary, URLtoFF, config);

        // String fmtCmds = " -single_file 0 -use_template 1 -window_size 5 -f dash -index_correction 1 -streaming 1";
        // String fmtCmds = " -f dash -single_file 0 -window_size 4 -extra_window_size 2 -streaming 1 -remove_at_exit 1
        // "
        // + "-seg_duration 2 -frag_type duration -frag_duration 0.2 -index_correction 1 -target_latency 1 "
        // + "-ldash 1 -use_template 1 -use_timeline 0 -write_prft 1 -avioflags direct -fflags +nobuffer+flush_packets "
        // + "-format_options movflags=+cmaf ";

        String fmtCmds = " -f dash -window_size 30 -remove_at_exit 1 " + config.ffDASHPackagingCommands;

        this.pathfromFF = readerPath + ".mpd";
        logger.debug("sending stream to {}", this.pathfromFF);

        this.startOnLoad = config.ffDASHStartProducerOnLoad;

        // use PWD as prefix for now

        this.ffHelper.BuildFFMPEGCommand(ffBinary, URLtoFF, this.pathfromFF, config.ffDASHTranscodeCommands + fmtCmds,
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
        logger.debug("Checking stream started: path {}", this.ffHelper.GetDestinationPath() + "/" + this.pathfromFF);
        return (f.exists() && f.isFile());
    }

    ///////////////////////////////////////////////////////////////////////
    // canAccept
    //
    // DASH streams will request the playlist (xxxx.mpd), the chunk-init
    // and chunk-streams for each segment

    @Override
    public boolean canAccept(String pathInfo) {
        String pattern = "(" + this.readerPath + "(\\.mpd))";
        pattern += "|(chunk-stream\\S+)|(init-stream\\S+)";
        logger.debug("Pattern to match: |{}| against |{}|", pattern, pathInfo);
        return (pathInfo.matches(pattern)) ? true : false;
    }

    /////////////////////////////////////////////////////////////////////////
    // Getter
    //
    // If we get here, we must be asking for an .mpd, a chunk-stream or a
    // chunk-init, and on this path. We don't just send any old file. The
    // request path has already been verified.
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

        logger.debug("processing DASH get request");

        // a hit is a request for any of the components

        hitCount++;

        // note when we get here, pathInfo is trimmed and has any leading
        // slashes chopped off. We have also verified that this call
        // is for us (via the regex in canAccept we only allow requests
        // for an .m3u8 or a .ts (and the latter only if we are running)

        if (pathInfo.equals(this.readerPath + ".mpd")) {

            // someone wants the master playlist..

            StartStreams();
        }

        // Serve either the playlist, or one of the segment files

        if (this.isStreamRunning) {
            this.SendFile(resp, this.ffHelper.GetDestinationPath() + "/" + pathInfo, "");
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }
}
