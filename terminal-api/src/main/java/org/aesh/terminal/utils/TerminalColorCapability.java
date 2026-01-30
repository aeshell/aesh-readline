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
        this.colorDepth = colorDepth != null ? colorDepth : ColorDepth.COLORS_8;
        this.theme = theme != null ? theme : TerminalTheme.UNKNOWN;
        this.foregroundRGB = foregroundRGB;
        this.backgroundRGB = backgroundRGB;
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
     * Check if the foreground color was detected.
     *
     * @return true if foreground RGB is available
     */
    public boolean hasForegroundColor() {
        return foregroundRGB != null;
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
     * Returns a color that contrasts well with the detected background:
     * <ul>
     * <li>For dark themes: suggests white/light gray (37)</li>
     * <li>For light themes: suggests black/dark gray (30)</li>
     * </ul>
     *
     * @return ANSI color code (30-37) for suggested foreground
     */
    public int getSuggestedForegroundCode() {
        return theme.isLight() ? 30 : 37; // black for light bg, white for dark bg
    }

    /**
     * Get the suggested "error" foreground ANSI color code.
     * <p>
     * Returns a red variant that contrasts well with the detected background:
     * <ul>
     * <li>For dark themes: bright red (91)</li>
     * <li>For light themes: dark red (31)</li>
     * </ul>
     *
     * @return ANSI color code for suggested error foreground
     */
    public int getSuggestedErrorCode() {
        return theme.isLight() ? 31 : 91; // dark red for light bg, bright red for dark bg
    }

    /**
     * Get the suggested "success" foreground ANSI color code.
     * <p>
     * Returns a green variant that contrasts well with the detected background.
     *
     * @return ANSI color code for suggested success foreground
     */
    public int getSuggestedSuccessCode() {
        return theme.isLight() ? 32 : 92; // dark green for light bg, bright green for dark bg
    }

    /**
     * Get the suggested "warning" foreground ANSI color code.
     * <p>
     * Returns a yellow variant that contrasts well with the detected background.
     *
     * @return ANSI color code for suggested warning foreground
     */
    public int getSuggestedWarningCode() {
        return theme.isLight() ? 33 : 93; // dark yellow for light bg, bright yellow for dark bg
    }

    /**
     * Get the suggested "info" foreground ANSI color code.
     * <p>
     * Returns a cyan/blue variant that contrasts well with the detected background.
     *
     * @return ANSI color code for suggested info foreground
     */
    public int getSuggestedInfoCode() {
        return theme.isLight() ? 34 : 94; // dark blue for light bg, bright blue for dark bg
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
     *
     * @return the detected color depth
     */
    public static ColorDepth detectColorDepthFromEnvironment() {
        // First check environment variables for true color support
        String colorterm = System.getenv("COLORTERM");
        if ("truecolor".equalsIgnoreCase(colorterm) || "24bit".equalsIgnoreCase(colorterm)) {
            return ColorDepth.TRUE_COLOR;
        }

        // Check for Windows Terminal (always supports true color)
        if (System.getenv("WT_SESSION") != null) {
            return ColorDepth.TRUE_COLOR;
        }

        // Check for ConEmu/Cmder (supports true color)
        if (System.getenv("ConEmuPID") != null || System.getenv("ConEmuANSI") != null) {
            return ColorDepth.TRUE_COLOR;
        }

        // Check TERM environment variable
        String term = System.getenv("TERM");
        if (term != null) {
            String termLower = term.toLowerCase();
            if (termLower.contains("truecolor") || termLower.contains("24bit")) {
                return ColorDepth.TRUE_COLOR;
            }
            if (termLower.contains("256color") || termLower.contains("256-color")) {
                return ColorDepth.COLORS_256;
            }
            if (termLower.contains("color") || termLower.contains("xterm") ||
                    termLower.contains("vt100") || termLower.contains("ansi")) {
                return ColorDepth.COLORS_8;
            }
        }

        // Check for Windows 10+ which supports true color via VT sequences
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("windows 10") || osName.contains("windows 11") ||
                osName.contains("windows server 2016") || osName.contains("windows server 2019") ||
                osName.contains("windows server 2022")) {
            // Modern Windows supports true color
            return ColorDepth.TRUE_COLOR;
        }

        return ColorDepth.COLORS_8;
    }

    /**
     * Detect terminal theme from environment variables.
     * This is a fast, non-blocking operation.
     *
     * @return the detected theme, or UNKNOWN if not detectable
     */
    public static TerminalTheme detectThemeFromEnvironment() {
        // Check common environment variables for theme hints
        String colorfgbg = System.getenv("COLORFGBG");
        if (colorfgbg != null) {
            // Format is typically "fg;bg" where values < 7 are dark, >= 7 are light
            String[] parts = colorfgbg.split(";");
            if (parts.length >= 2) {
                try {
                    int bg = Integer.parseInt(parts[parts.length - 1]);
                    // Colors 0, 1, 2, 3, 4, 5, 6 are typically dark
                    // Colors 7 and above are typically light
                    return bg < 7 ? TerminalTheme.DARK : TerminalTheme.LIGHT;
                } catch (NumberFormatException e) {
                    // Ignore, continue with other methods
                }
            }
        }

        // Check macOS dark mode via environment
        String appleInterfaceStyle = System.getenv("APPLE_INTERFACE_STYLE");
        if (appleInterfaceStyle != null) {
            return "Dark".equalsIgnoreCase(appleInterfaceStyle)
                    ? TerminalTheme.DARK
                    : TerminalTheme.LIGHT;
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
}
