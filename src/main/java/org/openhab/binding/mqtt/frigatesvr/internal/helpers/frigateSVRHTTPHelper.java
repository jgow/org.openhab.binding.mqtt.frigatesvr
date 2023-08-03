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

import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.core.library.types.RawType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link mqtt.frigateSVRHTTPHelper} is a helper class providing access to HTTP services
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRHTTPHelper {

    private @Nullable HttpClient client = null;
    private String baseurl = "";
    private final Logger logger = LoggerFactory.getLogger(frigateSVRHTTPHelper.class);

    public frigateSVRHTTPHelper() {
    }

    public void configure(HttpClient httpClient, String address) {
        this.setBaseURL(address);
        this.client = httpClient;
    }

    public String getBaseURL() {
        return baseurl;
    }

    public void setBaseURL(String address) {
        StringBuilder sb = new StringBuilder();
        sb.append(address);
        if (!address.endsWith("/")) {
            sb.append("/");
        }
        this.baseurl = sb.toString();
    }

    public String getHostAndPort() {
        try {
            URL url = new URL(baseurl);
            return url.getHost() + url.getPort();
        } catch (Exception e) {
            return new String("");
        }
    }

    private String buildURL(String request) {
        StringBuilder sb = new StringBuilder();
        return sb.append(baseurl).append(request).toString();
    }

    public @Nullable String runGet(String call) {
        try {
            Request request = ((@NonNull HttpClient) this.client).newRequest(buildURL(call));
            request.method(HttpMethod.GET);
            request.timeout(100, TimeUnit.MILLISECONDS);

            String errorMsg;
            try {
                ContentResponse response = request.send();
                if (response.getStatus() == HttpStatus.OK_200) {
                    RawType jsonrq = new RawType(response.getContent(),
                            response.getHeaders().get(HttpHeader.CONTENT_TYPE));
                    return new String(jsonrq.getBytes());
                } else {
                    errorMsg = String.format("HTTP GET failed: %d, %s", response.getStatus(), response.getReason());
                }
            } catch (TimeoutException e) {
                errorMsg = String.format("TimeoutException: Call to Frigate Server timed out after {} msec", 100);
            } catch (ExecutionException e) {
                errorMsg = String.format("ExecutionException: %s", e.getMessage());
            } catch (InterruptedException e) {
                errorMsg = String.format("InterruptedException: %s", e.getMessage());
                Thread.currentThread().interrupt();
            }
            logger.debug("{}", errorMsg);
        } catch (Exception e) {
            logger.debug("HTTP helper called in unconfigured state");
        }
        return null;
    }
}
