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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRHTTPHelper;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRNetworkHelper;
import org.openhab.binding.mqtt.frigatesvr.internal.servlet.frigateSVRServlet;
import org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateSVRChannelState;
import org.openhab.binding.mqtt.handler.AbstractBrokerHandler;
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
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.types.Command;
import org.openhab.core.types.RefreshType;
import org.openhab.core.types.State;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link mqtt.frigateSVRHandlerBase} is a base class providing functionality common to the
 * frigateSVRServer and frigateSVRCamera handler
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRHandlerBase extends BaseThingHandler implements MqttMessageSubscriber {

    private final Logger logger = LoggerFactory.getLogger(frigateSVRHandlerBase.class);

    protected frigateSVRNetworkHelper networkHelper = new frigateSVRNetworkHelper();
    protected HttpClient httpClient;
    protected @Nullable MqttBrokerConnection MQTTConnection = null;
    protected frigateSVRHTTPHelper httpHelper = new frigateSVRHTTPHelper();
    protected Map<String, frigateSVRChannelState> Channels = new HashMap<String, frigateSVRChannelState>();
    protected frigateSVRServlet httpServlet;

    public frigateSVRHandlerBase(Thing thing, HttpClient httpClient, HttpService httpService) {
        super(thing);
        this.httpClient = httpClient;
        this.httpServlet = new frigateSVRServlet(httpService);
    }

    @Override
    public void initialize() {

        // the descendant should have set the 'config' before calling this function

        // check we have a valid MQTT and HTTP connection

        this.bridgeStatusChanged(GetMQTTConnectionStatus());
    }

    //
    // Cleanup.

    @Override
    public void dispose() {
        logger.debug("dispose: handler being destroyed");
        this.httpServlet.StopServer();
        super.dispose();
    }

    /////////////////////////////////////////////////////////////////////////
    // GetMQTTConnectionStatus
    //
    // Helper to return the status of the MQTT bridge below us.

    public ThingStatusInfo GetMQTTConnectionStatus() {
        Bridge b = getBridge();
        if (b != null) {
            return b.getStatusInfo();
        } else {
            return new ThingStatusInfo(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE, null);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // bridgeStatusChanged
    //
    // If the MQTT handler changes status, we check that we have both a valid
    // MQTT connection AND a valid config/version from the HTTP API before we
    // notify ourselves as online.

    @Override
    public void bridgeStatusChanged(ThingStatusInfo bridgeStatusInfo) {

        if (bridgeStatusInfo.getStatus() == ThingStatus.OFFLINE) {
            MQTTConnection = null;
            this.BridgeGoingOffline();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_OFFLINE);
            return;
        }
        if (bridgeStatusInfo.getStatus() != ThingStatus.ONLINE) {
            MQTTConnection = null;
            this.BridgeGoingOffline();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.COMMUNICATION_ERROR);
            return;
        }

        Bridge localBridge = this.getBridge();
        if (localBridge == null) {
            this.BridgeGoingOffline();
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED, "@text/error.bridgeoffline");
            return;
        }

        ThingHandler handler = localBridge.getHandler();
        if (handler instanceof AbstractBrokerHandler) {
            AbstractBrokerHandler abh = (AbstractBrokerHandler) handler;
            final MqttBrokerConnection connection;
            try {
                connection = abh.getConnectionAsync().get(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException ignored) {
                MQTTConnection = null;
                this.BridgeGoingOffline();
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.BRIDGE_UNINITIALIZED, "@text/error.bridgeconn");
                return;
            }
            this.MQTTConnection = connection;
            this.BridgeGoingOnline(connection);
        }
        return;
    }

    //
    // These next three functions are overloaded by descendants
    //

    ///////////////////////////////////////////////////////////////////
    // BridgeGoingOnline
    //
    // A callback when the MQTT bridge is going online

    protected void BridgeGoingOnline(MqttBrokerConnection connection) {
    }

    ///////////////////////////////////////////////////////////////////
    // BridgeGoingOnline
    //
    // A callback when the MQTT bridge is going online

    protected void BridgeGoingOffline() {
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
    // ProcessCommand
    //
    // To be overloaded by descendants, this allows thing-specific
    // processing of commands.

    protected void ProcessCommand(String suffix, String payload) {
        return;
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
                logger.debug("Refreshing channel :{} value :{}", channelUID.getId(),
                        ((@NonNull frigateSVRChannelState) s).state.toString());
                updateState(channelUID.getId(), ((@NonNull frigateSVRChannelState) s).state);
            } else {
                if (((@NonNull frigateSVRChannelState) s).commandable) {
                    if ((command instanceof OnOffType) || (command instanceof OpenClosedType)
                            || (command instanceof DecimalType)) {
                        ((@NonNull frigateSVRChannelState) this.Channels
                                .get(channelUID.getId())).state = (State) command;
                        String payload = ((@NonNull frigateSVRChannelState) this.Channels.get(channelUID.getId()))
                                .toMQTT();
                        logger.debug("Setting channel {} to {}", channelUID.getId(), payload);
                        ProcessCommand(((@NonNull frigateSVRChannelState) s).MQTTTopicSuffix, payload);
                    }
                }
            }
        }
    }

    ///////////////////////////////////////////////////////////////////
    // processMessage
    //
    // In this base class, it is used only to provide a trace dump
    // of the MQTT message being sent.

    @Override
    public void processMessage(String topic, byte[] payload) {
        logger.debug("Received MQTT message:{}:{}", topic, new String(payload, StandardCharsets.UTF_8));
    }
}
