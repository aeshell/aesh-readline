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

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight terminal capability detection.
 * <p>
 * Detects terminal features using environment variables and heuristics
 * without requiring a terminal connection or any external dependencies.
 * <p>
 * Usage:
 *
 * <pre>
 * // Fast heuristic detection (~1-2ms)
 * TerminalCapabilities caps = TerminalCapabilities.detect();
 *
 * // Async detection with background color queries
 * TerminalCapabilities caps = TerminalCapabilities.detectAsync();
 * // ... do other startup work ...
 * caps.awaitColors(500, TimeUnit.MILLISECONDS);
 * int[] bg = caps.backgroundRGB(); // available once query completes
 * </pre>
 */
public final class TerminalCapabilities {

    private static volatile TerminalCapabilities instance;

    private final TerminalDetector detector;
    private volatile TerminalTheme resolvedTheme;
    private volatile int[] foregroundRGB;
    private volatile int[] backgroundRGB;
    private volatile Map<Integer, int[]> paletteColors;
    private volatile Boolean queried256;
    private volatile ImageProtocol queriedImageProtocol;
    private volatile ModeSupport mode2026Support;
    private volatile ModeSupport mode2027Support;
    private volatile Boolean nativeGraphemeClustering;
    private final CountDownLatch colorQueryLatch;

    private TerminalCapabilities(TerminalDetector detector, CountDownLatch latch) {
        this.detector = detector;
        this.colorQueryLatch = latch;
    }

    /**
     * Get the shared instance, creating it lazily via {@link #detect()}.
     * <p>
     * Use {@link #setInstance(TerminalCapabilities)} to inject a pre-built
     * instance from early startup (e.g., Quarkus bootstrap). For example,
     * call {@code setInstance(detectAsync())} to enable background color
     * queries before other modules initialize.
     *
     * @return the shared capabilities instance
     */
    public static TerminalCapabilities getInstance() {
        if (instance == null) {
            synchronized (TerminalCapabilities.class) {
                if (instance == null) {
                    instance = detect();
                }
            }
        }
        return instance;
    }

    /**
     * Set the shared instance. Call this early in application startup
     * to make pre-detected capabilities available to all later consumers.
     *
     * @param caps the pre-detected capabilities
     */
    public static void setInstance(TerminalCapabilities caps) {
        instance = caps;
    }

    /**
     * Detect terminal capabilities from environment variables only.
     * Fast (~1-2ms), no subprocess calls on Linux/macOS.
     *
     * @return the detected capabilities
     */
    public static TerminalCapabilities detect() {
        return new TerminalCapabilities(new TerminalDetector(), null);
    }

    /**
     * Detect terminal capabilities with full platform theme probing.
     * <p>
     * Same as {@link #detect()} but when the theme cannot be determined
     * from environment variables, also checks platform-specific sources:
     * <ul>
     * <li>macOS: {@code defaults read -g AppleInterfaceStyle}</li>
     * <li>Linux: {@code gsettings} (GNOME) or {@code kreadconfig5} (KDE)</li>
     * <li>Windows: Apps dark mode registry key</li>
     * </ul>
     * This may take 10-50ms due to subprocess calls.
     *
     * @return the detected capabilities with resolved theme
     */
    public static TerminalCapabilities detectFull() {
        TerminalCapabilities caps = detect();
        if (caps.detector.theme == TerminalTheme.UNKNOWN) {
            caps.resolvedTheme = TerminalDetector.detectPlatformTheme();
        }

        // Run terminal mode + color queries
        if (!caps.detector.isInMultiplexer()) {
            TerminalColorQuery result = TerminalColorQuery.query();
            if (result != null) {
                caps.foregroundRGB = result.foreground;
                caps.backgroundRGB = result.background;
                caps.paletteColors = result.palette;
                caps.queried256 = result.supports256;
                caps.mode2026Support = result.mode2026;
                caps.mode2027Support = result.mode2027;
                if (result.supportsSixel && caps.detector.imageProtocol == ImageProtocol.NONE) {
                    caps.queriedImageProtocol = ImageProtocol.SIXEL;
                }
                if (result.background != null && caps.resolvedTheme == null) {
                    caps.resolvedTheme = themeFromRGB(result.background);
                }

                // Cursor-position grapheme probe: only when DA1 responded
                // but Mode 2027 is not supported
                if (result.da1Received && result.mode2027 == ModeSupport.NOT_SUPPORTED) {
                    boolean nativeGC = TerminalColorQuery.probeGraphemeClustering();
                    caps.nativeGraphemeClustering = nativeGC;
                }
            }
        }
        return caps;
    }

