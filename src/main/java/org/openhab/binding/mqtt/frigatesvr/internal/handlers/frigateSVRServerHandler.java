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
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRChannelState;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRServerConfiguration;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRServerState;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
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
public class frigateSVRServerHandler extends frigateSVRHandlerBase implements MqttMessageSubscriber {

    private final Logger logger = LoggerFactory.getLogger(frigateSVRServerHandler.class);

    private frigateSVRServerConfiguration config = new frigateSVRServerConfiguration();
    private @Nullable String version = new String("");
    private String MQTTTopicPrefix = "";
    private @Nullable ScheduledFuture<?> servercheck;
    private String svrTopicPrefix = "";
    private frigateSVRServerState svrState = new frigateSVRServerState();

    public frigateSVRServerHandler(Thing thing, HttpClient httpClient) {
        super(thing, httpClient);
        this.Channels = Map.ofEntries(
                Map.entry(CHANNEL_API_VERSION,
                        new frigateSVRChannelState(CHANNEL_API_VERSION, frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_UI_URL, new frigateSVRChannelState(CHANNEL_UI_URL,
                        frigateSVRChannelState::fromStringMQTT, frigateSVRChannelState::toStringMQTT, false)));
    }

    @Override
    public void initialize() {
        updateStatus(ThingStatus.UNKNOWN);
        config = getConfigAs(frigateSVRServerConfiguration.class);

        // Foreground initiation of the basics of HTTPClient. We need the stuff from the configuration.

        String baseurl = config.serverURL;
        if (!config.serverClientID.equals("")) {
            baseurl += "/" + config.serverClientID;
        }
        this.httpHelper.configure(this.httpClient, baseurl);

        this.svrTopicPrefix = "frigateSVR/" + this.getThing().getUID().getAsString();
        this.svrState.status = "offline";
        this.svrState.topicPrefix = "frigate";
        this.svrState.url = this.httpHelper.getBaseURL();
        this.svrState.Cameras = new ArrayList<>();

        super.initialize();
    }

    //
    // Cleanup.

    @Override
    public void dispose() {
        if (servercheck != null) {
            ((@NonNull ScheduledFuture<?>) servercheck).cancel(true);
            servercheck = null;
        }
        this.svrState.status = "offline";
        UnsubscribeMQTTTopics(this.MQTTTopicPrefix);
        if (this.MQTTConnection != null) {
            ((@NonNull MqttBrokerConnection) this.MQTTConnection).unsubscribe(this.svrTopicPrefix + "/camOnLine", this);
            ((@NonNull MqttBrokerConnection) this.MQTTConnection).publish(this.svrTopicPrefix + "/status",
                    this.svrState.GetJsonString().getBytes(), 1, false);
        }
        super.dispose();
    }

    ///////////////////////////////////////////////////////////////////
    // CheckServerAccessThread
    //
    // A bit more than a simple ping - this ensures that we have
    // (a) access to the HTTP server and (b) have extricated
    // the version, config and topic prefix, thus allowing us to
    // correctly subscribe to MQTT messages

    private void CheckServerAccessThread() {

        // Ok - we have started. Now, if we are running in an OFFLINE
        // state, then we have to try and retrieve the config block. If we
        // are already online, we can simply ping the version command
        // to ensure we are still alive.
        // If we are already ONLINE, then this is reduced to a ping. We
        // use the VERSION command as this results in the shortest
        // data packet.

        do {

            if (this.getThing().getStatus().equals(ThingStatus.OFFLINE)) {

                logger.info(" - Frigate server is offline");

                // Get the version string.

                this.version = this.httpHelper.runGet("/api/version");

                if (this.version == null) {
                    logger.warn("unable to get version string");
                    break;
                }

                // Get the full Frigate server configuration. We will need
                // this for all descendants.

                String cfg = this.httpHelper.runGet("/api/config");

                // If this fails, we can go no further.

                if (cfg == null) {
                    logger.warn("Unable to obtain Frigate configuration");
                    break;
                }

                // extricate the configuration - this can stay as a simple
                // JSON object as we are only going to pick bits out of it

                try {
                    JsonElement jp = JsonParser.parseString(cfg);
                    if (jp.isJsonObject()) {
                        this.frigateConfig = jp.getAsJsonObject();
                    } else {
                        break;
                    }
                } catch (Exception e) {
                    // again, if this fails, we can go no further.
                    logger.warn("server config block not valid");
                    break;
                }

                logger.debug("have configuration block");

                // Ok, here we have comms. Now since we are transitioning from
                // OFFLINE to ONLINE, it is entirely possible the MQTT topic prefix
                // has changed.

                // Now, yank the topic prefix out of the config. If for some reason
                // Frigate doesn't feed it to us, assume it is 'frigate'.

                @Nullable
                String prefix = null;

                @Nullable
                JsonObject mqttBlock = this.frigateConfig.getAsJsonObject("mqtt");

                if (mqttBlock != null) {
                    prefix = mqttBlock.get("topic_prefix").getAsString();
                }
                if (prefix == null) {
                    prefix = new String("frigate");
                }

                if (!prefix.equals(this.MQTTTopicPrefix)) {
                    UnsubscribeMQTTTopics(this.MQTTTopicPrefix);
                    this.MQTTTopicPrefix = prefix;
                    SubscribeMQTTTopics(this.MQTTTopicPrefix);
                }

                this.svrState.status = "online";
                this.svrState.Cameras = this.GetCameraList();
                this.svrState.topicPrefix = this.MQTTTopicPrefix;

                // cocked, locked and ready to rock..

                logger.debug("onlining server thing");

                updateStatus(ThingStatus.ONLINE);
                logger.debug("publishing status message on {}/status", this.svrTopicPrefix);
                ((@NonNull MqttBrokerConnection) MQTTConnection).publish(this.svrTopicPrefix + "/status",
                        this.svrState.GetJsonString().getBytes(), 1, false);

                updateState(CHANNEL_API_VERSION,
                        ((@NonNull frigateSVRChannelState) (this.Channels.get(CHANNEL_API_VERSION)))
                                .toState(GetVersionString()));
                updateState(CHANNEL_UI_URL, ((@NonNull frigateSVRChannelState) (this.Channels.get(CHANNEL_UI_URL)))
                        .toState(this.httpHelper.getBaseURL()));
            }

            // if we are online, we need to ping to check. The config from Frigate does not change at
            // runtime.

            if (this.getThing().getStatus().equals(ThingStatus.ONLINE)) {

                logger.debug("keep-alive: device is online");

                // Get the version string.

                this.version = this.httpHelper.runGet("/api/version");

                if (this.version == null) {

                    // we need to offline ourselves, but leave the pinger working. At this stage
                    // do not unsubscribe our MQTT transports.

                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "@text/error.servercomm");
                    this.svrState.status = "offline";
                    ((@NonNull MqttBrokerConnection) MQTTConnection).publish(this.svrTopicPrefix + "/status",
                            this.svrState.GetJsonString().getBytes(), 1, false);
                }
            }
        } while (false);
    }

