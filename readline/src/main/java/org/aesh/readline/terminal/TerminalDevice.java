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
package org.aesh.readline.terminal;

import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.tty.Capability;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Contains info regarding the current device connected to readline
 *
 * @author Ståle W. Pedersen <stale.pedersen@jboss.org>
 */
public class TerminalDevice extends BaseDevice {

    private String type;
    private final Set<Capability> bools = new HashSet<>();
    private final Map<Capability, Integer> ints = new HashMap<>();
    private final Map<Capability, String> strings = new HashMap<>();

    public TerminalDevice(String type) {
        this.type = type;
    }

    @Override
    public String type() {
        return type;
    }

    public void addCapability(Capability capability, Integer integer) {
        this.ints.put(capability, integer);
    }

    public void addAllCapabilityInts(Map<Capability,Integer> integers) {
        this.ints.putAll(integers);
    }

    public void addCapability(Capability capability) {
        bools.add(capability);
    }

    public void addAllCapabilityBooleans(Set<Capability> capabilities) {
        bools.addAll(capabilities);
    }

    public void addCapability(Capability capability, String s) {
        strings.put(capability, s);
    }

    public void addAllCapabilityStrings(Map<Capability,String> strings) {
        this.strings.putAll(strings);
    }

    @Override
    public boolean getBooleanCapability(Capability capability) {
        return bools.contains(capability);
    }

    @Override
    public Integer getNumericCapability(Capability capability) {
        return ints.get(capability);
    }

    @Override
    public String getStringCapability(Capability capability) {
        return strings.get(capability);
    }


}
