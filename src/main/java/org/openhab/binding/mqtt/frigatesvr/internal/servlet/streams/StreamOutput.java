/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link StreamOutput} Streams mjpeg out to a client
 *
 * @author Matthew Skinner - Initial contribution (to ipcamera)
 * @author Dr J Gow - imported from ipcamera with some redundant functions removed
 */

@NonNullByDefault
public class StreamOutput {
    public final Logger logger = LoggerFactory.getLogger(getClass());
    private final HttpServletResponse response;
    private String boundary = new String();
    private String contentType = new String();
    private final ServletOutputStream output;
    private BlockingQueue<byte[]> fifo = new ArrayBlockingQueue<byte[]>(50);
    private boolean connected = false;
    public boolean isSnapshotBased = false;

    public StreamOutput(HttpServletResponse response, String contentType) throws IOException {
        this.response = response;
        output = response.getOutputStream();
        if (contentType.equals("video/x-motion-jpeg") || contentType.isEmpty()) {
            boundary = "thisMjpegStream";
            this.contentType = "multipart/x-mixed-replace; boundary=" + boundary;
            isSnapshotBased = true;
        } else {
            this.contentType = contentType;
            sendInitialHeaders();
            connected = true;
        }
    }

    public void sendSnapshotBasedFrame(byte[] currentSnapshot) throws IOException {
        String header = "--" + boundary + "\r\n" + "Content-Type: image/jpeg" + "\r\n" + "Content-Length: "
                + currentSnapshot.length + "\r\n\r\n";
        if (!connected) {
            sendInitialHeaders();
            // iOS needs to have two jpgs sent for the picture to appear instantly.
            output.write(header.getBytes());
            output.write(currentSnapshot);
            output.write("\r\n".getBytes());
            connected = true;
        }
        output.write(header.getBytes());
        output.write(currentSnapshot);
        output.write("\r\n".getBytes());
    }

    public void queueFrame(byte[] frame) {
        try {
            fifo.add(frame);
        } catch (IllegalStateException e) {
            logger.debug("FIFO buffer has run out of space:{}", e.getMessage());
            fifo.remove();
            fifo.add(frame);
        }
    }

    public void sendFrame() throws IOException, InterruptedException {
        if (isSnapshotBased) {
            sendSnapshotBasedFrame(fifo.take());
        } else if (connected) {
            output.write(fifo.take());
        }
    }

    private void sendInitialHeaders() {
        response.setContentType(this.contentType);
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Expose-Headers", "*");
    }

    public void close() {
        try {
            output.close();
        } catch (IOException e) {
        }
    }
}
