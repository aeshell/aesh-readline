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
package org.aesh.terminal.detect;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct terminal color queries via OSC escape sequences.
 * I/O goes through a {@link TerminalProbeTransport}: the built-in
 * {@code /dev/tty} + {@code stty} transport on POSIX, or a custom
 * transport injected via {@link #setTransport} (e.g. Win32 Console API
 * on native Windows). Response parsing is transport-agnostic.
 */
final class TerminalColorQuery {

    private static final File DEV_TTY = new File("/dev/tty");
    // OSC 10/11: foreground/background
    // OSC 4;N: palette color N
    // Query param is "?" terminated by BEL
    private static final char BEL = '\007';

    int[] foreground;
    int[] background;
    Map<Integer, int[]> palette;
    boolean supports256;
    boolean supportsSixel;
    int da1DeviceClass = -1;
    List<Integer> da1Features;
    boolean da1Received;
    ModeSupport mode2026 = ModeSupport.NO_RESPONSE;
    ModeSupport mode2027 = ModeSupport.NO_RESPONSE;
    boolean nativeGraphemeClustering;

    TerminalColorQuery() {
    }

    /**
     * Injected probe transport. Null selects the built-in /dev/tty
     * transport. Volatile for safe publication across the background
     * query thread started by {@code detectAsync()}.
     */
    private static volatile TerminalProbeTransport customTransport;

    /**
     * Inject a custom probe transport. Null restores the built-in
     * {@code /dev/tty} transport. Public entry point is
     * {@link TerminalCapabilities#setProbeTransport}.
     *
     * @param transport the transport to use, or null for the built-in one
     */
    static void setTransport(TerminalProbeTransport transport) {
        customTransport = transport;
    }

    static TerminalColorQuery query() {
        TerminalProbeTransport custom = customTransport;
        if (custom != null) {
            // An injected transport replaces the built-in one exclusively:
            // no silent fallback to /dev/tty, which could steal input from
            // an embedder's own reader loop.
            return query(custom);
        }
        if (!DevTtyProbeTransport.INSTANCE.isAvailable()) {
            return null;
        }
        return query(DevTtyProbeTransport.INSTANCE);
    }

    static TerminalColorQuery query(TerminalProbeTransport transport) {
        if (!transport.isAvailable()) {
            return null;
        }
        try (TerminalProbeSession session = transport.open()) {
            session.write(buildColorQuery());

            // 22 expected terminators: 2 DECRPM + 1 DA1 + 19 OSC responses
            // (terminals that don't support DECRQM won't send DECRPM, so
            // the DA1 fence ensures we don't wait for them)
            String response = readResponse(session.input(), 22);
            if (response == null || response.isEmpty()) {
                return null;
            }

            TerminalColorQuery result = new TerminalColorQuery();
            parseDA1Response(response, result);
            parseDECRPMResponses(response, result);
            result.foreground = parseOscColorResponse(response, 10, -1);
            result.background = parseOscColorResponse(response, 11, -1);
            result.palette = new LinkedHashMap<>();
            for (int i = 0; i <= 15; i++) {
                int[] color = parseOscColorResponse(response, 4, i);
                if (color != null) {
                    result.palette.put(i, color);
                }
            }
            result.supports256 = parseOscColorResponse(response, 4, 255) != null;
            return result;
        } catch (IOException ignored) {
            return null;
        }
    }

