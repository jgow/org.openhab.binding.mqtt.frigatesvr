/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
 * The {@link ResultStruct} class is a result return for API calls
 *
 * @author J Gow - Initial contribution
 */

@NonNullByDefault
public class ResultStruct {
    public boolean rc;
    public String message;
    public byte[] raw;

    public ResultStruct() {
        this.rc = false;
        this.message = "result uninitialized";
        this.raw = new byte[0];
    }

    public ResultStruct(boolean rc, String desc) {
        this.rc = rc;
        this.message = desc;
        this.raw = new byte[0];
    }

    public ResultStruct(boolean rc, String desc, byte[] raw) {
        this.rc = rc;
        this.message = desc;
        this.raw = raw;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> rc = new HashMap<>();
        rc.put("rc", this.rc);
        rc.put("message", this.message);
        return rc;
    }
}
