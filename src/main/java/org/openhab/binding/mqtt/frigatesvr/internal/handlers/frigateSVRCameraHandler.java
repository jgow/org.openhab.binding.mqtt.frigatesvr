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

import static org.openhab.binding.mqtt.frigatesvr.internal.frigateSVRBindingConstants.*;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.actions.CameraActions;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRHTTPHelper;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRNetworkHelper;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.HTTPHandler;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.frigateSVRServlet;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams.DASHStream;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams.HLSStream;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams.MJPEGStream;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APIBase;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.CameraPTZCaps;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRCameraConfiguration;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRChannelState;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRFrigateConfig.frigateSVRFrigateConfigBlock;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRServerConfiguration;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRServerState;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRServices;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.openhab.core.thing.ThingStatusInfo;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.binding.BaseThingHandler;
import org.openhab.core.thing.binding.BridgeHandler;
import org.openhab.core.thing.binding.ThingHandlerService;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The {@link mqtt.frigateSVRHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRCameraHandler extends BaseThingHandler
        implements MqttMessageSubscriber, frigateSVRActionProcessor {

    private final Logger logger = LoggerFactory.getLogger(frigateSVRCameraHandler.class);
    private frigateSVRCameraConfiguration config = new frigateSVRCameraConfiguration();
    private frigateSVRServerState svrState = new frigateSVRServerState();
    private String pfxFrigateToCam = "";

    private frigateSVRNetworkHelper networkHelper;
    private @Nullable MqttBrokerConnection MQTTConnection = null;
    private frigateSVRHTTPHelper httpHelper = new frigateSVRHTTPHelper();
    private Map<String, frigateSVRChannelState> Channels = new HashMap<String, frigateSVRChannelState>();
    private frigateSVRServlet httpServlet;

    // PTZ caps for the camera

    private CameraPTZCaps PTZCaps = new CameraPTZCaps();

    // list of tracked objects; updated from server when Thing is onlined

    private List<String> trackedObjects = Collections.emptyList();

    // use relative URLs

    private boolean useRelativeURLs = true;

    // makes for easy change if Frigate ever extend the API
    //
    // First map maps from MQTT topic suffix to channel ID
    // Second map maps from channel ID to channel state.
    // May be better to use a HashMap as it could be quicker
    // to find keys?

    private Map<String, String> MQTTGettersToChannels = Map.ofEntries(
            Map.entry(MQTT_DETECTION_GET, CHANNEL_STATE_DETECTION),
            Map.entry(MQTT_RECORDING_GET, CHANNEL_STATE_RECORDING),
            Map.entry(MQTT_SNAPSHOTS_GET, CHANNEL_STATE_SNAPSHOTS),
            Map.entry(MQTT_MOTIONDET_GET, CHANNEL_STATE_MOTIONDET),
            Map.entry(MQTT_IMPCONTRAST_GET, CHANNEL_STATE_IMPCONTRAST),
            Map.entry(MQTT_MOTIONTHRESH_GET, CHANNEL_STATE_MOTIONTHRESH),
            Map.entry(MQTT_MOTIONCONTOUR_GET, CHANNEL_STATE_MOTIONCONTOUR),
            Map.entry(MQTT_MOTION, CHANNEL_STATE_MOTIONDETECTED));

    // private Map<String, String> MQTTServerMessageGettersToChannels = Map
    // .ofEntries(Map.entry(MQTT_CAMACTIONRESULT, CHANNEL_CAM_ACTION_RESULT));

    private Map<String, String> JSONEventGettersToPrev = Map.ofEntries(Map.entry("frame_time", CHANNEL_PREV_FRAME_TIME),
            Map.entry("snapshot_time", CHANNEL_PREV_SNAPSHOT_TIME), Map.entry("label", CHANNEL_PREV_LABEL),
            Map.entry("sub_label", CHANNEL_PREV_SUBLABEL), Map.entry("top_score", CHANNEL_PREV_TOP_SCORE),
            Map.entry("false_positive", CHANNEL_PREV_FALSE_POSITIVE), Map.entry("start_time", CHANNEL_PREV_START_TIME),
            Map.entry("end_time", CHANNEL_PREV_END_TIME), Map.entry("score", CHANNEL_PREV_SCORE),
            Map.entry("box", CHANNEL_PREV_BOX), Map.entry("area", CHANNEL_PREV_AREA),
            Map.entry("ratio", CHANNEL_PREV_RATIO), Map.entry("region", CHANNEL_PREV_REGION),
            Map.entry("current_zones", CHANNEL_PREV_CURRENT_ZONE),
            Map.entry("entered_zones", CHANNEL_PREV_ENTERED_ZONE), Map.entry("has_snapshot", CHANNEL_PREV_HAS_SNAPSHOT),
            Map.entry("has_clip", CHANNEL_PREV_HAS_CLIP), Map.entry("stationary", CHANNEL_PREV_STATIONARY),
            Map.entry("motionless_count", CHANNEL_PREV_MOTIONLESSCOUNT),
            Map.entry("position_changes", CHANNEL_PREV_POSITIONCHANGES),
            Map.entry("max_severity", CHANNEL_PREV_MAXSEVERITY),
            Map.entry("recognized_license_plate", CHANNEL_PREV_LICENCEPLATE));

    private Map<String, String> JSONEventGettersToCur = Map.ofEntries(Map.entry("frame_time", CHANNEL_CUR_FRAME_TIME),
            Map.entry("snapshot_time", CHANNEL_CUR_SNAPSHOT_TIME), Map.entry("label", CHANNEL_CUR_LABEL),
            Map.entry("sub_label", CHANNEL_CUR_SUBLABEL), Map.entry("top_score", CHANNEL_CUR_TOP_SCORE),
            Map.entry("false_positive", CHANNEL_CUR_FALSE_POSITIVE), Map.entry("start_time", CHANNEL_CUR_START_TIME),
            Map.entry("end_time", CHANNEL_CUR_END_TIME), Map.entry("score", CHANNEL_CUR_SCORE),
            Map.entry("box", CHANNEL_CUR_BOX), Map.entry("area", CHANNEL_CUR_AREA),
            Map.entry("ratio", CHANNEL_CUR_RATIO), Map.entry("region", CHANNEL_CUR_REGION),
            Map.entry("current_zones", CHANNEL_CUR_CURRENT_ZONE), Map.entry("entered_zones", CHANNEL_CUR_ENTERED_ZONE),
            Map.entry("has_snapshot", CHANNEL_CUR_HAS_SNAPSHOT), Map.entry("has_clip", CHANNEL_CUR_HAS_CLIP),
            Map.entry("stationary", CHANNEL_CUR_STATIONARY), Map.entry("motionless_count", CHANNEL_CUR_MOTIONLESSCOUNT),
            Map.entry("position_changes", CHANNEL_CUR_POSITIONCHANGES),
            Map.entry("max_severity", CHANNEL_CUR_MAXSEVERITY),
            Map.entry("recognized_license_plate", CHANNEL_CUR_LICENCEPLATE));

    private Map<String, String> JSONStateGetters = Map.ofEntries(Map.entry("camera_fps", CHANNEL_CAM_CAMFPS),
            Map.entry("process_fps", CHANNEL_CAM_PROCESSFPS), Map.entry("skipped_fps", CHANNEL_CAM_SKIPPEDFPS),
            Map.entry("detection_fps", CHANNEL_CAM_DETECTIONFPS));

    //
    // Good, now we need a generic way of extracting data to channels based on the mappings in
    // the getters and the channel map.

    private java.util.function.BiConsumer<Map<String, String>, JsonObject> HandleEventPart = (getter, evtPart) -> {
        for (var ch : getter.entrySet()) {
            if (evtPart.has(ch.getKey())) {
                logger.debug("updating channel {}", ch.getKey());
                JsonElement st = evtPart.get(ch.getKey());
                String chstate = (!st.isJsonNull()) ? st.toString() : null;
                updateState((ch.getValue()),
                        ((@NonNull frigateSVRChannelState) this.Channels.get(ch.getValue())).toState(chstate));
            } else {
                // if we have a channel that doesn't exist in the
                // event packet back from the camera, we must null out the
                // channel.
                logger.debug("channel {} does not exist; nulling", ch.getKey());
                updateState((ch.getValue()),
                        ((@NonNull frigateSVRChannelState) this.Channels.get(ch.getValue())).toNullState());
            }
        }
    };

    //////////////////////////////////////////////////////////////////
    // frigateSVRCameraHandler
    //
    // We build the channel map in the constructor

    public frigateSVRCameraHandler(Thing thing, frigateSVRServices services) {
        super(thing);

        // Camera channel map

        this.Channels = Map.ofEntries(
                Map.entry(CHANNEL_CAM_CAMFPS,
                        new frigateSVRChannelState("camera_fps", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_CAM_PROCESSFPS,
                        new frigateSVRChannelState("process_fps", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_CAM_SKIPPEDFPS,
                        new frigateSVRChannelState("skipped_fps", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_CAM_DETECTIONFPS,
                        new frigateSVRChannelState("detection_fps", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_STATE_DETECTION,
                        new frigateSVRChannelState(MQTT_DETECTION_SET, frigateSVRChannelState::fromSwitchMQTT,
                                frigateSVRChannelState::toSwitchMQTT, true)),
                Map.entry(CHANNEL_STATE_RECORDING,
                        new frigateSVRChannelState(MQTT_RECORDING_SET, frigateSVRChannelState::fromSwitchMQTT,
                                frigateSVRChannelState::toSwitchMQTT, true)),
                Map.entry(CHANNEL_STATE_SNAPSHOTS,
                        new frigateSVRChannelState(MQTT_SNAPSHOTS_SET, frigateSVRChannelState::fromSwitchMQTT,
                                frigateSVRChannelState::toSwitchMQTT, true)),
                Map.entry(CHANNEL_STATE_MOTIONDET,
                        new frigateSVRChannelState(MQTT_MOTIONDET_SET, frigateSVRChannelState::fromSwitchMQTT,
                                frigateSVRChannelState::toSwitchMQTT, true)),
                Map.entry(CHANNEL_STATE_IMPCONTRAST,
                        new frigateSVRChannelState(MQTT_IMPCONTRAST_SET, frigateSVRChannelState::fromSwitchMQTT,
                                frigateSVRChannelState::toSwitchMQTT, true)),
                Map.entry(CHANNEL_STATE_MOTIONTHRESH,
                        new frigateSVRChannelState(MQTT_MOTIONTHRESH_SET, frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, true)),
                Map.entry(CHANNEL_STATE_MOTIONCONTOUR,
                        new frigateSVRChannelState(MQTT_MOTIONCONTOUR_SET, frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, true)),
                Map.entry(CHANNEL_STATE_MOTIONDETECTED,
                        new frigateSVRChannelState(MQTT_MOTION, frigateSVRChannelState::fromContactMQTT,
                                frigateSVRChannelState::toContactMQTT, false)),
                Map.entry(CHANNEL_EVENT_JSON,
                        new frigateSVRChannelState("", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_EVENT_TYPE,
                        new frigateSVRChannelState("update", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_EVENT_ID,
                        new frigateSVRChannelState("id", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_EVENT_CLIP_URL,
                        new frigateSVRChannelState("", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_PREV_FRAME_TIME,
                        new frigateSVRChannelState("frame_time", frigateSVRChannelState::fromTimestampMQTT,
                                frigateSVRChannelState::toTimestampMQTT, false)),
                Map.entry(CHANNEL_PREV_SNAPSHOT_TIME,
                        new frigateSVRChannelState("snapshot_time", frigateSVRChannelState::fromTimestampMQTT,
                                frigateSVRChannelState::toTimestampMQTT, false)),
                Map.entry(CHANNEL_PREV_LABEL,
                        new frigateSVRChannelState("label", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_PREV_SUBLABEL,
                        new frigateSVRChannelState("sub_label", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_PREV_TOP_SCORE,
                        new frigateSVRChannelState("top_score", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_PREV_FALSE_POSITIVE,
                        new frigateSVRChannelState("false_positive", frigateSVRChannelState::fromContactJSON,
                                frigateSVRChannelState::toContactJSON, false)),
                Map.entry(CHANNEL_PREV_START_TIME,
                        new frigateSVRChannelState("end_time", frigateSVRChannelState::fromTimestampMQTT,
                                frigateSVRChannelState::toTimestampMQTT, false)),
                Map.entry(CHANNEL_PREV_END_TIME,
                        new frigateSVRChannelState("end_time", frigateSVRChannelState::fromTimestampMQTT,
                                frigateSVRChannelState::toTimestampMQTT, false)),
                Map.entry(CHANNEL_PREV_SCORE,
                        new frigateSVRChannelState("score", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_PREV_BOX,
                        new frigateSVRChannelState("box", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_PREV_AREA,
                        new frigateSVRChannelState("area", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_PREV_RATIO,
                        new frigateSVRChannelState("ratio", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_PREV_REGION,
                        new frigateSVRChannelState("region", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_PREV_CURRENT_ZONE,
                        new frigateSVRChannelState("current_zones", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_PREV_ENTERED_ZONE,
                        new frigateSVRChannelState("entered_zones", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_PREV_HAS_SNAPSHOT,
                        new frigateSVRChannelState("has_snapshot", frigateSVRChannelState::fromContactJSON,
                                frigateSVRChannelState::toContactJSON, false)),
                Map.entry(CHANNEL_PREV_HAS_CLIP,
                        new frigateSVRChannelState("has_clip", frigateSVRChannelState::fromContactJSON,
                                frigateSVRChannelState::toContactJSON, false)),
                Map.entry(CHANNEL_PREV_STATIONARY,
                        new frigateSVRChannelState("stationary", frigateSVRChannelState::fromContactJSON,
                                frigateSVRChannelState::toContactJSON, false)),
                Map.entry(CHANNEL_PREV_MOTIONLESSCOUNT,
                        new frigateSVRChannelState("motionless_count", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_PREV_POSITIONCHANGES,
                        new frigateSVRChannelState("position_changes", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_PREV_MAXSEVERITY,
                        new frigateSVRChannelState("max_severity", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_CUR_FRAME_TIME,
                        new frigateSVRChannelState("frame_time", frigateSVRChannelState::fromTimestampMQTT,
                                frigateSVRChannelState::toTimestampMQTT, false)),
                Map.entry(CHANNEL_CUR_SNAPSHOT_TIME,
                        new frigateSVRChannelState("snapshot_time", frigateSVRChannelState::fromTimestampMQTT,
                                frigateSVRChannelState::toTimestampMQTT, false)),
                Map.entry(CHANNEL_CUR_LABEL,
                        new frigateSVRChannelState("label", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_CUR_SUBLABEL,
                        new frigateSVRChannelState("sub_label", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_CUR_TOP_SCORE,
                        new frigateSVRChannelState("top_score", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_CUR_FALSE_POSITIVE,
                        new frigateSVRChannelState("false_positive", frigateSVRChannelState::fromContactJSON,
                                frigateSVRChannelState::toContactJSON, false)),
                Map.entry(CHANNEL_CUR_START_TIME,
                        new frigateSVRChannelState("end_time", frigateSVRChannelState::fromTimestampMQTT,
                                frigateSVRChannelState::toTimestampMQTT, false)),
                Map.entry(CHANNEL_CUR_END_TIME,
                        new frigateSVRChannelState("end_time", frigateSVRChannelState::fromTimestampMQTT,
                                frigateSVRChannelState::toTimestampMQTT, false)),
                Map.entry(CHANNEL_CUR_SCORE,
                        new frigateSVRChannelState("score", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_CUR_BOX,
                        new frigateSVRChannelState("box", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_CUR_AREA,
                        new frigateSVRChannelState("area", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_CUR_RATIO,
                        new frigateSVRChannelState("ratio", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_CUR_REGION,
                        new frigateSVRChannelState("region", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_CUR_CURRENT_ZONE,
                        new frigateSVRChannelState("current_zones", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_CUR_ENTERED_ZONE,
                        new frigateSVRChannelState("entered_zones", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_CUR_HAS_SNAPSHOT,
                        new frigateSVRChannelState("has_snapshot", frigateSVRChannelState::fromContactJSON,
                                frigateSVRChannelState::toContactJSON, false)),
                Map.entry(CHANNEL_CUR_HAS_CLIP,
                        new frigateSVRChannelState("has_clip", frigateSVRChannelState::fromContactJSON,
                                frigateSVRChannelState::toContactJSON, false)),
                Map.entry(CHANNEL_CUR_STATIONARY,
                        new frigateSVRChannelState("stationary", frigateSVRChannelState::fromContactJSON,
                                frigateSVRChannelState::toContactJSON, false)),
                Map.entry(CHANNEL_CUR_MOTIONLESSCOUNT,
                        new frigateSVRChannelState("motionless_count", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_CUR_POSITIONCHANGES,
                        new frigateSVRChannelState("position_changes", frigateSVRChannelState::fromNumberMQTT,
                                frigateSVRChannelState::toNumberMQTT, false)),
                Map.entry(CHANNEL_CUR_MAXSEVERITY,
                        new frigateSVRChannelState("max_severity", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_LAST_SNAPSHOT_OBJECT,
                        new frigateSVRChannelState("", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_LAST_SNAPSHOT,
                        new frigateSVRChannelState("", frigateSVRChannelState::fromNoConversion,
                                frigateSVRChannelState::toNoConversion, false)),
                Map.entry(CHANNEL_STREAM_URL,
                        new frigateSVRChannelState("", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_LAST_FRAME,
                        new frigateSVRChannelState("", frigateSVRChannelState::fromNoConversion,
                                frigateSVRChannelState::toNoConversion, false)),
                Map.entry(CHANNEL_OBJ_COUNT,
                        new frigateSVRChannelState("", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_OBJ_COUNT_ACTIVE,
                        new frigateSVRChannelState("", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_ACTION_LAST_FRAME,
                        new frigateSVRChannelState("", frigateSVRChannelState::fromNoConversion,
                                frigateSVRChannelState::toNoConversion, false)),
                Map.entry(CHANNEL_ACTION_EVENT_THUMBNAIL, new frigateSVRChannelState("",
                        frigateSVRChannelState::fromNoConversion, frigateSVRChannelState::toNoConversion, false)));

        // Required for frigate re-streams

        this.networkHelper = new frigateSVRNetworkHelper(services);
        this.httpServlet = new frigateSVRServlet(services.httpService);
    }

    ///////////////////////////////////////////////////////////////////////////
    /// getServices
    ///
    /// Required to enable the Thing actions
    ///

    @Override
    public Collection<Class<? extends ThingHandlerService>> getServices() {
        return Collections.singleton(CameraActions.class);
    }

    ///////////////////////////////////////////////////////////////////////////
    /// UpdateChannel
    ///
    /// Update the state channel from a selected ID - primarily implemented
    /// to support ThingActions.

    public void UpdateChannel(String ChannelID, ResultStruct rc) {

        if (this.Channels.containsKey(ChannelID)) {
            updateState(ChannelID, ((@NonNull frigateSVRChannelState) this.Channels.get(ChannelID)).toState(rc));
        } else {
            logger.error("channel {} not in channel map, ignoring", ChannelID);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    /// Initialize
    ///

    @Override
    public void initialize() {

        config = getConfigAs(frigateSVRCameraConfiguration.class);

        logger.debug("camera {} INITIALIZATION handler called ", config.cameraName);

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING);

        Bridge bridge = getBridge();

        ThingStatusInfo bridgeStatus = (bridge != null) ? bridge.getStatusInfo()
                : new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.NONE, null);

        this.bridgeStatusChanged(bridgeStatus);
    }

    ///////////////////////////////////////////////////////////////////////////
    // dispose.
    //
    // General cleanup

    @Override
    public void dispose() {
        logger.debug("camera {} dispose handler called", config.cameraName);
        this.SetOffline();
        logger.debug("dispose: handler being destroyed");
        this.httpServlet.StopServer();
        super.dispose();
    }

    /////////////////////////////////////////////////////////////////////////
    // bridgeStatusChanged
    //
    // If the MQTT handler changes status, we check that we have both a valid
    // MQTT connection AND a valid config/version from the HTTP API before we
    // notify ourselves as online.

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {

        Bridge bridge = getBridge();
        BridgeHandler bridgeHandler = bridge.getHandler();

        if (bridgeHandler instanceof frigateSVRServerHandler) {

            if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
                MQTTConnection = null;
                this.BridgeGoingOffline();
                return;
            }
            if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
                MQTTConnection = null;
                this.BridgeGoingOffline();
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
                return;
            } else {
                BridgeGoingOnline();
            }
        }
    }

    ///////////////////////////////////////////////////////////////////
    // GetBaseURL
    //
    // Return the base URL of the Frigate server as seen by the
    // internal HTTP client

    public String GetBaseURL() {
        return this.httpHelper.getBaseURL();
    }

    ///////////////////////////////////////////////////////////////////
    // GetPTZCaps
    //
    // Return the PTZ caps for the camera

    public CameraPTZCaps GetPTZCaps() {
        return this.PTZCaps;
    }

    ///////////////////////////////////////////////////////////////////
    // GetHostAndPort
    //
    // Return the host and port of the attached Frigate server

    public String GetHostAndPort() {
        return this.httpHelper.getHostAndPort();
    }

    ///////////////////////////////////////////////////////////////////
    // GetBaseURL
    //
    // Return the base URL of the Frigate server as seen by the
    // internal HTTP client

    public @Nullable ThingUID GetBridgeUID() {
        if (this.getBridge() != null) {
            return ((@NonNull Bridge) getBridge()).getUID();
        } else {
            return null;
        }
    }

    ///////////////////////////////////////////////////////////////////
    // handleCommand
    //
    // Used and required only by inheriting classes

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {

        if (this.Channels.containsKey(channelUID.getId())) {
            frigateSVRChannelState s = this.Channels.get(channelUID.getId());
            if (command instanceof RefreshType) {
                logger.debug("Refreshing channel :{} value :{}", channelUID.getId(), s.state.toString());
                updateState(channelUID.getId(), s.state);
            } else {
                if (s.commandable) {
                    if ((command instanceof OnOffType) || (command instanceof OpenClosedType)
                            || (command instanceof DecimalType)) {
                        this.Channels.get(channelUID.getId()).state = (State) command;
                        String payload = ((@NonNull frigateSVRChannelState) this.Channels.get(channelUID.getId()))
                                .toMQTT();
                        logger.debug("Setting channel {} to {}", channelUID.getId(), payload);
                        SendMQTTCommand(s.MQTTTopicSuffix, payload);
                    }
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////
    // SendActionEvent
    //
    // Process an action event.
    //
    // This changes in v3.x.
    // Firstly many of the calls no longer need to be asynchronous.
    // Secondly, the processing is carried out in the ThingAction API
    // which then calls camera (MQTT) or server APIs (HTTP) as necessary
    //
    // Server ThingActions are only processed by the server handler,
    // we may not exist if only a server handler is present in the system

    public ResultStruct SendActionEvent(APIBase action) {

        Bridge serverThing = getBridge();
        ResultStruct rc = new ResultStruct();

        do {
            if (serverThing == null) {
                rc.message = "Could not find bridge handler";
                break;
            }

            BridgeHandler serverBridgeHandler = serverThing.getHandler();

            if (serverBridgeHandler == null) {
                rc.message = "wrong bridge handler type";
                break;
            }

            action.SetCamera(config.cameraName);
            rc = action.Validate();
            if (!rc.rc) {
                break;
            }

            frigateSVRServerHandler serverHandler = (frigateSVRServerHandler) serverBridgeHandler;

            rc = action.Process(serverHandler.getAPIHelper(), this);

        } while (false);
        return rc;
    }

    ///////////////////////////////////////////////////////////////////
    // BridgeGoingOffline
    //
    // A callback when the MQTT bridge is going offline. We just need
    // to update the state here and stop the streaming server

    private void BridgeGoingOffline() {
        SetOffline();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, "@text/error.bridgeoffline");
    }

    ///////////////////////////////////////////////////////////////////
    // BridgeGoingOnline
    //
    // A callback when the MQTT bridge is going online

    private void BridgeGoingOnline() {

        logger.info("camera {}: bridge going online", config.cameraName);

        Bridge bridge = getBridge();

        if (bridge != null) {

            BridgeHandler bridgeHandler = bridge.getHandler();

            if (bridgeHandler instanceof frigateSVRServerHandler) {

                frigateSVRServerHandler fb = (frigateSVRServerHandler) bridgeHandler;
                frigateSVRFrigateConfigBlock configBlock = fb.getFrigateConfig().block;
                frigateSVRServerConfiguration svrCfg = fb.GetServerConfig();

                this.useRelativeURLs = svrCfg.useRelativeURLs;
                this.pfxFrigateToCam = configBlock.mqtt.topicPrefix + "/" + config.cameraName;
                // this.pfxFrigateInstance = configBlock.mqtt.topicPrefix;
                logger.debug("Set camera topic to {}", this.pfxFrigateToCam);

                this.MQTTConnection = fb.getMQTTConnection();

                // We can retrieve the server state and the tracked object list directly from the
                // upstream bridge.

                this.svrState = fb.GetServerState();
                this.trackedObjects = fb.GetTrackedObjectList();
                this.PTZCaps = fb.getAPIHelper().GetCameraPTZCaps(this.config.cameraName);

                // subscribe to MQTT, start the camera stream, and flag us online

                SubscribeMQTTTopics();
                scheduler.execute(() -> {
                    StartCameraStream();
                });

                logger.debug("Camera {} onlining complete", this.config.cameraName);
                updateStatus(ThingStatus.ONLINE);

            }
        }
    }

    ///////////////////////////////////////////////////////////////////
    // StartCameraStream
    //
    // If camera local stream is enabled, then start it.

    private void StartCameraStream() {
        if (this.config.enableStream == true) {

            String ffmpegSource = this.svrState.rtspbase + "/";

            if (this.config.ffmpegCameraNameOverride.equals("")) {
                ffmpegSource += this.config.cameraName;
            } else {
                ffmpegSource += this.config.ffmpegCameraNameOverride;
            }

            String serverBase = "/" + this.svrState.clientID + "/" + this.getThing().getUID().getId();
            String viewURL = this.svrState.URLChannelPrefix + "/" + this.getThing().getUID().getId() + "/camera";

            ArrayList<HTTPHandler> handlers = new ArrayList<HTTPHandler>();
            handlers.add(new MJPEGStream("camera", this.svrState.ffmpegPath, ffmpegSource, serverBase, config));
            handlers.add(new HLSStream("camera", this.svrState.ffmpegPath, ffmpegSource, config));
            handlers.add(new DASHStream("camera", this.svrState.ffmpegPath, ffmpegSource, config));

            logger.debug("camera-thing: starting streaming server");
            this.httpServlet.SetWhitelist(this.svrState.whitelist);
            this.httpServlet.StartServer(serverBase, handlers);

            logger.debug("Multistream server process running");
            updateState(CHANNEL_STREAM_URL,
                    ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_STREAM_URL)).toState(viewURL));
        } else {
            logger.debug("Multistream server process disabled in configuration");
            updateState(CHANNEL_STREAM_URL,
                    ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_STREAM_URL)).toState(""));
        }
    }

    ////////////////////////////////////////////////
    // SendMQTTCommand
    //
    // Send an MQTT string to the Frigate server.

    public void SendMQTTCommand(String suffix, String command) {
        MqttBrokerConnection conn = this.MQTTConnection;
        logger.debug("Process command: mqttConnection != null?: {}", conn != null);
        if (conn != null) {
            String topic = this.svrState.topicPrefix + "/" + config.cameraName + "/" + suffix;
            logger.debug("Sending command to topic: {} command: {}", topic, command.getBytes());
            conn.publish(topic, command.getBytes(), 1, false);
        }
    }

    ///////////////////////////////////////////////////////////////////
    // SubscribeMQTTTopics
    //
    // Called during initialization to subscribe to the relevant MQTT
    // topics other than the server status messages. In other words,
    // all MQTT messages originating from Frigate

    private void SubscribeMQTTTopics() {
        MqttBrokerConnection conn = this.MQTTConnection;
        if (conn != null) {
            conn.subscribe(this.svrState.topicPrefix + "/" + MQTT_EVENTS_SUFFIX, this);
            conn.subscribe(this.svrState.topicPrefix + "/" + MQTT_STATS_SUFFIX, this);
            conn.subscribe(this.pfxFrigateToCam + "/#", this);
        }
    }

    ///////////////////////////////////////////////////////////////////
    // UnsubscribeMQTTTopics
    //
    // Called when offlined to unsubscribe the MQTT topics originating
    // from Frigate itself, rather than from the server Thing.

    private void UnsubscribeMQTTTopics() {

        logger.debug("camera {}: unsubscribing from MQTT", config.cameraName);
        MqttBrokerConnection conn = this.MQTTConnection;
        if (conn != null) {
            conn.unsubscribe(this.pfxFrigateToCam + "/#", this);
            conn.unsubscribe(this.svrState.topicPrefix + "/" + MQTT_STATS_SUFFIX, this);
            conn.unsubscribe(this.svrState.topicPrefix + "/" + MQTT_EVENTS_SUFFIX, this);
        } else {
            logger.debug("unsubscribe: connection is null");
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // SetOffline
    //
    // Mechanics of setting ourselves offline. When a config change
    // comes in, it seems that 'initialize' is called again. This
    // function ensures we are in a known offline state before
    // resetting ourselves

    private void SetOffline() {

        logger.debug("camera: SetOffline called, stopping streamer");
        UnsubscribeMQTTTopics();
        scheduler.execute(() -> {
            this.httpServlet.StopServer();
        });
        logger.debug("offlining device");
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
    }

    ///////////////////////////////////////////////////////////////////////////
    // processMessage
    //
    // Process incoming MQTT messages for this camera.

    @Override
    public void processMessage(String topic, byte[] payload) {

        String state = new String(payload, StandardCharsets.UTF_8);

        do {

            logger.debug("cam {}: Receiving message on topic: {}", config.cameraName, topic);

            // Messages direct from Frigate server

            String action = topic.substring(this.svrState.topicPrefix.length() + 1);
            String[] bits = action.split("/"); // bits[0] will be message-dependent

            logger.debug("cam {}: Received trimmed server message {} (pfxSvrMsg test)", config.cameraName, action);

            // Camera stats

            if (action.endsWith(MQTT_STATS_SUFFIX)) {
                JsonObject statObj = JsonParser.parseString(state).getAsJsonObject();
                if (statObj.has("cameras")) {
                    statObj = statObj.get("cameras").getAsJsonObject();
                    if (statObj.has(config.cameraName)) {
                        logger.debug("have status for camera {}", config.cameraName);
                        JsonObject statusBlock = statObj.get(config.cameraName).getAsJsonObject();
                        HandleEventPart.accept(JSONStateGetters, statusBlock);
                    } else {

                        // If we don't have the camera listed in the status message, then
                        // we iterate the status channels and null them out. This avoids
                        // confusion with 'old' states being left in place if Frigate sends us
                        // status without the camera in the block

                        for (var ch : JSONStateGetters.entrySet()) {
                            updateState((ch.getValue()),
                                    ((@NonNull frigateSVRChannelState) this.Channels.get(ch.getValue())).toNullState());
                        }
                    }
                }

                // do keep-alive stuff for streams here

                this.httpServlet.PokeMe();
                break;
            }

            // Events
            //

            if (action.endsWith(MQTT_EVENTS_SUFFIX)) {

                // Frigate sends us a complex event consisting of the current state
                // along with the previous state.
                //
                // Note that events do not use the camera prefix in the topic. We get
                // all events for all cameras, so we have to filter here. It is a shame
                // that Frigate does not discriminate by camera allowing the topic subscription
                // to do the filtering for us.

                // we are handling an event. The bag of bits is actually a bunch of JSON.
                // By parsing the JSON, rather than stuffing it into a Java object, we
                // can make the channel handling more generic.

                JsonObject evtObj = JsonParser.parseString(state).getAsJsonObject();
                String evtType = evtObj.get("type").getAsString();
                JsonObject evtPrev = evtObj.get("before").getAsJsonObject();
                JsonObject evtCur = evtObj.get("after").getAsJsonObject();

                // first check the camera name

                String cam = evtCur.get("camera").getAsString();
                if (config.cameraName.equals(cam)) {

                    // start with current stuff, then process the previous state

                    HandleEventPart.accept(JSONEventGettersToCur, evtCur);
                    HandleEventPart.accept(JSONEventGettersToPrev, evtPrev);

                    // now deal with the id, snapshot URL and finally update the event
                    // type. We do these manually rather than from the getter map - to
                    // control sequencing - with the event type last.

                    String id = evtCur.get("id").getAsString();
                    String hasClip = evtCur.get("has_clip").getAsString();

                    String ecURL = new String("");
                    if (hasClip.equals("true")) {
                        ecURL = this.svrState.url + "api/events/" + id + "/clip.mp4";
                    } else {
                        ecURL = "";
                    }

                    // update 'em

                    updateState(CHANNEL_EVENT_CLIP_URL,
                            ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_EVENT_CLIP_URL))
                                    .toState(ecURL));
                    updateState(CHANNEL_EVENT_ID,
                            ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_EVENT_ID)).toState(id));
                    updateState(CHANNEL_EVENT_JSON,
                            ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_EVENT_JSON)).toState(state));
                    updateState(CHANNEL_EVENT_TYPE,
                            ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_EVENT_TYPE)).toState(evtType));
                }
                break;
            }

            //
            // messages between Frigate server direct to cameras

            if (action.startsWith(config.cameraName + "/")) {

                String cammsg = topic.substring(this.pfxFrigateToCam.length() + 1);
                logger.debug("cam {}: Received trimmed Frigate server->camera message :{}", config.cameraName, cammsg);

                // MQTT messages pertaining to configuration other than events:

                if (this.MQTTGettersToChannels.containsKey(cammsg)) {
                    String channel = this.MQTTGettersToChannels.get(cammsg);
                    if (channel != null) {
                        updateState(channel,
                                ((@NonNull frigateSVRChannelState) this.Channels.get(channel)).toState(state));
                    }
                    break;
                }

                // Here, we have now covered the bases where the channel is a standard getter.

                // Object counts and snapshots.
                //
                // Now, if we have the format <camname>/<object_name>/<something>, then the
                // 'something' could be empty (a total count), 'active' (a count of active objects
                // or 'snapshot' - a snapshot. We need to check the object name and the suffix.
                // In this case, bits[0] will be the cam name, bits[1] the obj name, and bits[2]
                // the 'something' if it exists. Why Frigate couldn't have added a <something>
                // for total counts is a complete mystery....and would simplify the code

                if (bits.length >= 2) { // bits[0] is the prefix

                    if (this.trackedObjects.contains(bits[1]) || bits[1].equals("all")) {
                        logger.info("camera: {}: have tracked object: {}", config.cameraName, bits[1]);
                        String MsgType = "totcount";

                        if (bits.length == 3) {
                            // the message will be a snapshot or an active count
                            MsgType = bits[2];
                        }

                        String objcount = "";
                        switch (MsgType) {

                            case "totcount":
                                objcount = new String("{ \"object\" : \"" + bits[1] + "\", \"count\" : " + state + "}");
                                this.updateState(CHANNEL_OBJ_COUNT,
                                        ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_OBJ_COUNT))
                                                .toState(objcount));
                                break;

                            case "active":
                                objcount = new String("{ \"object\" : \"" + bits[1] + "\", \"count\" : " + state + "}");
                                this.updateState(CHANNEL_OBJ_COUNT_ACTIVE,
                                        ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_OBJ_COUNT_ACTIVE))
                                                .toState(objcount));
                                break;

                            case "snapshot":
                                logger.debug("received snapshot for cam {} object {}", config.cameraName, bits[1]);
                                this.updateState(CHANNEL_LAST_SNAPSHOT_OBJECT,
                                        ((@NonNull frigateSVRChannelState) this.Channels
                                                .get(CHANNEL_LAST_SNAPSHOT_OBJECT)).toState(bits[1]));
                                this.updateState(CHANNEL_LAST_SNAPSHOT,
                                        ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_LAST_SNAPSHOT))
                                                .toStateFromRaw(payload, "image/jpeg"));
                                break;

                            default:
                                logger.warn("received unknown camera message {} for cam {}", MsgType,
                                        config.cameraName);
                                break;

                        }

                        break;
                    }
                }
            }

            logger.debug("no handler for topic: {}", topic);

        } while (false);
    }
}
