/**
 * Copyright (c) 2010-2024 Contributors to the openHAB project
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
package org.openhab.binding.mqtt.frigatesvr.internal.structures;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.DecimalType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.OpenClosedType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.types.State;
import org.openhab.core.types.UnDefType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link mqtt.frigateSVRChannelState} maintains the state of a channel and provides a series of
 * converter functions to go between MQTT topic/JSON payloads and OH states.
 * Basically because Java can't do function pointers properly, if at all.
 *
 * @author J Gow - Initial contribution
 */
@NonNullByDefault
public class frigateSVRChannelState {

    // conversion functions

    public interface ConverterState {
        State fromString(String s);
    }

    public interface ConverterMQTT {
        String toMQTT(State s);
    }

    // to and from Switch types

    public static State fromSwitchMQTT(String s) {
        return (s.equals("ON")) ? OnOffType.ON : OnOffType.OFF;
    }

    public static String toSwitchMQTT(State s) {
        return ((OnOffType) s).toString();
    }

    // to and from Contact types

    public static State fromContactMQTT(String s) {
        return (s.equals("ON")) ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
    }

    public static String toContactMQTT(State s) {
        return ((((OpenClosedType) s).toString().equals("CLOSED")) ? "ON" : "OFF");
    }

    // to and from Contact types for the JSON spat out by the event notification
    // process (this spits out 'true' or 'false'

    public static State fromContactJSON(String s) {
        return (s.equals("true")) ? OpenClosedType.CLOSED : OpenClosedType.OPEN;
    }

    public static String toContactJSON(State s) {
        return ((((OpenClosedType) s).toString().equals("CLOSED")) ? "true" : "false");
    }

    // to and from Number types

    public static State fromNumberMQTT(String s) {
        return new DecimalType(s);
    }

    public static String toNumberMQTT(State s) {
        return new String("" + ((DecimalType) s).intValue());
    }

    // to and from String types

    public static State fromStringMQTT(String s) {
        return new StringType(s);
    }

    public static String toStringMQTT(State s) {
        return s.toString();
    }

    // to and from JPEG types

    public static State fromJPEGMQTT(String s) {
        return new StringType(s);
    }

    public static String toJPEGMQTT(State s) {
        return new String(""); // not used
    }
    // to and from Frigate timestamp types

    public static State fromTimestampMQTT(String s) {
        double ts = Double.parseDouble(s);
        if (ts != 0.0f) {
            long sec = (long) ts;
            long nano = (long) ((ts - sec) * 1000000000);
            ZonedDateTime zonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochSecond(sec, nano),
                    ZoneId.systemDefault());
            return new DateTimeType(zonedDateTime);
        } else {
            return UnDefType.NULL;
        }
    }

    public static String toTimestampMQTT(State s) {
        // we never have to do this.
        return new String("");
    }

    // no direct conversion possible

    public static State fromNoConversion(String s) {
        return UnDefType.NULL;
    }

    public static String toNoConversion(State s) {
        return new String("");
    }

    // end of converter functions.

    private final Logger logger = LoggerFactory.getLogger(frigateSVRChannelState.class);
    public String MQTTTopicSuffix;
    public State state = UnDefType.NULL;
    public ConverterState ConvertToState;
    public ConverterMQTT ConvertToMQTT;
    public boolean commandable;

    public frigateSVRChannelState(String MQTTTopicSuffix, ConverterState toState, ConverterMQTT toMQTT,
            boolean commandable) {
        this.MQTTTopicSuffix = MQTTTopicSuffix;
        this.ConvertToState = toState;
        this.ConvertToMQTT = toMQTT;
        this.commandable = commandable;
        this.state = UnDefType.NULL;
    }

    public State toState(@Nullable String s) {
        if (s != null) {
            this.state = ConvertToState.fromString(s);
        } else {
            this.state = UnDefType.NULL;
        }
        return this.state;
    }

    public State toStateFromRaw(byte[] b, String mimetype) {
        logger.debug("Channel - updating raw type");
        this.state = new RawType(b, mimetype);
        return this.state;
    }

    public String toMQTT() {
        return ConvertToMQTT.toMQTT(this.state);
    }
}
