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

import java.util.function.Consumer;

import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.utils.ColorDepth;

/**
 * Contains info regarding the current device connected to readline
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public interface Device {

    String type();

    boolean getBooleanCapability(Capability capability);

    Integer getNumericCapability(Capability capability);

    String getStringCapability(Capability capability);

    int[] getStringCapabilityAsInts(Capability capability, Object... params);

    boolean puts(Consumer<int[]> output, Capability capability, Object... params);

    boolean puts(Consumer<int[]> output, String capability, Object... params);

    /**
     * Check if this device supports OSC (Operating System Command) queries.
     * <p>
     * OSC queries like OSC 10/11 are used to query foreground/background colors.
     * Not all terminals support these queries, and some terminal multiplexers
     * (like tmux, screen) may intercept or block them.
     *
     * @return true if OSC queries are likely supported
     */
    default boolean supportsOscQueries() {
        String termType = type();
        if (termType == null) {
            return false;
        }

        String typeLower = termType.toLowerCase();

        // Terminal multiplexers typically don't pass through OSC queries properly
        // unless allow-passthrough is enabled (checked separately)
        if (typeLower.startsWith("screen") || typeLower.startsWith("tmux")) {
            return false;
        }

        // Known terminals that support OSC 10/11 queries
        return typeLower.contains("xterm") ||
                typeLower.contains("vte") ||
                typeLower.contains("rxvt") ||
                typeLower.contains("konsole") ||
                typeLower.contains("iterm") ||
                typeLower.contains("alacritty") ||
                typeLower.contains("kitty") ||
                typeLower.contains("ghostty") ||
                typeLower.contains("wezterm") ||
                typeLower.contains("foot") ||
                typeLower.contains("contour") ||
                typeLower.contains("rio") ||
                typeLower.contains("warp") ||
                typeLower.contains("hyper") ||
                typeLower.contains("terminus") ||
                typeLower.contains("tabby") ||
                typeLower.contains("extraterm") ||
                typeLower.contains("wave");
    }

    /**
     * Get the color depth of this device based on terminfo capabilities.
     *
     * @return the detected color depth, or null if not determinable from terminfo
     */
    default ColorDepth getColorDepth() {
        Integer maxColors = getNumericCapability(Capability.max_colors);
        if (maxColors != null) {
            return ColorDepth.fromColorCount(maxColors);
        }
        return null;
    }

    /**
     * Check if this device is running inside a terminal multiplexer
     * (like tmux or screen).
     *
     * @return true if running inside a multiplexer
     */
    default boolean isMultiplexer() {
        String termType = type();
        if (termType == null) {
            return false;
        }
        String typeLower = termType.toLowerCase();
        return typeLower.startsWith("screen") || typeLower.startsWith("tmux");
    }
}
