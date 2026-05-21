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
package org.aesh.terminal.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.aesh.terminal.tty.Capability;

/**
 * Infocmp helper methods.
 *
 * @author <a href="mailto:gnodet@gmail.com">Guillaume Nodet</a>
 */
public final class InfoCmp {

    private static final Map<String, String> CAPS = new HashMap<>();

    private InfoCmp() {
    }

    /**
     * Get the terminal capabilities for the specified terminal type.
     * The result is cached for subsequent calls with the same terminal type.
     *
     * @param terminal the terminal type (e.g., "xterm-256color")
     * @return the infocmp output for the terminal
     * @throws IOException if an I/O error occurs
     * @throws InterruptedException if the thread is interrupted
     */
    public static String getInfoCmp(String terminal) throws IOException, InterruptedException {
        String caps = CAPS.get(terminal);
        if (caps == null) {
            // Try bundled .src files first — no subprocess, always available
            caps = getDefaultInfoCmp(terminal);
            // Fall back to spawning infocmp if no bundled match
            if (caps == null || caps.isEmpty()) {
                try {
                    Process p = new ProcessBuilder(OSUtils.INFOCMP_COMMAND, terminal).start();
                    caps = ExecHelper.waitAndCapture(p);
                } catch (IOException e) {
                    // infocmp not available either — caps stays null
                }
            }
            if (caps != null) {
                CAPS.put(terminal, caps);
            }
        }
        return caps;
    }

    /**
     * Get the default built-in terminal capabilities for the specified terminal type.
     * This is used as a fallback when the infocmp command is not available.
     *
     * @param terminal the terminal type (e.g., "xterm-256color")
     * @return the default capabilities string, or null if not found
     */
    public static String getDefaultInfoCmp(String terminal) {
        String lower = terminal.toLowerCase();
        if (lower.contains("windows")) {
            return readDefaultInfoCmp("windows_caps.src");
        } else if (lower.contains("tmux-256color") || lower.contains("tmux_256color")) {
            return readDefaultInfoCmp("tmux-256color_caps.src");
        } else if (lower.contains("screen-256color") || lower.contains("screen_256color")) {
            return readDefaultInfoCmp("screen-256color_caps.src");
        } else if (lower.contains("xterm-256color") || lower.contains("xterm_256color")) {
            return readDefaultInfoCmp("xterm-256color_caps.src");
        } else if (lower.contains("xterm-kitty")) {
            return readDefaultInfoCmp("xterm-kitty_caps.src");
        } else if (lower.contains("alacritty")) {
            return readDefaultInfoCmp("alacritty_caps.src");
        } else if (lower.contains("xterm")) {
            return readDefaultInfoCmp("xterm_caps.src");
        } else if (lower.equals("linux")) {
            return readDefaultInfoCmp("linux_caps.src");
        } else if (lower.contains("vt100")) {
            return readDefaultInfoCmp("vt100_caps.src");
        } else {
            return readDefaultInfoCmp("ansi_caps.src");
        }
    }

    private static String readDefaultInfoCmp(String filename) {
        ByteArrayOutputStream res = new ByteArrayOutputStream();
        try (InputStream in = InfoCmp.class.getResourceAsStream(filename)) {
            byte[] buffer = new byte[256];
            while (true) {
                int len = in.read(buffer);
                if (len == -1) {
                    break;
                }
                res.write(buffer, 0, len);
            }
            return res.toString("ISO-8859-1");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Set the default terminal capabilities for a terminal type.
     * This will only set the value if no capabilities have been set for this terminal.
     *
     * @param terminal the terminal type
     * @param caps the capabilities string to set
     */
    public static void setDefaultInfoCmp(String terminal, String caps) {
        CAPS.putIfAbsent(terminal, caps);
    }

    /**
     * Parse an infocmp capabilities string and populate the provided collections.
     * Boolean capabilities are added to the bools set, integer capabilities to the ints map,
     * and string capabilities to the strings map.
     *
     * @param capabilities the raw capabilities string from infocmp
     * @param bools set to populate with boolean capabilities
     * @param ints map to populate with integer capabilities
     * @param strings map to populate with string capabilities
     */
    public static void parseInfoCmp(
            String capabilities,
            Set<Capability> bools,
            Map<Capability, Integer> ints,
            Map<Capability, String> strings) {

        if (capabilities == null || capabilities.isEmpty()) {
            return;
        }

        String[] lines = capabilities.split("\n");
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty())
                continue;

            java.util.List<String> tokens = splitTokens(line);
            for (String cap : tokens) {
                if (cap == null)
                    continue;
                cap = cap.trim();
                if (cap.isEmpty())
                    continue;

                int hash = cap.indexOf('#');
                int eq = cap.indexOf('=');

                if (hash >= 0) {
                    String key = cap.substring(0, hash).trim();
                    String val = cap.substring(hash + 1).trim();
                    try {
                        int iVal;
                        if (val.startsWith("0x") || val.startsWith("0X")) {
                            iVal = Integer.parseInt(val.substring(2), 16);
                        } else if (val.startsWith("0")) {
                            iVal = Integer.parseInt(val.substring(1), 8);
                        } else {
                            iVal = Integer.parseInt(val);
                        }
                        Capability c = Capability.byName(key);
                        if (c != null) {
                            ints.put(c, iVal);
                        }
                    } catch (NumberFormatException e) {
                        // ignore malformed integer values
                    }
                } else if (eq >= 0) {
                    String key = cap.substring(0, eq).trim();
                    String val = cap.substring(eq + 1).trim();
                    Capability c = Capability.byName(key);
                    if (c != null) {
                        strings.put(c, val);
                    }
                } else {
                    // boolean capability
                    // remove any trailing punctuation
                    String key = cap.replaceAll("[,\\.]$", "").trim();
                    Capability c = Capability.byName(key);
                    if (c != null) {
                        bools.add(c);
                    }
                }
            }
        }
    }

    private static java.util.List<String> splitTokens(String line) {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '\\' && i + 1 < line.length()) {
                char next = line.charAt(i + 1);
                if (next == ',') {
                    // escaped comma -> include literal comma
                    sb.append(',');
                    i++; // skip next
                } else {
                    // keep the backslash for other escapes
                    sb.append(ch);
                }
                continue;
            }
            if (ch == ',') {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        if (sb.length() > 0) {
            tokens.add(sb.toString().trim());
        }

        // clean tokens: remove trailing commas or dots leftover
        for (int j = 0; j < tokens.size(); j++) {
            String t = tokens.get(j);
            t = t.replaceAll("[,\\.]$", "").trim();
            tokens.set(j, t);
        }

        return tokens;
    }
}
