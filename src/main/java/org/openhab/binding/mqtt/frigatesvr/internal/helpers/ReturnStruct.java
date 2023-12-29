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

/**
 * The {@link ReturnStruct} is a helper class providing a pair to handle
 * returns from HTTP services
 *
 * @author J Gow - Initial contribution
 */
public class ReturnStruct {
    public boolean rc; // return code: false (fail), true (success)
    public String message; // error message (fail) or valid data (success)

    public ReturnStruct() {
        rc = false;
        message = new String("unspecified error");
    }

    public ReturnStruct(boolean rcode, String msg) {
        rc = rcode;
        message = msg;
    }
};
