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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.tty.Capability;

/**
 * Contains info regarding the current device connected to readline
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
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

    public void addAllCapabilityInts(Map<Capability, Integer> integers) {
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

    public void addAllCapabilityStrings(Map<Capability, String> strings) {
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

    @Override
    public boolean supportsOscQueries() {
        String termType = type();

        // IMPORTANT: Check for known outer terminals FIRST, before multiplexer check.
        // When running tmux inside JetBrains/IntelliJ, TERMINAL_EMULATOR is set
        // but note that JediTerm does NOT support OSC 10/11 color queries.
        // We need to check for actual OSC-capable outer terminals.

        // Check TERMINAL_EMULATOR for JetBrains IDEs (IntelliJ, etc.)
        // JediTerm does NOT support OSC 10/11 queries - it throws errors on them
        String terminalEmulator = System.getenv("TERMINAL_EMULATOR");
        if (terminalEmulator != null &&
                terminalEmulator.toLowerCase().contains("jetbrains")) {
            // JetBrains/JediTerm doesn't support OSC color queries
            return false;
        }

        // Check for terminal-specific environment variables that indicate
        // the outer terminal supports OSC queries (even if running in tmux)
        if (System.getenv("GHOSTTY_RESOURCES_DIR") != null ||
                System.getenv("KITTY_WINDOW_ID") != null ||
                System.getenv("ALACRITTY_SOCKET") != null ||
                System.getenv("WEZTERM_PANE") != null ||
                System.getenv("ITERM_SESSION_ID") != null) {
            return true;
        }

        // Now check for terminal multiplexers - these block OSC queries
        // unless passthrough is enabled
        if (termType != null) {
            String typeLower = termType.toLowerCase();

            // Terminal multiplexers typically don't pass through OSC queries properly
            if (typeLower.startsWith("screen") || typeLower.startsWith("tmux")) {
                return isTmuxPassthroughEnabled();
            }
        }

        // Check TERM_PROGRAM for additional info
        String termProgram = System.getenv("TERM_PROGRAM");
        if (termProgram != null) {
            String lower = termProgram.toLowerCase();
            if (lower.equals("tmux") || lower.equals("screen")) {
                // Check for tmux passthrough
                return isTmuxPassthroughEnabled();
            }
            // Known good terminals
            if (lower.contains("iterm") ||
                    lower.contains("alacritty") ||
                    lower.contains("kitty") ||
                    lower.contains("ghostty") ||
                    lower.contains("wezterm") ||
                    lower.contains("vscode") ||
                    lower.contains("hyper") ||
                    lower.contains("terminus") ||
                    lower.contains("tabby")) {
                return true;
            }
        }

        // Check terminal type for known good terminals
        if (termType != null) {
            String typeLower = termType.toLowerCase();
            if (typeLower.contains("xterm") ||
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
                    typeLower.contains("wave")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if tmux passthrough is enabled via environment variable.
     */
    private boolean isTmuxPassthroughEnabled() {
        String passthrough = System.getenv("TMUX_PASSTHROUGH");
        if (passthrough != null) {
            return "1".equals(passthrough) ||
                    "true".equalsIgnoreCase(passthrough) ||
                    "on".equalsIgnoreCase(passthrough);
        }
        return false;
    }

    @Override
    public boolean isMultiplexer() {
        // Check device type first
        String termType = type();
        if (termType != null) {
            String typeLower = termType.toLowerCase();
            if (typeLower.startsWith("screen") || typeLower.startsWith("tmux")) {
                return true;
            }
        }

        // Also check TERM_PROGRAM
        String termProgram = System.getenv("TERM_PROGRAM");
        if (termProgram != null) {
            String lower = termProgram.toLowerCase();
            return lower.equals("tmux") || lower.equals("screen");
        }

        return false;
    }
}