    private void SubscribeMQTTTopics(String prefix) {
        if (this.MQTTConnection != null) {
            ((@NonNull MqttBrokerConnection) this.MQTTConnection).subscribe(prefix + "/" + MQTT_AVAILABILITY_SUFFIX,
                    this);
        }
    }

    private void UnsubscribeMQTTTopics(String prefix) {
        if (this.MQTTConnection != null) {
            ((@NonNull MqttBrokerConnection) this.MQTTConnection).unsubscribe(prefix + "/" + MQTT_AVAILABILITY_SUFFIX,
                    this);
        }
    }

    ///////////////////////////////////////////////////////////////////
    // BridgeGoingOnline
    //
    // A callback when the MQTT bridge is going online

    @Override
    protected void BridgeGoingOnline(MqttBrokerConnection connection) {

        // If the bridge is transitioning from offline to online, we can then
        // start the server access check.
        int keepalive = config.serverKeepAlive;
        if (keepalive < 5) {
            keepalive = 5;
        }
        if (keepalive > 60) {
            keepalive = 60;
        }
        servercheck = scheduler.scheduleWithFixedDelay(this::CheckServerAccessThread, 0, keepalive, TimeUnit.SECONDS);
        logger.debug("subscribing to SVR topic {}/camOnLine", this.svrTopicPrefix);
        connection.subscribe(this.svrTopicPrefix + "/camOnLine", this);
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING);
    }

    ///////////////////////////////////////////////////////////////////
    // BridgeGoingOffline
    //
    // A callback when the MQTT bridge is going offline. If the MQTT
    // bridge is going offline, we lose our connection to MQTT. We
    // thus also shut down the regular server access check and mark
    // ourselves as OFFLINE. The caller will handle the thing state
    // change.

    @Override
    protected void BridgeGoingOffline() {
        if (servercheck != null) {
            ((@NonNull ScheduledFuture<?>) servercheck).cancel(true);
            servercheck = null;
        }
    }

    //
    // Process incoming MQTT messages for this server.

    @Override
    public void processMessage(String topic, byte[] payload) {

        super.processMessage(topic, payload);

        // if a camera comes online, send out our status

        if (topic.equals(this.svrTopicPrefix + "/camOnLine")) {
            logger.debug("received request for SVR status update; publishing");
            ((@NonNull MqttBrokerConnection) this.MQTTConnection).publish(this.svrTopicPrefix + "/status",
                    this.svrState.GetJsonString().getBytes(), 1, false);
        }

        // We remain handling the availability topic, even when the Frigate server appears
        // offline. When it comes back, if the topic prefix hasn't changed, it will post an
        // 'online' message.

        if (topic.equals(this.MQTTTopicPrefix + "/" + MQTT_AVAILABILITY_SUFFIX)) {

            String serverState = new String(payload, StandardCharsets.UTF_8);

            if (serverState.equals("offline")) {

                // This message gets posted once Frigate is online. Sometimes it
                // may get posted when the thing is still online. However, it is useless
                // as an indicator that the server has just come online, as it seems
                // to be posted _before_ the HTTP API is available. So we don't use
                // it as a tell-tale for availability.
                //
                // Note: this is never triggered simply on a new connection, only
                // when the server itself restarts. This is why we need to handle
                // it as a special case.

                logger.debug("received 'online' message from Frigate server");

            }
            if (serverState.equals("online")) {

                // According to the docs, this should be posted when Frigate stops.
                // However, we can't rely on it as I couldn't get Frigate to actually
                // post this. So we handle it anyway just in case. If the thing is
                // offline, leave it alone. Otherwise just set it offline.
                // We keep all our MQTT state so we can pick up the 'online' message and
                // the pinger is kept running
                // However, as I could not get Frigate to actually send this, there
                // is a possibility it may post this while the HTTP API is alive. Thus,
                // the keepalive may restore the online status, only to switch back
                // to offline once the server's keepalive stops responding

                logger.debug("received offline message from Frigate svr");
                if (this.getThing().getStatus().equals(ThingStatus.ONLINE)) {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR,
                            "@text/error.serveroffline");
                    this.svrState.status = "offline";
                    MQTTConnection.publish(this.svrTopicPrefix + "/status", this.svrState.GetJsonString().getBytes(), 1,
                            false);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////
    // GetVersionString
    //
    // Return the version string

    private @Nullable String GetVersionString() {
        return this.httpHelper.runGet("/api/version");
    }

    //////////////////////////////////////////////////////////////////
    // GetCameraList
    //
    // Returns a list of camera names from the configuration. Will
    // return an empty list if the handler is offline or not
    // initialized.

    public ArrayList<String> GetCameraList() {
        ArrayList<String> cameras = new ArrayList<String>();
        JsonObject cams = frigateConfig.getAsJsonObject("cameras");
        if (cams != null) {
            cams.keySet().forEach(key -> {
                logger.debug("Adding camera :{}", key);
                cameras.add(key);
            });
        } else {
            logger.info("no cameras in Frigate config");
        }
        return cameras;
    }
}
