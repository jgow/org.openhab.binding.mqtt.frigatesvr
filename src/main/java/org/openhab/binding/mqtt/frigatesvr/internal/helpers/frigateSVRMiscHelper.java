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
package org.openhab.binding.mqtt.frigatesvr.internal.helpers;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * The {@link mqtt.frigateSVRMiscHelper} provides miscellaneous utility functions
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRMiscHelper {

    ////////////////////////////////////////////////////////////////////////
    // StripLeadingSlash
    //
    // Remove any leading slashes and return the string

    public static String StripLeadingSlash(@Nullable String str) {
        if (str != null) {
            String rc = str.trim();
            if (rc.charAt(0) == '/') {
                rc = rc.substring(1);
            }
            return rc;
        } else {
            return new String("");
        }
    }

    ///////////////////////////////////////////////////////////////////////
    // StripTrailingSlash
    //
    // Remove any trailing slashes and return the string

    public static String StripTrailingSlash(String str) {
        return str.replaceFirst("/*$", "");
    }
}
