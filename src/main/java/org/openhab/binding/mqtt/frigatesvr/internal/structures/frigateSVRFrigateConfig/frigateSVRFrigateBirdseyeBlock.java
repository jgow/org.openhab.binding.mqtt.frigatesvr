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
package org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRFrigateConfig;

import org.eclipse.jdt.annotation.NonNullByDefault;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * The {@link mqtt.frigateSVRServerBirdseyeBlock} class contains fields mapping Frigate
 * configuration parameters from the Birdseye block.
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRFrigateBirdseyeBlock {

    @Expose
    @SerializedName("restream")
    public boolean clientID = false;
}
