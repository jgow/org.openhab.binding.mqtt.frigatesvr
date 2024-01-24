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
package org.openhab.binding.mqtt.frigatesvr.internal.structures.frigateAPI;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.ResultStruct;
import org.openhab.binding.mqtt.frigatesvr.internal.helpers.frigateSVRHTTPHelper;
import org.openhab.core.io.transport.mqtt.MqttBrokerConnection;

/**
 * The {@link mqtt.frigateSVRConfiguration} class contains mappings to the
 * Frigate SVR config block
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public abstract class APIBase {

    protected String eventID = "";
    protected String param = "";
    @Nullable
    protected String payload = null;

    // Constructor stays empty for now.

    public APIBase(String eventID) {
        this.eventID = eventID;
    }

    public APIBase(String[] bits, String eventID) {
        this.eventID = eventID;
    }

    public void SetPayload(@Nullable String payload) {
        this.payload = payload;
    }

    public abstract ResultStruct ParseFromBits(String[] bits, @Nullable String payload);

    public abstract ResultStruct Process(frigateSVRHTTPHelper httpHelper, MqttBrokerConnection connection,
            String topicPrefix);

    public abstract ResultStruct Validate();

    protected abstract String BuildTopicSuffix();

    public ResultStruct ResQueueMessageToServer(MqttBrokerConnection connection, String topicPrefix) {
        String topic = topicPrefix + "/" + BuildTopicSuffix();
        connection.publish(topic, (payload != null) ? payload.getBytes() : new String("").getBytes(), 1, false);
        return new ResultStruct(true, "message queued");
    }

    public ResultStruct ResQueueMessageToServer(MqttBrokerConnection connection, String topicPrefix, String cam) {
        return ResQueueMessageToServer(connection, topicPrefix + "/" + cam);
    }
}
