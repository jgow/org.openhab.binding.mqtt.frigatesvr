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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link ResultStruct} class is a result return for API and ThingAction
 * calls
 *
 * @author J Gow - Initial contribution
 */

@NonNullByDefault
public class ResultStruct {

    public boolean rc; // return code
    public String message; // return message
    public String type; // MIME type
    public byte[] raw; // raw data

    ////////////////////////////////////////////////////////////////////
    /// Constructor - default is an error conditon

    public ResultStruct() {
        this.rc = false;
        this.message = "result uninitialized";
        this.type = "";
        this.raw = new byte[0];
    }

    public ResultStruct(boolean rc, String desc) {
        this.rc = rc;
        this.message = desc;
        this.type = "";
        this.raw = new byte[0];
    }

    public ResultStruct(boolean rc, String desc, byte[] raw) {
        this.rc = rc;
        this.message = desc;
        this.type = "";
        this.raw = raw;
    }

    /////////////////////////////////////////////////////////////////////////
    // toMap
    //
    // Used by ThingActions. Currently only the return code and message
    // are used, with the raw data being returned by an item update.

    public Map<String, Object> toMap() {
        Map<String, Object> rc = new HashMap<>();
        rc.put("rc", this.rc);
        rc.put("message", this.message);
        if (this.type.equals("application/json")) {
            rc.put("result", new String(this.raw));
        } else {
            rc.put("result", "");
        }
        return rc;
    }
}
