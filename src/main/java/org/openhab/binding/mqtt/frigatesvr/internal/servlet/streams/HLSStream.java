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
import java.io.FileFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
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
    private int hitCount = 0;
    private boolean isStreamRunning = false;
    private Path strmDir;

    public HLSStream(String baseURL, String ffBinary, String URLtoFF, String ffcmds, String readerPath) {
        super(baseURL, ffBinary, URLtoFF, ffcmds, readerPath);
        // create a subdir for this stream type so we don't splatter files everywhere
        try {
            strmDir = Files.createTempDirectory("HLS");
            strmDir.toFile().deleteOnExit();
            logger.info("created working path {}", strmDir.toString());
        } catch (IOException e) {
            logger.warn("can not create stream dir - using current working area");
            strmDir = Paths.get("");
        }

        this.pathfromFF = strmDir.toString() + "/" + readerPath + ".m3u8";
        logger.info("sending stream to {}", this.pathfromFF);
        this.ffHelper.BuildFFMPEGCommand(ffBinary, URLtoFF, this.pathfromFF, ffcmds);

        // lose any old cruft left behind

        DeleteStreamFiles();
    }

    /////////////////////////////////////////////////////////////////////////
    // StartStreams
    //
    // To start the streams, if we are not already running. This could come
    // in on multiple contexts - so use a context lock

    public synchronized void StartStreams() {

        // if our ffmpeg process is already running, we need
        // do nothing.

        if (!this.ffHelper.isRunning()) {

            // otherwise we need to start it and wait for it to start

            this.ffHelper.StartStream();

            // Right then: now wait until ffmpeg has created the m3u8 file
            // and that it is available, otherwise we may get browser error
            // messages. Need to have this online before the browser timeouts
            // This can take some time. While this code doesn't actually block,
            // it could sit in the loop up to 15 seconds in order to get
            // ffmpeg started.

            int count = 0;
            do {
                logger.info("waiting 1000ms for .m3u8 to appear");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    break;
                }
                File f = new File(this.pathfromFF);
                if (f.exists() && f.isFile()) {
                    logger.info("m3u8 exists");

                    // guarantees we always wait one timeout interval
                    // once the stream is marked 'running'

                    hitCount = 1;
                    isStreamRunning = true;

                    break;
                } else {
                    if (count++ == 15) {
                        isStreamRunning = false;
                        logger.warn("ffmpeg start failed");
                        this.StopStreams();
                        break;
                    }
                }
            } while (true);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // StopStreams
    //
    // Overloaded to delete the files once the relevant stream is stopped

    @Override
    public void StopStreams() {
        super.StopStreams();
        DeleteStreamFiles();
        isStreamRunning = false;
    }

    /////////////////////////////////////////////////////////////////////////
    // PokeMe
    //
    // Overloaded from the base in order to verify the hitcount. If we get
    // no hits between keepalives, then shut down the stream. However, it can
    // take a while for the ffmpeg producer to start loading files, so
    // do not check the hit count unless the ffmpeg process has written
    // the playlist.

    @Override
    public void PokeMe() {
        if (isStreamRunning) {
            // no-one has requested the playlist between now and the last
            // keepalive. Assume we're not wanted, so go and eat worms.
            if (hitCount == 0) {
                logger.info("no further requestors; shutting down stream");
                StopStreams();
            }
        }
        hitCount = 0;
        this.ffHelper.PokeMe();
    }

    ///////////////////////////////////////////////////////////////////////
    // canPost
    //
    // HLS streams do not require ffmpeg to post directly to the server
    // for streaming - they use files. So this endpoint need not accept
    // posting.

    public boolean canPost(String pathInfo) {
        return false; // no posting needed
    }

    ///////////////////////////////////////////////////////////////////////
    // canAccept
    //
    // HLS streams will request the playlist (xxxx.m3u8) and the transport
    // streams (xxxx.ts). We thus limit any get requests to files of this
    // type - and only accept requests for the .ts after the .m3u8 has
    // been accessed at least once.

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

    public void Getter(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {

        logger.debug("processing HLS get request");
        String contentType;

        // note when we get here, pathInfo is trimmed and has any leading
        // slashes chopped off. We have also verified that this call
        // is for us (via the regex in canAccept we only allow requests
        // for an .m3u8 or a .ts (and the latter only if we are running)

        if (pathInfo.equals(this.readerPath + ".m3u8")) {

            // someone wants the playlist..

            hitCount++;

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

            StartStreams();

            // playlist type

            contentType = "application/x-mpegURL";

        } else {

            // transport stream
            contentType = "video/MP2T";

        }

        // Then just serve the file. It will be either the m3u8 or a
        // numbered .ts. It will respond with error to the client
        // if the file not found. However, we send only if the stream
        // is running by this point.

        if (isStreamRunning) {
            this.SendFile(resp, strmDir.toString() + "/" + pathInfo, contentType);
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // DeleteStreamFiles
    //
    // Helper function to delete any and all stream files so we can clean
    // up after ourselves

    private void DeleteStreamFiles() {
        File path = strmDir.toFile();
        if (path.isDirectory()) {
            File[] deleteMe = path.listFiles(new FileFilter() {
                public boolean accept(@Nullable File file) {
                    if (file != null) {
                        if (file.isFile()) {
                            return (file.getName().endsWith(".m3u8") || file.getName().endsWith(".ts"));
                        }
                    }
                    return false;
                }
            });

            for (File del : deleteMe) {
                logger.info("deleted {}", del.getPath().toString());
                del.delete();
            }
        } else {
            logger.info("stream file dir not present");
        }
    }
}
