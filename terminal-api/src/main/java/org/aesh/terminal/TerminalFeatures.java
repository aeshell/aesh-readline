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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import org.aesh.terminal.detect.ImageProtocol;
import org.aesh.terminal.detect.ModeSupport;
import org.aesh.terminal.detect.TerminalReplyFramer;
import org.aesh.terminal.detect.TerminalReplyFramer.Kind;
import org.aesh.terminal.detect.TerminalReplyFramer.Span;
import org.aesh.terminal.detect.TerminalTheme;
import org.aesh.terminal.image.ImageProtocolDetector;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Point;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.CodePointUtils;
import org.aesh.terminal.utils.ColorDepth;
import org.aesh.terminal.utils.TerminalColorCapability;

/**
 * Advanced terminal features: queries, capability detection, semantic output,
 * and mode management.
 * <p>
 * This class wraps a {@link Connection} and provides access to all
 * terminal-specific functionality that goes beyond basic I/O. Access it
 * via {@link Connection#terminal()}.
 * <p>
 * Features are organized into four areas:
 * <ul>
 * <li><b>Queries</b> — send escape sequences and parse responses (OSC, DA1/DA2, DECRQM, theme DSR)</li>
 * <li><b>Capability Detection</b> — heuristic checks based on terminal type and environment</li>
 * <li><b>Semantic Output</b> — shell integration markers, hyperlinks, clipboard</li>
 * <li><b>Mode Toggles</b> — synchronized output, grapheme cluster mode, theme notifications</li>
 * </ul>
 * <p>
 * Capability ownership: the device, live queries, seeded probe modes,
 * and this connection's own theme subscription belong to the
 * connection. Process-global startup defaults never answer for a
 * connection directly — local connections seed a snapshot from them
 * (see {@link #seedProbedModes}), while remote ones answer from
 * device heuristics plus live queries.
 *
 * @see Connection#terminal()
 */
public class TerminalFeatures {

    private static final Logger LOGGER = Logger.getLogger(TerminalFeatures.class.getName());

    // EventDecoder updates the shared theme cache before invoking its handler.
    // This marker enables interception when no application callback was supplied.
    private static final Consumer<TerminalTheme> CACHE_ONLY_THEME_HANDLER = new CacheOnlyThemeHandler();

    /**
     * Startup-probe snapshot seeding this connection. Set by local
     * connections from the shared probe results; null stays null, so
     * connections without a local terminal (ssh, telnet, http) never
     * consult process-global probe facts that describe another machine.
     */
    private volatile ModeSupport seededMode2026;
    private volatile ModeSupport seededMode2027;
    private volatile Boolean seededNativeGraphemeClustering;

    private static final class CacheOnlyThemeHandler implements Consumer<TerminalTheme> {
        @Override
        public void accept(TerminalTheme theme) {
            // EventDecoder already applied this notification to the cache.
        }
    }

    /**
     * Default timeout in milliseconds for terminal queries (OSC, DA1, DA2, etc.).
     * Suitable for most query operations where the terminal is expected to respond.
     */
    public static final long DEFAULT_QUERY_TIMEOUT_MS = 500;

    /**
     * Fast timeout in milliseconds for lightweight terminal queries.
     * Use for simple queries like theme DSR or when low latency is important.
     */
    public static final long FAST_QUERY_TIMEOUT_MS = 150;

    private final Connection connection;

    /**
     * Constructor.
     *
     * @param connection the connection
     */
    public TerminalFeatures(Connection connection) {
        this.connection = connection;
    }

    /**
     * Access the underlying connection.
     *
     * @return the connection this features object wraps
     */
    public Connection connection() {
        return connection;
    }

    // ==================== Queries ====================

