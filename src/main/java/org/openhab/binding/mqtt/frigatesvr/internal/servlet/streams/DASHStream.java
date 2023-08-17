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

    public DASHStream(String baseURL, String ffBinary, String URLtoFF, String readerPath,
            frigateSVRCommonConfiguration config) {
        super(baseURL, ffBinary, URLtoFF, readerPath, config);

        // String fmtCmds = " -single_file 0 -use_template 1 -window_size 5 -f dash -index_correction 1 -streaming 1";
        // String fmtCmds = " -f dash -single_file 0 -window_size 4 -extra_window_size 2 -streaming 1 -remove_at_exit 1
        // "
        // + "-seg_duration 2 -frag_type duration -frag_duration 0.2 -index_correction 1 -target_latency 1 "
        // + "-ldash 1 -use_template 1 -use_timeline 0 -write_prft 1 -avioflags direct -fflags +nobuffer+flush_packets "
        // + "-format_options movflags=+cmaf ";

        String fmtCmds = " -f dash -seg_duration 1 -streaming 1 -window_size 30 -remove_at_exit 1 ";

        this.pathfromFF = readerPath + ".mpd";
        logger.info("sending stream to {}", this.pathfromFF);

        this.startOnLoad = config.ffDASHStartProducerOnLoad;

        // use PWD as prefix for now

        this.ffHelper.BuildFFMPEGCommand(ffBinary, URLtoFF, this.pathfromFF, config.ffDASHTranscodeCommands + fmtCmds,
                "./");
    }

    /////////////////////////////////////////////////////////////////////////
    // CheckStarted
    //
    // This member function is overloaded per stream type to return true
    // as soon as output has been generated.

    public boolean CheckStarted() {
        File f = new File(this.ffHelper.GetDestinationPath() + "/" + this.pathfromFF);
        logger.debug("Checking stream started: path {}", this.ffHelper.GetDestinationPath() + "/" + this.pathfromFF);
        return (f.exists() && f.isFile());
    }

    ///////////////////////////////////////////////////////////////////////
    // canPost
    //
    // HLS streams do not require ffmpeg to post directly to the server
    // for streaming - they use files. So this endpoint need not accept
    // posting.

    public boolean canPost(String pathInfo) {
        return false;
    }

    ///////////////////////////////////////////////////////////////////////
    // canAccept
    //
    // HLS streams will request the playlist (xxxx.m3u8) and the transport
    // streams (xxxx.ts). We thus limit any get requests to files of this
    // type - and only accept requests for the .ts after the .m3u8 has
    // been accessed at least once.

    public boolean canAccept(String pathInfo) {
        String pattern = "(" + this.readerPath + "(\\.mpd))";
        pattern += "|(chunk-stream\\S+)|(init-stream\\S+)";
        logger.debug("Pattern to match: |{}| against |{}|", pattern, pathInfo);
        return (pathInfo.matches(pattern)) ? true : false;
    }

    /////////////////////////////////////////////////////////////////////////
    // Getter
    //
    // If we get here, we must be asking for an .m3u8 or a .ts, and on this
    // path. We don't just send any old file. The request path has already
    // been verified.
    // We need to start the ffmpeg helper if it is not already
    // started. It will not know if the stream is no longer required;
    // the keepalives will shut it down if hitCount is zero between two
    // keepalives. With the ffmpeg options we have, we won't fill up the
    // HD as it will keep wrapping, but it may be good practice to stop
    // the producer if there is no-one listening.
    // Use the regular keepalive? This may be too long?
    //
    // However, considering an option to leave the ffmpeg converter on
    // so as to speed up initial access. This could be user-selectable.
    // The HLS stream does not transcode (let Frigate do this) so the
    // footprint of the ffmpeg process is very small, and only in the
    // stream packaging
    //
    // We also need to serialize access to this block - if we are
    // not running, then multiple clients need to wait until either
    // we are running, or have errored out in the ffmpeg starting sequence
    // The 'StartStreams' member function is serialized, and will do
    // nothing if the ffmpeg producer is already running

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
