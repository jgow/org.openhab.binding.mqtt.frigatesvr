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

import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.handlers.frigateSVRCameraHandler;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APIGetLastFrame;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APIGetRecordingSummary;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APITriggerEvent;
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
    @ActionOutput(name = "message", label = "@text/action.TriggerEvent.desc.label", description = "@text/action.TriggerEvent.desc.description", type = "java.util.List<String>")
    public Map<String, Object> TriggerEvent(
            @ActionInput(name = "eventLabel", label = "@text/action.TriggerEvent.eventLabel.label", description = "@text/action.TriggerEvent.eventLabel.description") @Nullable String eventLabel,
            @ActionInput(name = "eventParams", label = "@text/action.TriggerEvent.eventParams.label", description = "@text/action.TriggerEvent.eventParams.description") @Nullable String eventParams) {
        ResultStruct rc = new ResultStruct();
        if (this.handler != null) {
            logger.debug("Action triggered: label {}", eventLabel);
            if (eventLabel != null) {
                rc = this.handler.SendActionEvent(new APITriggerEvent((@NonNull String) eventLabel, eventParams));
            } else {
                rc.message = "error: event ID label is null";
            }
        } else {
            rc.message = "action not processed; no handler";
        }
        return rc.toMap();
    }

    public static Map<String, Object> TriggerEvent(@Nullable ThingActions actions, @Nullable String eventLabel,
            @Nullable String eventParams) {
        if (actions instanceof CameraActions) {
            return ((CameraActions) actions).TriggerEvent(eventLabel, eventParams);
        } else {
            throw new IllegalArgumentException("Instance is not a CameraActions class.");
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetLastFrame
    //
    // Get the last frame that Frigate has finished processing. This is a
    // full resolution image, returned in the fgLatestImage channel.
    //
    // OH architecture does not seem to provide a means for Things to speak
    // to each other directly (indeed seems to be discouraged). Due to this
    // omission, the result is that we process the action asynchronously.
    //
    // Static member function is provided for older OH variants.

    @RuleAction(label = "GetLastFrame", description = "Get the last processed frame")
    @ActionOutput(name = "rc", label = "@text/action.GetLastFrame.rc.label", description = "@text/action.GetLastFrame.rc.description", type = "java.util.List<String>")
    @ActionOutput(name = "message", label = "@text/action.GetLastFrame.desc.label", description = "@text/action.GetLastFrame.desc.description", type = "java.util.List<String>")
    public Map<String, Object> GetLastFrame(
            @ActionInput(name = "eventParams", label = "@text/action.TriggerEvent.GetLastFrame.label", description = "@text/action.TriggerEvent.GetLastFrame.description") @Nullable String eventParams) {
        ResultStruct rc = new ResultStruct();
        if (this.handler != null) {
            logger.debug("Action triggered: label GetLastFrame: {}", eventParams);
            rc = this.handler.SendActionEvent(new APIGetLastFrame(eventParams));
        } else {
            rc.message = "action not processed; no handler";
        }
        return rc.toMap();
    }

    public static Map<String, Object> GetLastFrame(@Nullable ThingActions actions, @Nullable String params) {
        if (actions instanceof CameraActions) {
            return ((CameraActions) actions).GetLastFrame(params);
        } else {
            throw new IllegalArgumentException("Instance is not a CameraActions class.");
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetRecordingSummary
    //
    // Get the summary of recordings as a JSON block
    //
    // OH architecture does not seem to provide a means for Things to speak
    // to each other directly (indeed seems to be discouraged). Due to this
    // omission, the result is that we process the action asynchronously.
    //
    // Static member function is provided for older OH variants.

    @RuleAction(label = "GetRecordingSummary", description = "Get the summary of recordings for this camera")
    @ActionOutput(name = "rc", label = "@text/action.GetRecordingSummary.rc.label", description = "@text/action.GetRecordingSummary.rc.description", type = "java.util.List<String>")
    @ActionOutput(name = "message", label = "@text/action.GetRecordingSummary.desc.label", description = "@text/action.GetRecordingSummary.desc.description", type = "java.util.List<String>")
    public Map<String, Object> GetRecordingSummary() {
        ResultStruct rc = new ResultStruct();
        if (this.handler != null) {
            logger.debug("Action triggered: label GetRecordingSummary");
            rc = this.handler.SendActionEvent(new APIGetRecordingSummary());
        } else {
            rc.message = "action not queued; no handler";
        }
        return rc.toMap();
    }

    public static Map<String, Object> GetRecordingSummary(@Nullable ThingActions actions, @Nullable String params) {
        if (actions instanceof CameraActions) {
            return ((CameraActions) actions).GetRecordingSummary();
        } else {
            throw new IllegalArgumentException("Instance is not a CameraActions class.");
        }
    }
}
