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
package org.aesh.terminal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.utils.CodePointUtils;
import org.aesh.terminal.utils.Curses;

/**
 * Abstract base implementation of the Device interface providing common
 * functionality for terminal device capabilities.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class BaseDevice implements Device {

    /** Set of boolean capabilities supported by this device. */
    protected final Set<Capability> bools = new HashSet<>();
    /** Map of numeric capabilities and their values. */
    protected final Map<Capability, Integer> ints = new HashMap<>();
    /** Map of string capabilities and their values. */
    protected final Map<Capability, String> strings = new HashMap<>();
    /** The terminal type identifier. */
    protected final String type;

    /**
     * Creates a new BaseDevice with the default "ansi" terminal type.
     */
    public BaseDevice() {
        this.type = "ansi";
    }

    /**
     * Creates a new BaseDevice with the specified terminal type.
     *
     * @param type the terminal type identifier
     */
    public BaseDevice(String type) {
        this.type = type;
    }

    @Override
    public String type() {
        return type;
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

    @Override
    public int[] getStringCapabilityAsInts(Capability capability, Object... params) {
        String str = getStringCapability(capability);
        if (str != null) {
            StringBuilder sb = new StringBuilder();
            Curses.tputs(sb, str, params);
            return CodePointUtils.toCodePoints(sb.toString());
        } else
            return null;
    }

    @Override
    public boolean puts(Consumer<int[]> output, Capability capability, Object... params) {
        int[] seq = getStringCapabilityAsInts(capability, params);
        if (seq != null) {
            output.accept(seq);
            return true;
        }
        return false;
    }

    @Override
    public boolean puts(Consumer<int[]> output, String capability, Object... params) {
        int[] seq = getStringCapabilityAsInts(Capability.byName(capability), params);
        if (seq != null) {
            output.accept(seq);
            return true;
        }
        return false;
    }

}
