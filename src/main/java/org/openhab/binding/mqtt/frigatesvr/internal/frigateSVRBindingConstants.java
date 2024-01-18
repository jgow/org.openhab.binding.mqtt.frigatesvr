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
package org.openhab.binding.mqtt.frigatesvr.internal;

import static org.openhab.binding.mqtt.MqttBindingConstants.BINDING_ID;

import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.thing.ThingTypeUID;

/**
 * The {@link mqtt.frigateSVRBindingConstants} class defines common constants, which are
 * used across the whole binding.
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRBindingConstants {

    // We support two Thing types - a camera (representing a single Frigate camera feed) and
    // a server (representing the instance of the Frigate server itself).

    public static final ThingTypeUID THING_TYPE_CAMERA = new ThingTypeUID(BINDING_ID, "frigateCamera");
    public static final ThingTypeUID THING_TYPE_SERVER = new ThingTypeUID(BINDING_ID, "frigateServer");

    // Supported UID list
    public static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_SERVER, THING_TYPE_CAMERA);

    // List of all Channel ids
    public static final String CHANNEL_API_VERSION = "fgAPIVersion";
    public static final String CHANNEL_UI_URL = "fgUI";
    public static final String CHANNEL_APIFORWARDER_URL = "fgAPIForwarderURL";
    public static final String CHANNEL_BIRDSEYE_URL = "fgBirdseyeURL";

    public static final String CHANNEL_CAM_CAMFPS = "fgCameraFPS";
    public static final String CHANNEL_CAM_PROCESSFPS = "fgCameraProcessFPS";
    public static final String CHANNEL_CAM_SKIPPEDFPS = "fgCameraSkippedFPS";
    public static final String CHANNEL_CAM_DETECTIONFPS = "fgCameraDetectionFPS";
    public static final String CHANNEL_STATE_DETECTION = "fgDetectionState";
    public static final String CHANNEL_STATE_RECORDING = "fgRecordingState";
    public static final String CHANNEL_STATE_SNAPSHOTS = "fgSnapshotState";
    public static final String CHANNEL_STATE_MOTIONDET = "fgMotionState";
    public static final String CHANNEL_STATE_IMPCONTRAST = "fgContrastState";
    public static final String CHANNEL_STATE_MOTIONTHRESH = "fgMotionThreshold";
    public static final String CHANNEL_STATE_MOTIONCONTOUR = "fgMotionContourArea";
    public static final String CHANNEL_STATE_MOTIONDETECTED = "fgMotionDetected";

    public static final String CHANNEL_EVENT_TYPE = "fgEventType";
    public static final String CHANNEL_EVENT_ID = "fgEventID";
    public static final String CHANNEL_EVENT_CLIP_URL = "fgEventClipURL";
    public static final String CHANNEL_STREAM_URL = "fgStreamURL";
    public static final String CHANNEL_EVENT_JSON = "fgEventJson";

    public static final String CHANNEL_LAST_SNAPSHOT_OBJECT = "fgLastSnapshotObject";
    public static final String CHANNEL_LAST_SNAPSHOT = "fgLastSnapshot";
    public static final String CHANNEL_CAM_ACTION_RESULT = "fgCamActionResult";
    public static final String CHANNEL_LAST_FRAME = "fgLastProcessedFrame";

    public static final String CHANNEL_PREV_FRAME_TIME = "fgPrevFrameTime";
    public static final String CHANNEL_PREV_SNAPSHOT_TIME = "fgPrevSnapshotTime";
    public static final String CHANNEL_PREV_LABEL = "fgPrevLabel";
    public static final String CHANNEL_PREV_SUBLABEL = "fgPrevSubLabel";
    public static final String CHANNEL_PREV_TOP_SCORE = "fgPrevTopScore";
    public static final String CHANNEL_PREV_FALSE_POSITIVE = "fgPrevFalsePositive";
    public static final String CHANNEL_PREV_START_TIME = "fgPrevStartTime";
    public static final String CHANNEL_PREV_END_TIME = "fgPrevEndTime";
    public static final String CHANNEL_PREV_SCORE = "fgPrevScore";
    public static final String CHANNEL_PREV_BOX = "fgPrevBox";
    public static final String CHANNEL_PREV_AREA = "fgPrevArea";
    public static final String CHANNEL_PREV_RATIO = "fgPrevRatio";
    public static final String CHANNEL_PREV_REGION = "fgPrevRegion";
    public static final String CHANNEL_PREV_CURRENT_ZONE = "fgPrevCurrentzone";
    public static final String CHANNEL_PREV_ENTERED_ZONE = "fgPrevEnteredZone";
    public static final String CHANNEL_PREV_HAS_SNAPSHOT = "fgPrevHasSnapshot";
    public static final String CHANNEL_PREV_HAS_CLIP = "fgPrevHasClip";
    public static final String CHANNEL_PREV_STATIONARY = "fgPrevStationary";
    public static final String CHANNEL_PREV_MOTIONLESSCOUNT = "fgPrevMotionlessCount";
    public static final String CHANNEL_PREV_POSITIONCHANGES = "fgPrevPositionChanges";

    public static final String CHANNEL_CUR_FRAME_TIME = "fgCurFrameTime";
    public static final String CHANNEL_CUR_SNAPSHOT_TIME = "fgCurSnapshotTime";
    public static final String CHANNEL_CUR_LABEL = "fgCurLabel";
    public static final String CHANNEL_CUR_SUBLABEL = "fgCurSubLabel";
    public static final String CHANNEL_CUR_TOP_SCORE = "fgCurTopScore";
    public static final String CHANNEL_CUR_FALSE_POSITIVE = "fgCurFalsePositive";
    public static final String CHANNEL_CUR_START_TIME = "fgCurStartTime";
    public static final String CHANNEL_CUR_END_TIME = "fgCurEndTime";
    public static final String CHANNEL_CUR_SCORE = "fgCurScore";
    public static final String CHANNEL_CUR_BOX = "fgCurBox";
    public static final String CHANNEL_CUR_AREA = "fgCurArea";
    public static final String CHANNEL_CUR_RATIO = "fgCurRatio";
    public static final String CHANNEL_CUR_REGION = "fgCurRegion";
    public static final String CHANNEL_CUR_CURRENT_ZONE = "fgCurCurrentzone";
    public static final String CHANNEL_CUR_ENTERED_ZONE = "fgCurEnteredZone";
    public static final String CHANNEL_CUR_HAS_SNAPSHOT = "fgCurHasSnapshot";
    public static final String CHANNEL_CUR_HAS_CLIP = "fgCurHasClip";
    public static final String CHANNEL_CUR_STATIONARY = "fgCurStationary";
    public static final String CHANNEL_CUR_MOTIONLESSCOUNT = "fgCurMotionlessCount";
    public static final String CHANNEL_CUR_POSITIONCHANGES = "fgCurPositionChanges";

    // MQTT topic suffixes
    public static final String MQTT_EVENTS_SUFFIX = "events";
    public static final String MQTT_STATS_SUFFIX = "stats";
    public static final String MQTT_KEEPALIVE_SUFFIX = "keepalive";
    public static final String MQTT_AVAILABILITY_SUFFIX = "available";
    public static final String MQTT_ONLINE_SUFFIX = "camOnLine";
    public static final String MQTT_EVTTRIGGER_SUFFIX = "TriggerEvent";
    public static final String MQTT_GETLASTFRAME_SUFFIX = "GetLastFrame";
    public static final String MQTT_DETECTION_SET = "detect/set";
    public static final String MQTT_DETECTION_GET = "detect/state";
    public static final String MQTT_RECORDING_SET = "recordings/set";
    public static final String MQTT_RECORDING_GET = "recordings/state";
    public static final String MQTT_SNAPSHOTS_SET = "snapshots/set";
    public static final String MQTT_SNAPSHOTS_GET = "snapshots/state";
    public static final String MQTT_MOTIONDET_SET = "motion/set";
    public static final String MQTT_MOTIONDET_GET = "motion/state";
    public static final String MQTT_IMPCONTRAST_SET = "improve_contrast/set";
    public static final String MQTT_IMPCONTRAST_GET = "improve_contrast/state";
    public static final String MQTT_MOTIONTHRESH_SET = "motion_threshold/set";
    public static final String MQTT_MOTIONTHRESH_GET = "motion_threshold/state";
    public static final String MQTT_MOTIONCONTOUR_SET = "motion_contour_area/set";
    public static final String MQTT_MOTIONCONTOUR_GET = "motion_contour_area/state";
    public static final String MQTT_MOTION = "motion";
    public static final String MQTT_CAMACTIONRESULT = "CameraActionResult";

    // config properties
    public static final String CONF_ID_UNIQUE = "uniqueID";
    public static final String CONF_ID_CAMNAME = "cameraName";
    public static final String CONF_ID_SERVERURL = "serverURL";
    public static final String CONF_ID_SERVERID = "serverID";
    public static final String CONF_ID_ENABLESTREAM = "enableStream";
    public static final String CONF_ID_FFMPEGLOCATION = "ffmpegLocation";
}