    /**
     * Send a query to the terminal and wait for a response with timeout.
     * <p>
     * This method enters raw mode, sends the query, collects the response,
     * and restores the original terminal attributes. It's useful for both
     * OSC queries and CSI queries (DA1/DA2, DECRQM, theme DSR, etc.).
     * <p>
     * Response chunks accumulate: each arriving chunk extends a buffer that
     * the parser re-examines whole, so replies split across reads match
     * once their frame completes. Bytes that can never belong to a reply
     * (runs without ESC, framed by {@code TerminalReplyFramer} shared
     * with standalone probing) are forwarded to the pre-lease input
     * handler promptly instead of waiting out the query. On timeout
     * nothing matched, so every held byte is unclaimed input — handed
     * back to the restored input handler rather than dropped. On success
     * the matched frames stay consumed while complete non-reply shapes
     * caught alongside them are handed back the same way.
     * <p>
     * This method uses {@link Connection#setStdinHandler(Consumer)} to receive responses,
     * which requires the connection to be actively reading input (i.e.,
     * {@link Connection#reading()} returns true). If not reading, this method returns null.
     *
     * @param query the query sequence to send
     * @param timeoutMs timeout in milliseconds to wait for response
     * @param responseParser function to parse the response; should return non-null when
     *        a complete response is received, null to continue waiting
     * @param <T> the type of the parsed response
     * @return the parsed response, or null if not reading, timeout, or parsing failed
     */
    public <T> T queryTerminal(String query, long timeoutMs,
            Function<int[], T> responseParser) {
        if (!connection.supportsAnsi()) {
            return null;
        }

        if (!connection.reading()) {
            return null;
        }

        CountDownLatch latch = new CountDownLatch(1);
        final Object[] result = { null };
        Consumer<int[]> appHandler = connection.stdinHandler();
        final ReplyAccumulator responses = new ReplyAccumulator(appHandler);
        Attributes savedAttributes = connection.enterRawMode();

        try (StdinLease ignored = connection.captureStdin(new Consumer<int[]>() {
            @Override
            public void accept(int[] ints) {
                if (result[0] != null) {
                    return;
                }
                responses.append(ints);
                T parsed = responseParser.apply(responses.snapshot());
                if (parsed != null) {
                    result[0] = parsed;
                    latch.countDown();
                }
            }
        })) {
            try {
                connection.write(query);
                latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } finally {
            connection.setAttributes(savedAttributes);
        }

        @SuppressWarnings("unchecked")
        T typedResult = (T) result[0];
        if (typedResult == null) {
            redeliverUnmatched(responses.snapshot());
        } else {
            // A matched reply consumes its frames; complete non-reply
            // shapes caught in the buffer (arrow keys and friends) still
            // belong to the application.
            redeliverUnmatched(responses.unmatchedOnSuccess());
        }
        return typedResult;
    }

    /**
     * Hand bytes no parser claimed back to the live input handler.
     * <p>
     * Called after the query lease closes (handler restored) when the
     * query found nothing: the buffered bytes are unrelated input worth
     * keeping — keystrokes, signals, a partial reply — not response
     * traffic. Success needs no redelivery: its buffer holds the matched
     * frame, which must not be re-injected as input.
     *
     * @param responses the accumulated query input
     */
    private void redeliverUnmatched(int[] leftovers) {
        if (leftovers.length > 0) {
            Consumer<int[]> restored = connection.stdinHandler();
            if (restored != null) {
                restored.accept(leftovers);
            }
        }
    }

    /**
     * Query-input accumulator with prompt plain-text forwarding.
     * <p>
     * Each chunk is scanned once together with the carried tail: plain
     * runs go straight to the pre-lease application handler, reply
     * frames and complete non-reply shapes accumulate for the parser,
     * and the trailing partial becomes the next tail. Parsers therefore
     * see the same frames as a hold-everything buffer minus already
     * delivered plain input, while keystrokes surface immediately.
     * Without a pre-lease handler everything accumulates, as before.
     */
    private static final class ReplyAccumulator {
        private final StringBuilder frames = new StringBuilder();
        private String tail = "";
        private final Consumer<int[]> appHandler;

        ReplyAccumulator(Consumer<int[]> appHandler) {
            this.appHandler = appHandler;
        }

        /**
         * Append a chunk; forward provably-non-reply runs promptly.
         *
         * @param chunk the arriving code points
         */
        void append(int[] chunk) {
            String text = tail + new String(chunk, 0, chunk.length);
            tail = "";
            if (appHandler == null) {
                frames.append(text);
                return;
            }
            int end = text.length();
            int cursor = 0;
            for (Span span : TerminalReplyFramer.split(text)) {
                if (span.kind == Kind.PARTIAL) {
                    tail = text.substring(span.start, span.end);
                    end = span.start;
                    break;
                }
                if (span.kind == Kind.PLAIN_TEXT) {
                    frames.append(text, cursor, span.start);
                    forward(text.substring(span.start, span.end));
                    cursor = span.end;
                }
            }
            frames.append(text, cursor, end);
        }

        /**
         * The held bytes for parsing and timeout redelivery: frames plus
         * the current tail. Plain runs were already delivered and stay out.
         *
         * @return the buffered code points
         */
        int[] snapshot() {
            return toCodePoints(frames.toString() + tail);
        }

        /**
         * Held bytes minus consumed reply frames, for success redelivery:
         * complete non-reply shapes (arrow keys and friends) reach the
         * application instead of being dropped with the matched frames.
         * Partial tails may be reply fragments and stay dropped, as do
         * already-delivered plain runs.
         *
         * @return the redeliverable code points
         */
        int[] unmatchedOnSuccess() {
            String held = frames.toString();
            StringBuilder out = new StringBuilder();
            int cursor = 0;
            for (Span span : TerminalReplyFramer.split(held)) {
                if (isReply(span.kind)) {
                    out.append(held, cursor, span.start);
                    cursor = span.end;
                }
            }
            out.append(held, cursor, held.length());
            return toCodePoints(out.toString());
        }

        private static boolean isReply(Kind kind) {
            return kind == Kind.OSC || kind == Kind.DEVICE_ATTRIBUTES
                    || kind == Kind.MODE_REPORT || kind == Kind.CURSOR_POSITION;
        }

        private static int[] toCodePoints(String text) {
            return text.codePoints().toArray();
        }

        private void forward(String text) {
            appHandler.accept(toCodePoints(text));
        }
    }

    // No probe methods here — terminal mode detection is done in
    // terminal-detect (TerminalColorQuery) via direct /dev/tty I/O.

    /**
     * Get the current cursor position in the terminal.
     *
     * @return the current cursor position as a Point (row, column)
     */
    public Point getCursorPosition() {
        CountDownLatch latch = new CountDownLatch(1);
        final Point[] p = { null };
        final ReplyAccumulator responses = new ReplyAccumulator(connection.stdinHandler());
        Attributes attributes = connection.enterRawMode();
        // try-with-resources restores the previous handler on every exit
        // path, including query timeout — the previous hand-rolled version
        // restored only inside the response callback, leaking the hijack
        // (and raw mode) when no response arrived. Chunks accumulate so a
        // CPR split across reads still matches once complete.
        try (StdinLease ignored = connection.captureStdin(new Consumer<int[]>() {
            @Override
            public void accept(int[] ints) {
                if (p[0] != null) {
                    return;
                }
                responses.append(ints);
                p[0] = ANSI.getActualCursor(responses.snapshot());
                if (p[0] != null) {
                    latch.countDown();
                }
            }
        })) {
            connection.stdoutHandler().accept(ANSI.CURSOR_POSITION_QUERY);
            try {
                latch.await(DEFAULT_QUERY_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } finally {
            connection.setAttributes(attributes);
        }
        if (p[0] == null) {
            redeliverUnmatched(responses.snapshot());
        } else {
            redeliverUnmatched(responses.unmatchedOnSuccess());
        }
        return p[0];
    }

    /**
     * Send an OSC (Operating System Command) query to the terminal.
     *
     * @param oscCode the OSC code (e.g., 10 for foreground, 11 for background)
     * @param param the query parameter (typically "?" for queries)
     * @param timeoutMs timeout in milliseconds to wait for response
     * @param responseParser function to parse the response
     * @param <T> the type of the parsed response
     * @return the parsed response, or null if timeout or not supported
     */
    public <T> T queryOsc(int oscCode, String param, long timeoutMs,
            Function<int[], T> responseParser) {
        if (connection.device() != null && !connection.device().supportsOscQueries()) {
            return null;
        }
        String query = ANSI.buildOscQuery(oscCode, param);
        return queryTerminal(query, timeoutMs, responseParser);
    }

    /**
     * Query the terminal for its foreground color using OSC 10.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-255 each), or null if not supported or timeout
     */
    public int[] queryForegroundColor(long timeoutMs) {
        return queryOsc(ANSI.OSC_FOREGROUND, "?", timeoutMs,
                input -> ANSI.parseOscColorResponse(input, ANSI.OSC_FOREGROUND));
    }

    /**
     * Query the terminal for its background color using OSC 11.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-255 each), or null if not supported or timeout
     */
    public int[] queryBackgroundColor(long timeoutMs) {
        return queryOsc(ANSI.OSC_BACKGROUND, "?", timeoutMs,
                input -> ANSI.parseOscColorResponse(input, ANSI.OSC_BACKGROUND));
    }

    /**
     * Query the terminal for its cursor color using OSC 12.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-255 each), or null if not supported or timeout
     */
    public int[] queryCursorColor(long timeoutMs) {
        return queryOsc(ANSI.OSC_CURSOR_COLOR, "?", timeoutMs,
                input -> ANSI.parseOscColorResponse(input, ANSI.OSC_CURSOR_COLOR));
    }

    /**
     * Query the terminal for its foreground color with 16-bit precision.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-65535 each), or null if not supported or timeout
     */
    public int[] queryForegroundColor16(long timeoutMs) {
        return queryOsc(ANSI.OSC_FOREGROUND, "?", timeoutMs,
                input -> ANSI.parseOscColorResponse16(input, ANSI.OSC_FOREGROUND));
    }

    /**
     * Query the terminal for its background color with 16-bit precision.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-65535 each), or null if not supported or timeout
     */
    public int[] queryBackgroundColor16(long timeoutMs) {
        return queryOsc(ANSI.OSC_BACKGROUND, "?", timeoutMs,
                input -> ANSI.parseOscColorResponse16(input, ANSI.OSC_BACKGROUND));
    }

    /**
     * Query the terminal for its cursor color with 16-bit precision.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-65535 each), or null if not supported or timeout
     */
    public int[] queryCursorColor16(long timeoutMs) {
        return queryOsc(ANSI.OSC_CURSOR_COLOR, "?", timeoutMs,
                input -> ANSI.parseOscColorResponse16(input, ANSI.OSC_CURSOR_COLOR));
    }

    /**
     * Query the terminal for its current theme mode using the CSI ? 996 n protocol.
     * <p>
     * Coordinates with theme notification handling instead of racing it:
     * {@code EventDecoder} intercepts the reply whenever a theme
     * subscription is active, so this method subscribes a recording
     * wrapper around the current handler for the wait. The wrapper
     * forwards each reply to the saved subscriber and reports the first
     * theme to the query; the saved subscription is restored verbatim
     * afterwards, including null. There is no double delivery (one
     * invocation path) and fragmented replies reassemble in the decoder
     * before dispatch.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return {@link TerminalTheme#DARK} or {@link TerminalTheme#LIGHT},
     *         or {@code null} if not supported or timeout
     */
    public TerminalTheme queryThemeMode(long timeoutMs) {
        if (!connection.supportsAnsi()) {
            return null;
        }
        if (connection.device() != null && !connection.device().supportsThemeQuery()) {
            return null;
        }
        if (!connection.reading()) {
            return null;
        }
        CountDownLatch latch = new CountDownLatch(1);
        final TerminalTheme[] result = { null };
        final Consumer<TerminalTheme> saved = connection.themeChangeHandler();
        connection.setThemeChangeHandler(new Consumer<TerminalTheme>() {
            @Override
            public void accept(TerminalTheme theme) {
                if (saved != null) {
                    saved.accept(theme);
                }
                if (result[0] == null) {
                    result[0] = theme;
                    latch.countDown();
                }
            }
        });
        try {
            connection.write(ANSI.THEME_MODE_QUERY);
            latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            connection.setThemeChangeHandler(saved);
        }
        return result[0];
    }

    /**
     * Send an OSC query with an index parameter to the terminal.
     *
     * @param oscCode the OSC code (e.g., 4 for palette color)
     * @param index the index parameter (e.g., palette color index 0-255)
     * @param param the query parameter (typically "?" for queries)
     * @param timeoutMs timeout in milliseconds to wait for response
     * @param responseParser function to parse the response
     * @param <T> the type of the parsed response
     * @return the parsed response, or null if timeout or not supported
     */
    public <T> T queryOsc(int oscCode, int index, String param, long timeoutMs,
            Function<int[], T> responseParser) {
        if (connection.device() != null && !connection.device().supportsOscQueries()) {
            return null;
        }
        String query = ANSI.buildOscQuery(oscCode, index, param);
        return queryTerminal(query, timeoutMs, responseParser);
    }

    /**
     * Query the terminal for a palette color using OSC 4.
     *
     * @param index the palette color index (0-255)
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-255 each), or null if not supported or timeout
     */
    public int[] queryPaletteColor(int index, long timeoutMs) {
        return queryOsc(ANSI.OSC_PALETTE, index, "?", timeoutMs,
                input -> ANSI.parseOscColorResponse(input, ANSI.OSC_PALETTE, index));
    }

    // ==================== Batch OSC Queries ====================

    /**
     * Shared orchestration for batch OSC queries with response buffering.
     *
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @param expectedCount the number of expected responses
     * @param batchQuery the concatenated query string to send
     * @param parser function to parse the buffered response into a result map
     * @return map from key to RGB array [r, g, b] (0-255 each)
     */
    public Map<Integer, int[]> queryWithResponseBuffering(
            long timeoutMs, int expectedCount,
            String batchQuery,
            Function<int[], Map<Integer, int[]>> parser) {

        CountDownLatch latch = new CountDownLatch(1);
        final Map<Integer, int[]> results = new ConcurrentHashMap<>();
        final StringBuilder responseBuffer = new StringBuilder();
        Attributes savedAttributes = connection.enterRawMode();

        try (StdinLease ignored = connection.captureStdin(new Consumer<int[]>() {
            @Override
            public void accept(int[] ints) {
                for (int c : ints) {
                    responseBuffer.appendCodePoint(c);
                }

                Map<Integer, int[]> parsed = parser.apply(
                        CodePointUtils.toCodePoints(responseBuffer.toString()));
                results.putAll(parsed);

                if (results.size() >= expectedCount) {
                    latch.countDown();
                }
            }
        })) {
            try {
                connection.write(batchQuery);
                latch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } finally {
            connection.setAttributes(savedAttributes);
        }

        return results;
    }

    /**
     * Query multiple OSC color codes in a single batch operation.
     *
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @param oscCodes the OSC codes to query (e.g., 10 for foreground, 11 for background)
     * @return map from OSC code to RGB array [r, g, b] (0-255 each)
     */
    public Map<Integer, int[]> queryBatchOsc(long timeoutMs, int... oscCodes) {
        if (!connection.supportsAnsi() || oscCodes == null || oscCodes.length == 0) {
            return Collections.emptyMap();
        }

        if (connection.device() != null && !connection.device().supportsOscQueries()) {
            return Collections.emptyMap();
        }

        String batchQuery = ANSI.buildBatchOscQuery(oscCodes);
        return queryWithResponseBuffering(timeoutMs, oscCodes.length, batchQuery,
                input -> ANSI.parseMultipleOscColorResponses(input, oscCodes));
    }

    /**
     * Query foreground, background, and cursor colors in a single batch operation.
     *
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @return map from OSC code to RGB array [r, g, b] (0-255 each)
     */
    public Map<Integer, int[]> queryColors(long timeoutMs) {
        return queryBatchOsc(timeoutMs,
                ANSI.OSC_FOREGROUND, ANSI.OSC_BACKGROUND, ANSI.OSC_CURSOR_COLOR);
    }

    /**
     * Query multiple palette colors in a single batch operation.
     *
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @param indices the palette color indices to query (0-255)
     * @return map from palette index to RGB array [r, g, b] (0-255 each)
     */
    public Map<Integer, int[]> queryPaletteColors(long timeoutMs, int... indices) {
        if (!connection.supportsAnsi() || indices == null || indices.length == 0) {
            return Collections.emptyMap();
        }

        if (!supportsPaletteQuery()) {
            return Collections.emptyMap();
        }

        String batchQuery = ANSI.buildBatchOscQueryWithIndices(ANSI.OSC_PALETTE, indices);
        return queryWithResponseBuffering(timeoutMs, indices.length, batchQuery,
                input -> ANSI.parseMultiplePaletteResponses(input, indices));
    }

    /**
     * Query the ANSI 16-color palette (colors 0-15) in a single batch operation.
     *
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @return map from palette index (0-15) to RGB array [r, g, b] (0-255 each)
     */
    public Map<Integer, int[]> queryAnsi16Colors(long timeoutMs) {
        return queryPaletteColors(timeoutMs, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    }

    // ==================== Device Attributes (DA1/DA2) ====================

    /**
     * Query the terminal for its primary device attributes (DA1).
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return DeviceAttributes with DA1 data, or null if not supported or timeout
     */
    public DeviceAttributes queryPrimaryDeviceAttributes(long timeoutMs) {
        return queryTerminal(ANSI.DA1_QUERY, timeoutMs, ANSI::parseDA1Response);
    }

    /**
     * Query the terminal for its secondary device attributes (DA2).
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return DeviceAttributes with DA2 data, or null if not supported or timeout
     */
    public DeviceAttributes querySecondaryDeviceAttributes(long timeoutMs) {
        return queryTerminal(ANSI.DA2_QUERY, timeoutMs, ANSI::parseDA2Response);
    }

    /**
     * Query the terminal for both primary and secondary device attributes.
     *
     * @param timeoutMs timeout in milliseconds to wait for each response
     * @return DeviceAttributes with merged DA1 and DA2 data, or null if neither succeeded
     */
    public DeviceAttributes queryDeviceAttributes(long timeoutMs) {
        DeviceAttributes da1 = queryPrimaryDeviceAttributes(timeoutMs);
        DeviceAttributes da2 = querySecondaryDeviceAttributes(timeoutMs);

        if (da1 != null && da2 != null) {
            return da1.merge(da2);
        } else if (da1 != null) {
            return da1;
        } else {
            return da2;
        }
    }

    /**
     * Detect the image protocol using heuristics first, falling back to a DA1
     * query when heuristic detection returns NONE and the connection is reading.
     * <p>
     * This is the recommended entry point for image protocol detection when a
     * {@link Connection} is available. It avoids the DA1 query cost when
     * environment variables or terminal type already identify the protocol.
     *
     * @return the detected image protocol, or NONE
     */
    public ImageProtocol getImageProtocol() {
        ImageProtocol protocol = connection.device() != null
                ? connection.device().getImageProtocol()
                : ImageProtocolDetector.detectFromEnvironment();
        if (protocol != ImageProtocol.NONE) {
            return protocol;
        }
        return queryImageProtocol(DEFAULT_QUERY_TIMEOUT_MS);
    }

    /**
     * Query the terminal for its image protocol support.
     *
     * @param timeoutMs timeout in milliseconds to wait for DA1 response
     * @return the detected image protocol
     */
    public ImageProtocol queryImageProtocol(long timeoutMs) {
        DeviceAttributes attrs = queryPrimaryDeviceAttributes(timeoutMs);
        String termType = connection.device() != null ? connection.device().type() : null;
        return ImageProtocolDetector.detect(attrs, termType);
    }

    /**
     * Query the terminal to check if Mode 2026 is supported via DECRQM.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return true if supported, false if not supported, null if no response/timeout
     */
    public Boolean querySynchronizedOutput(long timeoutMs) {
        return queryTerminal(ANSI.MODE_2026_QUERY, timeoutMs, ANSI::parseMode2026Response);
    }

    /**
     * Query the terminal to check if Mode 2027 is supported via DECRQM.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return true if supported, false if not supported, null if no response/timeout
     */
    public Boolean queryGraphemeClusterMode(long timeoutMs) {
        return queryTerminal(ANSI.MODE_2027_QUERY, timeoutMs, ANSI::parseMode2027Response);
    }

    /**
     * Query the terminal to check if OSC queries are supported.
     *
     * @param timeoutMs timeout in milliseconds for the DA1 query
     * @return true if OSC queries are likely supported
     */
    public boolean querySupportsOscQueries(long timeoutMs) {
        DeviceAttributes attrs = queryPrimaryDeviceAttributes(timeoutMs);
        return supportsOscQueries(attrs);
    }

    /**
     * Query palette color only if the terminal supports it.
     *
     * @param index the palette color index (0-255)
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-255 each), or null if not supported or timeout
     */
    public int[] queryPaletteColorIfSupported(int index, long timeoutMs) {
        if (!supportsPaletteQuery()) {
            return null;
        }
        return queryPaletteColor(index, timeoutMs);
    }

    // ==================== Capability Detection ====================

    /**
     * Check if OSC (Operating System Command) queries are supported.
     *
     * @return true if OSC queries are likely supported
     */
    public boolean supportsOscQueries() {
        if (connection.device() != null) {
            return connection.device().supportsOscQueries();
        }
        return org.aesh.terminal.utils.TerminalEnvironment.getInstance().supportsOscQueries()
                && connection.supportsAnsi();
    }

    /**
     * Check if OSC queries are supported, using DA1 device attributes for
     * improved detection.
     *
     * @param attrs the device attributes from DA1 query (may be null)
     * @return true if OSC queries are likely supported
     */
    public boolean supportsOscQueries(DeviceAttributes attrs) {
        if (attrs != null && attrs.likelySupportsOscQueries()) {
            return true;
        }
        return supportsOscQueries();
    }

    /**
     * Get the color depth of this terminal connection.
     *
     * @return the detected color depth
     */
    public ColorDepth colorDepth() {
        if (connection.device() != null) {
            Integer maxColors = connection.device().getNumericCapability(Capability.max_colors);
            if (maxColors != null) {
                return ColorDepth.fromColorCount(maxColors);
            }
        }
        return TerminalColorCapability.detectColorDepthFromEnvironment();
    }

    /**
     * Get the color capabilities of this terminal connection.
     *
     * @return the detected color capabilities
     */
    public TerminalColorCapability colorCapability() {
        ColorDepth depth = colorDepth();
        return new TerminalColorCapability(depth, TerminalColorCapability.detectThemeFromEnvironment());
    }

    /**
     * Check if the terminal supports the CSI ? 996 n theme mode query.
     *
     * @return true if the terminal supports theme mode queries
     */
    public boolean supportsThemeQuery() {
        return connection.device() != null && connection.device().supportsThemeQuery();
    }

    /**
     * Seed this connection's probed modes from startup detection.
     * <p>
     * Local-only: callers with a local terminal copy the shared probe
     * results in so per-connection answers keep working after the
     * global instance is invalidated or replaced. Remote connections
     * leave these null and answer from device heuristics plus live
     * queries. Re-seeding overwrites unconditionally; null arguments
     * clear back to unseeded.
     *
     * @param mode2026 the probed Mode 2026 support, or null if unprobed
     * @param mode2027 the probed Mode 2027 support, or null if unprobed
     * @param nativeGraphemeClustering the probed native clustering,
     *        or null if unprobed
     */
    public void seedProbedModes(ModeSupport mode2026, ModeSupport mode2027,
            Boolean nativeGraphemeClustering) {
        seededMode2026 = mode2026;
        seededMode2027 = mode2027;
        seededNativeGraphemeClustering = nativeGraphemeClustering;
    }

    /**
     * Check if Mode 2026 (synchronized output) is supported.
     * <p>
     * Uses the batched probe result if available (most accurate).
     * Falls back to heuristic detection based on terminal type.
     *
     * @return true if Mode 2026 is supported
     */
    public boolean supportsSynchronizedOutput() {
        if (connection.device() == null || !connection.supportsAnsi()) {
            return false;
        }
        // Seeded startup-probe snapshot first, then the device heuristic.
        // The shared probe instance is never consulted: it describes the
        // local process terminal, not necessarily this connection.
        if (seededMode2026 != null) {
            if (seededMode2026 == ModeSupport.SUPPORTED)
                return true;
            if (seededMode2026 == ModeSupport.NOT_SUPPORTED)
                return false;
        }
        // Not seeded or NO_RESPONSE: fall back to heuristic
        return connection.device().supportsSynchronizedOutput();
    }

    /**
     * Check if OSC 133 (shell integration) is likely supported.
     *
     * @return true if OSC 133 shell integration is likely supported
     */
    public boolean supportsShellIntegration() {
        if (connection.device() == null || !connection.supportsAnsi()) {
            return false;
        }
        return connection.device().supportsShellIntegration();
    }

    /**
     * Check if Mode 2027 (grapheme cluster segmentation) is supported.
     * <p>
     * Uses the batched probe result if available (most accurate).
     * Also considers native grapheme clustering detected via cursor probe.
     * Falls back to heuristic detection based on terminal type.
     *
     * @return true if Mode 2027 or native grapheme clustering is supported
     */
    public boolean supportsGraphemeClusterMode() {
        if (connection.device() == null || !connection.supportsAnsi()) {
            return false;
        }
        if (seededMode2027 != null) {
            if (seededMode2027 == ModeSupport.SUPPORTED)
                return true;
            if (seededMode2027 == ModeSupport.NOT_SUPPORTED) {
                // Mode 2027 not supported, but check native clustering
                if (seededNativeGraphemeClustering != null && seededNativeGraphemeClustering)
                    return true;
                return false;
            }
        }
        // Not seeded or NO_RESPONSE: fall back to heuristic
        return connection.device().supportsGraphemeClusterMode();
    }

    /**
     * Check if OSC 8 hyperlinks are likely supported.
     *
     * @return true if OSC 8 hyperlinks are likely supported
     */
    public boolean supportsHyperlinks() {
        if (connection.device() == null || !connection.supportsAnsi()) {
            return false;
        }
        return connection.device().supportsHyperlinks();
    }

    /**
     * Get the detected terminal type based on environment variables.
     *
     * @return the detected terminal type
     */
    public Device.TerminalType getTerminalType() {
        return connection.device() != null ? connection.device().detectTerminalType() : Device.TerminalType.UNKNOWN;
    }

    /**
     * Check if the terminal likely supports a specific OSC code.
     *
     * @param oscCode the OSC code to check
     * @return true if the terminal likely supports this OSC code
     */
    public boolean supportsOscCode(Device.OscCode oscCode) {
        return connection.device() != null && connection.device().supportsOscCode(oscCode);
    }

    /**
     * Check if the terminal likely supports OSC 4 palette color queries.
     *
     * @return true if OSC 4 is likely supported
     */
    public boolean supportsPaletteQuery() {
        return supportsOscCode(Device.OscCode.PALETTE);
    }

    /**
     * Check if the terminal likely supports OSC 10/11 color queries.
     *
     * @return true if OSC 10/11 are likely supported
     */
    public boolean supportsColorQuery() {
        if (connection.device() == null) {
            return false;
        }
        Device.TerminalType type = connection.device().detectTerminalType();
        return type.supports(Device.OscCode.FOREGROUND) && type.supports(Device.OscCode.BACKGROUND);
    }

    /**
     * Check if the terminal likely supports OSC 52 clipboard access.
     *
     * @return true if OSC 52 clipboard access is likely supported
     */
    public boolean supportsClipboard() {
        return supportsOscCode(Device.OscCode.CLIPBOARD);
    }

    // ==================== Semantic Output ====================

    /**
     * Enable Mode 2026 (synchronized output) — Begin Synchronized Update (BSU).
     *
     * @return the underlying connection
     */
    public Connection enableSynchronizedOutput() {
        return connection.write(ANSI.MODE_2026_ENABLE);
    }

    /**
     * Disable Mode 2026 (synchronized output) — End Synchronized Update (ESU).
     *
     * @return the underlying connection
     */
    public Connection disableSynchronizedOutput() {
        return connection.write(ANSI.MODE_2026_DISABLE);
    }

    /**
     * Write OSC 133;A (Prompt Start) to the terminal.
     *
     * @return the underlying connection
     */
    public Connection writePromptStart() {
        return connection.write(ANSI.OSC_133_PROMPT_START);
    }

    /**
     * Write OSC 133;B (Prompt End) to the terminal.
     *
     * @return the underlying connection
     */
    public Connection writePromptEnd() {
        return connection.write(ANSI.OSC_133_PROMPT_END);
    }

    /**
     * Write OSC 133;C (Command Start) to the terminal.
     *
     * @return the underlying connection
     */
    public Connection writeCommandStart() {
        return connection.write(ANSI.OSC_133_COMMAND_START);
    }

    /**
     * Write OSC 133;D (Command Finished) to the terminal without an exit code.
     *
     * @return the underlying connection
     */
    public Connection writeCommandFinished() {
        return connection.write(ANSI.OSC_133_COMMAND_FINISHED);
    }

    /**
     * Write OSC 133;D (Command Finished) to the terminal with an exit code.
     *
     * @param exitCode the command exit code
     * @return the underlying connection
     */
    public Connection writeCommandFinished(int exitCode) {
        return connection.write(ANSI.osc133CommandFinished(exitCode));
    }

    /**
     * Enable Mode 2027 (grapheme cluster segmentation).
     *
     * @return the underlying connection
     */
    public Connection enableGraphemeClusterMode() {
        return connection.write(ANSI.MODE_2027_ENABLE);
    }

    /**
     * Disable Mode 2027 (grapheme cluster segmentation).
     *
     * @return the underlying connection
     */
    public Connection disableGraphemeClusterMode() {
        return connection.write(ANSI.MODE_2027_DISABLE);
    }

    /**
     * Write a clickable hyperlink to the terminal.
     *
     * @param url the hyperlink URL
     * @param text the visible text
     * @return the underlying connection
     */
    public Connection writeHyperlink(String url, String text) {
        return connection.write(ANSI.hyperlink(url, text));
    }

    /**
     * Write a clickable hyperlink with a grouping id.
     *
     * @param url the hyperlink URL
     * @param text the visible text
     * @param id the grouping id
     * @return the underlying connection
     */
    public Connection writeHyperlink(String url, String text, String id) {
        return connection.write(ANSI.hyperlink(url, text, id));
    }

    /**
     * Write text to the system clipboard via OSC 52.
     *
     * @param text the text to copy to the clipboard
     * @return the underlying connection
     */
    public Connection writeClipboard(String text) {
        if (text != null && !text.isEmpty() && supportsClipboard()) {
            connection.write(ANSI.buildOsc52Write(text));
        }
        return connection;
    }

    // ==================== Theme Change Notifications ====================

    /**
     * Enable unsolicited theme change notifications. The connection
     * intercepts them even without an application callback, updating
     * the shared theme cache and discarding stale RGB values.
     */
    public void enableThemeChangeNotification() {
        if (supportsThemeQuery()) {
            if (connection.themeChangeHandler() == null) {
                connection.setThemeChangeHandler(CACHE_ONLY_THEME_HANDLER);
            }
            connection.write(ANSI.THEME_NOTIFY_ENABLE);
        }
    }

    /**
     * Enable unsolicited theme change notifications with a handler.
     *
     * @param handler the handler to invoke with the new theme
     */
    public void enableThemeChangeNotification(Consumer<TerminalTheme> handler) {
        connection.setThemeChangeHandler(handler);
        enableThemeChangeNotification();
    }

    /**
     * Disable unsolicited theme change notifications.
     */
    public void disableThemeChangeNotification() {
        if (supportsThemeQuery()) {
            connection.write(ANSI.THEME_NOTIFY_DISABLE);
        }
        if (connection.themeChangeHandler() == CACHE_ONLY_THEME_HANDLER) {
            connection.setThemeChangeHandler(null);
        }
    }

    // ==================== Focus Tracking ====================

    /**
     * Enable focus tracking. The terminal will send {@code ESC [ I} when
     * focused and {@code ESC [ O} when unfocused.
     */
    public void enableFocusTracking() {
        connection.write(ANSI.FOCUS_TRACKING_ENABLE);
    }

    /**
     * Enable focus tracking with a handler.
     *
     * @param handler receives {@code true} on focus gained, {@code false} on focus lost
     */
    public void enableFocusTracking(Consumer<Boolean> handler) {
        connection.setFocusHandler(handler);
        enableFocusTracking();
    }

    /**
     * Disable focus tracking.
     */
    public void disableFocusTracking() {
        connection.write(ANSI.FOCUS_TRACKING_DISABLE);
    }
}