    /**
     * Detect terminal capabilities and query actual colors in the background.
     * <p>
     * Returns immediately with heuristic results (~1-2ms). A daemon thread
     * queries the terminal for foreground/background colors via OSC escape
     * sequences. Use {@link #awaitColors(long, TimeUnit)} to wait for the
     * query to complete, or check {@link #backgroundRGB()} / {@link #foregroundRGB()}
     * which return {@code null} until the query finishes.
     * <p>
     * On platforms where terminal queries are not possible (Windows, pipes,
     * containers, tmux without passthrough), the background thread completes
     * immediately with no results and falls back to platform theme detection.
     *
     * @return the detected capabilities with background color query running
     */
    public static TerminalCapabilities detectAsync() {
        TerminalDetector detector = new TerminalDetector();
        CountDownLatch latch = new CountDownLatch(1);
        TerminalCapabilities caps = new TerminalCapabilities(detector, latch);

        Thread queryThread = new Thread(() -> {
            try {
                if (!detector.isInMultiplexer()) {
                    TerminalColorQuery result = TerminalColorQuery.query();
                    if (result != null) {
                        caps.foregroundRGB = result.foreground;
                        caps.backgroundRGB = result.background;
                        caps.paletteColors = result.palette;
                        caps.queried256 = result.supports256;
                        caps.mode2026Support = result.mode2026;
                        caps.mode2027Support = result.mode2027;
                        caps.nativeGraphemeClustering = result.nativeGraphemeClustering;
                        if (result.supportsSixel && detector.imageProtocol == ImageProtocol.NONE) {
                            caps.queriedImageProtocol = ImageProtocol.SIXEL;
                        }
                        if (result.background != null) {
                            caps.resolvedTheme = themeFromRGB(result.background);
                        }
                    }
                }
                if (caps.resolvedTheme == null && detector.theme == TerminalTheme.UNKNOWN) {
                    caps.resolvedTheme = TerminalDetector.detectPlatformTheme();
                }
            } finally {
                latch.countDown();
            }
        }, "terminal-detect-color-query");
        queryThread.setDaemon(true);
        queryThread.start();

        return caps;
    }

    /**
     * Wait for the background color query to complete.
     * <p>
     * Only relevant when created via {@link #detectAsync()}. For other
     * factory methods, this returns {@code true} immediately.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return {@code true} if the query completed, {@code false} if timed out
     * @throws InterruptedException if interrupted while waiting
     */
    public boolean awaitColors(long timeout, TimeUnit unit) throws InterruptedException {
        if (colorQueryLatch == null) {
            return true;
        }
        return colorQueryLatch.await(timeout, unit);
    }

    /**
     * Check if the terminal supports 24-bit true color (16 million colors).
     * <p>
     * When created via {@link #detectAsync()}, this may upgrade to
     * {@code true} after the color query confirms true color support
     * (terminals that respond to OSC color queries support true color).
     *
     * @return true if true color is supported
     */
    public boolean supportsTrueColor() {
        return detector.trueColor || foregroundRGB != null || backgroundRGB != null;
    }

    /**
     * Check if the terminal supports at least 256 colors.
     * Always true when {@link #supportsTrueColor()} is true.
     * <p>
     * When created via {@link #detectAsync()}, this may upgrade to
     * {@code true} after the color query confirms 256-color support.
     *
     * @return true if 256 colors are supported
     */
    public boolean supports256Colors() {
        return detector.colors256 || (queried256 != null && queried256);
    }

