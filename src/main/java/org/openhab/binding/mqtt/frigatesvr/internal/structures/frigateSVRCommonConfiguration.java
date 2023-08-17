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
package org.openhab.binding.mqtt.frigatesvr.internal.structures;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link mqtt.frigateSVRConfiguration} class contains fields mapping thing configuration parameters.
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRCommonConfiguration {
    public boolean enableStream = true;
    public boolean ffMJPEGStartProducerOnLoad = false;
    public String ffMJPEGTranscodeCommands = "";
    public boolean ffHLSStartProducerOnLoad = false;
    public String ffHLSTranscodeCommands = "";
    public boolean ffDASHStartProducerOnLoad = false;
    public String ffDASHTranscodeCommands = "";
    public String ffDASHPackagingCommands = "";
    public int ffKeepalivesBeforeExit = 2;
    public String ffTempDir = "";
}
