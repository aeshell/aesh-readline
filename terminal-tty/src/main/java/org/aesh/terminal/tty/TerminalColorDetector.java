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
package org.aesh.terminal.tty;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.TerminalFeatures;
import org.aesh.terminal.tty.utils.ColorUtils;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.CodePointUtils;
import org.aesh.terminal.utils.ColorDepth;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.TerminalColorCapability;
import org.aesh.terminal.utils.TerminalEnvironment;
import org.aesh.terminal.utils.TerminalTheme;

/**
 * Utility class to detect terminal color capabilities.
 * <p>
 * This detector can query the terminal to determine:
 * <ul>
 * <li>Color depth (8, 16, 256, or true color)</li>
 * <li>Background theme (light or dark)</li>
 * <li>Actual foreground and background RGB colors</li>
 * </ul>
 * <p>
 * Detection methods:
 * <ul>
 * <li>Environment variables (COLORTERM, TERM) via {@link TerminalEnvironment}</li>
 * <li>terminfo/infocmp database (max_colors capability)</li>
 * <li>OSC 10/11 queries for actual terminal colors</li>
 * <li>Platform-specific config files via {@link PlatformThemeDetector}</li>
 * </ul>
 * <p>
 * The detector supports caching to avoid repeated detection overhead.
 * Use {@link #detectCached(TerminalFeatures)} for cached detection.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public final class TerminalColorDetector {

    private static final Logger LOGGER = LoggerUtil.getLogger(TerminalColorDetector.class.getName());

    /** Cached color capability (thread-safe via volatile). */
    private static volatile TerminalColorCapability cachedCapability;

    /** Default timeout for full OSC queries (FG + BG + cursor + palette) in milliseconds. */
    private static final long DEFAULT_TIMEOUT_MS = 500;

    /** Timeout for fast OSC queries (FG + BG only) in milliseconds. */
    private static final long FAST_TIMEOUT_MS = 150;

    /** DCS prefix for tmux passthrough. */
    private static final String DCS_TMUX_PREFIX = "\u001BPtmux;\u001B";
    /** DCS suffix for tmux passthrough. */
    private static final String DCS_TMUX_SUFFIX = "\u001B\\";

    private TerminalColorDetector() {
    }

    // ==================== Public API ====================

    /**
     * Detect terminal color capabilities using cached result if available.
     *
     * @param terminal the terminal features
     * @return detected color capabilities (may be cached)
     */
    public static TerminalColorCapability detectCached(TerminalFeatures terminal) {
        TerminalColorCapability cached = cachedCapability;
        if (cached != null) {
            return cached;
        }

        TerminalColorCapability result = detect(terminal);
        cachedCapability = result;
        return result;
    }

    /**
     * Clear the cached color capability.
     */
    public static void clearCache() {
        cachedCapability = null;
    }

    /**
     * Get the cached color capability without performing detection.
     *
     * @return the cached capability, or null if not cached
     */
    public static TerminalColorCapability getCached() {
        return cachedCapability;
    }

    /**
     * Detect terminal color capabilities using a fast OSC query (FG + BG only).
     *
     * @param terminal the terminal features
     * @return detected color capabilities
     */
    public static TerminalColorCapability detect(TerminalFeatures terminal) {
        return detect(terminal, true);
    }

    /**
     * Detect terminal color capabilities.
     *
     * @param terminal the terminal features
     * @param queryTerminal if true, send OSC queries to the terminal
     * @return detected color capabilities
     */
    public static TerminalColorCapability detect(TerminalFeatures terminal, boolean queryTerminal) {
        return doDetect(terminal, queryTerminal, false);
    }

    /**
     * Detect terminal color capabilities including cursor and all 16 palette colors.
     *
     * @param terminal the terminal features
     * @return detected color capabilities including palette colors
     */
    public static TerminalColorCapability detectFull(TerminalFeatures terminal) {
        return doDetect(terminal, true, true);
    }

    /**
     * Get a fast, non-blocking color capability based only on environment detection.
     *
     * @param terminal the terminal features (may be null)
     * @return detected color capabilities
     */
    public static TerminalColorCapability detectFast(TerminalFeatures terminal) {
        ColorDepth depth = detectColorDepth(terminal);
        TerminalTheme theme = detectThemeFromEnvironment();
        return new TerminalColorCapability(depth, theme);
    }

    /**
     * Detect only the color depth without querying the terminal for colors.
     *
     * @param terminal the terminal features
     * @return the detected color depth
     */
    public static ColorDepth detectColorDepth(TerminalFeatures terminal) {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // Check environment variables for true color support
        if (env.isTrueColorIndicated()) {
            return ColorDepth.TRUE_COLOR;
        }

        // Check TERM environment variable
        String term = env.getTerm();
        if (term != null) {
            String termLower = term.toLowerCase();
            if (termLower.contains("truecolor") || termLower.contains("24bit")) {
                return ColorDepth.TRUE_COLOR;
            }
            if (termLower.contains("ghostty") ||
                    termLower.contains("kitty") ||
                    termLower.contains("alacritty")) {
                return ColorDepth.TRUE_COLOR;
            }
            if (termLower.contains("256color") || termLower.contains("256-color")) {
                return ColorDepth.COLORS_256;
            }
        }

        // Check TERM_PROGRAM for true color terminals
        String termProgram = env.getTermProgram();
        if (termProgram != null) {
            String lower = termProgram.toLowerCase();
            if (lower.contains("ghostty") ||
                    lower.contains("kitty") ||
                    lower.contains("alacritty") ||
                    lower.contains("iterm") ||
                    lower.contains("wezterm") ||
                    lower.contains("hyper")) {
                return ColorDepth.TRUE_COLOR;
            }
        }

        // Check for terminal-specific environment variables
        if (env.isGhostty() || env.isKitty() || env.isAlacritty() || env.isWezTerm()) {
            return ColorDepth.TRUE_COLOR;
        }

        // Check for JetBrains IDE
        if (env.isJetBrains()) {
            return ColorDepth.TRUE_COLOR;
        }

        // Check for Windows 10+ with Virtual Terminal support
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            ColorDepth windowsDepth = detectWindowsColorDepth(env);
            if (windowsDepth != null) {
                return windowsDepth;
            }
        }

        // Check terminfo max_colors capability
        if (terminal != null && terminal.connection().device() != null) {
            Device device = terminal.connection().device();
            Integer maxColors = device.getNumericCapability(Capability.max_colors);
            if (maxColors != null) {
                return ColorDepth.fromColorCount(maxColors);
            }
        }

        // Default to 8 colors if ANSI is supported
        if (terminal != null && terminal.connection().supportsAnsi()) {
            return ColorDepth.COLORS_8;
        }

        return ColorDepth.NO_COLOR;
    }

    /**
     * Detect terminal theme from environment variables and platform-specific config files.
     *
     * @return the detected theme, or UNKNOWN if not detectable
     */
    public static TerminalTheme detectThemeFromEnvironment() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // Check for IDE-specific terminals FIRST
        // IDE terminals may inherit COLORFGBG from the system shell, which doesn't
        // reflect the actual IDE theme.
        boolean isIDETerminal = env.isJetBrains() ||
                (env.getTermProgram() != null && "vscode".equalsIgnoreCase(env.getTermProgram()));

        // Try platform-specific detection for IDE terminals
        if (isIDETerminal) {
            TerminalTheme theme = PlatformThemeDetector.detectPlatformTheme(env);
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }
            // IDE detection failed - skip COLORFGBG (unreliable in IDEs)
            LOGGER.log(Level.FINE, "IDE theme detection failed, skipping COLORFGBG");
        }

        // Check COLORFGBG for non-IDE terminals
        if (!isIDETerminal) {
            String colorfgbg = env.getColorFgBg();
            if (colorfgbg != null) {
                String[] parts = colorfgbg.split(";");
                if (parts.length >= 2) {
                    try {
                        int bg = Integer.parseInt(parts[parts.length - 1]);
                        boolean isDark = bg < 7 || bg == 8;
                        return isDark ? TerminalTheme.DARK : TerminalTheme.LIGHT;
                    } catch (NumberFormatException e) {
                        // Ignore, continue with other methods
                    }
                }
            }
        }

        // Check macOS dark mode via environment
        String appleInterfaceStyle = env.getAppleInterfaceStyle();
        if (appleInterfaceStyle != null) {
            return "Dark".equalsIgnoreCase(appleInterfaceStyle)
                    ? TerminalTheme.DARK
                    : TerminalTheme.LIGHT;
        }

        // Delegate to PlatformThemeDetector for remaining platform checks
        // (macOS defaults read, Windows registry, Alacritty config, etc.)
        TerminalTheme platformTheme = PlatformThemeDetector.detectPlatformTheme(env);
        if (platformTheme != TerminalTheme.UNKNOWN) {
            return platformTheme;
        }

        // Windows default: assume dark
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            LOGGER.log(Level.FINE, "Windows detected but theme not determined, defaulting to dark");
            return TerminalTheme.DARK;
        }

        return TerminalTheme.UNKNOWN;
    }

    /**
     * Query terminal color capabilities using synchronous I/O.
     *
     * @param terminal the terminal features (must wrap a TerminalConnection)
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @return TerminalColorCapability with detected colors, or null if not supported
     */
    public static TerminalColorCapability queryColorCapability(TerminalFeatures terminal, long timeoutMs) {
        if (terminal == null || !terminal.connection().supportsAnsi()) {
            return null;
        }

        if (!(terminal.connection() instanceof TerminalConnection)) {
            return null;
        }

        Terminal rawTerminal = ((TerminalConnection) terminal.connection()).getTerminal();
        if (rawTerminal == null) {
            return null;
        }

        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isJetBrains()) {
            LOGGER.log(Level.FINE, "JetBrains/JediTerm detected - OSC queries not supported");
            return null;
        }

        boolean inTmux = env.isInTmux();
        boolean canUseTmuxPassthrough = shouldUseTmuxPassthrough(env);

        if (!inTmux) {
            Device device = terminal.connection().device();
            if (device != null && !device.supportsOscQueries()) {
                LOGGER.log(Level.FINE, "OSC queries not supported by device");
                return null;
            }
        }

        boolean supportsCursor = inTmux || terminal.supportsOscCode(Device.OscCode.CURSOR_COLOR);
        boolean supportsPalette = inTmux || terminal.supportsPaletteQuery();

        // Try CSI ? 996 n for direct theme detection first
        TerminalTheme dsrTheme = null;
        if (terminal.supportsThemeQuery()) {
            dsrTheme = queryThemeDsr(terminal, Math.min(timeoutMs, FAST_TIMEOUT_MS));
            if (dsrTheme != null) {
                LOGGER.log(Level.FINE, "Theme detected via CSI ? 996 n: " + dsrTheme);
            }
        }

        LOGGER.log(Level.FINE, "Trying plain OSC color query");
        TerminalColorCapability result = doSynchronousColorQuery(
                terminal, rawTerminal, timeoutMs, supportsCursor, supportsPalette, false);

        boolean needPalettePassthrough = inTmux && canUseTmuxPassthrough && supportsPalette
                && (result == null || result.getPaletteColors() == null || result.getPaletteColors().isEmpty());

        if (needPalettePassthrough) {
            LOGGER.log(Level.FINE, "Trying tmux DCS passthrough for palette colors");
            TerminalColorCapability paletteResult = doSynchronousColorQuery(
                    terminal, rawTerminal, timeoutMs / 2, false, true, true);

            if (paletteResult != null && paletteResult.getPaletteColors() != null
                    && !paletteResult.getPaletteColors().isEmpty()) {
                LOGGER.log(Level.FINE, "DCS passthrough palette query succeeded");
                if (result != null) {
                    result = new TerminalColorCapability(
                            result.getColorDepth(),
                            result.getTheme(),
                            result.getForegroundRGB(),
                            result.getBackgroundRGB(),
                            result.getCursorRGB() != null ? result.getCursorRGB() : paletteResult.getCursorRGB(),
                            paletteResult.getPaletteColors());
                } else {
                    result = paletteResult;
                }
            }
        } else if (!hasColors(result)) {
            if (inTmux && canUseTmuxPassthrough) {
                LOGGER.log(Level.FINE, "Trying tmux DCS passthrough for all colors");
                result = doSynchronousColorQuery(
                        terminal, rawTerminal, timeoutMs, supportsCursor, supportsPalette, true);
            }
        }

        if (hasColors(result)) {
            LOGGER.log(Level.FINE, "Color query succeeded");
        }

        // If we got a theme from CSI ? 996 n, override the OSC-derived theme
        if (dsrTheme != null && result != null) {
            result = new TerminalColorCapability(
                    result.getColorDepth(),
                    dsrTheme,
                    result.getForegroundRGB(),
                    result.getBackgroundRGB(),
                    result.getCursorRGB(),
                    result.getPaletteColors());
        }

        return result;
    }

    /**
     * Fast query for theme-relevant colors only (foreground and background).
     *
     * @param terminal the terminal features
     * @param timeoutMs timeout in milliseconds
     * @return TerminalColorCapability with FG/BG colors, or null if not supported
     */
    public static TerminalColorCapability queryThemeColors(TerminalFeatures terminal, long timeoutMs) {
        if (terminal == null || !terminal.connection().supportsAnsi()) {
            return null;
        }

        Terminal rawTerminal = null;
        if (terminal.connection() instanceof TerminalConnection) {
            rawTerminal = ((TerminalConnection) terminal.connection()).getTerminal();
        }
        if (rawTerminal == null) {
            return null;
        }

        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isJetBrains()) {
            return null;
        }

        boolean inTmux = env.isInTmux();

        if (!inTmux) {
            Device device = terminal.connection().device();
            if (device != null && !device.supportsOscQueries()) {
                return null;
            }
        }

        LOGGER.log(Level.FINE, "Trying fast OSC color query (FG+BG only)");
        TerminalColorCapability result = doSynchronousColorQuery(
                terminal, rawTerminal, timeoutMs, false, false, false);

        if (!hasColors(result)) {
            if (inTmux && shouldUseTmuxPassthrough(env)) {
                LOGGER.log(Level.FINE, "Trying tmux DCS passthrough for FG+BG");
                result = doSynchronousColorQuery(
                        terminal, rawTerminal, timeoutMs, false, false, true);
            }
        }

        return result;
    }

    /**
     * Query foreground, background, and cursor colors in a single batch operation.
     *
     * @param terminal the terminal features
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @return map from OSC code to RGB array [r, g, b] (0-255 each)
     */
    public static Map<Integer, int[]> queryColors(TerminalFeatures terminal, long timeoutMs) {
        if (terminal == null) {
            return Collections.emptyMap();
        }

        if (!isOscColorQuerySupported(terminal)) {
            LOGGER.log(Level.FINE, "OSC color queries not supported, returning empty result");
            return Collections.emptyMap();
        }

        return terminal.queryColors(timeoutMs);
    }

    /**
     * Query multiple palette colors in a single batch operation.
     *
     * @param terminal the terminal features
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @param indices the palette color indices to query (0-255)
     * @return map from palette index to RGB array [r, g, b] (0-255 each)
     */
    public static Map<Integer, int[]> queryPaletteColors(TerminalFeatures terminal, long timeoutMs, int... indices) {
        if (terminal == null) {
            return Collections.emptyMap();
        }

        if (!terminal.supportsPaletteQuery()) {
            LOGGER.log(Level.FINE, "OSC 4 palette queries not supported");
            return Collections.emptyMap();
        }

        return terminal.queryPaletteColors(timeoutMs, indices);
    }

    /**
     * Query the ANSI 16-color palette (colors 0-15) in a single batch operation.
     *
     * @param terminal the terminal features
     * @param timeoutMs timeout in milliseconds to wait for all responses
     * @return map from palette index (0-15) to RGB array [r, g, b] (0-255 each)
     */
    public static Map<Integer, int[]> queryAnsi16Colors(TerminalFeatures terminal, long timeoutMs) {
        if (terminal == null) {
            return Collections.emptyMap();
        }

        if (!terminal.supportsPaletteQuery()) {
            LOGGER.log(Level.FINE, "OSC 4 palette queries not supported");
            return Collections.emptyMap();
        }

        return terminal.queryAnsi16Colors(timeoutMs);
    }

    /**
     * Query colors with automatic fallback to environment-based detection.
     *
     * @param terminal the terminal features
     * @param timeoutMs timeout in milliseconds for OSC queries
     * @return map from OSC code to RGB array; always contains at least
     *         estimated foreground and background colors
     */
    public static Map<Integer, int[]> queryColorsWithFallback(TerminalFeatures terminal, long timeoutMs) {
        Map<Integer, int[]> results = queryColors(terminal, timeoutMs);

        if (!results.isEmpty()) {
            return results;
        }

        LOGGER.log(Level.FINE, "OSC queries failed or not supported, using environment-based fallback");

        Map<Integer, int[]> fallback = new LinkedHashMap<>();
        TerminalTheme theme = detectThemeFromEnvironment();

        if (theme == TerminalTheme.LIGHT) {
            fallback.put(ANSI.OSC_FOREGROUND, new int[] { 0, 0, 0 });
            fallback.put(ANSI.OSC_BACKGROUND, new int[] { 255, 255, 255 });
        } else {
            fallback.put(ANSI.OSC_FOREGROUND, new int[] { 204, 204, 204 });
            fallback.put(ANSI.OSC_BACKGROUND, new int[] { 30, 30, 30 });
        }

        return fallback;
    }

    /**
     * Check if the terminal likely supports OSC color queries.
     *
     * @param terminal the terminal features to check
     * @return true if OSC color queries are likely supported
     */
    public static boolean isOscColorQuerySupported(TerminalFeatures terminal) {
        return terminal != null && terminal.supportsColorQuery();
    }

    /**
     * Check if an RGB color represents a dark theme (luminance &lt; 0.5).
     *
     * @param rgb the RGB color array [r, g, b] (0-255 each)
     * @return true if the color is dark, false if light
     */
    public static boolean isDarkColor(int[] rgb) {
        return ColorUtils.isDarkColor(rgb);
    }

    // ==================== Internal Methods ====================

    private static TerminalColorCapability doDetect(TerminalFeatures terminal, boolean queryTerminal,
            boolean fullDetection) {
        ColorDepth colorDepth = detectColorDepth(terminal);
        TerminalTheme theme = TerminalTheme.UNKNOWN;
        int[] foregroundRGB = null;
        int[] backgroundRGB = null;
        int[] cursorRGB = null;
        Map<Integer, int[]> paletteColors = null;

        if (queryTerminal && terminal != null && terminal.connection().supportsAnsi()) {
            // Try CSI ? 996 n theme DSR first — it's faster and simpler than OSC 10/11
            if (terminal.supportsThemeQuery()) {
                try {
                    TerminalTheme dsrTheme = queryThemeDsr(terminal, FAST_TIMEOUT_MS);
                    if (dsrTheme != null) {
                        theme = dsrTheme;
                        LOGGER.log(Level.FINE, "Theme detected via CSI ? 996 n: " + theme);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "CSI ? 996 n theme query failed", e);
                }
            }

            // Query colors via OSC for RGB values (still useful for palette, FG/BG colors)
            try {
                long timeoutMs = fullDetection ? DEFAULT_TIMEOUT_MS : FAST_TIMEOUT_MS;
                TerminalColorCapability queryResult;
                if (fullDetection) {
                    queryResult = queryColorCapability(terminal, timeoutMs);
                } else {
                    queryResult = queryThemeColors(terminal, timeoutMs);
                }
                if (queryResult != null) {
                    foregroundRGB = queryResult.getForegroundRGB();
                    backgroundRGB = queryResult.getBackgroundRGB();
                    cursorRGB = queryResult.getCursorRGB();
                    paletteColors = queryResult.getPaletteColors();
                    // Only use OSC-derived theme if CSI ? 996 n didn't provide one
                    if (theme == TerminalTheme.UNKNOWN) {
                        theme = queryResult.getTheme();
                    }

                    LOGGER.log(Level.FINE, "Queried colors: FG=" + (foregroundRGB != null) +
                            ", BG=" + (backgroundRGB != null) + ", cursor=" + (cursorRGB != null) +
                            ", palette=" + (paletteColors != null ? paletteColors.size() : 0));
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Failed to query terminal colors", e);
            }
        }

        if (theme == TerminalTheme.UNKNOWN) {
            theme = detectThemeFromEnvironment();
        }

        return new TerminalColorCapability(colorDepth, theme, foregroundRGB, backgroundRGB,
                cursorRGB, paletteColors);
    }

    private static boolean hasColors(TerminalColorCapability cap) {
        return cap != null && (cap.getForegroundRGB() != null || cap.getBackgroundRGB() != null);
    }

    private static boolean shouldUseTmuxPassthrough(TerminalEnvironment env) {
        if (!env.isInTmux()) {
            return false;
        }

        if (env.isTmuxPassthroughEnabled()) {
            return true;
        }

        // Check if we detect a known OSC-capable outer terminal
        return env.hasKnownOuterTerminal();
    }

    private static ColorDepth detectWindowsColorDepth(TerminalEnvironment env) {
        if (env.isWindowsTerminal()) {
            LOGGER.log(Level.FINE, "Windows Terminal detected - true color supported");
            return ColorDepth.TRUE_COLOR;
        }

        if (env.isConEmu()) {
            LOGGER.log(Level.FINE, "ConEmu detected - true color supported");
            return ColorDepth.TRUE_COLOR;
        }

        // Check Windows version for VT support
        String osVersion = System.getProperty("os.version", "");
        try {
            if (osVersion.startsWith("10.") || osVersion.startsWith("11.")) {
                int buildNumber = PlatformThemeDetector.getWindowsBuildNumber();
                if (buildNumber >= 14931) {
                    LOGGER.log(Level.FINE, "Windows 10+ build " + buildNumber + " detected - true color supported");
                    return ColorDepth.TRUE_COLOR;
                } else if (buildNumber > 0) {
                    LOGGER.log(Level.FINE, "Windows 10 build " + buildNumber + " - 16 colors");
                    return ColorDepth.COLORS_16;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to detect Windows version", e);
        }

        // Fallback: check TERM for MSYS2/Git Bash/Cygwin
        String term = env.getTerm();
        if (term != null) {
            String termLower = term.toLowerCase();
            if (termLower.contains("256color")) {
                return ColorDepth.COLORS_256;
            }
            if (termLower.contains("xterm") || termLower.contains("cygwin") ||
                    termLower.contains("msys")) {
                return ColorDepth.COLORS_256;
            }
        }

        // Default for modern Windows
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("windows 10") || osName.contains("windows 11") ||
                osName.contains("windows server 2016") || osName.contains("windows server 2019") ||
                osName.contains("windows server 2022")) {
            LOGGER.log(Level.FINE, "Modern Windows detected by name - assuming true color");
            return ColorDepth.TRUE_COLOR;
        }

        return null;
    }

    // ==================== Synchronous Theme DSR Query ====================

    /**
     * Query the terminal for its theme mode using CSI ? 996 n via synchronous I/O.
     * <p>
     * This uses the same synchronous I/O pattern as {@code doSynchronousColorQuery}
     * and works regardless of whether the connection is actively reading.
     * <p>
     * For terminals that support this protocol (Contour, Ghostty, Kitty 0.38.1+,
     * tmux, VTE 0.82.0+), this is faster than OSC 10/11 because it returns a
     * direct dark/light answer.
     *
     * @param terminal the terminal features
     * @param timeoutMs timeout in milliseconds
     * @return {@link TerminalTheme#DARK} or {@link TerminalTheme#LIGHT},
     *         or null if not supported or timeout
     */
    private static TerminalTheme queryThemeDsr(TerminalFeatures terminal, long timeoutMs) {
        Connection connection = terminal.connection();

        if (!(connection instanceof TerminalConnection)) {
            // For non-TerminalConnection, try the handler-based approach
            return terminal.queryThemeMode(timeoutMs);
        }

        TerminalConnection termConn = (TerminalConnection) connection;
        Terminal rawTerminal = termConn.getTerminal();
        if (rawTerminal == null) {
            return null;
        }

        Attributes savedAttributes = connection.attributes();
        Attributes rawAttributes = new Attributes(savedAttributes);
        rawAttributes.setLocalFlags(
                EnumSet.of(Attributes.LocalFlag.ICANON, Attributes.LocalFlag.ECHO),
                false);
        rawAttributes.setControlChar(Attributes.ControlChar.VMIN, 0);
        rawAttributes.setControlChar(Attributes.ControlChar.VTIME, 1);
        connection.setAttributes(rawAttributes);

        try {
            InputStream input = rawTerminal.input();

            // Drain any pending input
            while (input.available() > 0) {
                input.read();
            }

            // Send CSI ? 996 n
            connection.write(ANSI.THEME_MODE_QUERY);
            rawTerminal.output().flush();

            // Read response — expecting ESC [ ? 997 ; Ps n
            StringBuilder response = new StringBuilder();
            long endTime = System.currentTimeMillis() + timeoutMs;
            byte[] buffer = new byte[256];

            while (System.currentTimeMillis() < endTime) {
                if (input.available() > 0) {
                    int read = input.read(buffer);
                    if (read > 0) {
                        for (int i = 0; i < read; i++) {
                            response.append((char) (buffer[i] & 0xFF));
                        }
                        // Check if we have a complete response (terminated by 'n')
                        String resp = response.toString();
                        if (resp.contains("n") && resp.contains("\u001B[?997;")) {
                            break;
                        }
                    } else if (read < 0) {
                        break;
                    }
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            drainInput(input, 10);

            String responseStr = response.toString();
            if (!responseStr.isEmpty()) {
                int[] responseCodePoints = CodePointUtils.toCodePoints(responseStr);
                TerminalTheme result = ANSI.parseThemeDsrResponse(responseCodePoints);
                if (result != null) {
                    return result;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to query theme via CSI ? 996 n", e);
        } finally {
            connection.setAttributes(savedAttributes);
        }

        return null;
    }

    // ==================== Synchronous Color Query ====================

    private static TerminalColorCapability doSynchronousColorQuery(
            TerminalFeatures terminal, Terminal rawTerminal, long timeoutMs,
            boolean supportsCursor, boolean supportsPalette, boolean useDcsPassthrough) {

        int[] foregroundRGB = null;
        int[] backgroundRGB = null;
        int[] cursorRGB = null;
        Map<Integer, int[]> paletteColors = null;

        Connection connection = terminal.connection();
        Attributes savedAttributes = connection.attributes();
        Attributes rawAttributes = new Attributes(savedAttributes);
        rawAttributes.setLocalFlags(
                EnumSet.of(Attributes.LocalFlag.ICANON, Attributes.LocalFlag.ECHO),
                false);
        rawAttributes.setControlChar(Attributes.ControlChar.VMIN, 0);
        rawAttributes.setControlChar(Attributes.ControlChar.VTIME, 1);
        connection.setAttributes(rawAttributes);

        try {
            InputStream input = rawTerminal.input();

            while (input.available() > 0) {
                input.read();
            }

            String combinedQuery = buildSyncColorQuery(supportsCursor, supportsPalette, useDcsPassthrough);
            connection.write(combinedQuery);

            rawTerminal.output().flush();

            int expectedResponses = 2; // FG + BG
            if (supportsCursor)
                expectedResponses++;
            if (supportsPalette)
                expectedResponses += 16;

            StringBuilder response = new StringBuilder();
            long endTime = System.currentTimeMillis() + timeoutMs;
            byte[] buffer = new byte[1024];

            while (System.currentTimeMillis() < endTime) {
                if (input.available() > 0) {
                    int read = input.read(buffer);
                    if (read > 0) {
                        for (int i = 0; i < read; i++) {
                            response.append((char) (buffer[i] & 0xFF));
                        }
                        int responseCount = countResponses(response.toString());
                        if (responseCount >= expectedResponses) {
                            break;
                        }
                    } else if (read < 0) {
                        break;
                    }
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            drainInput(input, 20);

            String responseStr = response.toString();
            if (!responseStr.isEmpty()) {
                LOGGER.log(Level.FINE, "OSC color response received, length: " + responseStr.length());

                int[] responseCodePoints = CodePointUtils.toCodePoints(responseStr);
                Map<Integer, int[]> fgBgColors = ANSI.parseMultipleOscColorResponses(
                        responseCodePoints,
                        ANSI.OSC_FOREGROUND,
                        ANSI.OSC_BACKGROUND);
                foregroundRGB = fgBgColors.get(ANSI.OSC_FOREGROUND);
                backgroundRGB = fgBgColors.get(ANSI.OSC_BACKGROUND);

                if (supportsCursor) {
                    cursorRGB = ANSI.parseOscColorResponse(
                            responseCodePoints,
                            ANSI.OSC_CURSOR_COLOR);
                }

                if (supportsPalette) {
                    paletteColors = ANSI.parseMultiplePaletteResponses(
                            responseCodePoints,
                            0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15);
                }
            }

        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to query color capability", e);
            return null;
        } finally {
            connection.setAttributes(savedAttributes);
        }

        ColorDepth colorDepth = terminal.colorDepth();
        TerminalTheme theme = TerminalTheme.UNKNOWN;
        if (backgroundRGB != null) {
            theme = TerminalTheme.fromRGB(backgroundRGB[0], backgroundRGB[1], backgroundRGB[2]);
        }

        return new TerminalColorCapability(colorDepth, theme, foregroundRGB, backgroundRGB, cursorRGB, paletteColors);
    }

    private static String buildSyncColorQuery(boolean supportsCursor, boolean supportsPalette, boolean useDcsPassthrough) {
        StringBuilder queryBuilder = new StringBuilder();

        if (useDcsPassthrough) {
            queryBuilder.append(DCS_TMUX_PREFIX).append("\u001B]10;?\u0007").append(DCS_TMUX_SUFFIX);
            queryBuilder.append(DCS_TMUX_PREFIX).append("\u001B]11;?\u0007").append(DCS_TMUX_SUFFIX);
            if (supportsCursor) {
                queryBuilder.append(DCS_TMUX_PREFIX).append("\u001B]12;?\u0007").append(DCS_TMUX_SUFFIX);
            }
            if (supportsPalette) {
                for (int i = 0; i < 16; i++) {
                    queryBuilder.append(DCS_TMUX_PREFIX)
                            .append("\u001B]4;").append(i).append(";?\u0007")
                            .append(DCS_TMUX_SUFFIX);
                }
            }
        } else {
            queryBuilder.append("\u001B]10;?\u0007");
            queryBuilder.append("\u001B]11;?\u0007");
            if (supportsCursor) {
                queryBuilder.append("\u001B]12;?\u0007");
            }
            if (supportsPalette) {
                for (int i = 0; i < 16; i++) {
                    queryBuilder.append("\u001B]4;").append(i).append(";?\u0007");
                }
            }
        }

        return queryBuilder.toString();
    }

    private static int countResponses(String response) {
        int count = 0;
        for (int i = 0; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '\u0007') {
                count++;
            } else if (c == '\\' && i > 0 && response.charAt(i - 1) == '\u001B') {
                count++;
            }
        }
        return count;
    }

    private static void drainInput(InputStream input, long maxWaitMs) throws IOException {
        byte[] buffer = new byte[256];
        long endTime = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < endTime) {
            if (input.available() > 0) {
                input.read(buffer);
            } else {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (input.available() == 0) {
                    break;
                }
            }
        }
    }
}
