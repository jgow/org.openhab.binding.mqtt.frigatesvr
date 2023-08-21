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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRHTTPHelper;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.HTTPHandler;
import org.openhab.core.library.types.RawType;
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
    private frigateSVRHTTPHelper helper;

    public FrigateAPIForwarder(String prefix, frigateSVRHTTPHelper helper) {
        super();
        this.prefix = prefix;
        this.helper = helper;
    }

    ///////////////////////////////////////////////////////////////////////
    // canPost
    //
    // We can post from anything in our prefix

    public boolean canPost(String pathInfo) {
        logger.debug("canPost: pathInfo: {} prefix: {}", pathInfo, this.prefix);
        return pathInfo.startsWith(this.prefix);
    }

    ///////////////////////////////////////////////////////////////////////
    // canAccept
    //
    // We accept anything that starts with our prefix.

    public boolean canAccept(String pathInfo) {
        logger.debug("canAccept: pathInfo: {} prefix: {}", pathInfo, this.prefix);
        return pathInfo.startsWith(this.prefix);
    }

    ///////////////////////////////////////////////////////////////////////
    // canDelete
    //
    // We accept anything that starts with our prefix.

    public boolean canDelete(String pathInfo) {
        logger.debug("canDelete: pathInfo: {} prefix: {}", pathInfo, this.prefix);
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

            Request request = this.helper.GetFrigateRequest(GetAPIPathString(pathInfo, req));
            request.method(HttpMethod.POST);

            // copy the request headers over

            @Nullable
            Enumeration<String> allHeaders = req.getHeaderNames();
            if (allHeaders != null) {
                while (allHeaders.hasMoreElements() == true) {
                    String header = allHeaders.nextElement();
                    @Nullable
                    Enumeration<String> values = req.getHeaders(header);
                    while (values.hasMoreElements()) {
                        request.header(header, values.nextElement());
                    }
                }
            }

            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            req.getInputStream().transferTo(bs);

            // send it

            request.content(new StringContentProvider(bs.toString()));
            ContentResponse response = request.method(HttpMethod.POST).send();

            // do we need to keep the returned headers? Probably

            for (HttpField field : response.getHeaders()) {
                resp.setHeader(field.getName(), field.getValue());
            }

            // POST can send us something back.

            RawType raw = new RawType(response.getContent(), response.getHeaders().get(HttpHeader.CONTENT_TYPE));
            ByteArrayInputStream is = new ByteArrayInputStream(raw.getBytes());
            is.transferTo(resp.getOutputStream());

            resp.setStatus(response.getStatus());
            logger.info("response: {} {}", response.getStatus(), response.getReason());

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

            Request request = this.helper.GetFrigateRequest(GetAPIPathString(pathInfo, req));
            request.method(HttpMethod.POST);

            // copy the request headers over

            @Nullable
            Enumeration<String> allHeaders = req.getHeaderNames();
            if (allHeaders != null) {
                while (allHeaders.hasMoreElements() == true) {
                    String header = allHeaders.nextElement();
                    @Nullable
                    Enumeration<String> values = req.getHeaders(header);
                    while (values.hasMoreElements()) {
                        request.header(header, values.nextElement());
                    }
                }
            }

            // send it

            ContentResponse response = request.method(HttpMethod.GET).send();

            // do we need to keep the returned headers? Probably

            for (HttpField field : response.getHeaders()) {
                resp.setHeader(field.getName(), field.getValue());
            }

            // GET can send us something back.

            RawType raw = new RawType(response.getContent(), response.getHeaders().get(HttpHeader.CONTENT_TYPE));
            ByteArrayInputStream is = new ByteArrayInputStream(raw.getBytes());
            is.transferTo(resp.getOutputStream());

            resp.setStatus(response.getStatus());
            logger.info("response: {} {}", response.getStatus(), response.getReason());

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

            Request request = this.helper.GetFrigateRequest(GetAPIPathString(pathInfo, req));
            request.method(HttpMethod.POST);

            // copy the request headers over

            @Nullable
            Enumeration<String> allHeaders = req.getHeaderNames();
            if (allHeaders != null) {
                while (allHeaders.hasMoreElements() == true) {
                    String header = allHeaders.nextElement();
                    @Nullable
                    Enumeration<String> values = req.getHeaders(header);
                    while (values.hasMoreElements()) {
                        request.header(header, values.nextElement());
                    }
                }
            }

            // DELETE can have a data block

            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            req.getInputStream().transferTo(bs);

            // send it

            request.content(new StringContentProvider(bs.toString()));
            ContentResponse response = request.method(HttpMethod.DELETE).send();

            // do we need to keep the returned headers? Probably

            for (HttpField field : response.getHeaders()) {
                resp.setHeader(field.getName(), field.getValue());
            }

            // POST can send us something back.

            RawType raw = new RawType(response.getContent(), response.getHeaders().get(HttpHeader.CONTENT_TYPE));
            ByteArrayInputStream is = new ByteArrayInputStream(raw.getBytes());
            is.transferTo(resp.getOutputStream());

            resp.setStatus(response.getStatus());
            logger.info("response: {} {}", response.getStatus(), response.getReason());

        } catch (Exception e) {
            resp.setStatus(500);
            resp.sendError(500, "Operation not supported");
            logger.warn("forwarder failed: {}", e.getMessage());
        }
    }

    private String GetAPIPathString(String pathInfo, HttpServletRequest req) {
        String urlString = pathInfo.substring(this.prefix.length());
        if (req.getQueryString() != null) {
            urlString += "?" + req.getQueryString();
        }
        logger.info("API call {}", urlString);
        return urlString;
    }
}
