/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.terminal.tty;

import java.util.Map;
import java.util.Set;

import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.detect.ImageProtocol;
import org.aesh.terminal.image.ImageProtocolDetector;
import org.aesh.terminal.utils.ColorDepth;
import org.aesh.terminal.utils.TerminalEnvironment;

/**
 * Contains info regarding the current device connected to readline
 *
 * @author <a href="mailto:spederse@redhat.com">Stale W. Pedersen</a>
 */
public class TerminalDevice extends BaseDevice {

    private final TerminalEnvironment environment;

    /**
     * Create a new terminal device with the specified type.
     *
     * @param type the terminal type
     */
    public TerminalDevice(String type) {
        this(type, null);
    }

    /**
     * Create a device with explicitly captured local host facts. The type
     * still selects its terminfo entry; the environment identifies the
     * host behind that entry. Without an environment, all heuristics use
     * the device's own type.
     *
     * @param type the terminfo type
     * @param environment captured host facts, or null for a typed device
     */
    public TerminalDevice(String type, TerminalEnvironment environment) {
        super(type);
        this.environment = environment;
    }

    @Override
    public TerminalType detectTerminalType() {
        return environment != null && environment.getTerminalType() != TerminalType.UNKNOWN
                ? environment.getTerminalType()
                : super.detectTerminalType();
    }

    @Override
    public boolean supportsOscQueries() {
        return environment != null && environment.getTerminalType() != TerminalType.UNKNOWN
                ? environment.supportsOscQueries()
                : super.supportsOscQueries();
    }

    @Override
    public boolean isMultiplexer() {
        return environment != null ? environment.isInMultiplexer() : super.isMultiplexer();
    }

    @Override
    public boolean isTmuxPassthroughEnabled() {
        return environment != null && environment.isTmuxPassthroughEnabled();
    }

    @Override
    public ColorDepth getColorDepth() {
        ColorDepth baseline = super.getColorDepth();
        ColorDepth detected = environment != null ? environment.getDefaultColorDepth() : baseline;
        return detected.getColorCount() > baseline.getColorCount() ? detected : baseline;
    }

    @Override
    public ImageProtocol getImageProtocol() {
        return environment != null && environment.getTerminalType() != TerminalType.UNKNOWN
                ? ImageProtocolDetector.detectFromEnvironment(environment)
                : super.getImageProtocol();
    }

    /**
     * Add an integer capability.
     *
     * @param capability the capability to add
     * @param integer the integer value
     */
    public void addCapability(Capability capability, Integer integer) {
        this.ints.put(capability, integer);
    }

    /**
     * Add all integer capabilities from a map.
     *
     * @param integers the map of capabilities to integers
     */
    public void addAllCapabilityInts(Map<Capability, Integer> integers) {
        this.ints.putAll(integers);
    }

    /**
     * Add a boolean capability.
     *
     * @param capability the capability to add
     */
    public void addCapability(Capability capability) {
        bools.add(capability);
    }

    /**
     * Add all boolean capabilities from a set.
     *
     * @param capabilities the set of capabilities
     */
    public void addAllCapabilityBooleans(Set<Capability> capabilities) {
        bools.addAll(capabilities);
    }

    /**
     * Add a string capability.
     *
     * @param capability the capability to add
     * @param s the string value
     */
    public void addCapability(Capability capability, String s) {
        strings.put(capability, s);
    }

    /**
     * Add all string capabilities from a map.
     *
     * @param strings the map of capabilities to strings
     */
    public void addAllCapabilityStrings(Map<Capability, String> strings) {
        this.strings.putAll(strings);
    }

}