    /**
     * Check if the terminal supports any color output.
     *
     * @return true if color is supported
     */
    public boolean supportsColor() {
        return detector.colors256 || detector.trueColor;
    }

    /**
     * Get the image display protocol supported by this terminal.
     * <p>
     * When created via {@link #detectAsync()}, this may upgrade from
     * NONE to SIXEL after the DA1 query confirms Sixel support.
     *
     * @return the image protocol, or {@link ImageProtocol#NONE}
     */
    public ImageProtocol imageProtocol() {
        return queriedImageProtocol != null ? queriedImageProtocol : detector.imageProtocol;
    }

    /**
     * Get the terminal background theme.
     * <p>
     * When created via {@link #detectAsync()}, this may return a more
     * accurate result after the color query completes (derived from
     * the actual background RGB).
     *
     * @return the theme (DARK, LIGHT, or UNKNOWN)
     */
    public TerminalTheme theme() {
        return resolvedTheme != null ? resolvedTheme : detector.theme;
    }

    /**
     * Get the queried foreground color.
     * <p>
     * Only available after a successful color query via {@link #detectAsync()}.
     *
     * @return RGB array [r, g, b] (0-255 each), or {@code null} if not queried
     */
    public int[] foregroundRGB() {
        return foregroundRGB;
    }

    /**
     * Get the queried background color.
     * <p>
     * Only available after a successful color query via {@link #detectAsync()}.
     *
     * @return RGB array [r, g, b] (0-255 each), or {@code null} if not queried
     */
    public int[] backgroundRGB() {
        return backgroundRGB;
    }

    /**
     * Get the queried base 16 palette colors (ANSI colors 0-15).
     * <p>
     * Only available after a successful color query via {@link #detectAsync()}.
     *
     * @return map from color index (0-15) to RGB array [r, g, b], or empty map if not queried
     */
    public Map<Integer, int[]> paletteColors() {
        Map<Integer, int[]> p = paletteColors;
        return p != null ? Collections.unmodifiableMap(p) : Collections.<Integer, int[]> emptyMap();
    }

    /**
     * Get a palette color by ANSI index (0-15).
     *
     * @param index the ANSI color index
     * @return RGB array [r, g, b] (0-255 each), or {@code null} if not queried
     */
    public int[] paletteColor(int index) {
        Map<Integer, int[]> p = paletteColors;
        return p != null ? p.get(index) : null;
    }

    // ANSI standard color accessors (indices 0-7)

    /**
     * Returns the RGB color for ANSI black (index 0).
     *
     * @return RGB array for ANSI black (index 0), or null if not queried.
     */
    public int[] black() {
        return paletteColor(0);
    }

    /**
     * Returns the RGB color for ANSI red (index 1).
     *
     * @return RGB array for ANSI red (index 1), or null if not queried.
     */
    public int[] red() {
        return paletteColor(1);
    }

    /**
     * Returns the RGB color for ANSI green (index 2).
     *
     * @return RGB array for ANSI green (index 2), or null if not queried.
     */
    public int[] green() {
        return paletteColor(2);
    }

    /**
     * Returns the RGB color for ANSI yellow (index 3).
     *
     * @return RGB array for ANSI yellow (index 3), or null if not queried.
     */
    public int[] yellow() {
        return paletteColor(3);
    }

    /**
     * Returns the RGB color for ANSI blue (index 4).
     *
     * @return RGB array for ANSI blue (index 4), or null if not queried.
     */
    public int[] blue() {
        return paletteColor(4);
    }

    /**
     * Returns the RGB color for ANSI magenta (index 5).
     *
     * @return RGB array for ANSI magenta (index 5), or null if not queried.
     */
    public int[] magenta() {
        return paletteColor(5);
    }

    /**
     * Returns the RGB color for ANSI cyan (index 6).
     *
     * @return RGB array for ANSI cyan (index 6), or null if not queried.
     */
    public int[] cyan() {
        return paletteColor(6);
    }

    /**
     * Returns the RGB color for ANSI white (index 7).
     *
     * @return RGB array for ANSI white (index 7), or null if not queried.
     */
    public int[] white() {
        return paletteColor(7);
    }

    // Bright color accessors (indices 8-15)

