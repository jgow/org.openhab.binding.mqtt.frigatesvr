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
package org.openhab.binding.mqtt.frigatesvr.internal.handlers;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link mqtt.frigateSVRActionProcessor} is an Interface used by the
 * ThingAction processor mechanism.
 * Since v3.x, the Camera and Server things no longer have a common base
 * class. This somewhat complicates the ThingActions where some have
 * to be processed at the camera level, others at the server level
 * 
 * There may be other functions here later.
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public interface frigateSVRActionProcessor {

    /////////////////////////////////////////////////////////////////////
    /// SendMQTTCommand
    ///
    /// This differs slightly between camera and server, as the topic
    /// will differ

    public void SendMQTTCommand(String suffix, String command);
}