    /**
     * The batched color/mode query: DECRQM probes + DA1 fence + OSC colors.
     * All ASCII, so US-ASCII encoding is exact.
     *
     * @return the query bytes to write to the terminal
     */
    static byte[] buildColorQuery() {
        // Build batch: DECRQM probes + DA1 + OSC colors
        // DECRQM responses arrive before DA1 (DA1 acts as fence)
        StringBuilder queries = new StringBuilder();
        queries.append("\033[?2026$p"); // DECRQM: Mode 2026 (synchronized output)
        queries.append("\033[?2027$p"); // DECRQM: Mode 2027 (grapheme cluster)
        queries.append("\033[c"); // DA1 query (fence)
        queries.append("\033]10;?").append(BEL);
        queries.append("\033]11;?").append(BEL);
        for (int i = 0; i <= 15; i++) {
            queries.append("\033]4;").append(i).append(";?").append(BEL);
        }
        queries.append("\033]4;255;?").append(BEL);
        return queries.toString().getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Built-in POSIX transport: /dev/tty with stty raw-mode handling.
     */
    private static final class DevTtyProbeTransport implements TerminalProbeTransport {
        static final DevTtyProbeTransport INSTANCE = new DevTtyProbeTransport();

        @Override
        public boolean isAvailable() {
            return DEV_TTY.exists() && DEV_TTY.canRead() && DEV_TTY.canWrite();
        }

        @Override
        public TerminalProbeSession open() throws IOException {
            String savedState = sttyGet();
            if (savedState == null) {
                throw new IOException("stty unavailable");
            }
            sttyRaw();
            return new DevTtyProbeSession(savedState);
        }
    }

    private static final class DevTtyProbeSession implements TerminalProbeSession {
        private final String savedState;
        private final FileOutputStream ttyOut;
        private final FileInputStream ttyIn;

        DevTtyProbeSession(String savedState) throws IOException {
            this.savedState = savedState;
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(DEV_TTY);
                FileInputStream in = new FileInputStream(DEV_TTY);
                this.ttyOut = out;
                this.ttyIn = in;
            } catch (IOException e) {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
                sttyRestore(savedState);
                throw e;
            }
        }

        @Override
        public void write(byte[] data) throws IOException {
            ttyOut.write(data);
            ttyOut.flush();
        }

        @Override
        public InputStream input() {
            return ttyIn;
        }

        @Override
        public void close() {
            try {
                ttyIn.close();
            } catch (IOException ignored) {
            }
            try {
                ttyOut.close();
            } catch (IOException ignored) {
            } finally {
                sttyRestore(savedState);
            }
        }
    }

