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

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRMiscHelper;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.HTTPHandler;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRServerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link FrigateAPIForwarded} encapsulates a simple forwarder to the
 * Frigate API from the local IP address of the openHAB instance.
 *
 * @author Dr J Gow - initial contribution
 */
@NonNullByDefault
public class FrigateAPIForwarder extends HTTPHandler {

    private final Logger logger = LoggerFactory.getLogger(FrigateAPIForwarder.class);
    private String prefix = "";
    private String fgServerURL = "";

    public FrigateAPIForwarder(String prefix, frigateSVRServerConfiguration config) {
        super();
        this.prefix = prefix;
        this.fgServerURL = frigateSVRMiscHelper.StripTrailingSlash(config.serverURL);
    }

    ///////////////////////////////////////////////////////////////////////
    // canPost
    //
    // We can post from anything in our prefix

    public boolean canPost(String pathInfo) {
        logger.info("canPost: pathInfo: {} prefix: {}", pathInfo, this.prefix);
        return pathInfo.startsWith(this.prefix);
    }

    ///////////////////////////////////////////////////////////////////////
    // canAccept
    //
    // We accept anything that starts with our prefix.

    public boolean canAccept(String pathInfo) {
        logger.info("canAccept: pathInfo: {} prefix: {}", pathInfo, this.prefix);
        return pathInfo.startsWith(this.prefix);
    }

    ///////////////////////////////////////////////////////////////////////
    // canDelete
    //
    // We accept anything that starts with our prefix.

    public boolean canDelete(String pathInfo) {
        logger.info("canDelete: pathInfo: {} prefix: {}", pathInfo, this.prefix);
        return pathInfo.startsWith(this.prefix);
    }

    ////////////////////////////////////////////////////////////////////////
    // Poster
    //
    // Callback in response to a valid POST response sent to this endpoint

    @SuppressWarnings("null")
    public void Poster(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {

        logger.debug("processing API forwarder request - POST");

        try {
            URL url = BuildURL(pathInfo, req);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // copy the request headers over

            @Nullable
            Enumeration<String> allHeaders = req.getHeaderNames();
            if (allHeaders != null) {
                while (allHeaders.hasMoreElements() == true) {
                    String header = allHeaders.nextElement();
                    @Nullable
                    Enumeration<String> values = req.getHeaders(header);
                    while (values.hasMoreElements()) {
                        connection.addRequestProperty(header, values.nextElement());
                    }
                }
            }

            connection.setRequestMethod("POST");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.connect();

            // send the data block

            req.getInputStream().transferTo(connection.getOutputStream());

            // do we need to keep the returned headers? Probably

            int idx = 0;
            do {
                String header = connection.getHeaderFieldKey(idx);
                if (header == null) {
                    break;
                }
                String value = connection.getHeaderField(idx);
                logger.info("adding header {}: {}", header, value);
                resp.setHeader(header, value);
                idx++;
            } while (true);

            // POST can send us something back.

            connection.getInputStream().transferTo(resp.getOutputStream());

            resp.setStatus(connection.getResponseCode());
            logger.info("response: {} {}", connection.getResponseCode(), connection.getResponseMessage());

        } catch (Exception e) {
            resp.setStatus(500);
            resp.sendError(500, "Operation not supported");
            logger.warn("forwarder failed: {}", e.getMessage());
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Getter
    //
    // Forward GET requests to server

    @SuppressWarnings("null")
    public void Getter(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {

        logger.debug("processing API forwarder request - GET");

        try {
            URL url = BuildURL(pathInfo, req);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // copy the request headers over

            Enumeration<String> allHeaders = req.getHeaderNames();
            while (allHeaders.hasMoreElements()) {
                String header = allHeaders.nextElement();
                Enumeration<String> values = req.getHeaders(header);
                while (values.hasMoreElements()) {
                    connection.addRequestProperty(header, values.nextElement());
                }
            }

            connection.setRequestMethod("GET");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.connect();

            // do we need to keep the returned headers? Probably

            int idx = 0;
            do {
                String header = connection.getHeaderFieldKey(idx);
                if (header == null) {
                    break;
                }
                String value = connection.getHeaderField(idx);
                logger.info("adding header {}: {}", header, value);
                resp.setHeader(header, value);
                idx++;
            } while (true);

            connection.getInputStream().transferTo(resp.getOutputStream());
            resp.setStatus(connection.getResponseCode());
            logger.info("response: {} {}", connection.getResponseCode(), connection.getResponseMessage());

        } catch (Exception e) {
            resp.setStatus(500);
            resp.sendError(500, "Operation not supported");
            logger.warn("forwarder failed: {}", e.getMessage());
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Deleter
    //
    // Forward DELETE requests to server

    @SuppressWarnings("null")
    public void Deleter(HttpServletRequest req, HttpServletResponse resp, String pathInfo) throws IOException {

        logger.debug("processing API forwarder request - DELETE");

        try {
            URL url = BuildURL(pathInfo, req);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // copy the request headers over

            Enumeration<String> allHeaders = req.getHeaderNames();
            while (allHeaders.hasMoreElements()) {
                String header = allHeaders.nextElement();
                Enumeration<String> values = req.getHeaders(header);
                while (values.hasMoreElements()) {
                    connection.addRequestProperty(header, values.nextElement());
                }
            }

            connection.setRequestMethod("DELETE");
            connection.setUseCaches(false);
            connection.setDoInput(true);
            connection.connect();

            // DELETE may have a data block

            req.getInputStream().transferTo(connection.getOutputStream());

            // do we need to keep the returned headers? Probably

            int idx = 0;
            do {
                String header = connection.getHeaderFieldKey(idx);
                if (header == null) {
                    break;
                }
                String value = connection.getHeaderField(idx);
                logger.info("adding header {}: {}", header, value);
                resp.setHeader(header, value);
                idx++;
            } while (true);

            // DELETE may send us something back
            connection.getInputStream().transferTo(resp.getOutputStream());
            resp.setStatus(connection.getResponseCode());
            logger.info("response: {} {}", connection.getResponseCode(), connection.getResponseMessage());

        } catch (Exception e) {
            resp.setStatus(500);
            resp.sendError(500, "Operation not supported");
            logger.warn("forwarder failed: {}", e.getMessage());
        }
    }

    private URL BuildURL(String pathInfo, HttpServletRequest req) throws MalformedURLException {

        // Here we just need to grab everything we need after the prefix.
        // string. The Frigate API does use a query string for some
        // API calls.

        String urlString = this.fgServerURL + "/" + pathInfo.substring(this.prefix.length());
        if (req.getQueryString() != null) {
            urlString += "?" + req.getQueryString();
        }
        logger.info("forwarding API request to {}", urlString);
        return new URL(urlString);
    }
}
