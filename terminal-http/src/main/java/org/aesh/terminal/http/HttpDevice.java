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
package org.aesh.terminal.http;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.utils.InfoCmp;

/**
 * Device implementation for HTTP-based terminal connections.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class HttpDevice extends BaseDevice {

    private final String type;
    private final Set<Capability> bools;
    private final Map<Capability, Integer> ints;
    private final Map<Capability, String> strings;

    /**
     * Creates a new HTTP device with the specified terminal type.
     *
     * @param type the terminal type (e.g., "vt100", "xterm")
     */
    public HttpDevice(String type) {
        this.type = type;
        bools = new HashSet<>();
        ints = new HashMap<>();
        strings = new HashMap<>();
        String data = InfoCmp.getDefaultInfoCmp(type);
        InfoCmp.parseInfoCmp(data, bools, ints, strings);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String type() {
        return type;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean getBooleanCapability(Capability capability) {
        return bools.contains(capability);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Integer getNumericCapability(Capability capability) {
        return ints.get(capability);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getStringCapability(Capability capability) {
        return strings.get(capability);
    }
}