    /**
     * Returns the RGB color for bright black (index 8).
     *
     * @return RGB array for bright black (index 8), or null if not queried.
     */
    public int[] brightBlack() {
        return paletteColor(8);
    }

    /**
     * Returns the RGB color for bright red (index 9).
     *
     * @return RGB array for bright red (index 9), or null if not queried.
     */
    public int[] brightRed() {
        return paletteColor(9);
    }

    /**
     * Returns the RGB color for bright green (index 10).
     *
     * @return RGB array for bright green (index 10), or null if not queried.
     */
    public int[] brightGreen() {
        return paletteColor(10);
    }

    /**
     * Returns the RGB color for bright yellow (index 11).
     *
     * @return RGB array for bright yellow (index 11), or null if not queried.
     */
    public int[] brightYellow() {
        return paletteColor(11);
    }

    /**
     * Returns the RGB color for bright blue (index 12).
     *
     * @return RGB array for bright blue (index 12), or null if not queried.
     */
    public int[] brightBlue() {
        return paletteColor(12);
    }

    /**
     * Returns the RGB color for bright magenta (index 13).
     *
     * @return RGB array for bright magenta (index 13), or null if not queried.
     */
    public int[] brightMagenta() {
        return paletteColor(13);
    }

    /**
     * Returns the RGB color for bright cyan (index 14).
     *
     * @return RGB array for bright cyan (index 14), or null if not queried.
     */
    public int[] brightCyan() {
        return paletteColor(14);
    }

    /**
     * Returns the RGB color for bright white (index 15).
     *
     * @return RGB array for bright white (index 15), or null if not queried.
     */
    public int[] brightWhite() {
        return paletteColor(15);
    }

    /**
     * Support status for Mode 2026 (synchronized output).
     * <p>
     * Only available after a terminal query via {@link #detectAsync()}.
     * Returns {@code null} if not yet queried.
     *
     * @return the mode support status, or null if not queried
     */
    public ModeSupport synchronizedOutputSupport() {
        return mode2026Support;
    }

    /**
     * Support status for Mode 2027 (grapheme cluster mode).
     * <p>
     * Only available after a terminal query via {@link #detectAsync()}.
     * Returns {@code null} if not yet queried.
     *
     * @return the mode support status, or null if not queried
     */
    public ModeSupport graphemeClusterSupport() {
        return mode2027Support;
    }

    /**
     * Whether the terminal natively clusters grapheme sequences even
     * without Mode 2027 support. Detected via cursor-position probe
     * in {@link #detectFull()}.
     *
     * @return true if native clustering detected, null if not probed
     */
    public Boolean nativeGraphemeClustering() {
        return nativeGraphemeClustering;
    }

    /**
     * Get the detected terminal name.
     *
     * @return terminal name (e.g., "kitty", "ghostty", "iterm2", "unknown")
     */
    public String terminalName() {
        return detector.terminalName;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("TerminalCapabilities{");
        sb.append("terminal=").append(detector.terminalName);
        sb.append(", trueColor=").append(detector.trueColor);
        sb.append(", 256colors=").append(supports256Colors());
        sb.append(", imageProtocol=").append(detector.imageProtocol);
        sb.append(", theme=").append(theme());
        if (foregroundRGB != null)
            sb.append(", fg=").append(formatRGB(foregroundRGB));
        if (backgroundRGB != null)
            sb.append(", bg=").append(formatRGB(backgroundRGB));
        if (paletteColors != null)
            sb.append(", palette=").append(paletteColors.size()).append(" colors");
        sb.append('}');
        return sb.toString();
    }

    private static String formatRGB(int[] rgb) {
        return "[" + rgb[0] + "," + rgb[1] + "," + rgb[2] + "]";
    }

    private static TerminalTheme themeFromRGB(int[] rgb) {
        // Perceived luminance (ITU-R BT.601)
        double luminance = 0.299 * rgb[0] + 0.587 * rgb[1] + 0.114 * rgb[2];
        return luminance < 128 ? TerminalTheme.DARK : TerminalTheme.LIGHT;
    }
}
