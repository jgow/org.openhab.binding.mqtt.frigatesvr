/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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

import static org.openhab.binding.mqtt.frigatesvr.internal.frigateSVRBindingConstants.*;

import java.util.Hashtable;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.mqtt.frigatesvr.internal.discovery.frigateSVRCameraDiscoveryService;
import org.openhab.binding.mqtt.frigatesvr.internal.handlers.frigateSVRCameraHandler;
import org.openhab.binding.mqtt.frigatesvr.internal.handlers.frigateSVRServerHandler;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.http.HttpService;

/**
 * The {@link mqtt.frigateSVRHandlerFactory} is responsible for creating things and thing
 * handlers.
 *
 * @author J Gow - Initial contribution
 */

@NonNullByDefault
@Component(configurationPid = "mqtt:frigateCamera", service = ThingHandlerFactory.class)
public class frigateSVRHandlerFactory extends BaseThingHandlerFactory {

    private static HttpClient httpClient = new HttpClient(); // common HTTP client
    private @Nullable ServiceRegistration<?> CameraDiscoveryServiceRegistration;
    private final HttpService httpService;

    //
    // Standard stuff...

    @Activate
    public frigateSVRHandlerFactory(final @Reference HttpClientFactory httpClientFactory,
            final @Reference HttpService httpService) {
        frigateSVRHandlerFactory.httpClient = httpClientFactory.getCommonHttpClient();
        this.httpService = httpService;
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    ////////////////////////////////////////////////////////////////////////////
    // createHandler
    //
    // The function behaves as expected, however the discovery service works
    // a little differently: we can not discover cameras until we have a Frigate
    // server connection. Thus we only register the autodiscovery handler when
    // a server Thing has been created. However, the newly-created server Thing
    // will not export cameras to the autodiscovery service until a connection
    // to the Frigate server has been established.

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();
        if (thingTypeUID.equals(THING_TYPE_SERVER)) {
            frigateSVRServerHandler handler = new frigateSVRServerHandler((Bridge) thing,
                    frigateSVRHandlerFactory.httpClient, httpService);
            registerCameraDiscoveryService(handler);
            return handler;
        }
        if (thingTypeUID.equals(THING_TYPE_CAMERA)) {
            return new frigateSVRCameraHandler(thing, frigateSVRHandlerFactory.httpClient, httpService);
        }
        return null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // removeHandler
    //
    // Required as we need to deregister the autodiscovery service if someone
    // removes the running server Thing instance - without it we can not
    // discover cameras.

    @SuppressWarnings("null")
    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof frigateSVRServerHandler) {
            if (this.CameraDiscoveryServiceRegistration != null) {
                this.CameraDiscoveryServiceRegistration.unregister();
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // registerCameraDiscoveryService
    //
    // Local helper to register our camera discovery service. As a note, we
    // can only discover cameras, not servers. The server Thing has to be created
    // manually and connected to a Frigate instance before we are able to
    // discover cameras.

    private void registerCameraDiscoveryService(frigateSVRServerHandler handler) {
        frigateSVRCameraDiscoveryService discoService = new frigateSVRCameraDiscoveryService(handler);
        if (bundleContext != null) {
            this.CameraDiscoveryServiceRegistration = bundleContext.registerService(DiscoveryService.class.getName(),
                    discoService, new Hashtable<>());
        }
    }
}
