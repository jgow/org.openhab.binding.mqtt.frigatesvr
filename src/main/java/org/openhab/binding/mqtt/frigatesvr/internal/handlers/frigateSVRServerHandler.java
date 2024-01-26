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
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.HTTPHandler;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams.DASHStream;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams.FrigateAPIForwarder;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams.HLSStream;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.streams.MJPEGStream;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APIBase;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APICamOnline;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APIGetLastFrame;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APIGetRecordingSummary;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI.APITriggerEvent;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRChannelState;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRFrigateConfiguration;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRServerConfiguration;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRServerState;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;
import org.openhab.core.io.transport.mqtt.MqttMessageSubscriber;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingStatus;
import org.openhab.core.thing.ThingStatusDetail;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private @Nullable ScheduledFuture<?> servercheck;
    private @Nullable String version = new String("");
    private String MQTTTopicPrefix = "";
    private String svrTopicPrefix = "";
    private frigateSVRServerState svrState = new frigateSVRServerState();
    private frigateSVRFrigateConfiguration frigateConfig = new frigateSVRFrigateConfiguration();

    private Map<String, APIBase> cm = Map.ofEntries(Map.entry(MQTT_EVTTRIGGER_SUFFIX, new APITriggerEvent()),
            Map.entry(MQTT_GETLASTFRAME_SUFFIX, new APIGetLastFrame()),
            Map.entry(MQTT_GETRECORDINGSUMMARY_SUFFIX, new APIGetRecordingSummary()),
            Map.entry(MQTT_ONLINE_SUFFIX, new APICamOnline(this.svrState)));

    public frigateSVRServerHandler(Thing thing, HttpClient httpClient, HttpService httpService) {
        super(thing, httpClient, httpService);

        // the channel map

        this.Channels = Map.ofEntries(
                Map.entry(CHANNEL_API_VERSION,
                        new frigateSVRChannelState(CHANNEL_API_VERSION, frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_UI_URL,
                        new frigateSVRChannelState(CHANNEL_UI_URL, frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_APIFORWARDER_URL,
                        new frigateSVRChannelState(CHANNEL_APIFORWARDER_URL, frigateSVRChannelState::fromStringMQTT,
                                frigateSVRChannelState::toStringMQTT, false)),
                Map.entry(CHANNEL_BIRDSEYE_URL, new frigateSVRChannelState(CHANNEL_BIRDSEYE_URL,
                        frigateSVRChannelState::fromStringMQTT, frigateSVRChannelState::toStringMQTT, false)));
    }

    @Override
    public void initialize() {
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
        this.svrState.rtspbase = new String("rtsp://") + this.httpHelper.getHost() + ":8554";
        this.svrState.Cameras = new ArrayList<>();
        this.svrState.whitelist = config.streamWhitelist;
        this.svrState.ffmpegPath = config.ffmpegLocation;

        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING);
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
            // ((@NonNull MqttBrokerConnection) this.MQTTConnection)
            // .unsubscribe(this.svrTopicPrefix + "/" + MQTT_ONLINE_SUFFIX, this);
            // ((@NonNull MqttBrokerConnection) this.MQTTConnection)
            // .unsubscribe(this.svrTopicPrefix + "/" + MQTT_EVTTRIGGER_SUFFIX + "/#", this);
            ((@NonNull MqttBrokerConnection) this.MQTTConnection).unsubscribe(this.svrTopicPrefix + "/#", this);
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

                ResultStruct r = this.httpHelper.runGet("/api/version");

                if (!r.rc) {
                    logger.warn("unable to get version string");
                    break;
                }
                this.version = new String(r.raw);

                // Get the full Frigate server configuration. We will need
                // this for all descendants.

                r = this.httpHelper.runGet("/api/config");

                // If this fails, we can go no further.

                if (!r.rc) {
                    logger.warn("Unable to obtain Frigate configuration");
                    break;
                }

                String cfg = new String(r.raw);

                // extricate the configuration - this can stay as a simple
                // JSON object as we are only going to pick bits out of it

                try {
                    frigateConfig.GetConfiguration(cfg);
                } catch (Exception e) {
                    // again, if this fails, we can go no further.
                    logger.warn("server config block not valid ({})", e.getMessage());
                    break;
                }

                logger.debug("have configuration block");

                // Ok, here we have comms. Now since we are transitioning from
                // OFFLINE to ONLINE, it is entirely possible the MQTT topic prefix
                // has changed.

                // Now, yank the topic prefix out of the config. If for some reason
                // Frigate doesn't feed it to us, assume it is 'frigate'.

                if (!frigateConfig.block.mqtt.topicPrefix.equals(this.MQTTTopicPrefix)) {
                    UnsubscribeMQTTTopics(this.MQTTTopicPrefix);
                    this.MQTTTopicPrefix = frigateConfig.block.mqtt.topicPrefix;
                    SubscribeMQTTTopics(this.MQTTTopicPrefix);
                }

                this.svrState.status = "online";
                this.svrState.Cameras = this.frigateConfig.block.GetCameraList();
                this.svrState.topicPrefix = this.MQTTTopicPrefix;

                // cocked, locked and ready to rock..

                logger.info("onlining server thing");

                updateStatus(ThingStatus.ONLINE);

                // now we can start the streaming server - if enabled in config.
                // This is for the birdseye view - we do this before we
                // notify the cameras that we are online. Seems to avoid a conflict

                this.StartStream();

                logger.debug("publishing status message on {}/status", this.svrTopicPrefix);
                ((@NonNull MqttBrokerConnection) MQTTConnection).publish(this.svrTopicPrefix + "/status",
                        this.svrState.GetJsonString().getBytes(), 1, false);

                updateState(CHANNEL_API_VERSION,
                        ((@NonNull frigateSVRChannelState) (this.Channels.get(CHANNEL_API_VERSION)))
                                .toState(this.version));
                updateState(CHANNEL_UI_URL, ((@NonNull frigateSVRChannelState) (this.Channels.get(CHANNEL_UI_URL)))
                        .toState(this.httpHelper.getBaseURL()));
            }

            // if we are online, we need to ping to check. The config from Frigate does not change at
            // runtime.

            if (this.getThing().getStatus().equals(ThingStatus.ONLINE)) {

                logger.debug("keep-alive: device is online");

                // Get the version string.

                ResultStruct r = this.httpHelper.runGet("/api/version");

                if (!r.rc) {

                    // we need to offline ourselves, but leave the pinger working. At this stage
                    // stop the streaming servers but do not unsubscribe our MQTT transports.

                    logger.debug("server-thing: keepalive - stopping streaming server");
                    this.httpServlet.StopServer();
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR, "@text/error.servercomm");
                    this.svrState.status = "offline";
                    ((@NonNull MqttBrokerConnection) MQTTConnection).publish(this.svrTopicPrefix + "/status",
                            this.svrState.GetJsonString().getBytes(), 1, false);
                } else {

                    this.version = r.message;

                    // here we send the keepalive message to the cams, and prod the stream

                    ((@NonNull MqttBrokerConnection) MQTTConnection)
                            .publish(this.svrTopicPrefix + "/" + MQTT_KEEPALIVE_SUFFIX, "OK".getBytes(), 1, false);

                    this.httpServlet.PokeMe();
                }
            }
        } while (false);
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
        logger.debug("subscribing to SVR topics");
        // connection.subscribe(this.svrTopicPrefix + "/" + MQTT_ONLINE_SUFFIX, this);
        // connection.subscribe(this.svrTopicPrefix + "/" + MQTT_EVTTRIGGER_SUFFIX + "/#", this);
        connection.subscribe(this.svrTopicPrefix + "/#", this);
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
            logger.debug("server-thing: stopping streaming server (BridgeGoingOffline)");
            this.httpServlet.StopServer();
            servercheck = null;
        }
    }

    ////////////////////////////////////////////////////////////////////
    // StartStream
    //
    // Checks whether the re-stream is enabled, and starts the server
    // if it is.

    private void StartStream() {

        ArrayList<HTTPHandler> handlers = new ArrayList<HTTPHandler>();
        String serverBase = new String("/") + this.svrTopicPrefix;
        String APIBase = new String("");
        String viewURL = new String("");

        if ((config.enableAPIForwarder == true)) {

            logger.info("enabling API forwarder");

            APIBase = this.networkHelper.GetHostBaseURL() + serverBase + "/frigatesvr";
            handlers.add(new FrigateAPIForwarder("frigatesvr", this.httpHelper));

        }

        if ((config.enableStream == true) && (this.frigateConfig.block.birdseye.enableRestream == true)) {

            logger.info("enabling birdseye streaming server");

            String birdseyeFrigateStreamPath = this.svrState.rtspbase + "/birdseye";
            viewURL = this.networkHelper.GetHostBaseURL() + serverBase + "/birdseye";

            handlers.add(new MJPEGStream("birdseye", this.svrState.ffmpegPath, birdseyeFrigateStreamPath, serverBase,
                    config));
            handlers.add(new HLSStream("birdseye", this.svrState.ffmpegPath, birdseyeFrigateStreamPath, config));
            handlers.add(new DASHStream("birdseye", this.svrState.ffmpegPath, birdseyeFrigateStreamPath, config));
        }

        if (!handlers.isEmpty()) {
            logger.info("starting streaming server");

            this.httpServlet.SetWhitelist(this.svrState.whitelist);
            this.httpServlet.StartServer(serverBase, handlers);

            updateState(CHANNEL_BIRDSEYE_URL,
                    ((@NonNull frigateSVRChannelState) (this.Channels.get(CHANNEL_BIRDSEYE_URL))).toState(viewURL));
            updateState(CHANNEL_APIFORWARDER_URL,
                    ((@NonNull frigateSVRChannelState) (this.Channels.get(CHANNEL_APIFORWARDER_URL))).toState(APIBase));
        } else {
            updateState(CHANNEL_BIRDSEYE_URL,
                    ((@NonNull frigateSVRChannelState) (this.Channels.get(CHANNEL_BIRDSEYE_URL))).toState(""));
            updateState(CHANNEL_APIFORWARDER_URL,
                    ((@NonNull frigateSVRChannelState) (this.Channels.get(CHANNEL_APIFORWARDER_URL))).toState(""));
        }
    }

    ////////////////////////////////////////////////////////////////////
    // SubscribeMQTTTopics
    //
    // We keep all our subscriptions/unsubscriptions in one place

    private void SubscribeMQTTTopics(String prefix) {
        if (this.MQTTConnection != null) {
            ((@NonNull MqttBrokerConnection) this.MQTTConnection).subscribe(prefix + "/" + MQTT_AVAILABILITY_SUFFIX,
                    this);
            // ((@NonNull MqttBrokerConnection) this.MQTTConnection).subscribe(prefix + "/" + MQTT_EVTTRIGGER_SUFFIX,
            // this);
            // ((@NonNull MqttBrokerConnection) this.MQTTConnection).subscribe(this.svrTopicPrefix + "/#", this);
        }
    }

    ////////////////////////////////////////////////////////////////////
    // UnsubscribeMQTTTopics
    //
    // We keep all our subscriptions/unsubscriptions in one place

    private void UnsubscribeMQTTTopics(String prefix) {
        if (this.MQTTConnection != null) {
            // ((@NonNull MqttBrokerConnection) this.MQTTConnection).unsubscribe(prefix + "/" + MQTT_EVTTRIGGER_SUFFIX,
            // this);
            ((@NonNull MqttBrokerConnection) this.MQTTConnection).unsubscribe(prefix + "/" + MQTT_AVAILABILITY_SUFFIX,
                    this);
        }
    }

    ///////////////////////////////////////////////////////////////////
    // HandleEvents
    //
    // Process actions, usually passed from cameras, via the HTTP
    // Frigate API

    private void HandleEvents(String topic, String payload) {

        String[] bits = topic.split("/");

        // These are for server 'thing' messages, not those originated directly by the Frigate server
        // itself.
        //
        // part 1 (index 0): 'frigateSVR'
        // part 2 (index 1): server thing ID
        // part 3 (index 2): cam ID (originator)
        // part 4 (index 3): event ID
        // part 5 (index 4): message specific (if present)

        if (this.svrState.status.equals("online")) {
            if (bits.length >= 4) {
                String event = bits[3];
                if (this.cm.containsKey(event)) {
                    logger.info("processing event {}", event);
                    APIBase api = this.cm.get(event);
                    ResultStruct rc = api.ParseFromBits(bits, payload);
                    if (rc.rc) {
                        rc = api.Process(httpHelper, ((@NonNull MqttBrokerConnection) MQTTConnection),
                                this.svrState.topicPrefix);
                    } else {
                        logger.error("Trigger event ignored, invalid topic string '{}'", topic);
                    }
                } else {
                    logger.info("event {} ignored - no handler", event);
                }
            } // else this is a keepalive or a server message: silently ignore it for now.
        } else {
            logger.warn("event ignored, server offline");
        }
    }

    ///////////////////////////////////////////////////////////////////
    // processMessage
    //
    // Process incoming MQTT messages for this server.

    @Override
    public void processMessage(String topic, byte[] payload) {

        super.processMessage(topic, payload);

        if (topic.startsWith(this.svrTopicPrefix)) {
            HandleEvents(topic, new String(payload));
        }

        // We remain handling the availability topic, even when the Frigate server appears
        // offline. When it comes back, if the topic prefix hasn't changed, it will post an
        // 'online' message.

        if (topic.equals(this.MQTTTopicPrefix + "/" + MQTT_AVAILABILITY_SUFFIX)) {

            String serverState = new String(payload, StandardCharsets.UTF_8);

            if (serverState.equals("online")) {

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
            if (serverState.equals("offline")) {

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
                    ((@NonNull MqttBrokerConnection) this.MQTTConnection).publish(this.svrTopicPrefix + "/status",
                            this.svrState.GetJsonString().getBytes(), 1, false);
                }
            }
        }
    }

    //////////////////////////////////////////////////////////////////
    // GetCameraList
    //
    // Used by the discovery service. Returns a list of camera names
    // from the configuration. Will return an empty list if the handler
    // is offline or not initialized.

    public ArrayList<String> GetCameraList() {
        return this.frigateConfig.block.GetCameraList();
    }
}
