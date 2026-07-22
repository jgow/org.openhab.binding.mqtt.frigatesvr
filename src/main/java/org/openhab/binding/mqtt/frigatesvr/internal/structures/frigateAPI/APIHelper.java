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

package org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRHTTPHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * The {@link APIHelper} class contains mappings to the
 * Frigate SVR config block
 *
 * @author J Gow - This is a layer between the HTTPHelper and the Frigate API. It will
 *         eventually replace the APIBase hierarchy for the ThingActions while
 *         provid
 */
@NonNullByDefault
public class APIHelper {

    private final Logger logger = LoggerFactory.getLogger(APIHelper.class);

    private frigateSVRHTTPHelper httpHelper;

    public APIHelper(frigateSVRHTTPHelper helper) {
        this.httpHelper = helper;
    }

    ////////////////////////////////////////////////////////////////////
    /// getTrackedObjects
    ///
    /// Return a list of strings containing tracked objects. Will throw
    /// exceptions on error accessing API.

    public List<String> getTrackedObjects() {
        List<String> rc = Collections.emptyList();
        ResultStruct r = httpHelper.runGet("/api/labels");
        if (r.rc) {
            String result = new String(r.raw);
            Gson gson = new Gson();
            Type listType = new TypeToken<List<String>>() {
            }.getType();
            rc = gson.fromJson(result, listType);
            if (rc == null) {
                rc = Collections.emptyList();
            }
        } else {
            logger.error("getTrackedObjects: failed to retrieve list of tracked objects");
        }
        return rc;
    }
}
