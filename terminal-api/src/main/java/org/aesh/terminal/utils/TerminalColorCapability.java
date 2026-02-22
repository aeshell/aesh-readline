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

/**
 * Represents the detected color capabilities of a terminal.
 * <p>
 * This class encapsulates information about a terminal's color support,
 * including:
 * <ul>
 * <li>Color depth (8, 16, 256, or true color)</li>
 * <li>Background theme (light or dark)</li>
 * <li>Foreground and background RGB colors (if detectable)</li>
 * </ul>
 * <p>
 * Use {@code TerminalColorDetector} from the readline module to obtain
 * instances of this class, or use the static factory methods like
 * {@link #detectFromEnvironment()} for quick detection.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class TerminalColorCapability {

    private final ColorDepth colorDepth;
    private final TerminalTheme theme;
    private final int[] foregroundRGB;
    private final int[] backgroundRGB;
    private final int[] cursorRGB;
    private final java.util.Map<Integer, int[]> paletteColors;

    // Custom color overrides (null means use theme-based default)
    private final Integer foregroundCodeOverride;
    private final Integer errorCodeOverride;
    private final Integer successCodeOverride;
    private final Integer warningCodeOverride;
    private final Integer infoCodeOverride;
    private final Integer debugCodeOverride;
    private final Integer traceCodeOverride;
    private final Integer timestampCodeOverride;
    private final Integer messageCodeOverride;
    private final Integer categoryCodeOverride;
    private final Integer threadNameCodeOverride;
    private final Integer fatalCodeOverride;

    /**
     * Create a new TerminalColorCapability with all detected values.
     *
     * @param colorDepth the detected color depth
     * @param theme the detected terminal theme
     * @param foregroundRGB the foreground RGB color (may be null if not detected)
     * @param backgroundRGB the background RGB color (may be null if not detected)
     */
    public TerminalColorCapability(ColorDepth colorDepth, TerminalTheme theme,
            int[] foregroundRGB, int[] backgroundRGB) {
        this(colorDepth, theme, foregroundRGB, backgroundRGB, null, null);
    }

    /**
     * Create a new TerminalColorCapability with all detected values including cursor and palette.
     *
     * @param colorDepth the detected color depth
     * @param theme the detected terminal theme
     * @param foregroundRGB the foreground RGB color (may be null if not detected)
     * @param backgroundRGB the background RGB color (may be null if not detected)
     * @param cursorRGB the cursor RGB color (may be null if not detected)
     * @param paletteColors the palette colors map (may be null if not detected)
     */
    public TerminalColorCapability(ColorDepth colorDepth, TerminalTheme theme,
            int[] foregroundRGB, int[] backgroundRGB, int[] cursorRGB,
            java.util.Map<Integer, int[]> paletteColors) {
        this.colorDepth = colorDepth != null ? colorDepth : ColorDepth.COLORS_8;
        this.theme = theme != null ? theme : TerminalTheme.UNKNOWN;
        this.foregroundRGB = foregroundRGB;
        this.backgroundRGB = backgroundRGB;
        this.cursorRGB = cursorRGB;
        this.paletteColors = paletteColors != null
                ? java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(paletteColors))
                : null;
        // No overrides in this constructor
        this.foregroundCodeOverride = null;
        this.errorCodeOverride = null;
        this.successCodeOverride = null;
        this.warningCodeOverride = null;
        this.infoCodeOverride = null;
        this.debugCodeOverride = null;
        this.traceCodeOverride = null;
        this.timestampCodeOverride = null;
        this.messageCodeOverride = null;
        this.categoryCodeOverride = null;
        this.threadNameCodeOverride = null;
        this.fatalCodeOverride = null;
    }

    /**
     * Private constructor used by the Builder.
     */
    private TerminalColorCapability(Builder builder) {
        this.colorDepth = builder.colorDepth != null ? builder.colorDepth : ColorDepth.COLORS_8;
        this.theme = builder.theme != null ? builder.theme : TerminalTheme.UNKNOWN;
        this.foregroundRGB = builder.foregroundRGB;
        this.backgroundRGB = builder.backgroundRGB;
        this.cursorRGB = builder.cursorRGB;
        this.paletteColors = builder.paletteColors != null
                ? java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(builder.paletteColors))
                : null;
        this.foregroundCodeOverride = builder.foregroundCodeOverride;
        this.errorCodeOverride = builder.errorCodeOverride;
        this.successCodeOverride = builder.successCodeOverride;
        this.warningCodeOverride = builder.warningCodeOverride;
        this.infoCodeOverride = builder.infoCodeOverride;
        this.debugCodeOverride = builder.debugCodeOverride;
        this.traceCodeOverride = builder.traceCodeOverride;
        this.timestampCodeOverride = builder.timestampCodeOverride;
        this.messageCodeOverride = builder.messageCodeOverride;
        this.categoryCodeOverride = builder.categoryCodeOverride;
        this.threadNameCodeOverride = builder.threadNameCodeOverride;
        this.fatalCodeOverride = builder.fatalCodeOverride;
    }

    /**
     * Create a new TerminalColorCapability with color depth and theme.
     *
     * @param colorDepth the detected color depth
     * @param theme the detected terminal theme
     */
    public TerminalColorCapability(ColorDepth colorDepth, TerminalTheme theme) {
        this(colorDepth, theme, null, null);
    }

    /**
     * Create a new TerminalColorCapability with only color depth.
     *
     * @param colorDepth the detected color depth
     */
    public TerminalColorCapability(ColorDepth colorDepth) {
        this(colorDepth, TerminalTheme.UNKNOWN, null, null);
    }

    /**
     * Get the detected color depth capability.
     *
     * @return the detected color depth capability
     */
    public ColorDepth getColorDepth() {
        return colorDepth;
    }

    /**
     * Get the detected terminal theme.
     *
     * @return the detected terminal theme (light/dark/unknown)
     */
    public TerminalTheme getTheme() {
        return theme;
    }

    /**
     * Get the detected foreground RGB color.
     *
     * @return the detected foreground RGB color as [r, g, b] (0-255 each),
     *         or null if not detected
     */
    public int[] getForegroundRGB() {
        return foregroundRGB != null ? foregroundRGB.clone() : null;
    }

    /**
     * Get the detected background RGB color.
     *
     * @return the detected background RGB color as [r, g, b] (0-255 each),
     *         or null if not detected
     */
    public int[] getBackgroundRGB() {
        return backgroundRGB != null ? backgroundRGB.clone() : null;
    }

    /**
     * Get the detected cursor RGB color.
     *
     * @return the detected cursor RGB color as [r, g, b] (0-255 each),
     *         or null if not detected
     */
    public int[] getCursorRGB() {
        return cursorRGB != null ? cursorRGB.clone() : null;
    }

    /**
     * Get the detected palette colors.
     * <p>
     * The map contains palette index to RGB color mappings. Common indices:
     * <ul>
     * <li>0-7: Standard ANSI colors (black, red, green, yellow, blue, magenta, cyan, white)</li>
     * <li>8-15: Bright ANSI colors</li>
     * </ul>
     *
     * @return unmodifiable map of palette index to RGB array, or null if not detected
     */
    public java.util.Map<Integer, int[]> getPaletteColors() {
        return paletteColors;
    }

    /**
     * Get a specific palette color by index.
     *
     * @param index the palette index (0-255)
     * @return the RGB color as [r, g, b] (0-255 each), or null if not available
     */
    public int[] getPaletteColor(int index) {
        if (paletteColors == null) {
            return null;
        }
        int[] color = paletteColors.get(index);
        return color != null ? color.clone() : null;
    }

    /**
     * Check if the foreground color was detected.
     *
     * @return true if foreground RGB is available
     */
    public boolean hasForegroundColor() {
        return foregroundRGB != null;
    }

    /**
     * Check if the cursor color was detected.
     *
     * @return true if cursor RGB is available
     */
    public boolean hasCursorColor() {
        return cursorRGB != null;
    }

    /**
     * Check if palette colors were detected.
     *
     * @return true if any palette colors are available
     */
    public boolean hasPaletteColors() {
        return paletteColors != null && !paletteColors.isEmpty();
    }

    /**
     * Check if the background color was detected.
     *
     * @return true if background RGB is available
     */
    public boolean hasBackgroundColor() {
        return backgroundRGB != null;
    }

    /**
     * Get the foreground color as a hex string (e.g., "#FF5733").
     *
     * @return the foreground color in hex format, or null if not detected
     */
    public String getForegroundHex() {
        return foregroundRGB != null ? rgbToHex(foregroundRGB) : null;
    }

    /**
     * Get the background color as a hex string (e.g., "#1E1E1E").
     *
     * @return the background color in hex format, or null if not detected
     */
    public String getBackgroundHex() {
        return backgroundRGB != null ? rgbToHex(backgroundRGB) : null;
    }

    /**
     * Get the foreground color as a hex integer (e.g., 0xFF5733).
     *
     * @return the foreground color as an integer, or -1 if not detected
     */
    public int getForegroundHexValue() {
        return foregroundRGB != null ? rgbToHexValue(foregroundRGB) : -1;
    }

    /**
     * Get the background color as a hex integer (e.g., 0x1E1E1E).
     *
     * @return the background color as an integer, or -1 if not detected
     */
    public int getBackgroundHexValue() {
        return backgroundRGB != null ? rgbToHexValue(backgroundRGB) : -1;
    }

    /**
     * Convert RGB array to hex string format.
     *
     * @param rgb the RGB array [r, g, b] with values 0-255
     * @return hex string in format "#RRGGBB"
     */
    private static String rgbToHex(int[] rgb) {
        return String.format("#%02X%02X%02X", rgb[0], rgb[1], rgb[2]);
    }

    /**
     * Convert RGB array to hex integer value.
     *
     * @param rgb the RGB array [r, g, b] with values 0-255
     * @return integer value representing the color (0xRRGGBB)
     */
    private static int rgbToHexValue(int[] rgb) {
        return (rgb[0] << 16) | (rgb[1] << 8) | rgb[2];
    }

    /**
     * Parse a hex color string to RGB array.
     *
     * @param hex the hex color string (with or without leading #)
     * @return RGB array [r, g, b] with values 0-255, or null if invalid
     */
    public static int[] hexToRgb(String hex) {
        if (hex == null || hex.isEmpty()) {
            return null;
        }
        // Remove leading # if present
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        // Handle shorthand format (e.g., "FFF" -> "FFFFFF")
        if (hex.length() == 3) {
            hex = "" + hex.charAt(0) + hex.charAt(0)
                    + hex.charAt(1) + hex.charAt(1)
                    + hex.charAt(2) + hex.charAt(2);
        }
        if (hex.length() != 6) {
            return null;
        }
        try {
            int r = Integer.parseInt(hex.substring(0, 2), 16);
            int g = Integer.parseInt(hex.substring(2, 4), 16);
            int b = Integer.parseInt(hex.substring(4, 6), 16);
            return new int[] { r, g, b };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse a hex color integer to RGB array.
     *
     * @param hexValue the hex color value (0xRRGGBB)
     * @return RGB array [r, g, b] with values 0-255
     */
    public static int[] hexToRgb(int hexValue) {
        int r = (hexValue >> 16) & 0xFF;
        int g = (hexValue >> 8) & 0xFF;
        int b = hexValue & 0xFF;
        return new int[] { r, g, b };
    }

    /**
     * Check if the terminal supports ANSI colors at all.
     *
     * @return true if at least basic ANSI colors are supported
     */
    public boolean supportsAnsiColors() {
        return colorDepth.supportsColor();
    }

    /**
     * Get the suggested foreground ANSI color code for text based on the theme.
     * <p>
     * If a custom foreground code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a color that contrasts well with the detected background:
     * <ul>
     * <li>For dark themes: suggests white/light gray (37)</li>
     * <li>For light themes: suggests black/dark gray (30)</li>
     * </ul>
     *
     * @return ANSI color code for suggested foreground
     */
    public int getSuggestedForegroundCode() {
        if (foregroundCodeOverride != null) {
            return foregroundCodeOverride;
        }
        return theme.isLight() ? 30 : 37; // black for light bg, white for dark bg
    }

    /**
     * Get the suggested "error" foreground ANSI color code.
     * <p>
     * If a custom error code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a red variant that contrasts well with the detected background:
     * <ul>
     * <li>For dark themes: bright red (91)</li>
     * <li>For light themes: dark red (31)</li>
     * </ul>
     *
     * @return ANSI color code for suggested error foreground
     */
    public int getSuggestedErrorCode() {
        if (errorCodeOverride != null) {
            return errorCodeOverride;
        }
        return theme.isLight() ? 31 : 91; // dark red for light bg, bright red for dark bg
    }

    /**
     * Get the suggested "success" foreground ANSI color code.
     * <p>
     * If a custom success code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a green variant that contrasts well with the detected background.
     *
     * @return ANSI color code for suggested success foreground
     */
    public int getSuggestedSuccessCode() {
        if (successCodeOverride != null) {
            return successCodeOverride;
        }
        return theme.isLight() ? 32 : 92; // dark green for light bg, bright green for dark bg
    }

    /**
     * Get the suggested "warning" foreground ANSI color code.
     * <p>
     * If a custom warning code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a yellow variant that contrasts well with the detected background.
     *
     * @return ANSI color code for suggested warning foreground
     */
    public int getSuggestedWarningCode() {
        if (warningCodeOverride != null) {
            return warningCodeOverride;
        }
        return theme.isLight() ? 33 : 93; // dark yellow for light bg, bright yellow for dark bg
    }

    /**
     * Get the suggested "info" foreground ANSI color code.
     * <p>
     * If a custom info code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a green variant that contrasts well with the detected background.
     * Aligns with JBoss LogManager's color spectrum where INFO maps to yellow-green.
     *
     * @return ANSI color code for suggested info foreground
     */
    public int getSuggestedInfoCode() {
        if (infoCodeOverride != null) {
            return infoCodeOverride;
        }
        return theme.isLight() ? 32 : 92; // dark green for light bg, bright green for dark bg
    }

    /**
     * Get the suggested "debug" foreground ANSI color code.
     * <p>
     * If a custom debug code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a cyan variant for debug-level log messages.
     * Aligns with JBoss LogManager's color spectrum where DEBUG maps to teal/cyan.
     * <ul>
     * <li>For dark themes: bright cyan (96)</li>
     * <li>For light themes: dark cyan (36)</li>
     * </ul>
     *
     * @return ANSI color code for suggested debug foreground
     */
    public int getSuggestedDebugCode() {
        if (debugCodeOverride != null) {
            return debugCodeOverride;
        }
        return theme.isLight() ? 36 : 96; // dark cyan for light bg, bright cyan for dark bg
    }

    /**
     * Get the suggested "trace" foreground ANSI color code.
     * <p>
     * If a custom trace code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a dim color for trace-level log messages (the least
     * prominent log level):
     * <ul>
     * <li>For both themes: bright black/gray (90) - very subdued</li>
     * </ul>
     *
     * @return ANSI color code for suggested trace foreground
     */
    public int getSuggestedTraceCode() {
        if (traceCodeOverride != null) {
            return traceCodeOverride;
        }
        // Use standard ANSI gray (90) for both themes - consistent and widely supported
        return 90;
    }

    /**
     * Get the suggested "timestamp" foreground ANSI color code.
     * <p>
     * If a custom timestamp code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a neutral gray that is subdued compared to the main message,
     * commonly used for timestamps in log output. Uses 256-color palette grays
     * to closely match JBoss LogManager's DATE color.
     * <ul>
     * <li>For dark themes: 252 (light gray, ~rgb 208,208,208)</li>
     * <li>For light themes: 240 (dark gray, ~rgb 88,88,88)</li>
     * </ul>
     *
     * @return ANSI color code for suggested timestamp foreground
     */
    public int getSuggestedTimestampCode() {
        if (timestampCodeOverride != null) {
            return timestampCodeOverride;
        }
        return theme.isLight() ? 240 : 252; // dark gray for light bg, light gray for dark bg
    }

    /**
     * Get the suggested "message" foreground ANSI color code.
     * <p>
     * If a custom message code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a magenta variant that stands out for highlighted messages
     * in log output.
     * <ul>
     * <li>For dark themes: bright magenta (95)</li>
     * <li>For light themes: dark magenta (35)</li>
     * </ul>
     *
     * @return ANSI color code for suggested message foreground
     */
    public int getSuggestedMessageCode() {
        if (messageCodeOverride != null) {
            return messageCodeOverride;
        }
        return theme.isLight() ? 30 : 37; // black for light bg, white for dark bg
    }

    /**
     * Get the suggested "category" (package/class name) foreground ANSI color code.
     * <p>
     * If a custom category code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a blue variant suitable for logger/category names in log output.
     * Aligns with JBoss LogManager's ColorPatternFormatter CATEGORY item
     * (HSL 220°, 0.9, 0.8 — a blue hue).
     * <ul>
     * <li>For dark themes: bright blue (94)</li>
     * <li>For light themes: dark blue (34)</li>
     * </ul>
     *
     * @return ANSI color code for suggested category foreground
     */
    public int getSuggestedCategoryCode() {
        if (categoryCodeOverride != null) {
            return categoryCodeOverride;
        }
        return theme.isLight() ? 34 : 94; // dark blue for light bg, bright blue for dark bg
    }

    /**
     * Get the suggested "thread name" foreground ANSI color code.
     * <p>
     * If a custom thread name code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a green variant suitable for thread names in log output.
     * Aligns with JBoss LogManager's ColorPatternFormatter THREAD_NAME item
     * (HSL 120°, 0.429, 0.8 — a muted green).
     * <ul>
     * <li>For dark themes: bright green (92)</li>
     * <li>For light themes: dark green (32)</li>
     * </ul>
     *
     * @return ANSI color code for suggested thread name foreground
     */
    public int getSuggestedThreadNameCode() {
        if (threadNameCodeOverride != null) {
            return threadNameCodeOverride;
        }
        return theme.isLight() ? 32 : 92; // dark green for light bg, bright green for dark bg
    }

    /**
     * Get the suggested "fatal" foreground ANSI color code.
     * <p>
     * If a custom fatal code was set via the {@link Builder}, it is returned.
     * Otherwise, returns a red variant for fatal-level log messages — the most
     * severe log level. Uses the same red base as error, but typically
     * distinguished by bold styling in usage.
     * <ul>
     * <li>For dark themes: bright red (91)</li>
     * <li>For light themes: dark red (31)</li>
     * </ul>
     *
     * @return ANSI color code for suggested fatal foreground
     */
    public int getSuggestedFatalCode() {
        if (fatalCodeOverride != null) {
            return fatalCodeOverride;
        }
        return theme.isLight() ? 31 : 91; // dark red for light bg, bright red for dark bg
    }

    /**
     * Create a default TerminalColorCapability assuming 8 colors and dark theme.
     *
     * @return a default TerminalColorCapability
     */
    public static TerminalColorCapability defaultCapability() {
        return new TerminalColorCapability(ColorDepth.COLORS_8, TerminalTheme.DARK);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TerminalColorCapability{");
        sb.append("colorDepth=").append(colorDepth);
        sb.append(", theme=").append(theme);
        if (foregroundRGB != null) {
            sb.append(", foreground=").append(getForegroundHex());
        }
        if (backgroundRGB != null) {
            sb.append(", background=").append(getBackgroundHex());
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Detect color depth from environment variables.
     * This is a fast, non-blocking operation.
     * <p>
     * This method delegates to {@link TerminalEnvironment} which provides
     * centralized, cached terminal environment detection.
     *
     * @return the detected color depth
     */
    public static ColorDepth detectColorDepthFromEnvironment() {
        return TerminalEnvironment.getInstance().getDefaultColorDepth();
    }

    /**
     * Detect terminal theme from environment variables.
     * This is a fast, non-blocking operation.
     *
     * @return the detected theme, or UNKNOWN if not detectable
     */
    public static TerminalTheme detectThemeFromEnvironment() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // Check COLORFGBG environment variable
        String colorfgbg = env.getColorFgBg();
        if (colorfgbg != null) {
            // Format is typically "fg;bg" where values < 7 are dark, >= 7 are light
            String[] parts = colorfgbg.split(";");
            if (parts.length >= 2) {
                try {
                    int bg = Integer.parseInt(parts[parts.length - 1]);
                    // Colors 0-6 are dark, 8 is bright black (also dark)
                    // Color 7 (white) and 9-15 (bright colors) are light
                    boolean isDark = bg < 7 || bg == 8;
                    return isDark ? TerminalTheme.DARK : TerminalTheme.LIGHT;
                } catch (NumberFormatException e) {
                    // Ignore, continue with other methods
                }
            }
        }

        // Check macOS dark mode
        if (env.isMacOsDarkMode()) {
            return TerminalTheme.DARK;
        }

        // Check if APPLE_INTERFACE_STYLE is set but not "Dark"
        if (env.getAppleInterfaceStyle() != null) {
            return TerminalTheme.LIGHT;
        }

        return TerminalTheme.UNKNOWN;
    }

    /**
     * Create a TerminalColorCapability by detecting from environment variables only.
     * This is a fast, non-blocking operation that does not query the terminal.
     *
     * @return detected color capabilities based on environment
     */
    public static TerminalColorCapability detectFromEnvironment() {
        return new TerminalColorCapability(
                detectColorDepthFromEnvironment(),
                detectThemeFromEnvironment());
    }

    /**
     * Create a new Builder for constructing a customized TerminalColorCapability.
     * <p>
     * Example usage:
     *
     * <pre>
     * TerminalColorCapability cap = TerminalColorCapability.builder()
     *         .colorDepth(ColorDepth.COLORS_256)
     *         .theme(TerminalTheme.DARK)
     *         .errorCode(196) // Custom 256-color red
     *         .successCode(46) // Custom 256-color green
     *         .timestampCode(244) // Custom gray for timestamps
     *         .build();
     * </pre>
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a new Builder initialized with values from an existing capability.
     * <p>
     * This allows you to start with detected values and customize specific colors:
     *
     * <pre>
     * TerminalColorCapability detected = TerminalColorDetector.detect(connection);
     * TerminalColorCapability custom = TerminalColorCapability.builder(detected)
     *         .errorCode(196) // Override just the error color
     *         .build();
     * </pre>
     *
     * @param base the existing capability to copy values from
     * @return a new Builder initialized with the base capability's values
     */
    public static Builder builder(TerminalColorCapability base) {
        return new Builder(base);
    }

    /**
     * Builder for creating customized TerminalColorCapability instances.
     * <p>
     * The builder allows overriding the suggested color codes for semantic
     * color types (error, success, warning, info, timestamp, message).
     * Any color not explicitly set will use the theme-based default.
     */
    public static class Builder {
        private ColorDepth colorDepth;
        private TerminalTheme theme;
        private int[] foregroundRGB;
        private int[] backgroundRGB;
        private int[] cursorRGB;
        private java.util.Map<Integer, int[]> paletteColors;
        private Integer foregroundCodeOverride;
        private Integer errorCodeOverride;
        private Integer successCodeOverride;
        private Integer warningCodeOverride;
        private Integer infoCodeOverride;
        private Integer debugCodeOverride;
        private Integer traceCodeOverride;
        private Integer timestampCodeOverride;
        private Integer messageCodeOverride;
        private Integer categoryCodeOverride;
        private Integer threadNameCodeOverride;
        private Integer fatalCodeOverride;

        /**
         * Create a new empty Builder.
         */
        public Builder() {
        }

        /**
         * Create a Builder initialized with values from an existing capability.
         *
         * @param base the capability to copy values from
         */
        public Builder(TerminalColorCapability base) {
            if (base != null) {
                this.colorDepth = base.colorDepth;
                this.theme = base.theme;
                this.foregroundRGB = base.foregroundRGB;
                this.backgroundRGB = base.backgroundRGB;
                this.cursorRGB = base.cursorRGB;
                this.paletteColors = base.paletteColors != null ? new java.util.LinkedHashMap<>(base.paletteColors) : null;
                this.foregroundCodeOverride = base.foregroundCodeOverride;
                this.errorCodeOverride = base.errorCodeOverride;
                this.successCodeOverride = base.successCodeOverride;
                this.warningCodeOverride = base.warningCodeOverride;
                this.infoCodeOverride = base.infoCodeOverride;
                this.debugCodeOverride = base.debugCodeOverride;
                this.traceCodeOverride = base.traceCodeOverride;
                this.timestampCodeOverride = base.timestampCodeOverride;
                this.messageCodeOverride = base.messageCodeOverride;
                this.categoryCodeOverride = base.categoryCodeOverride;
                this.threadNameCodeOverride = base.threadNameCodeOverride;
                this.fatalCodeOverride = base.fatalCodeOverride;
            }
        }

        /**
         * Set the color depth capability.
         *
         * @param colorDepth the color depth
         * @return this builder for method chaining
         */
        public Builder colorDepth(ColorDepth colorDepth) {
            this.colorDepth = colorDepth;
            return this;
        }

        /**
         * Set the terminal theme.
         *
         * @param theme the terminal theme
         * @return this builder for method chaining
         */
        public Builder theme(TerminalTheme theme) {
            this.theme = theme;
            return this;
        }

        /**
         * Set the foreground RGB color.
         *
         * @param rgb the RGB array [r, g, b] with values 0-255
         * @return this builder for method chaining
         */
        public Builder foregroundRGB(int[] rgb) {
            this.foregroundRGB = rgb;
            return this;
        }

        /**
         * Set the background RGB color.
         *
         * @param rgb the RGB array [r, g, b] with values 0-255
         * @return this builder for method chaining
         */
        public Builder backgroundRGB(int[] rgb) {
            this.backgroundRGB = rgb;
            return this;
        }

        /**
         * Set the cursor RGB color.
         *
         * @param rgb the RGB array [r, g, b] with values 0-255
         * @return this builder for method chaining
         */
        public Builder cursorRGB(int[] rgb) {
            this.cursorRGB = rgb;
            return this;
        }

        /**
         * Set the palette colors.
         *
         * @param colors map of palette index to RGB array
         * @return this builder for method chaining
         */
        public Builder paletteColors(java.util.Map<Integer, int[]> colors) {
            this.paletteColors = colors != null ? new java.util.LinkedHashMap<>(colors) : null;
            return this;
        }

        /**
         * Add a single palette color.
         *
         * @param index the palette index (0-255)
         * @param rgb the RGB array [r, g, b] with values 0-255
         * @return this builder for method chaining
         */
        public Builder paletteColor(int index, int[] rgb) {
            if (this.paletteColors == null) {
                this.paletteColors = new java.util.LinkedHashMap<>();
            }
            this.paletteColors.put(index, rgb);
            return this;
        }

        /**
         * Override the suggested foreground color code.
         *
         * @param code the ANSI color code to use for normal foreground text
         * @return this builder for method chaining
         */
        public Builder foregroundCode(int code) {
            this.foregroundCodeOverride = code;
            return this;
        }

        /**
         * Override the suggested error color code.
         * <p>
         * Common values:
         * <ul>
         * <li>31: dark red (good for light backgrounds)</li>
         * <li>91: bright red (good for dark backgrounds)</li>
         * <li>196: 256-color bright red</li>
         * </ul>
         *
         * @param code the ANSI color code to use for error messages
         * @return this builder for method chaining
         */
        public Builder errorCode(int code) {
            this.errorCodeOverride = code;
            return this;
        }

        /**
         * Override the suggested success color code.
         * <p>
         * Common values:
         * <ul>
         * <li>32: dark green (good for light backgrounds)</li>
         * <li>92: bright green (good for dark backgrounds)</li>
         * <li>46: 256-color bright green</li>
         * </ul>
         *
         * @param code the ANSI color code to use for success messages
         * @return this builder for method chaining
         */
        public Builder successCode(int code) {
            this.successCodeOverride = code;
            return this;
        }

        /**
         * Override the suggested warning color code.
         * <p>
         * Common values:
         * <ul>
         * <li>33: dark yellow/orange (good for light backgrounds)</li>
         * <li>93: bright yellow (good for dark backgrounds)</li>
         * <li>208: 256-color orange</li>
         * </ul>
         *
         * @param code the ANSI color code to use for warning messages
         * @return this builder for method chaining
         */
        public Builder warningCode(int code) {
            this.warningCodeOverride = code;
            return this;
        }

        /**
         * Override the suggested info color code.
         * <p>
         * Common values:
         * <ul>
         * <li>34: dark blue (good for light backgrounds)</li>
         * <li>94: bright blue (good for dark backgrounds)</li>
         * <li>75: 256-color light blue</li>
         * </ul>
         *
         * @param code the ANSI color code to use for info messages
         * @return this builder for method chaining
         */
        public Builder infoCode(int code) {
            this.infoCodeOverride = code;
            return this;
        }

        /**
         * Override the suggested debug color code.
         * <p>
         * Common values:
         * <ul>
         * <li>37: white (good for dark backgrounds)</li>
         * <li>90: bright black/gray (subdued, good for both)</li>
         * <li>244: 256-color gray</li>
         * </ul>
         *
         * @param code the ANSI color code to use for debug messages
         * @return this builder for method chaining
         */
        public Builder debugCode(int code) {
            this.debugCodeOverride = code;
            return this;
        }

        /**
         * Override the suggested trace color code.
         * <p>
         * Common values:
         * <ul>
         * <li>90: bright black/gray (default, very subdued)</li>
         * <li>240: 256-color dark gray</li>
         * </ul>
         *
         * @param code the ANSI color code to use for trace messages
         * @return this builder for method chaining
         */
        public Builder traceCode(int code) {
            this.traceCodeOverride = code;
            return this;
        }

        /**
         * Override the suggested timestamp color code.
         * <p>
         * Common values:
         * <ul>
         * <li>36: dark cyan (good for light backgrounds)</li>
         * <li>96: bright cyan (good for dark backgrounds)</li>
         * <li>244: 256-color gray (subdued)</li>
         * </ul>
         *
         * @param code the ANSI color code to use for timestamps
         * @return this builder for method chaining
         */
        public Builder timestampCode(int code) {
            this.timestampCodeOverride = code;
            return this;
        }

        /**
         * Override the suggested message color code.
         * <p>
         * Common values:
         * <ul>
         * <li>35: dark magenta (good for light backgrounds)</li>
         * <li>95: bright magenta (good for dark backgrounds)</li>
         * <li>255: 256-color white</li>
         * </ul>
         *
         * @param code the ANSI color code to use for highlighted messages
         * @return this builder for method chaining
         */
        public Builder messageCode(int code) {
            this.messageCodeOverride = code;
            return this;
        }

        /**
         * Override the suggested category (package/class name) color code.
         * <p>
         * Common values:
         * <ul>
         * <li>34: dark blue (good for light backgrounds)</li>
         * <li>94: bright blue (good for dark backgrounds)</li>
         * <li>75: 256-color light blue</li>
         * </ul>
         *
         * @param code the ANSI color code to use for category/logger names
         * @return this builder for method chaining
         */
        public Builder categoryCode(int code) {
            this.categoryCodeOverride = code;
            return this;
        }

        /**
         * Override the suggested thread name color code.
         * <p>
         * Common values:
         * <ul>
         * <li>32: dark green (good for light backgrounds)</li>
         * <li>92: bright green (good for dark backgrounds)</li>
         * <li>78: 256-color muted green</li>
         * </ul>
         *
         * @param code the ANSI color code to use for thread names
         * @return this builder for method chaining
         */
        public Builder threadNameCode(int code) {
            this.threadNameCodeOverride = code;
            return this;
        }

        /**
         * Override the suggested fatal color code.
         * <p>
         * Common values:
         * <ul>
         * <li>31: dark red (good for light backgrounds)</li>
         * <li>91: bright red (good for dark backgrounds)</li>
         * <li>196: 256-color bright red</li>
         * </ul>
         *
         * @param code the ANSI color code to use for fatal messages
         * @return this builder for method chaining
         */
        public Builder fatalCode(int code) {
            this.fatalCodeOverride = code;
            return this;
        }

        /**
         * Build the TerminalColorCapability with the configured values.
         *
         * @return a new TerminalColorCapability instance
         */
        public TerminalColorCapability build() {
            return new TerminalColorCapability(this);
        }
    }
}
