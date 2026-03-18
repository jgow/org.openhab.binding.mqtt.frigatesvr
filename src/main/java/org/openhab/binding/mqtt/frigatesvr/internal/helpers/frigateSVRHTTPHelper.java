/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http.MimeTypes;
import org.openhab.core.library.types.RawType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwt.JWT;
import com.auth0.jwt.exceptions.JWTDecodeException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
    private int timeout = 100;
    private String authtok = "";
    private String username = "";
    private String password = "";
    private boolean authNeeded = !password.trim().isBlank();
    private boolean authTokValid = false; // token is valid
    private Date tokExp = new Date();

    public frigateSVRHTTPHelper() {
    }

    /////////////////////////////////////////////////////////////////////////////
    // configure
    //
    // Configure at initialization

    public void configure(HttpClient httpClient, String address, int timeout, String username, String password) {
        this.setBaseURL(address);
        this.username = username;
        this.password = password;
        this.tokExp = new Date();
        this.client = httpClient;
        if (timeout > 0) {
            this.timeout = timeout;
        }
    }

    /////////////////////////////////////////////////////////////////////////////
    // getBaseURL
    //
    // Return the base URL

    public String getBaseURL() {
        return baseurl;
    }

    /////////////////////////////////////////////////////////////////////////////
    // setBaseURL
    //
    // Set the base URL at the start

    public void setBaseURL(String address) {
        StringBuilder sb = new StringBuilder();
        sb.append(address);
        if (!address.endsWith("/")) {
            sb.append("/");
        }
        this.baseurl = sb.toString();
    }

    /////////////////////////////////////////////////////////////////////////////
    // getHostAndPort
    //
    // Return the host name and port together: Frigate usually operates on
    // port 5000 but this can be changed. These are concatenated without the
    // usual colon as the intention is to use this string to build unique
    // identifiers for each Frigate server instance. To this end, we also
    // replace periods in the URL with dashes.

    public String getHostAndPort() {
        try {
            URL url = new URL(baseurl);
            String s = url.getHost() + url.getPort();
            return s.replace(".", "-");
        } catch (Exception e) {
            return new String("");
        }
    }

    /////////////////////////////////////////////////////////////////////////////
    // getHost
    //
    // Return the Frigate server host

    public String getHost() {
        try {
            URL url = new URL(baseurl);
            return url.getHost();
        } catch (Exception e) {
            return new String("");
        }
    }

    /////////////////////////////////////////////////////////////////////////////
    // buildURL
    //
    // Helper to generate URLs from the Frigate server base

    private String buildURL(String request) {
        StringBuilder sb = new StringBuilder();
        return sb.append(baseurl).append(request).toString();
    }

    /////////////////////////////////////////////////////////////////////////////
    // getAuth
    //
    // Obtain an authorization token from the server

    private boolean getAuth() {
        boolean rc = false;
        try {
            Request request = ((@NonNull HttpClient) this.client).POST(buildURL("/auth"));
            request.timeout(timeout, TimeUnit.MILLISECONDS);
            request.header(HttpHeader.CONTENT_TYPE, MimeTypes.Type.APPLICATION_JSON.asString());
            request.content(new StringContentProvider(
                    String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)));
            try {
                ContentResponse response = request.send();
                if (response.getStatus() == HttpStatus.OK_200) {
                    RawType jsonrq = new RawType(response.getContent(),
                            response.getHeaders().get(HttpHeader.CONTENT_TYPE));

                    // todo: this is JSON and will need parsing; find 'token'
                    JsonObject json = new Gson().fromJson(jsonrq.toString(), JsonObject.class);
                    this.authtok = json.get("token").toString();
                    // if rc is true, we must have a new authtok. Decode to get the exp. date

                    try {
                        DecodedJWT token = JWT.decode(this.authtok);
                        this.tokExp = token.getExpiresAt();
                        rc = true;
                        logger.info("have auth token");
                    } catch (JWTDecodeException k) {
                        // we don't have a valid token anyway; return false
                        logger.error("auth: token can not be decoded");
                    }
                } else {
                    logger.error("Auth failed; return status {}", response.getStatus());
                }
            } catch (TimeoutException e) {
                logger.error("auth: timeoutException: Call to Frigate Server timed out after {} msec", timeout);
            } catch (ExecutionException e) {
                logger.error("auth: ExecutionException: {}", e.getMessage());
            } catch (InterruptedException e) {
                logger.error("auth: InterruptedException: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        } catch (Exception e) {
            logger.error("auth: HTTP helper POST called in unconfigured state");
        }
        return rc;
    }

    /////////////////////////////////////////////////////////////////////////////
    // CheckAuthState
    //
    // Check and verify our auth token. If invalid, or about to expire, trigger
    // the request for a new one. If we can't get one, then return false. If
    // we return true, the auth token in the class is valid.
    // Note: if the password is blank, we assume auth is not needed and
    // proceed anyway

    private boolean CheckAuthState() {

        boolean rc = false;

        // If we don't need auth, we return true. Otherwise, we check
        // for a valid token that has, or is about to, expire and try
        // and renew it.

        if (this.authNeeded) {

            if (!this.authTokValid || this.tokExp.after(new Date())) {
                rc = this.getAuth();
            } else {
                rc = true; // valid and not expired
            }

        } else {
            rc = true;
        }
        return rc;
    }

    /////////////////////////////////////////////////////////////////////////////
    // GetFrigateRequest
    //
    // Used by the API forwarder to generate a specific request to the pre-
    // configured Frigate server client.

    public Request GetFrigateRequest(String APICall) throws UnsupportedOperationException {
        if (this.client != null) {
            assert this.client != null;
            return ((@NonNull HttpClient) this.client).newRequest(buildURL(APICall));
        } else {
            throw new UnsupportedOperationException("Client not available");
        }
    }

    ////////////////////////////////////////////////////////////////////////////
    // runGet
    //
    // Synchronous GET call to the Frigate API. To date we only use a couple
    // of different GET calls from within the binding

    public ResultStruct runGet(String call) {
        ResultStruct r = new ResultStruct();
        if (this.CheckAuthState()) {
            try {
                Request request = ((@NonNull HttpClient) this.client).newRequest(buildURL(call));
                request.method(HttpMethod.GET);
                if (this.authNeeded) {
                    request.header(HttpHeader.AUTHORIZATION, "Bearer " + this.authtok);
                }
                request.timeout(timeout, TimeUnit.MILLISECONDS);
                for (int idx = 0; idx < 2; idx++) {
                    try {
                        ContentResponse response = request.send();
                        if (response.getStatus() == HttpStatus.OK_200) {
                            RawType jsonrq = new RawType(response.getContent(),
                                    response.getHeaders().get(HttpHeader.CONTENT_TYPE));
                            r.rc = true;
                            r.raw = jsonrq.getBytes();
                            r.message = "ok";
                            break;
                        } else {
                            if ((response.getStatus() == HttpStatus.UNAUTHORIZED_401)) {
                                if (this.authNeeded) {
                                    // we try again if we can refresh the auth token. Invalidate it first
                                    this.authTokValid = false;
                                    if (!this.CheckAuthState()) {
                                        logger.error("reauth failed");
                                        break;
                                    }
                                } else {
                                    logger.error("server returned 401 but credentials not supplied");
                                    break;
                                }
                            } else {
                                r.message = String.format("HTTP GET failed: %d, %s", response.getStatus(),
                                        response.getReason());
                                break;
                            }
                        }
                    } catch (TimeoutException e) {
                        r.message = String.format("TimeoutException: Call to Frigate Server timed out after %d msec",
                                timeout);
                        break;
                    } catch (ExecutionException e) {
                        r.message = String.format("ExecutionException: %s", e.getMessage());
                        break;
                    } catch (InterruptedException e) {
                        r.message = String.format("InterruptedException: %s", e.getMessage());
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                r.message = new String("HTTP helper called in unconfigured state");
            }
        } else {
            r.message = new String("Unauthorized");
        }
        if (!r.rc) {
            logger.debug("{}", r.message);
        }
        return r;
    }

    ////////////////////////////////////////////////////////////////////////////
    // runPost
    //
    // Synchronous POST call to the Frigate API.import java.net.URL;

    public ResultStruct runPost(String call, @Nullable String payload) {
        ResultStruct r = new ResultStruct();
        if (this.CheckAuthState()) {
            try {
                Request request = ((@NonNull HttpClient) this.client).POST(buildURL(call));
                request.timeout(timeout, TimeUnit.MILLISECONDS);
                if (this.authNeeded) {
                    request.header(HttpHeader.AUTHORIZATION, "Bearer " + this.authtok);
                }
                if (payload != null) {
                    request.content(new StringContentProvider(payload));
                }
                for (int idx = 0; idx < 2; idx++) {
                    try {
                        ContentResponse response = request.send();
                        if (response.getStatus() == HttpStatus.OK_200) {
                            RawType jsonrq = new RawType(response.getContent(),
                                    response.getHeaders().get(HttpHeader.CONTENT_TYPE));
                            r.rc = true;
                            r.raw = jsonrq.getBytes();
                            r.message = new String("ok");
                        } else {
                            if ((response.getStatus() == HttpStatus.UNAUTHORIZED_401)) {
                                if (this.authNeeded) {
                                    // we try again if we can refresh the auth token. Invalidate it first
                                    this.authTokValid = false;
                                    if (!this.CheckAuthState()) {
                                        logger.error("reauth failed");
                                        break;
                                    }
                                } else {
                                    logger.error("server returned 401 but credentials not supplied");
                                    break;
                                }
                            } else {
                                r.message = String.format("HTTP POST failed: %d, %s", response.getStatus(),
                                        response.getReason());
                                break;
                            }
                        }
                    } catch (TimeoutException e) {
                        r.message = String.format("TimeoutException: Call to Frigate Server timed out after %d msec",
                                timeout);
                        break;
                    } catch (ExecutionException e) {
                        r.message = String.format("ExecutionException: %s", e.getMessage());
                        break;
                    } catch (InterruptedException e) {
                        r.message = String.format("InterruptedException: %s", e.getMessage());
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                r.message = new String("HTTP helper POST called in unconfigured state");
            }
        } else {
            r.rc = false;
            r.message = new String("Unauthorized");
        }
        if (!r.rc) {
            logger.error("{}", r.message);
        }
        return r;
    }
}
