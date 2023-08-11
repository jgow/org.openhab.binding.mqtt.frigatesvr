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
package org.openhab.binding.mqtt.frigatesvr.internal.handlers;

import static org.openhab.binding.mqtt.frigatesvr.internal.frigateSVRBindingConstants.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRCameraConfiguration;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRChannelState;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRServerState;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
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
public class frigateSVRCameraHandler extends frigateSVRHandlerBase implements MqttMessageSubscriber {

    private final Logger logger = LoggerFactory.getLogger(frigateSVRCameraHandler.class);
    private frigateSVRCameraConfiguration config = new frigateSVRCameraConfiguration();
    private frigateSVRServerState svrState = new frigateSVRServerState();
    private String svrTopicPrefix = "frigate/";
    private String cameraTopicPrefix = new String();

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
            Map.entry("position_changes", CHANNEL_PREV_POSITIONCHANGES));

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
            Map.entry("position_changes", CHANNEL_CUR_POSITIONCHANGES));

    private Map<String, String> JSONStateGetters = Map.ofEntries(Map.entry("camera_fps", CHANNEL_CAM_CAMFPS),
            Map.entry("process_fps", CHANNEL_CAM_PROCESSFPS), Map.entry("skipped_fps", CHANNEL_CAM_SKIPPEDFPS),
            Map.entry("detection_fps", CHANNEL_CAM_DETECTIONFPS));

    //
    // Good, now we need a generic way of extracting data to channels based on the mappings in
    // the getters and the channel map.

    private java.util.function.BiConsumer<Map<String, String>, JsonObject> HandleEventPart = (getter, evtPart) -> {
        for (var ch : getter.entrySet()) {
            if (evtPart.has(ch.getKey())) {
                logger.debug("Cur: updating channel {}", ch.getKey());
                JsonElement st = evtPart.get(ch.getKey());
                String chstate = (!st.isJsonNull()) ? st.toString() : null;
                updateState((@NonNull String) (ch.getValue()),
                        ((@NonNull frigateSVRChannelState) this.Channels.get(ch.getValue())).toState(chstate));
            } else {
                // if we have a channel that doesn't exist in the
                // event packet back from the camera, we must null out the
                // channel.
                updateState((@NonNull String) (ch.getValue()),
                        ((@NonNull frigateSVRChannelState) this.Channels.get(ch.getValue())).toState(null));
            }
        }
    };

    //////////////////////////////////////////////////////////////////
    // frigateSVRCameraHandler
    //
    // We build the channel map in the constructor

    public frigateSVRCameraHandler(Thing thing, HttpClient httpClient, HttpService httpService) {

        super(thing, httpClient, httpService);
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
                Map.entry(CHANNEL_LAST_SNAPSHOT_OBJECT,
                        new frigateSVRChannelState("", frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_LAST_SNAPSHOT,
                        new frigateSVRChannelState("", frigateSVRChannelState::fromNoConversion,
                                frigateSVRChannelState::toNoConversion, false)),
                Map.entry(CHANNEL_MJPEG_URL, new frigateSVRChannelState("", frigateSVRChannelState::fromStringMQTT,
                        frigateSVRChannelState::toStringMQTT, false)));
    }

    @Override
    public void initialize() {
        config = getConfigAs(frigateSVRCameraConfiguration.class);
        this.svrTopicPrefix = "frigateSVR/" + config.serverID;
        logger.info("marking camera config pending");
        this.SetOffline();
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING);
        super.initialize();
    }

    //
    // Cleanup.

    @Override
    public void dispose() {
        if (this.MQTTConnection != null) {
            UnsubscribeMQTTTopics(this.svrState.topicPrefix);
            ((@NonNull MqttBrokerConnection) this.MQTTConnection).unsubscribe(this.svrTopicPrefix + "/status", this);
        }
        super.dispose();
    }

    ///////////////////////////////////////////////////////////////////
    // BridgeGoingOffline
    //
    // A callback when the MQTT bridge is going offline. We just need
    // to update the state here

    protected void BridgeGoingOffline() {
        this.httpServlet.StopServer();
        this.svrState.status = "offline";
    }

    ///////////////////////////////////////////////////////////////////
    // BridgeGoingOnline
    //
    // A callback when the MQTT bridge is going online

    protected void BridgeGoingOnline(MqttBrokerConnection connection) {

        ((@NonNull MqttBrokerConnection) this.MQTTConnection).subscribe(this.svrTopicPrefix + "/status", this);
        logger.info("publishing req. for status: {}/camOnLine", this.svrTopicPrefix);

        // tell the server we are going online

        ((@NonNull MqttBrokerConnection) this.MQTTConnection).publish(this.svrTopicPrefix + "/camOnLine",
                this.config.cameraName.getBytes(), 1, false);
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING);
    }

    ///////////////////////////////////////////////////////////////////
    // HandleServerChange
    //
    // Handle changes in the state of the server

    private void HandleServerStateChange(frigateSVRServerState newState) {

        // Ok, if we don't have an MQTT connection, we will be offlined
        // anyway by the bridge handlers. Then, we will request a new
        // server state when the bridge comes back. So we only need
        // handle the state changes when we have a connection:

        logger.debug("handling server state change - old state: {} new state : {}", this.svrState.status,
                newState.status);

        if (this.MQTTConnection != null) {

            // if we are online, we are looking for changes to offline

            if ((this.svrState.status.equals("online")) && (newState.status.equals("offline"))) {

                SetOffline();
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "Frigate server went offline");

            }

            // if we are offline, we are looking for changes online

            if ((this.svrState.status.equals("offline")) && (newState.status.equals("online"))) {

                logger.info("server going online: Number of cams: {}", newState.Cameras.size());

                for (String cam : newState.Cameras) {
                    logger.debug("have camera {}", cam);
                }

                // check that the camera is indeed one of ours:
                if (newState.Cameras.contains(this.config.cameraName)) {
                    // we are ok.

                    this.svrState = newState;
                    this.cameraTopicPrefix = this.svrState.topicPrefix + "/" + config.cameraName + "/";

                    // subscribe to MQTT, start the camera stream, and flag us online

                    SubscribeMQTTTopics(this.svrState.topicPrefix);
                    StartCameraStream();
                    updateStatus(ThingStatus.ONLINE);
                } else {
                    logger.info("Camera {} not found in list", this.config.cameraName);
                }
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

            String serverBase = new String("/frigateSVR/") + this.getThing().getUID().getId();
            String viewURL = this.networkHelper.GetHostBaseURL() + serverBase + "/camera";

            this.httpServlet.SetWhitelist(this.svrState.whitelist);
            this.httpServlet.StartServer(serverBase, "camera", this.svrState.ffmpegPath, ffmpegSource,
                    config.ffmpegCommands);

            logger.info("MJPEG streaming process running");
            updateState(CHANNEL_MJPEG_URL,
                    ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_MJPEG_URL)).toState(viewURL));
        } else {
            logger.info("MJPEG streaming disabled in configuration");
            updateState(CHANNEL_MJPEG_URL,
                    ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_MJPEG_URL)).toState(""));
        }
    }

    ////////////////////////////////////////////////
    // ProcessCommand
    //
    // Send an MQTT string to the camera.

    protected void ProcessCommand(String suffix, String command) {
        if (this.MQTTConnection != null) {
            String topic = this.svrState.topicPrefix + "/" + config.cameraName + "/" + suffix;
            ((@NonNull MqttBrokerConnection) this.MQTTConnection).publish(topic, command.getBytes(), 1, false);
        }
    }

    ///////////////////////////////////////////////////////////////////
    // SubscribeMQTTTopics
    //
    // Called during initialization to subscribe to the relevant MQTT
    // topics other than the server status messages.

    private void SubscribeMQTTTopics(String prefix) {
        MqttBrokerConnection conn = this.MQTTConnection;
        if (conn != null) {
            // - initially the events
            conn.subscribe(prefix + "/events", this);
            // - and then all snapshots intended for this cam.
            conn.subscribe(this.cameraTopicPrefix + "+/snapshot", this);
            conn.subscribe(prefix + "/" + MQTT_STATS_SUFFIX, this);
            conn.subscribe(prefix + "/" + MQTT_KEEPALIVE_SUFFIX, this);
            this.MQTTGettersToChannels.forEach((m, ch) -> conn.subscribe(this.cameraTopicPrefix + m, this));
        }
    }

    ///////////////////////////////////////////////////////////////////
    // UnsubscribeMQTTTopics
    //
    // Called when offlined to unsubscribe the MQTT topics used

    private void UnsubscribeMQTTTopics(String prefix) {

        MqttBrokerConnection conn = this.MQTTConnection;
        if (conn != null) {
            // snapshot state
            this.MQTTGettersToChannels.forEach((m, ch) -> conn.unsubscribe(this.cameraTopicPrefix + m, this));
            conn.unsubscribe(prefix + "/" + MQTT_KEEPALIVE_SUFFIX, this);
            conn.unsubscribe(prefix + "/" + MQTT_STATS_SUFFIX, this);
            conn.unsubscribe(this.cameraTopicPrefix + "+/snapshot", this);
            conn.unsubscribe(prefix + "/events", this);
        }
    }

    //////////////////////////////////////////////////////////////////
    // SetOffline
    //
    // Mechanics of setting ourselves offline. When a config change
    // comes in, it seems that 'initialize' is called again. This
    // function ensures we are in a known offline state before
    // resetting ourselves

    private void SetOffline() {
        this.httpServlet.StopServer();
        UnsubscribeMQTTTopics(this.svrState.topicPrefix);
        this.svrState.status = "offline";
        logger.debug("offlining device");
    }

    /////////////////////////////////////////////////////
    // processMessage
    //
    // Process incoming MQTT messages for this camera.

    @Override
    public void processMessage(String topic, byte[] payload) {

        super.processMessage(topic, payload);

        String state = new String(payload, StandardCharsets.UTF_8);

        //
        // Server status messages
        //
        // When the Frigate server goes on/offline, we are sent a status message
        // to enable us to speak both with the server and with our cameras. We
        // are offline until the first of these messages arrives and lets us
        // know the server is online

        if (topic.equals(this.svrTopicPrefix + "/status")) {
            logger.debug("received status update on {}/status", this.svrTopicPrefix);
            String evtJSON = new String(payload, StandardCharsets.UTF_8);
            frigateSVRServerState newState = new Gson().fromJson(evtJSON, frigateSVRServerState.class);
            this.HandleServerStateChange((@NonNull frigateSVRServerState) newState);
        }

        // Keepalives
        //
        // We take our keepalive cue from the server keepalive thread.

        if (topic.equals(this.svrTopicPrefix + "/" + MQTT_KEEPALIVE_SUFFIX)) {
            this.httpServlet.PokeMe();
        }

        // if we are marked offline, do not handle these.

        if (this.getThing().getStatus().equals(ThingStatus.ONLINE)) {

            // Camera stats
            //

            if (topic.equals(this.svrState.topicPrefix + "/" + MQTT_STATS_SUFFIX)) {
                JsonObject statObj = JsonParser.parseString(state).getAsJsonObject();
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
                        updateState((@NonNull String) (ch.getValue()),
                                ((@NonNull frigateSVRChannelState) this.Channels.get(ch.getValue())).toState(null));
                    }
                }
            }

            // Events
            //
            // Frigate sends us a complex event consisting of the current state
            // along with the previous state.
            //
            // Note that events do not use the camera prefix in the topic. We get
            // all events for all cameras, so we have to filter here. It is a shame
            // that Frigate does not discriminate by camera allowing the topic subscription
            // to do the filtering for us.

            if (topic.equals(this.svrState.topicPrefix + "/" + MQTT_EVENTS_SUFFIX)) {

                // we are handling an event. The bag of bits is actually a bunch of JSON.
                // By parsing the JSON, rather than stuffing it into a Java object, we
                // can make the channel handling more generic.

                JsonObject evtObj = JsonParser.parseString(state).getAsJsonObject();
                String evtType = evtObj.get("type").getAsString();
                JsonObject evtPrev = evtObj.get("before").getAsJsonObject();
                JsonObject evtCur = evtObj.get("after").getAsJsonObject();

                logger.debug("Event type : {}", evtType);

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

                    String ecURL = new String();
                    if ((evtType.equals("end")) && (hasClip.equals("true"))) {
                        ecURL = this.svrState.url + "events/" + id + "/clip.mp4";
                    }

                    // update 'em

                    updateState(CHANNEL_EVENT_CLIP_URL,
                            ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_EVENT_CLIP_URL))
                                    .toState(ecURL));
                    updateState(CHANNEL_EVENT_ID,
                            ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_EVENT_ID)).toState(id));
                    updateState(CHANNEL_EVENT_TYPE,
                            ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_EVENT_TYPE)).toState(evtType));
                }
            }

            // deal with our camera configuration and control info.

            if (topic.length() > this.cameraTopicPrefix.length()) {
                String camTopic = topic.substring(this.cameraTopicPrefix.length());
                logger.debug("Received trimmed camera state topic {}", camTopic);
                if (this.MQTTGettersToChannels.containsKey(camTopic)) {
                    String channel = this.MQTTGettersToChannels.get(camTopic);
                    updateState((@NonNull String) channel,
                            ((@NonNull frigateSVRChannelState) this.Channels.get(channel)).toState(state));
                    logger.debug("Received change - channel: {} state: {}", channel, state);
                } else {
                    logger.debug("Received topic {} not in map ", camTopic);
                }
            }

            // Snapshots
            //
            // Frigate sends us snapshots on the topic <prefix>/<camera name>/<object>/snapshots.
            // We can wildcard out the object, and filter by camera name. Then we can update two
            // channels, one with the detected object type and the other with the image.
            // The subscription filters on our camera name.

            if (topic.endsWith("/snapshot")) {
                String[] bits = topic.split("/");
                if (bits[1].equals(config.cameraName)) {
                    this.updateState(CHANNEL_LAST_SNAPSHOT_OBJECT,
                            ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_LAST_SNAPSHOT_OBJECT))
                                    .toState(bits[2]));
                    this.updateState(CHANNEL_LAST_SNAPSHOT,
                            ((@NonNull frigateSVRChannelState) this.Channels.get(CHANNEL_LAST_SNAPSHOT))
                                    .toStateFromRaw(payload, "image/jpeg"));
                }
            }
        }
    }
}
