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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.EnumSet;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.aesh.terminal.image.ImageProtocol;
import org.aesh.terminal.image.ImageProtocolDetector;
import org.aesh.terminal.io.Encoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Point;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.CodePointUtils;
import org.aesh.terminal.utils.ColorDepth;
import org.aesh.terminal.utils.Parser;
import org.aesh.terminal.utils.TerminalColorCapability;
import org.aesh.terminal.utils.TerminalTheme;

/**
 * Represent a connection to either a local/direct/remote Terminal.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public interface Connection extends Appendable, AutoCloseable {

    /**
     * Default timeout in milliseconds for terminal queries (OSC, DA1, DA2, etc.).
     * Suitable for most query operations where the terminal is expected to respond.
     */
    long DEFAULT_QUERY_TIMEOUT_MS = 500;

    /**
     * Fast timeout in milliseconds for lightweight terminal queries.
     * Use for simple queries like theme DSR or when low latency is important.
     */
    long FAST_QUERY_TIMEOUT_MS = 150;

    /**
     * Get the device associated with this connection.
     *
     * @return type of terminal
     */
    Device device();

    /**
     * Get the current terminal size.
     *
     * @return terminal size
     */
    Size size();

    /**
     * Get the size handler.
     *
     * @return Handler that's called when the terminal changes size
     */
    Consumer<Size> getSizeHandler();

    /**
     * Specify size handler that's called when the terminal changes size.
     *
     * @param handler the size change handler
     */
    void setSizeHandler(Consumer<Size> handler);

    /**
     * Get SignalHandler. A handler that's called when a Signal is sent to the terminal
     *
     * @return Signal handler
     */
    Consumer<Signal> getSignalHandler();

    /**
     * Specify the signal handler.
     * A handler that's called when a Signal is sent to the terminal
     *
     * @param handler signal handler
     */
    void setSignalHandler(Consumer<Signal> handler);

    /**
     * Get the standard input handler.
     *
     * @return the stdin handler that processes input as code point arrays
     */
    Consumer<int[]> getStdinHandler();

    /**
     * Set the standard input handler.
     *
     * @param handler the handler to process input as code point arrays
     */
    void setStdinHandler(Consumer<int[]> handler);

    /**
     * Handler that's called for all output
     *
     * @return output handler
     */
    Consumer<int[]> stdoutHandler();

    /**
     * Specify handler that's called when the input stream is closed.
     *
     * @param closeHandler handler
     */
    void setCloseHandler(Consumer<Void> closeHandler);

    /**
     * Get the close handler.
     *
     * @return handler thats called when the input stream is closed.
     */
    Consumer<Void> getCloseHandler();

    /**
     * Stop reading from the input stream.
     * The stream will be closed and cleanup methods will be called
     * Eg for terminals they will be restored to their original settings.
     *
     * Note that if the reader thread is blocking waiting for data it will wait until either
     * killed or if the input stream is closed.
     */
    @Override
    void close();

    /**
     * Close the connection with an exit code.
     *
     * @param exit the exit code
     */
    default void close(int exit) {
        close();
    }

    /**
     * Start reading from the input stream using the current thread.
     * The current thread will be blocked while reading/waiting to read from the stream
     */
    void openBlocking();

    /**
     * Start reading from the input stream in a separate thread.
     * The current thread will continue.
     */
    void openNonBlocking();

    /**
     * Check if the connection is actively reading from the input stream.
     * <p>
     * This returns true after {@link #openBlocking()} or {@link #openNonBlocking()}
     * has been called and before {@link #close()} is called.
     * <p>
     * When reading is active, query methods can use {@link #setStdinHandler(Consumer)}
     * to receive responses. When reading is not active, synchronous I/O must be used.
     *
     * @return true if the connection is actively reading input
     */
    default boolean reading() {
        return false;
    }

    /**
     * Specify terminal settings
     *
     * @param capability capability
     * @param params parameters
     * @return true if the terminal accepted the settings
     */
    boolean put(Capability capability, Object... params);

    /**
     * Get the current terminal attributes.
     *
     * @return the terminal attributes
     */
    Attributes getAttributes();

    /**
     * Set the terminal attributes.
     *
     * @param attr the attributes to set
     */
    void setAttributes(Attributes attr);

    /**
     * Get the input character encoding.
     *
     * @return the charset used for input encoding
     */
    Charset inputEncoding();

    /**
     * Get the output character encoding.
     *
     * @return the charset used for output encoding
     */
    Charset outputEncoding();

    /**
     * Check if this terminal supports ANSI escape sequences.
     *
     * @return true if ANSI is supported, false otherwise
     */
    boolean supportsAnsi();

    /**
     * Write a string to the output handler.
     * When the stdout handler is an Encoder, this uses a fast path that
     * encodes the String directly to bytes without an intermediate int[] allocation.
     *
     * @param s string
     * @return this connection
     */
    default Connection write(String s) {
        Consumer<int[]> handler = stdoutHandler();
        if (handler instanceof Encoder) {
            ((Encoder) handler).accept(s);
        } else {
            handler.accept(Parser.toCodePoints(s));
        }
        return this;
    }

    @Override
    default Appendable append(char c) throws IOException {
        return write(String.valueOf(c));
    }

    @Override
    default Appendable append(CharSequence csq) throws IOException {
        return write(csq.toString());
    }

    @Override
    default Appendable append(CharSequence csq, int start, int end) throws IOException {
        return write(csq.subSequence(start, end).toString());
    }

    /**
     * Enter raw mode for the terminal.
     * <p>
     * In raw mode, input is not line-buffered, echo is disabled, and special
     * character processing is turned off. This allows reading individual
     * keystrokes as they are typed.
     *
     * @return the previous terminal attributes (to restore later)
     */
    default Attributes enterRawMode() {
        Attributes prvAttr = getAttributes();
        Attributes newAttr = new Attributes(prvAttr);
        newAttr.setLocalFlags(EnumSet.of(Attributes.LocalFlag.ICANON, Attributes.LocalFlag.ECHO, Attributes.LocalFlag.IEXTEN),
                false);
        newAttr.setInputFlags(EnumSet.of(Attributes.InputFlag.IXON, Attributes.InputFlag.ICRNL, Attributes.InputFlag.INLCR),
                false);
        newAttr.setControlChar(Attributes.ControlChar.VMIN, 1);
        newAttr.setControlChar(Attributes.ControlChar.VTIME, 0);
        newAttr.setControlChar(Attributes.ControlChar.VINTR, 0);
        setAttributes(newAttr);
        return prvAttr;
    }

    /**
     * Get the current cursor position in the terminal.
     * <p>
     * This method sends a cursor position query to the terminal and waits
     * for the response. The terminal must be actively reading input for
     * this to work.
     *
     * @return the current cursor position as a Point (row, column)
     */
    default Point getCursorPosition() {
        Consumer<int[]> prevInputHandler = getStdinHandler();
        CountDownLatch latch = new CountDownLatch(1);
        final Point[] p = { null };
        Attributes attributes = enterRawMode();
        setStdinHandler(ints -> {
            p[0] = ANSI.getActualCursor(ints);
            setStdinHandler(prevInputHandler);
            latch.countDown();
            setAttributes(attributes);
        });
        stdoutHandler().accept(ANSI.CURSOR_POSITION_QUERY);
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return p[0];
    }

    /**
     * Send a query to the terminal and wait for a response with timeout.
     * <p>
     * This method enters raw mode, sends the query, collects the response,
     * and restores the original terminal attributes. It's useful for both
     * OSC queries and CSI queries (DA1/DA2, DECRQM, theme DSR, etc.).
     * <p>
     * This method uses {@link #setStdinHandler(Consumer)} to receive responses,
     * which requires the connection to be actively reading input (i.e.,
     * {@link #reading()} returns true). If not reading, this method returns null.
     * <p>
     * For queries that need to work before the connection is opened, use
     * {@link #queryColorCapability(long)} or implementation-specific synchronous methods.
     *
     * @param query the query sequence to send
     * @param timeoutMs timeout in milliseconds to wait for response
     * @param responseParser function to parse the response; should return non-null when
     *        a complete response is received, null to continue waiting
     * @param <T> the type of the parsed response
     * @return the parsed response, or null if not reading, timeout, or parsing failed
     */
    default <T> T queryTerminal(String query, long timeoutMs,
            java.util.function.Function<int[], T> responseParser) {
        if (!supportsAnsi()) {
            return null;
        }

        // This method uses setStdinHandler which requires active reading
        if (!reading()) {
            return null;
        }

        Consumer<int[]> prevInputHandler = getStdinHandler();
        CountDownLatch latch = new CountDownLatch(1);
        @SuppressWarnings("unchecked")
        final Object[] result = { null };
        Attributes savedAttributes = enterRawMode();

        setStdinHandler(ints -> {
            T parsed = responseParser.apply(ints);
            if (parsed != null) {
                result[0] = parsed;
                latch.countDown();
            }
        });

        try {
            write(query);
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            setStdinHandler(prevInputHandler);
            setAttributes(savedAttributes);
        }

        @SuppressWarnings("unchecked")
        T typedResult = (T) result[0];
        return typedResult;
    }

    /**
     * Check if OSC (Operating System Command) queries are supported.
     * <p>
     * This checks both the device type and environment variables to determine
     * if OSC queries like color detection are likely to work. Uses
     * {@link org.aesh.terminal.utils.TerminalEnvironment} for centralized detection.
     * <p>
     * For more accurate detection, use {@link #supportsOscQueries(DeviceAttributes)}
     * with DA1 query results.
     *
     * @return true if OSC queries are likely supported
     */
    default boolean supportsOscQueries() {
        // Delegate to Device which uses TerminalEnvironment
        if (device() != null) {
            return device().supportsOscQueries();
        }
        // Fallback to environment-based detection
        return org.aesh.terminal.utils.TerminalEnvironment.getInstance().supportsOscQueries()
                && supportsAnsi();
    }

    /**
     * Check if OSC queries are supported, using DA1 device attributes for
     * improved detection.
     * <p>
     * This method uses the device attributes from a DA1 query to provide
     * more accurate OSC support detection. If the terminal reports modern
     * features like ANSI color or Sixel graphics, it likely supports OSC queries.
     *
     * @param attrs the device attributes from DA1 query (may be null)
     * @return true if OSC queries are likely supported
     */
    default boolean supportsOscQueries(DeviceAttributes attrs) {
        // If we have DA1 data, use it for better detection
        if (attrs != null && attrs.likelySupportsOscQueries()) {
            return true;
        }

        // Fall back to heuristic detection
        return supportsOscQueries();
    }

    /**
     * Query the terminal to check if OSC queries are supported.
     * <p>
     * This method sends a DA1 query to get device attributes and uses them
     * to determine OSC support more accurately than heuristic detection.
     *
     * @param timeoutMs timeout in milliseconds for the DA1 query
     * @return true if OSC queries are likely supported
     */
    default boolean querySupportsOscQueries(long timeoutMs) {
        DeviceAttributes attrs = queryPrimaryDeviceAttributes(timeoutMs);
        return supportsOscQueries(attrs);
    }

    /**
     * Get the color depth of this terminal connection.
     * <p>
     * This method uses the device's max_colors capability if available,
     * otherwise falls back to environment variable detection.
     *
     * @return the detected color depth
     */
    default ColorDepth getColorDepth() {
        // Check terminfo max_colors capability first
        if (device() != null) {
            Integer maxColors = device().getNumericCapability(Capability.max_colors);
            if (maxColors != null) {
                return ColorDepth.fromColorCount(maxColors);
            }
        }

        // Fall back to environment detection
        return TerminalColorCapability.detectColorDepthFromEnvironment();
    }

    /**
     * Get the color capabilities of this terminal connection.
     * <p>
     * This is a fast, non-blocking operation that uses environment variables
     * and terminfo data. For more accurate color detection including
     * background theme detection via OSC queries, use the
     * {@code TerminalColorDetector} class in the readline module.
     *
     * @return the detected color capabilities
     */
    default TerminalColorCapability getColorCapability() {
        ColorDepth depth = getColorDepth();
        return new TerminalColorCapability(depth, TerminalColorCapability.detectThemeFromEnvironment());
    }

    /**
     * Send an OSC (Operating System Command) query to the terminal.
     * <p>
     * This method sends an OSC query sequence and waits for the terminal's response.
     * The terminal must be actively reading input (via {@link #openBlocking()} or
     * {@link #openNonBlocking()}) for this to work.
     * <p>
     * Common OSC codes:
     * <ul>
     * <li>10 - Query/set foreground color</li>
     * <li>11 - Query/set background color</li>
     * <li>12 - Query/set cursor color</li>
     * <li>4;N - Query/set palette color N</li>
     * </ul>
     *
     * @param oscCode the OSC code (e.g., 10 for foreground, 11 for background)
     * @param param the query parameter (typically "?" for queries)
     * @param timeoutMs timeout in milliseconds to wait for response
     * @param responseParser function to parse the response; should return non-null
     *        when a complete response is received, null to continue waiting
     * @param <T> the type of the parsed response
     * @return the parsed response, or null if timeout or not supported
     */
    default <T> T queryOsc(int oscCode, String param, long timeoutMs,
            java.util.function.Function<int[], T> responseParser) {
        // OSC queries require OSC support from the terminal
        if (device() != null && !device().supportsOscQueries()) {
            return null;
        }
        String query = ANSI.buildOscQuery(oscCode, param);
        return queryTerminal(query, timeoutMs, responseParser);
    }

    /**
     * Query the terminal for its foreground color using OSC 10.
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-255 each), or null if not supported or timeout
     */
    default int[] queryForegroundColor(long timeoutMs) {
        return queryOsc(ANSI.OSC_FOREGROUND, "?", timeoutMs,
                input -> ANSI.parseOscColorResponse(input, ANSI.OSC_FOREGROUND));
    }

    /**
     * Query the terminal for its background color using OSC 11.
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-255 each), or null if not supported or timeout
     */
    default int[] queryBackgroundColor(long timeoutMs) {
        return queryOsc(ANSI.OSC_BACKGROUND, "?", timeoutMs,
                input -> ANSI.parseOscColorResponse(input, ANSI.OSC_BACKGROUND));
    }

    /**
     * Query the terminal for its cursor color using OSC 12.
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-255 each), or null if not supported or timeout
     */
    default int[] queryCursorColor(long timeoutMs) {
        return queryOsc(ANSI.OSC_CURSOR_COLOR, "?", timeoutMs,
                input -> ANSI.parseOscColorResponse(input, ANSI.OSC_CURSOR_COLOR));
    }

    // ==================== Theme Mode DSR (CSI ? 996 n) ====================

    /**
     * Query the terminal for its current theme mode using the CSI ? 996 n protocol.
     * <p>
     * This sends {@code CSI ? 996 n} and expects a response of
     * {@code CSI ? 997 ; 1 n} (dark) or {@code CSI ? 997 ; 2 n} (light).
     * <p>
     * This is simpler and faster than OSC 10/11 RGB queries since it returns
     * a direct dark/light answer without needing luminance calculation.
     * <p>
     * The terminal must be actively reading input for this to work.
     * Only works on terminals that support this extension (Contour, Ghostty,
     * Kitty 0.38.1+, tmux, VTE 0.82.0+).
     * <p>
     * Ref: <a href="https://contour-terminal.org/vt-extensions/color-palette-update-notifications/">
     * Contour VT extension: Dark and Light Mode detection</a>
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return {@link TerminalTheme#DARK} or {@link TerminalTheme#LIGHT},
     *         or {@code null} if not supported or timeout
     */
    default TerminalTheme queryThemeMode(long timeoutMs) {
        if (!supportsAnsi()) {
            return null;
        }

        // Check if the terminal supports this protocol
        if (device() != null && !device().supportsThemeQuery()) {
            return null;
        }

        return queryTerminal(ANSI.THEME_MODE_QUERY, timeoutMs, ANSI::parseThemeDsrResponse);
    }

    /**
     * Check if the terminal supports the CSI ? 996 n theme mode query.
     * <p>
     * Delegates to {@link Device#supportsThemeQuery()}.
     *
     * @return true if the terminal supports theme mode queries
     */
    default boolean supportsThemeQuery() {
        return device() != null && device().supportsThemeQuery();
    }

    /**
     * Set a handler to be called when the terminal's theme changes.
     * <p>
     * When a handler is registered, the input pipeline will intercept unsolicited
     * {@code CSI ? 997 ; Ps n} responses and route them to this handler instead
     * of passing them through as input. This prevents theme change notifications
     * from corrupting the readline buffer.
     * <p>
     * Typically used together with {@link #enableThemeChangeNotification()} which
     * tells the terminal to send these notifications.
     * <p>
     * Note: The default implementation is a no-op. Implementations that extend
     * {@link AbstractConnection} get automatic support via {@link EventDecoder}.
     *
     * @param handler the handler to invoke with the new theme, or null to remove
     * @see #enableThemeChangeNotification()
     */
    default void setThemeChangeHandler(Consumer<TerminalTheme> handler) {
        // Default no-op; AbstractConnection delegates to EventDecoder
    }

    /**
     * Get the current theme change handler.
     *
     * @return the theme change handler, or null if not set
     * @see #setThemeChangeHandler(Consumer)
     */
    default Consumer<TerminalTheme> getThemeChangeHandler() {
        return null;
    }

    /**
     * Enable unsolicited theme change notifications.
     * <p>
     * Sends {@code CSI ? 2031 h} to the terminal. When enabled, the terminal
     * will send {@code CSI ? 997 ; 1 n} or {@code CSI ? 997 ; 2 n} whenever
     * the color palette changes (e.g., dark/light mode switch).
     * <p>
     * To receive these notifications, also register a handler with
     * {@link #setThemeChangeHandler(Consumer)}.
     * <p>
     * Use {@link #disableThemeChangeNotification()} to stop receiving notifications.
     * <p>
     * Ref: <a href="https://contour-terminal.org/vt-extensions/color-palette-update-notifications/">
     * Contour VT extension</a>
     */
    default void enableThemeChangeNotification() {
        if (supportsThemeQuery()) {
            write(ANSI.THEME_NOTIFY_ENABLE);
        }
    }

    /**
     * Enable unsolicited theme change notifications with a handler.
     * <p>
     * Convenience method that registers the theme change handler and enables
     * notifications in one call. Equivalent to:
     *
     * <pre>
     * connection.setThemeChangeHandler(handler);
     * connection.enableThemeChangeNotification();
     * </pre>
     * <p>
     * The handler is called whenever the terminal reports a theme change.
     * Applications can update their cached {@link TerminalColorCapability} in
     * the handler:
     *
     * <pre>
     * connection.enableThemeChangeNotification(theme -&gt; {
     *     capability = new TerminalColorCapability(capability.getColorDepth(), theme);
     * });
     * </pre>
     *
     * @param handler the handler to invoke with the new theme
     * @see #disableThemeChangeNotification()
     */
    default void enableThemeChangeNotification(Consumer<TerminalTheme> handler) {
        setThemeChangeHandler(handler);
        enableThemeChangeNotification();
    }

    /**
     * Disable unsolicited theme change notifications.
     * <p>
     * Sends {@code CSI ? 2031 l} to the terminal. Does not remove the
     * theme change handler — call {@link #setThemeChangeHandler(Consumer)}
     * with {@code null} to remove it.
     *
     * @see #enableThemeChangeNotification()
     */
    default void disableThemeChangeNotification() {
        if (supportsThemeQuery()) {
            write(ANSI.THEME_NOTIFY_DISABLE);
        }
    }

    /**
     * Send an OSC query with an index parameter to the terminal.
     * <p>
     * This is used for OSC codes that require an index, such as OSC 4 (palette colors).
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param oscCode the OSC code (e.g., 4 for palette color)
     * @param index the index parameter (e.g., palette color index 0-255)
     * @param param the query parameter (typically "?" for queries)
     * @param timeoutMs timeout in milliseconds to wait for response
     * @param responseParser function to parse the response; should return non-null
     *        when a complete response is received, null to continue waiting
     * @param <T> the type of the parsed response
     * @return the parsed response, or null if timeout or not supported
     */
    default <T> T queryOsc(int oscCode, int index, String param, long timeoutMs,
            java.util.function.Function<int[], T> responseParser) {
        // OSC queries require OSC support from the terminal
        if (device() != null && !device().supportsOscQueries()) {
            return null;
        }
        String query = ANSI.buildOscQuery(oscCode, index, param);
        return queryTerminal(query, timeoutMs, responseParser);
    }

    /**
     * Query the terminal for a palette color using OSC 4.
     * <p>
     * Palette colors are indexed 0-255, where:
     * <ul>
     * <li>0-7: Standard ANSI colors</li>
     * <li>8-15: Bright ANSI colors</li>
     * <li>16-231: 216-color cube</li>
     * <li>232-255: Grayscale ramp</li>
     * </ul>
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param index the palette color index (0-255)
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-255 each), or null if not supported or timeout
     */
    default int[] queryPaletteColor(int index, long timeoutMs) {
        return queryOsc(ANSI.OSC_PALETTE, index, "?", timeoutMs,
                input -> ANSI.parseOscColorResponse(input, ANSI.OSC_PALETTE, index));
    }

    // ==================== Batch OSC Queries ====================

    /**
     * Shared orchestration for batch OSC queries with response buffering.
     * <p>
     * This method handles the common pattern of saving the stdin handler,
     * entering raw mode, buffering responses, parsing them, and restoring state.
     *
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @param expectedCount the number of expected responses
     * @param batchQuery the concatenated query string to send
     * @param parser function to parse the buffered response into a result map
     * @return map from key to RGB array [r, g, b] (0-255 each)
     */
    default java.util.Map<Integer, int[]> queryWithResponseBuffering(
            long timeoutMs, int expectedCount,
            String batchQuery,
            java.util.function.Function<int[], java.util.Map<Integer, int[]>> parser) {

        Consumer<int[]> prevInputHandler = getStdinHandler();
        CountDownLatch latch = new CountDownLatch(1);
        final java.util.Map<Integer, int[]> results = new java.util.concurrent.ConcurrentHashMap<>();
        final StringBuilder responseBuffer = new StringBuilder();
        Attributes savedAttributes = enterRawMode();

        setStdinHandler(ints -> {
            // Append to response buffer
            for (int c : ints) {
                responseBuffer.appendCodePoint(c);
            }

            // Try to parse all expected responses
            java.util.Map<Integer, int[]> parsed = parser.apply(
                    CodePointUtils.toCodePoints(responseBuffer.toString()));
            results.putAll(parsed);

            // If we got all expected responses, signal completion
            if (results.size() >= expectedCount) {
                latch.countDown();
            }
        });

        try {
            write(batchQuery);
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            setStdinHandler(prevInputHandler);
            setAttributes(savedAttributes);
        }

        return results;
    }

    /**
     * Query multiple OSC color codes in a single batch operation.
     * <p>
     * This method is much more efficient than calling individual query methods
     * (like {@link #queryForegroundColor(long)}, {@link #queryBackgroundColor(long)})
     * multiple times. By sending all queries at once and collecting responses
     * in a single operation, latency is reduced from O(n * timeout) to O(timeout).
     * <p>
     * For example, querying 10 colors individually might take 600-700ms due to
     * serial round-trips, while batch querying takes only 50-100ms.
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @param oscCodes the OSC codes to query (e.g., 10 for foreground, 11 for background)
     * @return map from OSC code to RGB array [r, g, b] (0-255 each);
     *         missing entries indicate the terminal didn't respond to that query
     */
    default java.util.Map<Integer, int[]> queryBatchOsc(long timeoutMs, int... oscCodes) {
        if (!supportsAnsi() || oscCodes == null || oscCodes.length == 0) {
            return java.util.Collections.emptyMap();
        }

        // Check if OSC queries are supported
        if (device() != null && !device().supportsOscQueries()) {
            return java.util.Collections.emptyMap();
        }

        String batchQuery = ANSI.buildBatchOscQuery(oscCodes);
        return queryWithResponseBuffering(timeoutMs, oscCodes.length, batchQuery,
                input -> ANSI.parseMultipleOscColorResponses(input, oscCodes));
    }

    /**
     * Query foreground, background, and cursor colors in a single batch operation.
     * <p>
     * This is a convenience method equivalent to calling
     * {@code queryBatchOsc(timeoutMs, 10, 11, 12)}.
     * <p>
     * The returned map uses OSC codes as keys:
     * <ul>
     * <li>{@link ANSI#OSC_FOREGROUND} (10) - foreground color</li>
     * <li>{@link ANSI#OSC_BACKGROUND} (11) - background color</li>
     * <li>{@link ANSI#OSC_CURSOR_COLOR} (12) - cursor color</li>
     * </ul>
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @return map from OSC code to RGB array [r, g, b] (0-255 each)
     */
    default java.util.Map<Integer, int[]> queryColors(long timeoutMs) {
        return queryBatchOsc(timeoutMs,
                ANSI.OSC_FOREGROUND, ANSI.OSC_BACKGROUND, ANSI.OSC_CURSOR_COLOR);
    }

    /**
     * Query multiple palette colors in a single batch operation.
     * <p>
     * This method sends all palette color queries at once, significantly
     * reducing latency compared to calling {@link #queryPaletteColor(int, long)}
     * multiple times.
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @param indices the palette color indices to query (0-255)
     * @return map from palette index to RGB array [r, g, b] (0-255 each);
     *         missing entries indicate the terminal didn't respond to that query
     */
    default java.util.Map<Integer, int[]> queryPaletteColors(long timeoutMs, int... indices) {
        if (!supportsAnsi() || indices == null || indices.length == 0) {
            return java.util.Collections.emptyMap();
        }

        // Check if palette queries are supported
        if (!supportsPaletteQuery()) {
            return java.util.Collections.emptyMap();
        }

        String batchQuery = ANSI.buildBatchOscQueryWithIndices(ANSI.OSC_PALETTE, indices);
        return queryWithResponseBuffering(timeoutMs, indices.length, batchQuery,
                input -> ANSI.parseMultiplePaletteResponses(input, indices));
    }

    /**
     * Query the ANSI 16-color palette (colors 0-15) in a single batch operation.
     * <p>
     * This queries the 8 standard colors (0-7) and 8 bright colors (8-15).
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @return map from palette index (0-15) to RGB array [r, g, b] (0-255 each)
     */
    default java.util.Map<Integer, int[]> queryAnsi16Colors(long timeoutMs) {
        return queryPaletteColors(timeoutMs, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
    }

    // ==================== Device Attributes (DA1/DA2) ====================

    /**
     * Query the terminal for its primary device attributes (DA1).
     * <p>
     * DA1 returns the device conformance level and supported features.
     * This can be used to detect capabilities like:
     * <ul>
     * <li>Sixel graphics support</li>
     * <li>ANSI color support</li>
     * <li>Mouse/locator support</li>
     * <li>Rectangular editing operations</li>
     * </ul>
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return DeviceAttributes with DA1 data, or null if not supported or timeout
     */
    default DeviceAttributes queryPrimaryDeviceAttributes(long timeoutMs) {
        return queryTerminal(ANSI.DA1_QUERY, timeoutMs, ANSI::parseDA1Response);
    }

    /**
     * Query the terminal for its secondary device attributes (DA2).
     * <p>
     * DA2 returns terminal identification information:
     * <ul>
     * <li>Terminal type (VT100, VT220, xterm, etc.)</li>
     * <li>Firmware/version number</li>
     * <li>ROM cartridge registration</li>
     * </ul>
     * <p>
     * Note: Not all terminals support DA2. Some may return nothing or
     * the same response as DA1.
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return DeviceAttributes with DA2 data, or null if not supported or timeout
     */
    default DeviceAttributes querySecondaryDeviceAttributes(long timeoutMs) {
        return queryTerminal(ANSI.DA2_QUERY, timeoutMs, ANSI::parseDA2Response);
    }

    /**
     * Query the terminal for both primary and secondary device attributes.
     * <p>
     * This sends both DA1 and DA2 queries and merges the results into a
     * single DeviceAttributes object containing all available information.
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for each response
     * @return DeviceAttributes with merged DA1 and DA2 data, or null if neither succeeded
     */
    default DeviceAttributes queryDeviceAttributes(long timeoutMs) {
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
     * Query the terminal for its image protocol support.
     * <p>
     * This method sends a DA1 query to detect Sixel support authoritatively,
     * and combines it with heuristic detection based on terminal type.
     * <p>
     * For faster (but less accurate) detection without querying the terminal,
     * use {@link Device#getImageProtocol()} instead.
     *
     * @param timeoutMs timeout in milliseconds to wait for DA1 response
     * @return the detected image protocol
     */
    default ImageProtocol queryImageProtocol(long timeoutMs) {
        DeviceAttributes attrs = queryPrimaryDeviceAttributes(timeoutMs);
        String termType = device() != null ? device().type() : null;
        return ImageProtocolDetector.detect(attrs, termType);
    }

    // ==================== Mode 2026 (Synchronized Output) ====================

    /**
     * Check if Mode 2026 (synchronized output) is likely supported.
     * <p>
     * This delegates to the device's capability detection based on terminal type.
     * Returns false for dumb terminals or when the device is null.
     *
     * @return true if Mode 2026 is likely supported
     */
    default boolean supportsSynchronizedOutput() {
        if (device() == null || !supportsAnsi()) {
            return false;
        }
        return device().supportsSynchronizedOutput();
    }

    /**
     * Query the terminal to check if Mode 2026 is supported via DECRQM.
     * <p>
     * This sends a DECRQM (DEC Private Mode Request Mode) query for Mode 2026
     * and parses the DECRPM response.
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return true if supported, false if not supported, null if no response/timeout
     */
    default Boolean querySynchronizedOutput(long timeoutMs) {
        return queryTerminal(ANSI.MODE_2026_QUERY, timeoutMs, ANSI::parseMode2026Response);
    }

    /**
     * Enable Mode 2026 (synchronized output) — Begin Synchronized Update (BSU).
     * <p>
     * When enabled, the terminal buffers rendering until the mode is disabled,
     * then paints the final state in one frame.
     *
     * @return this connection
     */
    default Connection enableSynchronizedOutput() {
        return write(ANSI.MODE_2026_ENABLE);
    }

    /**
     * Disable Mode 2026 (synchronized output) — End Synchronized Update (ESU).
     * <p>
     * When disabled, the terminal renders the buffered output in a single frame.
     *
     * @return this connection
     */
    default Connection disableSynchronizedOutput() {
        return write(ANSI.MODE_2026_DISABLE);
    }

    // ==================== OSC 133 Shell Integration ====================

    /**
     * Check if OSC 133 (shell integration) is likely supported.
     * <p>
     * This delegates to the device's capability detection based on terminal type.
     * Returns false for dumb terminals or when the device is null.
     *
     * @return true if OSC 133 shell integration is likely supported
     */
    default boolean supportsShellIntegration() {
        if (device() == null || !supportsAnsi()) {
            return false;
        }
        return device().supportsShellIntegration();
    }

    /**
     * Write OSC 133;A (Prompt Start) to the terminal.
     * <p>
     * This marks the beginning of the prompt region. Terminals that support
     * shell integration use this to identify prompt boundaries for features
     * like click-to-scroll-to-prompt and visual prompt highlighting.
     *
     * @return this connection
     */
    default Connection writePromptStart() {
        return write(ANSI.OSC_133_PROMPT_START);
    }

    /**
     * Write OSC 133;B (Prompt End) to the terminal.
     * <p>
     * This marks the end of the prompt and the start of the user input area.
     *
     * @return this connection
     */
    default Connection writePromptEnd() {
        return write(ANSI.OSC_133_PROMPT_END);
    }

    /**
     * Write OSC 133;C (Command Start) to the terminal.
     * <p>
     * This marks that the user has pressed Enter and command output is about
     * to begin.
     *
     * @return this connection
     */
    default Connection writeCommandStart() {
        return write(ANSI.OSC_133_COMMAND_START);
    }

    /**
     * Write OSC 133;D (Command Finished) to the terminal without an exit code.
     *
     * @return this connection
     */
    default Connection writeCommandFinished() {
        return write(ANSI.OSC_133_COMMAND_FINISHED);
    }

    /**
     * Write OSC 133;D (Command Finished) to the terminal with an exit code.
     * <p>
     * The exit code allows the terminal to visually distinguish successful
     * commands (exit code 0) from failed commands.
     *
     * @param exitCode the command exit code
     * @return this connection
     */
    default Connection writeCommandFinished(int exitCode) {
        return write(ANSI.osc133CommandFinished(exitCode));
    }

    // ==================== Mode 2027 (Grapheme Cluster Mode) ====================

    /**
     * Check if Mode 2027 (grapheme cluster segmentation) is likely supported.
     * <p>
     * This delegates to the device's capability detection based on terminal type.
     * Returns false for dumb terminals or when the device is null.
     *
     * @return true if Mode 2027 is likely supported
     */
    default boolean supportsGraphemeClusterMode() {
        if (device() == null || !supportsAnsi()) {
            return false;
        }
        return device().supportsGraphemeClusterMode();
    }

    /**
     * Query the terminal to check if Mode 2027 is supported via DECRQM.
     * <p>
     * This sends a DECRQM (DEC Private Mode Request Mode) query for Mode 2027
     * and parses the DECRPM response.
     * <p>
     * The terminal must be actively reading input for this to work.
     *
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return true if supported, false if not supported, null if no response/timeout
     */
    default Boolean queryGraphemeClusterMode(long timeoutMs) {
        return queryTerminal(ANSI.MODE_2027_QUERY, timeoutMs, ANSI::parseMode2027Response);
    }

    /**
     * Enable Mode 2027 (grapheme cluster segmentation).
     * <p>
     * When enabled, the terminal uses UAX #29 grapheme cluster segmentation
     * for cursor positioning instead of per-codepoint wcwidth.
     *
     * @return this connection
     */
    default Connection enableGraphemeClusterMode() {
        return write(ANSI.MODE_2027_ENABLE);
    }

    /**
     * Disable Mode 2027 (grapheme cluster segmentation).
     * <p>
     * When disabled, the terminal reverts to per-codepoint wcwidth for
     * cursor positioning.
     *
     * @return this connection
     */
    default Connection disableGraphemeClusterMode() {
        return write(ANSI.MODE_2027_DISABLE);
    }

    // ==================== OSC 8 Hyperlink Support ====================

    /**
     * Check if OSC 8 hyperlinks are likely supported.
     *
     * @return true if OSC 8 hyperlinks are likely supported
     */
    default boolean supportsHyperlinks() {
        if (device() == null || !supportsAnsi()) {
            return false;
        }
        return device().supportsHyperlinks();
    }

    /**
     * Write a clickable hyperlink to the terminal.
     *
     * @param url the hyperlink URL
     * @param text the visible text
     * @return this connection
     */
    default Connection writeHyperlink(String url, String text) {
        return write(ANSI.hyperlink(url, text));
    }

    /**
     * Write a clickable hyperlink with a grouping id.
     *
     * @param url the hyperlink URL
     * @param text the visible text
     * @param id the grouping id
     * @return this connection
     */
    default Connection writeHyperlink(String url, String text, String id) {
        return write(ANSI.hyperlink(url, text, id));
    }

    // ==================== OSC Support Detection ====================

    /**
     * Get the detected terminal type based on environment variables.
     * <p>
     * This can be used to check OSC support before querying.
     *
     * @return the detected terminal type
     */
    default Device.TerminalType getTerminalType() {
        return device() != null ? device().detectTerminalType() : Device.TerminalType.UNKNOWN;
    }

    /**
     * Check if the terminal likely supports a specific OSC code.
     *
     * @param oscCode the OSC code to check
     * @return true if the terminal likely supports this OSC code
     */
    default boolean supportsOscCode(Device.OscCode oscCode) {
        return device() != null && device().supportsOscCode(oscCode);
    }

    /**
     * Check if the terminal likely supports OSC 4 palette color queries.
     * <p>
     * Some terminals (e.g., JetBrains IDEs) don't support OSC 4.
     * Use this method to avoid unnecessary timeout waits.
     *
     * @return true if OSC 4 is likely supported
     */
    default boolean supportsPaletteQuery() {
        return supportsOscCode(Device.OscCode.PALETTE);
    }

    /**
     * Check if the terminal likely supports OSC 10/11 color queries.
     *
     * @return true if OSC 10/11 are likely supported
     */
    default boolean supportsColorQuery() {
        if (device() == null) {
            return false;
        }
        Device.TerminalType type = device().detectTerminalType();
        return type.supports(Device.OscCode.FOREGROUND) && type.supports(Device.OscCode.BACKGROUND);
    }

    /**
     * Check if the terminal likely supports OSC 52 clipboard access.
     *
     * @return true if OSC 52 clipboard access is likely supported
     */
    default boolean supportsClipboard() {
        return supportsOscCode(Device.OscCode.CLIPBOARD);
    }

    /**
     * Write text to the system clipboard via OSC 52.
     * <p>
     * This is a write-only operation that copies the given text to the
     * system clipboard. Only writes if the text is non-empty and the
     * terminal supports OSC 52 clipboard access.
     *
     * @param text the text to copy to the clipboard
     * @return this connection
     */
    default Connection writeClipboard(String text) {
        if (text != null && !text.isEmpty() && supportsClipboard()) {
            write(ANSI.buildOsc52Write(text));
        }
        return this;
    }

    /**
     * Query palette color only if the terminal supports it.
     * <p>
     * This method first checks if the terminal is known to support OSC 4,
     * avoiding unnecessary timeout waits on unsupported terminals.
     *
     * @param index the palette color index (0-255)
     * @param timeoutMs timeout in milliseconds to wait for response
     * @return RGB array [r, g, b] (0-255 each), or null if not supported or timeout
     */
    default int[] queryPaletteColorIfSupported(int index, long timeoutMs) {
        if (!supportsPaletteQuery()) {
            return null;
        }
        return queryPaletteColor(index, timeoutMs);
    }

    // ==================== Color Capability Detection ====================

    /**
     * Query terminal color capabilities using synchronous I/O.
     * <p>
     * This method queries the terminal for color information including:
     * <ul>
     * <li>Foreground color (OSC 10)</li>
     * <li>Background color (OSC 11)</li>
     * <li>Cursor color (OSC 12, if supported)</li>
     * <li>Palette colors (OSC 4, if supported)</li>
     * </ul>
     * <p>
     * Unlike {@link #queryTerminal(String, long, java.util.function.Function)} and
     * other handler-based query methods, this method uses synchronous I/O and
     * works regardless of {@link #reading()} state. It can be called before
     * {@link #openBlocking()} or {@link #openNonBlocking()}.
     * <p>
     * The default implementation returns null. Implementations that support
     * synchronous queries (like TerminalConnection) should override this method.
     *
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @return TerminalColorCapability with detected colors, or null if not supported
     */
    default TerminalColorCapability queryColorCapability(long timeoutMs) {
        // Default implementation returns null.
        // TerminalConnection overrides this with direct I/O implementation.
        return null;
    }

}
