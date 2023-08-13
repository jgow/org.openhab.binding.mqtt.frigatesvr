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
package org.openhab.binding.mqtt.frigatesvr.internal.helpers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link mqtt.frigateSVRFFmpegHelper} is a helper class allowing ffmpeg to re-stream
 * the H264 streams coming from Frigate to MJPEG, in order that they can be displayed on
 * openHAB UIs
 * 
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRFFmpegHelper {

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private @Nullable Process process = null;
    private List<String> ffmpegargs = new ArrayList<String>();
    private boolean isStarted = false;
    private ExecutorService es = Executors.newSingleThreadExecutor();
    private Path tmpDir;

    public frigateSVRFFmpegHelper() {
        this.tmpDir = Paths.get("");
    }

    ////////////////////////////////////////////////////////////////////////
    // LogEater
    //
    // The LogEater is a process that pulls the stderr from ffmpeg for
    // debugging purposes, and also because ffmpeg seems to block if it
    // can't write stderr. If the ffmpeg process is not running, it wil
    // simply exit, and it will exit automatically if the ffmpeg process
    // exits (or is killed).

    private Runnable LogEater = () -> {
        if (process != null) {
            if (((@NonNull Process) process).isAlive()) {
                InputStream errorStream = ((@NonNull Process) process).getErrorStream();
                InputStreamReader errorStreamReader = new InputStreamReader(errorStream);
                BufferedReader bufferedReader = new BufferedReader(errorStreamReader);
                String line = null;
                try {
                    while ((line = bufferedReader.readLine()) != null) {
                        logger.debug("ffmpeg: {}", line);
                    }
                } catch (IOException e) {
                    logger.warn("exception in log eater: {}", e.getMessage());
                }
                logger.info("no further output from ffmpeg");
            }
        }
        logger.info("log-eater process exiting");
    };

    ////////////////////////////////////////////////////////////
    // BuildFFMPEGCommand
    //
    // Construct a command string for the FFMPEG process

    public void BuildFFMPEGCommand(String ffmpegLocation, String sourceURL, String destination, String ffmpegCommands,
            @Nullable String prefix) {

        // Build the temporary working dir, if needed

        CreateDestinationEnv(prefix);
        Path finalPath = this.tmpDir.resolve(destination);

        logger.info("destination for stream {}", finalPath.toString());
        logger.info("ffmpeg binary: {}", ffmpegLocation);
        logger.info("MJPEG options : {}", ffmpegCommands);

        ffmpegargs.clear();
        ffmpegargs.add(ffmpegLocation);
        String arglist = new String("-rtsp_transport tcp -hide_banner -i ");
        arglist += sourceURL + " " + ffmpegCommands;
        Collections.addAll(ffmpegargs, arglist.trim().split("\\s+"));
        ffmpegargs.add(finalPath.toString());
        logger.debug("FFMPEG command :{}", ffmpegargs.toString());
        ffmpegargs.forEach(n -> {
            logger.debug("FFMPEG command: {}", n);
        });
    }

    /////////////////////////////////////////////////////////////
    // StartStream
    //
    // Start the ffmpeg process.

    public boolean StartStream() {
        if (process == null) {
            try {
                logger.info("ffmpeg stream process starting");
                process = Runtime.getRuntime().exec(ffmpegargs.toArray(new String[ffmpegargs.size()]));
                // FFmpeg will block if we don't keep the stdout flushed - so we
                // have a little thread that brings ffmpeg output to the logs -
                // that way we can debug any issues in ffmpeg.
                //
                // now start the log-eater. The log-eater process will exit
                // automatically if the ffmpeg process quits and ceases to be active
                es.execute(LogEater);
                isStarted = true;
                logger.info("ffmpeg stream process running");
            } catch (IOException e) {
                logger.error("Unable to start ffmpeg:");
                process = null;
                isStarted = false;
            }
        }
        return isStarted;
    }

    /////////////////////////////////////////////////////////////
    // StopStream
    //
    // Stops the ffmpeg process, effectively by killing it.

    public void StopStream() {
        if (process != null) {
            ((@NonNull Process) process).destroyForcibly();
            process = null;
            isStarted = false;
        }
        // make sure we're clean in case ffmpeg crashed.
        DeleteStreamFiles();
    }

    ////////////////////////////////////////////////////////////
    // PokeMe
    //
    // Used by the keepalive mechanism - if we are meant to have
    // a running ffmpeg process and it has crashed/been killed,
    // (but not intentionally stopped) then attempt to restart us

    public void PokeMe() {
        do {
            if (!isStarted) {
                break;
            }
            if (process != null) {
                if (((@NonNull Process) process).isAlive()) {
                    break;
                }
            }
            // we must restart
            logger.info("keepalive: forcing restart");
            process = null;
            StartStream();
        } while (false);
    }

    ////////////////////////////////////////////////////////
    // isRunning
    //
    // Returns true if the ffmpeg process is still running

    public boolean isRunning() {
        return (process == null) ? false : (((@NonNull Process) process).isAlive()) ? true : false;
    }

    //////////////////////////////////////////////////////////////////////////
    // GetDestinationPath
    //
    // Get path to destination files (if files are being used)

    public String GetDestinationPath() {
        return this.tmpDir.toString();
    }

    //////////////////////////////////////////////////////////////////////////
    // CreateDestinationEnv
    //
    // Create the temporary working directory to store stream files
    // if the prefix is null, then do not create anything - this will
    // be used for local HTTP posted output, or files in the local
    // storage area.
    // Otherwise add a temporary path to 'prefix'. If prefix=".",
    // this will create the temporary path in the local working area
    // If prefix contains a relative path, a temporary directory will
    // be created it it doesn't exist
    // If prefix contains an absolute path, the temp directory
    // will be created at the end of the path.
    //

    private void CreateDestinationEnv(@Nullable String prefix) {
        if (prefix != null) {
            Path pathPrefix = Paths.get(prefix);
            try {
                this.tmpDir = Files.createTempDirectory(pathPrefix, "STM");
                this.tmpDir.toFile().deleteOnExit();
                logger.info("created working path {}", this.tmpDir.toString());
            } catch (IOException e) {
                logger.warn("can not create tmpdir on prefix {} - attempting to create tmpdir in current working area",
                        prefix);
                try {
                    pathPrefix = Paths.get("");
                    this.tmpDir = Files.createTempDirectory(pathPrefix, "STM");
                    this.tmpDir.toFile().deleteOnExit();
                    logger.info("created working path {}", this.tmpDir.toString());
                } catch (IOException e2) {
                    logger.warn("could not create temporary dir in working area");
                    this.tmpDir = Paths.get("");
                }
            }
        } else {
            // we do not need a destination environment.
            this.tmpDir = Paths.get("");
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // Cleanup
    //
    // Ensures all streams are shut down, and delete the environment

    public void Cleanup() {
        StopStream();
        DeleteStreamFiles();
        if (!this.tmpDir.toString().equals("")) {
            logger.info("cleaning up temporary dir {}", this.tmpDir.toString());
            try {
                Files.delete(this.tmpDir);
            } catch (IOException e) {
                logger.warn("unable to delete tmpdir {}, not empty", this.tmpDir.toString());
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // DeleteStreamFiles
    //
    // Helper function to delete any and all stream files so we can clean
    // up after ourselves

    private void DeleteStreamFiles() {
        // we only attempt to delete files if we don't have a prefix
        if (!this.tmpDir.toString().equals("")) {
            File path = this.tmpDir.toFile();
            if (path.isDirectory()) {
                File[] deleteMe = path.listFiles(new FileFilter() {
                    public boolean accept(@Nullable File file) {
                        if (file != null) {
                            if (file.isFile()) {
                                return true;
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
}
