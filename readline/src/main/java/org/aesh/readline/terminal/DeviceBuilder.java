/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
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

import org.aesh.utils.InfoCmp;
import org.aesh.terminal.tty.Capability;
import org.aesh.utils.Config;
import org.aesh.util.LoggerUtil;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Ståle W. Pedersen <stale.pedersen@jboss.org>
 */
public class DeviceBuilder {

    private String name;
    private final Logger LOGGER = LoggerUtil.getLogger(getClass().getName());

    private DeviceBuilder() {
    }

    public static DeviceBuilder builder() {
        return new DeviceBuilder();
    }

    public DeviceBuilder name(String name) {
        this.name = name;
        return this;
    }

    public TerminalDevice build() {
        if(name == null)
            name = Config.isOSPOSIXCompatible() ? "ansi" : "windows";
        String data = getCapabilityFromType();
        TerminalDevice device = new TerminalDevice(name);

        if(data != null) {
            Set<Capability> bools = new HashSet<>();
            Map<Capability, Integer> ints = new HashMap<>();
            Map<Capability, String> strings = new HashMap<>();

            InfoCmp.parseInfoCmp(data, bools,ints, strings);
            device.addAllCapabilityBooleans(bools);
            device.addAllCapabilityInts(ints);
            device.addAllCapabilityStrings(strings);
        }

        return device;
    }

    private String getCapabilityFromType() {
        try {
            return InfoCmp.getDefaultInfoCmp(name);
        }
        catch (Exception e) {
            LOGGER.log(Level.WARNING, "Unable to retrieve infocmp for type " + name, e);
            return null;
        }
    }

}
