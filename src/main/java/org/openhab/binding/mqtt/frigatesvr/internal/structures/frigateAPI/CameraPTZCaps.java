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

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link CameraPTZCaps} class is an object returned from the Camera PTZ
 * info API
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class CameraPTZCaps {
    public String name = "";
    public String[] features = new String[0];
    public String[] presets = new String[0];
}
