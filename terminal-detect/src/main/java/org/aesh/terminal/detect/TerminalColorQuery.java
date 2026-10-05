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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct terminal color queries via OSC escape sequences.
 * I/O goes through a {@link TerminalProbeTransport}: FFM raw-mode I/O
 * on Java 22+ POSIX, the Win32 Console API on native Windows, the
 * {@code stty} subprocess fallback on older runtimes — or a custom
 * transport injected via {@link #setTransport}. Response parsing is
 * transport-agnostic.
 */
final class TerminalColorQuery {

    // All built-in probe sessions change process-wide tty/console mode.
    // Serialize raw-mode capture and restore across synchronous, async,
    // color, and grapheme probes so their saved states cannot overlap.
    private static final Object PROBE_LOCK = new Object();

    // OSC 10/11: foreground/background
    // OSC 4;N: palette color N
    // Query param is "?" terminated by BEL
    private static final char BEL = '\007';

    /** Built-in priority order, loaded only as each probe needs a fallback. */
    private enum BuiltInTransport {
        FFM,
        WIN32,
        STTY;

        TerminalProbeTransport available() {
            switch (this) {
                case FFM:
                    return loadFfmTransport();
                case WIN32:
                    return loadWin32Transport();
                case STTY:
                    return SttyProbeTransport.INSTANCE.isAvailable() ? SttyProbeTransport.INSTANCE : null;
                default:
                    throw new AssertionError(this);
            }
        }
    }

    private static final BuiltInTransport[] BUILT_IN_ORDER = BuiltInTransport.values();

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
    /**
     * Native grapheme clustering measured in the same session, or null
     * when the cursor-position probe did not run (DA1 absent or Mode
     * 2027 supported). False means the probe ran without clustering.
     */
    Boolean graphemeClustering;

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
        return queryFirstAvailable(false);
    }

    /**
     * Load the FFM probe transport (Java 22+ MRJAR layer) reflectively.
     * Returns null on pre-22 runtimes, without native access, on Windows,
     * or when /dev/tty is absent — other built-in transports are then
     * tried. Class loading is cheap: downcall handles are lazy, so this
     * never pays Linker initialization just to check availability.
     *
     * @return an available FFM transport, or null to try the next transport
     */
    // Cached transport reflection handles. Resolved once on first use
    // and reused across queries — TerminalBuilder-style probing would
    // otherwise repeat Class.forName plus constructor lookup per query.
    // A benign race may repeat the lookup; all threads compute the same
    // values. Unavailability (pre-22 runtimes) is not cached: the lookup
    // simply fails again, which is rare next to actual probing.
    private static volatile Class<?> cachedFfmTransportClass;
    private static volatile Constructor<?> cachedFfmTransportConstructor;
    private static volatile Class<?> cachedWin32TransportClass;
    private static volatile Constructor<?> cachedWin32TransportConstructor;

    private static TerminalProbeTransport loadFfmTransport() {
        try {
            Class<?> clazz = cachedFfmTransportClass;
            Constructor<?> constructor = cachedFfmTransportConstructor;
            if (clazz == null || constructor == null) {
                clazz = Class.forName("org.aesh.terminal.detect.FfmProbeTransport");
                constructor = clazz.getDeclaredConstructor();
                cachedFfmTransportClass = clazz;
                cachedFfmTransportConstructor = constructor;
            }
            TerminalProbeTransport transport = (TerminalProbeTransport) constructor.newInstance();
            return transport.isAvailable() ? transport : null;
        } catch (LinkageError | Exception ignored) {
            return null;
        }
    }

    /**
     * Load the Win32 probe transport (Java 22+ MRJAR layer) reflectively.
     * Returns null off-Windows, on pre-22 runtimes, without native access,
     * or without a console — other built-in transports are then tried.
     * Under Cygwin/MSYS2 there is no Windows console on stdin, so this
     * transport stays unavailable and the POSIX stty path applies.
     *
     * @return an available Win32 transport, or null to try the next transport
     */
    private static TerminalProbeTransport loadWin32Transport() {
        try {
            Class<?> clazz = cachedWin32TransportClass;
            Constructor<?> constructor = cachedWin32TransportConstructor;
            if (clazz == null || constructor == null) {
                clazz = Class.forName("org.aesh.terminal.detect.Win32ProbeTransport");
                constructor = clazz.getDeclaredConstructor();
                cachedWin32TransportClass = clazz;
                cachedWin32TransportConstructor = constructor;
            }
            TerminalProbeTransport transport = (TerminalProbeTransport) constructor.newInstance();
            return transport.isAvailable() ? transport : null;
        } catch (LinkageError | Exception ignored) {
            return null;
        }
    }

    static TerminalColorQuery query(TerminalProbeTransport transport) {
        if (!transport.isAvailable()) {
            return null;
        }
        synchronized (PROBE_LOCK) {
            try (TerminalProbeSession session = transport.open()) {
                session.write(buildColorQuery());

                // Full batch: 2 DECRPM + 1 DA1 + 19 OSC responses.
                // Terminals without DECRQM send no DECRPM; the DA1 fence
                // finalizes the mode budget so the read ends with the
                // 19 post-fence OSC replies instead of a timeout.
                String response = readBatchResponse(session.input());
                if (response == null || response.isEmpty()) {
                    return null;
                }

                return parseColorBatch(response);
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    private static TerminalColorQuery parseColorBatch(String response) {
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
    }

    /**
     * Color/mode query plus the cursor-position grapheme probe in one
     * raw-mode session. The grapheme phase runs under the same gating
     * as the standalone probe (DA1 received, Mode 2027 unsupported) and
     * keeps its failure semantics: any failure records false, while a
     * skipped phase leaves {@link #graphemeClustering} null.
     *
     * @param transport the transport to probe through
     * @return the combined result, or null when the color phase fails
     */
    static TerminalColorQuery queryFull(TerminalProbeTransport transport) {
        if (!transport.isAvailable()) {
            return null;
        }
        synchronized (PROBE_LOCK) {
            try (TerminalProbeSession session = transport.open()) {
                session.write(buildColorQuery());
                String response = readBatchResponse(session.input());
                if (response == null || response.isEmpty()) {
                    return null;
                }
                TerminalColorQuery result = parseColorBatch(response);
                if (result.da1Received && result.mode2027 == ModeSupport.NOT_SUPPORTED) {
                    try {
                        session.write(buildGraphemeProbe());
                        String cpr = readCprResponse(session.input());
                        session.write(RESTORE_CURSOR_AND_ERASE);
                        boolean clustered = false;
                        if (cpr != null && !cpr.isEmpty()) {
                            int[] position = TerminalReplyParser.cursorPosition(cpr);
                            if (position != null) {
                                clustered = position[1] <= 3;
                            }
                        }
                        result.graphemeClustering = clustered;
                    } catch (IOException graphemeFailure) {
                        result.graphemeClustering = false;
                    }
                }
                return result;
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    static TerminalColorQuery queryFull() {
        return queryFirstAvailable(true);
    }

    private static TerminalColorQuery queryFirstAvailable(boolean full) {
        TerminalProbeTransport custom = customTransport;
        if (custom != null) {
            // An injected transport replaces the built-in one exclusively:
            // no silent fallback to /dev/tty, which could steal input from
            // an embedder's own reader loop.
            return full ? queryFull(custom) : query(custom);
        }
        for (BuiltInTransport kind : BUILT_IN_ORDER) {
            TerminalProbeTransport transport = kind.available();
            if (transport == null) {
                continue;
            }
            TerminalColorQuery result = full ? queryFull(transport) : query(transport);
            if (result != null) {
                return result;
            }
            // A missing color response is not definitive: try the next
            // built-in before giving up.
        }
        return null;
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

    private static String readBatchResponse(InputStream in) throws IOException {
        byte[] buf = new byte[4096];
        StringBuilder sb = new StringBuilder();
        int n;
        while ((n = in.read(buf)) > 0) {
            sb.append(new String(buf, 0, n));
            FrameCount count = countFrames(sb);
            // Full batch: 2 DECRPM + DA1 + 19 OSC. Terminals without DECRQM
            // legitimately send no DECRPM, so once the DA1 fence lands the
            // mode budget is final and only the 19 post-fence OSC replies
            // can still be outstanding — never wait the full count past it.
            int expected = count.da1Seen ? count.decrpm + 20 : 22;
            if (count.total >= expected) {
                break;
            }
        }
        return sb.toString();
    }

    private static String readCprResponse(InputStream in) throws IOException {
        byte[] buf = new byte[4096];
        StringBuilder sb = new StringBuilder();
        int n;
        while ((n = in.read(buf)) > 0) {
            sb.append(new String(buf, 0, n));
            if (countFrames(sb).cprSeen) {
                break;
            }
        }
        return sb.toString();
    }

    /** Frame totals from one scan of an accumulated probe response. */
    private static final class FrameCount {
        int total;
        int decrpm;
        boolean da1Seen;
        boolean cprSeen;
    }

    /**
     * Count reply frames through the shared splitter. Totals match the
     * previous hand-rolled scan exactly: every CSI ending in {@code c},
     * {@code y}, or a CPR-shaped {@code R} counts once, as do BEL/ST
     * OSC terminators. (A {@code >}-body DA2 never counted and still
     * does not; it accumulates for parsers without completing reads.)
     */
    private static FrameCount countFrames(StringBuilder sb) {
        FrameCount count = new FrameCount();
        for (TerminalReplyFramer.Span span : TerminalReplyFramer.split(sb)) {
            switch (span.kind) {
                case OSC:
                    count.total++;
                    break;
                case DEVICE_ATTRIBUTES:
                    count.total++;
                    count.da1Seen = true;
                    break;
                case MODE_REPORT:
                    count.total++;
                    count.decrpm++;
                    break;
                case CURSOR_POSITION:
                    count.total++;
                    count.cprSeen = true;
                    break;
                default:
                    break;
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
        return TerminalReplyParser.oscColor(response, oscCode, oscParam);
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
        // Native transports first when available; their answers are
        // authoritative (same device, same raw mode, same bytes as stty),
        // so no fallback — falling back on "false" would re-probe every
        // non-clustering terminal twice.
        for (BuiltInTransport kind : BUILT_IN_ORDER) {
            TerminalProbeTransport transport = kind.available();
            if (transport != null) {
                return probeGraphemeClustering(transport);
            }
        }
        return false;
    }

    static boolean probeGraphemeClustering(TerminalProbeTransport transport) {
        if (!transport.isAvailable()) {
            return false;
        }
        synchronized (PROBE_LOCK) {
            try (TerminalProbeSession session = transport.open()) {
                session.write(buildGraphemeProbe());

                // Read CPR response: ESC [ row ; col R
                String response = readCprResponse(session.input());

                // Restore cursor and erase the test emoji
                session.write(RESTORE_CURSOR_AND_ERASE);

                if (response == null || response.isEmpty()) {
                    return false;
                }

                // Parse CPR: first complete valid frame wins (stale
                // garbage never poisons a later well-formed frame).
                int[] position = TerminalReplyParser.cursorPosition(response);
                if (position == null) {
                    return false;
                }
                int col = position[1];
                // Flag emoji: 2 columns if clustered, 4 if not
                return col <= 3;
            } catch (IOException ignored) {
                return false;
            }
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
     * <p>
     * Each CSI is framed independently, mirroring {@link #parseDA1Response}:
     * only a sequence terminated by {@code $y} is a mode report. Other
     * CSI responses sharing the {@code ESC[?} prefix (e.g. DA1's
     * {@code ...c}) are skipped without consuming the next DECRPM.
     */
    static void parseDECRPMResponses(String response, TerminalColorQuery result) {
        // If DA1 was received, default unresponded modes to NOT_SUPPORTED
        if (result.da1DeviceClass >= 0) {
            result.da1Received = true;
            result.mode2026 = ModeSupport.NOT_SUPPORTED;
            result.mode2027 = ModeSupport.NOT_SUPPORTED;
        }

        // One framed scan per mode; the last valid report wins, anything
        // else leaves the default (or an earlier valid report) alone.
        ModeSupport mode2026 = TerminalReplyParser.modeSupport(response, 2026);
        if (mode2026 != ModeSupport.NO_RESPONSE) {
            result.mode2026 = mode2026;
        }
        ModeSupport mode2027 = TerminalReplyParser.modeSupport(response, 2027);
        if (mode2027 != ModeSupport.NO_RESPONSE) {
            result.mode2027 = mode2027;
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
        TerminalReplyParser.TerminalDeviceAttributes attributes = TerminalReplyParser.deviceAttributes(response);
        if (attributes == null) {
            return;
        }
        result.da1DeviceClass = attributes.deviceClass;
        result.da1Features = new ArrayList<>(attributes.features);
        for (int feature : attributes.features) {
            if (feature == DA1_FEATURE_SIXEL) {
                result.supportsSixel = true;
            }
        }
    }

}