    private static String sttyGet() {
        Process p = null;
        try {
            p = new ProcessBuilder("stty", "-g")
                    .redirectInput(DEV_TTY)
                    .redirectErrorStream(true)
                    .start();
            byte[] buf = new byte[256];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = p.getInputStream().read(buf)) != -1) {
                sb.append(new String(buf, 0, n));
            }
            p.waitFor();
            return p.exitValue() == 0 ? sb.toString().trim() : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (p != null)
                p.destroy();
        }
    }

    private static void sttyRaw() {
        Process p = null;
        try {
            p = new ProcessBuilder("stty", "-echo", "-icanon", "-ixon", "min", "0", "time", "5")
                    .redirectInput(DEV_TTY)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (Exception ignored) {
        } finally {
            if (p != null)
                p.destroy();
        }
    }

    private static void sttyRestore(String savedState) {
        Process p = null;
        try {
            p = new ProcessBuilder("stty", savedState)
                    .redirectInput(DEV_TTY)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (Exception ignored) {
        } finally {
            if (p != null)
                p.destroy();
        }
    }

    private static String readResponse(InputStream in, int expectedResponses) throws IOException {
        byte[] buf = new byte[4096];
        StringBuilder sb = new StringBuilder();
        int n;
        while ((n = in.read(buf)) > 0) {
            sb.append(new String(buf, 0, n));
            if (countTerminators(sb) >= expectedResponses) {
                break;
            }
        }
        return sb.toString();
    }

    private static int countTerminators(StringBuilder sb) {
        int count = 0;
        boolean inCsi = false;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == BEL) {
                count++;
                inCsi = false;
            } else if (c == '\033') {
                if (i + 1 < sb.length() && sb.charAt(i + 1) == '\\') {
                    count++;
                    i++;
                } else if (i + 1 < sb.length() && sb.charAt(i + 1) == '[') {
                    inCsi = true;
                    i++;
                }
            } else if (inCsi && c == 'c') {
                // DA1 response: ESC[?...c
                count++;
                inCsi = false;
            } else if (inCsi && c == 'y') {
                // DECRPM response: ESC[?<mode>;<Ps>$y
                count++;
                inCsi = false;
            } else if (inCsi && !Character.isDigit(c) && c != ';' && c != '?' && c != '$') {
                inCsi = false;
            }
        }
        return count;
    }

    // ==================== OSC Response Parsing ====================

    /**
     * Parse an OSC color response for a given code and optional parameter.
     *
     * @param response the raw terminal response
     * @param oscCode the OSC code (4, 10, 11)
     * @param oscParam the parameter index (-1 for none, 0-255 for palette)
     */
    static int[] parseOscColorResponse(String response, int oscCode, int oscParam) {
        if (response == null || response.length() < 10) {
            return null;
        }

        String oscMarker = "\033]" + oscCode + ";";
        int searchFrom = 0;

        while (true) {
            int start = response.indexOf(oscMarker, searchFrom);
            if (start < 0) {
                return null;
            }

            int afterMarker = start + oscMarker.length();

            if (oscParam >= 0) {
                String paramMarker = oscParam + ";";
                if (!response.substring(afterMarker).startsWith(paramMarker)) {
                    searchFrom = afterMarker;
                    continue;
                }
                afterMarker += paramMarker.length();
            }

            int rgbStart = response.indexOf("rgb:", afterMarker);
            if (rgbStart < 0) {
                return null;
            }

            int belPos = response.indexOf(BEL, afterMarker);
            int stPos = response.indexOf("\033\\", afterMarker);
            int terminatorPos = -1;
            if (belPos >= 0 && stPos >= 0) {
                terminatorPos = Math.min(belPos, stPos);
            } else if (belPos >= 0) {
                terminatorPos = belPos;
            } else if (stPos >= 0) {
                terminatorPos = stPos;
            }

            if (terminatorPos >= 0 && rgbStart > terminatorPos) {
                searchFrom = terminatorPos + 1;
                continue;
            }

            rgbStart += 4;

            int end = response.indexOf(BEL, rgbStart);
            if (end < 0) {
                end = response.indexOf("\033\\", rgbStart);
            }
            if (end < 0) {
                end = response.length();
            }

            String rgbPart = response.substring(rgbStart, end);
            String[] parts = rgbPart.split("/");
            return parseHexRgbParts(parts);
        }
    }

    private static int[] parseHexRgbParts(String[] parts) {
        if (parts.length != 3) {
            return null;
        }
        try {
            int[] rgb = new int[3];
            for (int i = 0; i < 3; i++) {
                String hex = parts[i].trim();
                if (hex.isEmpty() || hex.length() > 4) {
                    return null;
                }
                int raw = Integer.parseInt(hex, 16);
                int value;
                switch (hex.length()) {
                    case 1:
                        value = raw * 17;
                        break;
                    case 2:
                        value = raw;
                        break;
                    case 3:
                        value = raw >> 4;
                        break;
                    case 4:
                        value = raw >> 8;
                        break;
                    default:
                        return null;
                }
                rgb[i] = Math.min(255, Math.max(0, value));
            }
            return rgb;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== Grapheme Cluster Probe ====================

    /**
     * Probe grapheme cluster support via cursor position measurement.
     * Writes a flag emoji (🇫🇷), queries cursor position, checks whether
     * the terminal treated it as one cluster (2 columns) or two separate
     * regional indicators (4 columns).
     *
     * @return true if native grapheme clustering is detected
     */
    static boolean probeGraphemeClustering() {
        TerminalProbeTransport custom = customTransport;
        if (custom != null) {
            return probeGraphemeClustering(custom);
        }
        if (!DevTtyProbeTransport.INSTANCE.isAvailable()) {
            return false;
        }
        return probeGraphemeClustering(DevTtyProbeTransport.INSTANCE);
    }

    static boolean probeGraphemeClustering(TerminalProbeTransport transport) {
        if (!transport.isAvailable()) {
            return false;
        }
        try (TerminalProbeSession session = transport.open()) {
            session.write(buildGraphemeProbe());

            // Read CPR response: ESC [ row ; col R
            String response = readResponse(session.input(), 1); // expect 1 terminator (the 'R')

            // Restore cursor and erase the test emoji
            session.write(RESTORE_CURSOR_AND_ERASE);

            if (response == null || response.isEmpty()) {
                return false;
            }

            // Parse CPR: ESC [ row ; col R
            int rIdx = response.indexOf('R');
            if (rIdx < 0)
                return false;
            int escIdx = response.lastIndexOf('\033', rIdx);
            if (escIdx < 0 || escIdx + 2 >= rIdx)
                return false;
            String params = response.substring(escIdx + 2, rIdx);
            String[] parts = params.split(";");
            if (parts.length >= 2) {
                try {
                    int col = Integer.parseInt(parts[1].trim());
                    // Flag emoji: 2 columns if clustered, 4 if not
                    return col <= 3;
                } catch (NumberFormatException e) {
                    return false;
                }
            }
            return false;
        } catch (IOException ignored) {
            return false;
        }
    }

    /** Restore cursor (DECSC) + erase line after the grapheme probe. ASCII. */
    private static final byte[] RESTORE_CURSOR_AND_ERASE = "\0338\033[K".getBytes(StandardCharsets.US_ASCII);

    /**
     * Save cursor, move to column 0, erase line, write flag emoji, query
     * position. Contains a non-ASCII emoji, so UTF-8 encoding is required.
     *
     * @return the probe bytes to write to the terminal
     */
    static byte[] buildGraphemeProbe() {
        // Save cursor, move to column 0, erase line, write flag emoji, query position
        String probe = "\0337" // save cursor (DECSC)
                + "\r" // column 0
                + "\033[K" // erase line
                + "\uD83C\uDDEB\uD83C\uDDF7" // 🇫🇷 (two regional indicators)
                + "\033[6n"; // DSR: query cursor position
        return probe.getBytes(StandardCharsets.UTF_8);
    }

    // ==================== DECRPM Response Parsing ====================

    /**
     * Parse DECRPM (DEC Private Mode Report) responses for Mode 2026 and 2027.
     * Format: ESC [ ? {mode} ; {Ps} $ y
     * Ps: 0=not recognized, 1=set, 2=reset(recognized), 3=permanently set, 4=permanently reset
     */
    static void parseDECRPMResponses(String response, TerminalColorQuery result) {
        // If DA1 was received, default unresponded modes to NOT_SUPPORTED
        if (result.da1DeviceClass >= 0) {
            result.da1Received = true;
            result.mode2026 = ModeSupport.NOT_SUPPORTED;
            result.mode2027 = ModeSupport.NOT_SUPPORTED;
        }

        int pos = 0;
        while (pos < response.length()) {
            // Find ESC [ ?
            int start = response.indexOf("\033[?", pos);
            if (start < 0)
                break;

            // Find $ y (DECRPM terminator)
            int dollarY = response.indexOf("$y", start + 3);
            if (dollarY < 0) {
                // Try to find 'c' (DA1) or other terminator to advance past this CSI
                int cPos = response.indexOf('c', start + 3);
                if (cPos >= 0) {
                    pos = cPos + 1;
                    continue;
                }
                break;
            }

            // Parse the params between ESC[? and $y
            String params = response.substring(start + 3, dollarY);
            String[] parts = params.split(";");
            if (parts.length >= 2) {
                try {
                    int mode = Integer.parseInt(parts[0].trim());
                    int ps = Integer.parseInt(parts[1].trim());
                    // Ps: 1=set, 2=reset(recognized), 3=permanently set → SUPPORTED
                    //     0=not recognized, 4=permanently reset → NOT_SUPPORTED
                    ModeSupport support = (ps >= 1 && ps <= 3)
                            ? ModeSupport.SUPPORTED
                            : ModeSupport.NOT_SUPPORTED;

                    if (mode == 2026)
                        result.mode2026 = support;
                    else if (mode == 2027)
                        result.mode2027 = support;
                } catch (NumberFormatException ignored) {
                }
            }
            pos = dollarY + 2;
        }
    }

    // ==================== DA1 Response Parsing ====================

    private static final int DA1_FEATURE_SIXEL = 4;

    /**
     * Parse a DA1 (Primary Device Attributes) response.
     * Format: ESC[?{class};{feat1};{feat2};...c
     * Feature code 4 = Sixel graphics support.
     * <p>
     * Skips other CSI responses sharing the {@code ESC[?} prefix — in a
     * batched probe DECRPM responses ({@code ESC[?...$y}) arrive before
     * DA1, and grabbing the first {@code ESC[?} would misparse DECRPM
     * params as a device class (e.g. class 2026) and lose the feature
     * list. Only a sequence terminated by {@code c} is DA1.
     */
    static void parseDA1Response(String response, TerminalColorQuery result) {
        int pos = 0;
        while (true) {
            // Find ESC[? ...
            int start = response.indexOf("\033[?", pos);
            if (start < 0) {
                return;
            }
            // ... scan params (digits, separators, DECRPM's '$' intermediate)
            int end = start + 3;
            while (end < response.length() && (Character.isDigit(response.charAt(end))
                    || response.charAt(end) == ';' || response.charAt(end) == '?'
                    || response.charAt(end) == '$')) {
                end++;
            }
            if (end < response.length() && response.charAt(end) == 'c') {
                String params = response.substring(start + 3, end);
                String[] parts = params.split(";");
                if (parts.length == 0) {
                    return;
                }
                try {
                    result.da1DeviceClass = Integer.parseInt(parts[0].trim());
                    result.da1Features = new ArrayList<>();
                    for (int i = 1; i < parts.length; i++) {
                        int feature = Integer.parseInt(parts[i].trim());
                        result.da1Features.add(feature);
                        if (feature == DA1_FEATURE_SIXEL) {
                            result.supportsSixel = true;
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
                return;
            }
            // Some other CSI response (e.g. DECRPM $y) — keep looking
            pos = end + 1;
        }
    }
}
