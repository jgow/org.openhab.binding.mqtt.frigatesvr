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
package org.openhab.binding.mqtt.frigatesvr.internal.actions;

import static org.openhab.binding.mqtt.frigatesvr.internal.frigateSVRBindingConstants.*;

import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.handlers.frigateSVRCameraHandler;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APIGetLastFrame;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APIGetRecordingSummary;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APIGetThumbnail;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APIPtz;
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
    // Update: as of v3.x the camera 'things' are children of the server
    // bridge - thus it is possible to call into the server to access the
    // API, but not for the server to call into the camera (unless we maintain
    // a registry of bridge children.
    //
    // Static member function is provided for older OH variants.

    @RuleAction(label = "TriggerEvent", description = "@text/action.TriggerEvent.description")
    @ActionOutput(name = "rc", label = "@text/action.TriggerEvent.rc.label", description = "@text/action.TriggerEvent.rc.description", type = "java.util.List<String>")
    @ActionOutput(name = "message", label = "@text/action.TriggerEvent.desc.label", description = "@text/action.TriggerEvent.desc.description", type = "java.util.List<String>")
    public Map<String, Object> TriggerEvent(
            @ActionInput(name = "eventLabel", label = "@text/action.TriggerEvent.eventLabel.label", description = "@text/action.TriggerEvent.eventLabel.description") @Nullable String eventLabel,
            @ActionInput(name = "eventParams", label = "@text/action.TriggerEvent.eventParams.label", description = "@text/action.TriggerEvent.eventParams.description") @Nullable String eventParams) {
        ResultStruct rc = new ResultStruct();
        if (this.handler != null) {
            logger.debug("Action triggered: label {}", eventLabel);
            if (eventLabel != null) {
                APITriggerEvent api = new APITriggerEvent((@NonNull String) eventLabel, eventParams);
                rc = this.handler.SendActionEvent(api);
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
    public Map<String, Object> GetLastFrame() {
        ResultStruct rc = new ResultStruct();
        if (this.handler != null) {
            logger.debug("Action triggered: label GetLastFrame");
            rc = this.handler.SendActionEvent(new APIGetLastFrame());
            if (rc.rc) {
                // we need to update the image state in this handler.
                this.handler.UpdateChannel(CHANNEL_LAST_FRAME, rc);
            } else {
                logger.error("API not successful: {}", rc.message);
            }
        } else {
            rc.message = "action not processed; no handler";
        }
        return rc.toMap();
    }

    public static Map<String, Object> GetLastFrame(@Nullable ThingActions actions, @Nullable String params) {
        if (actions instanceof CameraActions) {
            return ((CameraActions) actions).GetLastFrame();
        } else {
            throw new IllegalArgumentException("Instance is not a CameraActions class.");
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // GetRecordingSummary
    //
    // Get the summary of recordings for a camera as a JSON block
    //
    //
    // Static member function is provided for older OH variants.

    @RuleAction(label = "GetRecordingSummary", description = "Get the summary of recordings for this camera")
    @ActionOutput(name = "rc", label = "@text/action.GetRecordingSummary.rc.label", description = "@text/action.GetRecordingSummary.rc.description", type = "String")
    @ActionOutput(name = "message", label = "@text/action.GetRecordingSummary.desc.label", description = "@text/action.GetRecordingSummary.desc.description", type = "String")
    @ActionOutput(name = "result", label = "Result", description = "Result", type = "String")
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

    ///////////////////////////////////////////////////////////////////////////
    // GetThumbnail
    //
    // Retrieve the thumbnail for this camera from the event label. Use 'any'
    // as the label to get the latest
    //
    // v3.x removes asynchronous operation of this API
    //
    // Static member function is provided for older OH variants.

    @RuleAction(label = "GetThumbnail", description = "Get thumbnail for event")
    @ActionOutput(name = "rc", label = "@text/action.GetThumbnail.rc.label", description = "@text/action.GetThumbnail.rc.description", type = "java.util.List<String>")
    @ActionOutput(name = "message", label = "@text/action.GetThumbnail.desc.label", description = "@text/action.GetThumbnail.desc.description", type = "java.util.List<String>")
    public Map<String, Object> GetThumbnail(
            @ActionInput(name = "eventLabel", label = "@text/action.GetThumbnail.eventLabel.label", description = "@text/action.GetThumbnail.eventLabel.description") @Nullable String eventLabel) {
        ResultStruct rc = new ResultStruct();
        if (this.handler != null) {
            logger.debug("Action triggered: label {}", eventLabel);
            if (eventLabel != null) {
                rc = this.handler.SendActionEvent(new APIGetThumbnail(eventLabel));
                if (rc.rc) {
                    // we need to update the image state in this handler.
                    this.handler.UpdateChannel(CHANNEL_LAST_FRAME, rc);
                }
            } else {
                rc.message = "error: event ID label is null";
            }
        } else {
            rc.message = "action not processed; no handler";
        }
        return rc.toMap();
    }

    public static Map<String, Object> GetThumbnail(@Nullable ThingActions actions, @Nullable String eventLabel) {
        if (actions instanceof CameraActions) {
            return ((CameraActions) actions).GetThumbnail(eventLabel);
        } else {
            throw new IllegalArgumentException("Instance is not a CameraActions class.");
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // PTZ
    //
    // Send PTZ information to the camera
    // One argument
    //
    // Static member function is provided for older OH variants.

    @RuleAction(label = "PTZ", description = "Control PTZ camera")
    @ActionOutput(name = "rc", label = "@text/action.PTZ.rc.label", description = "@text/action.PTZ.rc.description", type = "java.util.List<String>")
    @ActionOutput(name = "message", label = "@text/action.PTZ.desc.label", description = "@text/action.PTZ.desc.description", type = "java.util.List<String>")
    public Map<String, Object> PTZ(
            @ActionInput(name = "direction", label = "@text/action.ptz.dir.label", description = "@text/action.ptz.dir.description") @Nullable String param) {
        ResultStruct rc = new ResultStruct();
        if (this.handler != null) {
            logger.debug("PTZ action triggered: label {}", param);
            if (param != null) {
                rc = this.handler.SendActionEvent(new APIPtz(param, this.handler.GetPTZCaps()));
            } else {
                rc.message = "error: PTZ direction is null";
            }
        } else {
            rc.message = "action not processed; no handler";
        }
        return rc.toMap();
    }

    // public static Map<String, Object> PTZ(@Nullable ThingActions actions, @Nullable String eventLabel) {
    // if (actions instanceof CameraActions) {
    // return ((CameraActions) actions).GetThumbnail(eventLabel);
    // } else {
    // throw new IllegalArgumentException("Instance is not a CameraActions class.");
    // }
    // }
}
