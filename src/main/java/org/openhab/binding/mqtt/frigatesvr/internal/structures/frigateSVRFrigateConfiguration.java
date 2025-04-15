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
package org.openhab.binding.mqtt.frigatesvr.internal.structures;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRFrigateConfig.frigateSVRFrigateConfigBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * The {@link mqtt.frigateSVRFrigateConfiguration} provides a management class
 * for the Frigate server configuration, and provides member functions to allow
 * for extraction of key values
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRFrigateConfiguration {

    private final Logger logger = LoggerFactory.getLogger(frigateSVRFrigateConfiguration.class);
    public frigateSVRFrigateConfigBlock block = new frigateSVRFrigateConfigBlock();

    public frigateSVRFrigateConfiguration() {
    }

    //////////////////////////////////////////////////////////////////////////
    // GetConfiguration
    //
    // Passed a string containing JSON - will fail (with an exception)
    // if the string does not contain a valid config block, or is NULL

    public void GetConfiguration(@Nullable String cfg) throws Exception {

        Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

        // Parse into the block. If it isn't valid JSON, we choke.
        @Nullable
        frigateSVRFrigateConfigBlock blk = gson.fromJson(cfg, frigateSVRFrigateConfigBlock.class);
        if (blk == null) {
            logger.info("NULL returned from deserializer");
            throw (new NoSuchFieldException());
        }
        this.block = blk;
    }
}
