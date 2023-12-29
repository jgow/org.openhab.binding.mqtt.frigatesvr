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

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.handlers.frigateSVRCameraHandler;
import org.openhab.core.automation.annotation.ActionInput;
import org.openhab.core.automation.annotation.ActionOutput;
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

    ///////////////////////////////////////////////////////////////////////////
    ///
    /// Access to the handler objects

    @Override
    public void setThingHandler(@Nullable ThingHandler handler) {
        this.handler = (frigateSVRCameraHandler) handler;
    }

    @Override
    public @Nullable ThingHandler getThingHandler() {
        return handler;
    }

    ///////////////////////////////////////////////////////////////////////////
    // TriggerEvent
    //
    // Initiate an event on the specific camera. These in and of themselves
    // do not interact directly with OH channels.
    //
    // OH architecture does not seem to provide a means for Things to speak
    // to each other directly (indeed seems to be discouraged). Due to this
    // omission, the result is that we process the action asynchronously.
    //
    // Static member function is provided for older OH variants.

    @RuleAction(label = "TriggerEvent", description = "Trigger event on camera")
    @ActionOutput(name = "rc", label = "@text/action.TriggerEvent.rc.label", description = "@text/action.TriggerEvent.rc.description", type = "java.util.List<String>")
    @ActionOutput(name = "desc", label = "@text/action.TriggerEvent.desc.label", description = "@text/action.TriggerEvent.desc.description", type = "java.util.List<String>")
    public Map<String, Object> TriggerEvent(
            @ActionInput(name = "eventLabel", label = "@text/action.TriggerEvent.eventLabel.label", description = "@text/action.TriggerEvent.eventLabel.description") @Nullable String eventLabel,
            @ActionInput(name = "eventRequest", label = "@text/action.TriggerEvent.eventRequest.label", description = "@text/action.TriggerEvent.eventRequest.description") @Nullable String event) {
        Map<String, Object> rc = new HashMap<>();
        if (this.handler != null) {
            logger.debug("Action triggered: label {}", eventLabel);
            if (eventLabel != null) {
                this.handler.SendActionEvent(frigateSVRCameraHandler.camActions.CAMACTION_TRIGGEREVENT, eventLabel,
                        event);
                rc.put("rc", true);
                rc.put("desc", new String("event queued"));
            } else {
                rc.put("rc", false);
                rc.put("desc", "error: event label is null");
            }
        } else {
            rc.put("rc", false);
            rc.put("desc", "action not processed; handler null");
        }
        return rc;
    }

    public static Map<String, Object> TriggerEvent(@Nullable ThingActions actions, @Nullable String label,
            @Nullable String event) {
        if (actions instanceof CameraActions) {
            return ((CameraActions) actions).TriggerEvent(label, event);
        } else {
            throw new IllegalArgumentException("Instance is not a CameraActions class.");
        }
    }
}
