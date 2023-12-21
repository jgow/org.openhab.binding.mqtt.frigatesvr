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
package org.openhab.binding.mqtt.frigatesvr.internal.actions;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.handlers.frigateSVRCameraHandler;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.RuleAction;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.core.thing.binding.ThingActionsScope;
import org.openhab.core.thing.binding.ThingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link CameraActions} is responsible for handling camera actions, which are
 * sent to one of the channels.
 *
 * @author J Gow - Initial contribution
 */

@ThingActionsScope(name = "frigateCamera") // Your bindings id is usually the scope
@NonNullByDefault
public class CameraActions implements ThingActions {
    private final Logger logger = LoggerFactory.getLogger(CameraActions.class);
    private @Nullable frigateSVRCameraHandler handler;

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        this.handler = (frigateSVRCameraHandler) handler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    @RuleAction(label = "triggerEvent", description = "Trigger an event")
    public void TriggerEvent(
            @ActionInput(name = "label", label = "@text/EventLabel", description = "@text/EventLabelDesc") @Nullable String label) {
        if (this.handler != null) {
            logger.info("Action triggered: label {}", label);
            if (label != null) {
                logger.info("Handling action");
            } else {
                logger.error("event label is null");
            }
        } else {
            logger.error("action not processed; handler null");
        }
    }

    public static void TriggerEvent(@Nullable ThingActions actions, @Nullable String label) {
        if (actions instanceof CameraActions) {
            ((CameraActions) actions).TriggerEvent(label);
        } else {
            throw new IllegalArgumentException("Instance is not a CameraActions class.");
        }
    }
}
