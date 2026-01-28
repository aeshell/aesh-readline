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
package org.aesh.terminal.formatting;

import java.io.PrintStream;

import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.ColorDepth;
import org.aesh.terminal.utils.TerminalColorCapability;
import org.aesh.terminal.utils.TerminalTheme;

/**
 * Represents a combination of foreground and background colors for terminal output.
 * <p>
 * Supports three color modes:
 * <ul>
 * <li>Basic 8/16 colors using {@link Color} enum</li>
 * <li>256 colors using palette indices (0-255)</li>
 * <li>True color (24-bit RGB) using RGB values</li>
 * </ul>
 * <p>
 * Also provides theme-aware factory methods that automatically select appropriate
 * colors based on detected terminal capabilities.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class TerminalColor {

    private Color textColor = Color.DEFAULT;
    private int intTextColor = -1;
    private Color backgroundColor = Color.DEFAULT;
    private int intBackgroundColor = -1;
    private int length = -1;
    private String cache;

    // True color (24-bit RGB) support
    private int[] textRGB = null;
    private int[] backgroundRGB = null;

    private Color.Intensity intensity = Color.Intensity.NORMAL;

    public TerminalColor() {
    }

    public TerminalColor(Color text, Color background) {
        if (text != null)
            this.textColor = text;
        if (background != null)
            this.backgroundColor = background;
    }

    public TerminalColor(Color textColor, Color background, Color.Intensity intensity) {
        this(textColor, background);
        this.intensity = intensity;
    }

    /**
     * Create a TerminalColor using 256-color palette indices.
     * <p>
     * 0x00-0x07: standard colors (as in ESC [ 30..37 m)
     * 0x08-0x0f: high intensity colors (as in ESC [ 90..97 m)
     * 0x10-0xe7: 6*6*6=216 colors: 16 + 36*r + 6*g + b (0 le r,g,b le 5)
     * 0xe8-0xff: grayscale from black to white in 24 steps
     *
     * @param text foreground color index (0-255)
     * @param background background color index (0-255)
     */
    public TerminalColor(int text, int background) {
        this.intTextColor = text;
        this.intBackgroundColor = background;
    }

    /**
     * Create a TerminalColor with 256-color foreground and basic background.
     *
     * @param text foreground color index (0-255)
     * @param background background color
     */
    public TerminalColor(int text, Color background) {
        this.intTextColor = text;
        this.backgroundColor = background;
    }

    public TerminalColor(int text, Color background, Color.Intensity intensity) {
        this.intTextColor = text;
        this.backgroundColor = background;
        this.intensity = intensity;
    }

    public TerminalColor(Color text, int background) {
        this.textColor = text;
        this.intBackgroundColor = background;
    }

    public TerminalColor(Color text, int background, Color.Intensity intensity) {
        this.textColor = text;
        this.intBackgroundColor = background;
        this.intensity = intensity;
    }

    /**
     * Private constructor for RGB colors.
     */
    private TerminalColor(int[] textRGB, int[] backgroundRGB, boolean rgb) {
        this.textRGB = textRGB;
        this.backgroundRGB = backgroundRGB;
    }

    // ==================== True Color (RGB) Support ====================

    /**
     * Create a TerminalColor with true color (24-bit RGB) foreground.
     *
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @return a new TerminalColor with RGB foreground
     */
    public static TerminalColor fromRGB(int r, int g, int b) {
        return new TerminalColor(new int[] { r, g, b }, null, true);
    }

    /**
     * Create a TerminalColor with true color (24-bit RGB) foreground and background.
     *
     * @param textR foreground red component (0-255)
     * @param textG foreground green component (0-255)
     * @param textB foreground blue component (0-255)
     * @param bgR background red component (0-255)
     * @param bgG background green component (0-255)
     * @param bgB background blue component (0-255)
     * @return a new TerminalColor with RGB foreground and background
     */
    public static TerminalColor fromRGB(int textR, int textG, int textB, int bgR, int bgG, int bgB) {
        return new TerminalColor(new int[] { textR, textG, textB }, new int[] { bgR, bgG, bgB }, true);
    }

    /**
     * Create a TerminalColor with true color foreground from hex string.
     *
     * @param hex hex color string (e.g., "#FF5733" or "FF5733")
     * @return a new TerminalColor with RGB foreground, or default if invalid
     */
    public static TerminalColor fromHex(String hex) {
        int[] rgb = parseHex(hex);
        if (rgb != null) {
            return new TerminalColor(rgb, null, true);
        }
        return new TerminalColor();
    }

    /**
     * Create a TerminalColor with true color foreground and background from hex strings.
     *
     * @param textHex foreground hex color string
     * @param bgHex background hex color string
     * @return a new TerminalColor with RGB colors, or default if invalid
     */
    public static TerminalColor fromHex(String textHex, String bgHex) {
        int[] textRgb = parseHex(textHex);
        int[] bgRgb = parseHex(bgHex);
        if (textRgb != null || bgRgb != null) {
            return new TerminalColor(textRgb, bgRgb, true);
        }
        return new TerminalColor();
    }

    /**
     * Check if this color uses true color (24-bit RGB) mode.
     *
     * @return true if using RGB colors
     */
    public boolean isTrueColor() {
        return textRGB != null || backgroundRGB != null;
    }

    /**
     * Get the foreground RGB values if using true color.
     *
     * @return RGB array [r, g, b] or null if not using RGB foreground
     */
    public int[] getTextRGB() {
        return textRGB != null ? textRGB.clone() : null;
    }

    /**
     * Get the background RGB values if using true color.
     *
     * @return RGB array [r, g, b] or null if not using RGB background
     */
    public int[] getBackgroundRGB() {
        return backgroundRGB != null ? backgroundRGB.clone() : null;
    }

    // ==================== Theme-Aware Factory Methods ====================

    /**
     * Create a TerminalColor appropriate for error messages based on terminal theme.
     * <p>
     * For dark themes, uses bright red. For light themes, uses dark red.
     *
     * @param capability the detected terminal color capability
     * @return a TerminalColor suitable for error messages
     */
    public static TerminalColor forError(TerminalColorCapability capability) {
        if (capability == null || capability.getTheme() == TerminalTheme.UNKNOWN) {
            return new TerminalColor(Color.RED, Color.DEFAULT, Color.Intensity.BRIGHT);
        }
        return capability.getTheme().isLight()
                ? new TerminalColor(Color.RED, Color.DEFAULT, Color.Intensity.NORMAL)
                : new TerminalColor(Color.RED, Color.DEFAULT, Color.Intensity.BRIGHT);
    }

    /**
     * Create a TerminalColor appropriate for success messages based on terminal theme.
     * <p>
     * For dark themes, uses bright green. For light themes, uses dark green.
     *
     * @param capability the detected terminal color capability
     * @return a TerminalColor suitable for success messages
     */
    public static TerminalColor forSuccess(TerminalColorCapability capability) {
        if (capability == null || capability.getTheme() == TerminalTheme.UNKNOWN) {
            return new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT);
        }
        return capability.getTheme().isLight()
                ? new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.NORMAL)
                : new TerminalColor(Color.GREEN, Color.DEFAULT, Color.Intensity.BRIGHT);
    }

    /**
     * Create a TerminalColor appropriate for warning messages based on terminal theme.
     * <p>
     * For dark themes, uses bright yellow. For light themes, uses dark yellow/orange.
     *
     * @param capability the detected terminal color capability
     * @return a TerminalColor suitable for warning messages
     */
    public static TerminalColor forWarning(TerminalColorCapability capability) {
        if (capability == null || capability.getTheme() == TerminalTheme.UNKNOWN) {
            return new TerminalColor(Color.YELLOW, Color.DEFAULT, Color.Intensity.BRIGHT);
        }
        return capability.getTheme().isLight()
                ? new TerminalColor(Color.YELLOW, Color.DEFAULT, Color.Intensity.NORMAL)
                : new TerminalColor(Color.YELLOW, Color.DEFAULT, Color.Intensity.BRIGHT);
    }

    /**
     * Create a TerminalColor appropriate for info messages based on terminal theme.
     * <p>
     * For dark themes, uses bright blue/cyan. For light themes, uses dark blue.
     *
     * @param capability the detected terminal color capability
     * @return a TerminalColor suitable for info messages
     */
    public static TerminalColor forInfo(TerminalColorCapability capability) {
        if (capability == null || capability.getTheme() == TerminalTheme.UNKNOWN) {
            return new TerminalColor(Color.CYAN, Color.DEFAULT, Color.Intensity.BRIGHT);
        }
        return capability.getTheme().isLight()
                ? new TerminalColor(Color.BLUE, Color.DEFAULT, Color.Intensity.NORMAL)
                : new TerminalColor(Color.CYAN, Color.DEFAULT, Color.Intensity.BRIGHT);
    }

    /**
     * Create a TerminalColor appropriate for highlighted/emphasized text.
     * <p>
     * For dark themes, uses bright white. For light themes, uses black.
     *
     * @param capability the detected terminal color capability
     * @return a TerminalColor suitable for highlighted text
     */
    public static TerminalColor forHighlight(TerminalColorCapability capability) {
        if (capability == null || capability.getTheme() == TerminalTheme.UNKNOWN) {
            return new TerminalColor(Color.WHITE, Color.DEFAULT, Color.Intensity.BRIGHT);
        }
        return capability.getTheme().isLight()
                ? new TerminalColor(Color.BLACK, Color.DEFAULT, Color.Intensity.NORMAL)
                : new TerminalColor(Color.WHITE, Color.DEFAULT, Color.Intensity.BRIGHT);
    }

    /**
     * Create a TerminalColor appropriate for muted/secondary text.
     * <p>
     * Uses gray tones that are visible but not prominent.
     *
     * @param capability the detected terminal color capability
     * @return a TerminalColor suitable for muted text
     */
    public static TerminalColor forMuted(TerminalColorCapability capability) {
        if (capability == null || capability.getTheme() == TerminalTheme.UNKNOWN) {
            return new TerminalColor(Color.WHITE, Color.DEFAULT, Color.Intensity.NORMAL);
        }
        return capability.getTheme().isLight()
                ? new TerminalColor(Color.BLACK, Color.DEFAULT, Color.Intensity.NORMAL)
                : new TerminalColor(Color.WHITE, Color.DEFAULT, Color.Intensity.NORMAL);
    }

    // ==================== Color Depth Aware Methods ====================

    /**
     * Create the best representation of this color for the given terminal capability.
     * <p>
     * If the terminal doesn't support true color but this TerminalColor uses RGB,
     * it will be downgraded to the nearest 256-color or 16-color equivalent.
     *
     * @param capability the terminal's color capability
     * @return a TerminalColor compatible with the terminal's capabilities
     */
    public TerminalColor forCapability(TerminalColorCapability capability) {
        if (capability == null) {
            return this;
        }

        ColorDepth depth = capability.getColorDepth();

        // If terminal supports true color or we're not using RGB, return as-is
        if (depth.supportsTrueColor() || !isTrueColor()) {
            return this;
        }

        // Downgrade RGB to 256-color or 16-color
        if (depth.supports256Colors()) {
            return toColor256();
        } else {
            return toColor16();
        }
    }

    /**
     * Convert RGB color to nearest 256-color palette index.
     *
     * @return a new TerminalColor using 256-color mode
     */
    public TerminalColor toColor256() {
        if (!isTrueColor()) {
            return this;
        }

        int textIdx = textRGB != null ? rgbTo256(textRGB[0], textRGB[1], textRGB[2]) : -1;
        int bgIdx = backgroundRGB != null ? rgbTo256(backgroundRGB[0], backgroundRGB[1], backgroundRGB[2]) : -1;

        if (textIdx >= 0 && bgIdx >= 0) {
            return new TerminalColor(textIdx, bgIdx);
        } else if (textIdx >= 0) {
            return new TerminalColor(textIdx, backgroundColor);
        } else if (bgIdx >= 0) {
            return new TerminalColor(textColor, bgIdx);
        }
        return new TerminalColor();
    }

    /**
     * Convert RGB color to nearest 16-color.
     *
     * @return a new TerminalColor using basic 16 colors
     */
    public TerminalColor toColor16() {
        if (!isTrueColor()) {
            return this;
        }

        Color textCol = textRGB != null ? rgbToBasicColor(textRGB[0], textRGB[1], textRGB[2]) : textColor;
        Color bgCol = backgroundRGB != null ? rgbToBasicColor(backgroundRGB[0], backgroundRGB[1], backgroundRGB[2])
                : backgroundColor;
        Color.Intensity inten = textRGB != null ? rgbToIntensity(textRGB[0], textRGB[1], textRGB[2]) : intensity;

        return new TerminalColor(textCol, bgCol, inten);
    }

    // ==================== Existing Methods ====================

    public boolean isFormatted() {
        return !(textColor == Color.DEFAULT && backgroundColor == Color.DEFAULT
                && intensity == Color.Intensity.NORMAL && !isTrueColor()
                && intTextColor == -1 && intBackgroundColor == -1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TerminalColor))
            return false;

        TerminalColor that = (TerminalColor) o;

        if (intBackgroundColor != that.intBackgroundColor ||
                intTextColor != that.intTextColor ||
                backgroundColor != that.backgroundColor ||
                intensity != that.intensity ||
                textColor != that.textColor) {
            return false;
        }

        // Check RGB equality
        if (!java.util.Arrays.equals(textRGB, that.textRGB)) {
            return false;
        }
        return java.util.Arrays.equals(backgroundRGB, that.backgroundRGB);
    }

    @Override
    public int hashCode() {
        int result = textColor.hashCode();
        result = 31 * result + intTextColor;
        result = 31 * result + backgroundColor.hashCode();
        result = 31 * result + intBackgroundColor;
        result = 31 * result + intensity.hashCode();
        result = 31 * result + java.util.Arrays.hashCode(textRGB);
        result = 31 * result + java.util.Arrays.hashCode(backgroundRGB);
        return result;
    }

    public String fullString() {
        return ANSI.START + toString() + "m";
    }

    @Override
    public String toString() {
        if (cache != null)
            return cache;
        StringBuilder builder = new StringBuilder();

        // Foreground color
        if (textRGB != null) {
            // True color foreground: ESC[38;2;r;g;bm
            builder.append(38).append(';').append(2).append(';')
                    .append(textRGB[0]).append(';').append(textRGB[1]).append(';').append(textRGB[2]);
        } else if (intTextColor > -1) {
            // 256-color foreground: ESC[38;5;nm
            builder.append(38).append(';').append(5).append(';').append(intTextColor);
        } else {
            // Basic color foreground
            builder.append(intensity.getValue(Color.Type.FOREGROUND)).append(textColor.getValue());
        }

        builder.append(';');

        // Background color
        if (backgroundRGB != null) {
            // True color background: ESC[48;2;r;g;bm
            builder.append(48).append(';').append(2).append(';')
                    .append(backgroundRGB[0]).append(';').append(backgroundRGB[1]).append(';').append(backgroundRGB[2]);
        } else if (intBackgroundColor > -1) {
            // 256-color background: ESC[48;5;nm
            builder.append(48).append(';').append(5).append(';').append(intBackgroundColor);
        } else {
            // Basic color background
            builder.append(intensity.getValue(Color.Type.BACKGROUND)).append(backgroundColor.getValue());
        }

        cache = builder.toString();
        length = cache.length();

        return cache;
    }

    public int getLength() {
        if (length < 0)
            toString();
        return length;
    }

    public void write(PrintStream out) {
        out.print(toString());
    }

    public String toString(TerminalColor prev) {
        if (this.equals(prev))
            return "";
        else {
            String txt = textString(prev);
            String bg = backgroundString(prev);
            if (txt.length() > 0 && bg.length() > 0)
                return txt + ';' + bg;
            else
                return txt + bg;
        }
    }

    private String backgroundString(TerminalColor prev) {
        StringBuilder builder = new StringBuilder();

        // Check if background changed
        boolean bgChanged = false;
        if (backgroundRGB != null) {
            bgChanged = !java.util.Arrays.equals(backgroundRGB, prev.backgroundRGB);
        } else if (prev.backgroundRGB != null) {
            bgChanged = true;
        } else if (intBackgroundColor != prev.intBackgroundColor) {
            bgChanged = true;
        } else if (backgroundColor != prev.backgroundColor || intensity != prev.intensity) {
            bgChanged = intBackgroundColor <= -1 && prev.intBackgroundColor <= -1;
        }

        if (bgChanged) {
            if (backgroundRGB != null) {
                builder.append(48).append(';').append(2).append(';')
                        .append(backgroundRGB[0]).append(';').append(backgroundRGB[1]).append(';').append(backgroundRGB[2]);
            } else if (intBackgroundColor > -1) {
                builder.append(48).append(';').append(5).append(';').append(intBackgroundColor);
            } else {
                builder.append(intensity.getValue(Color.Type.BACKGROUND)).append(backgroundColor.getValue());
            }
        }

        return builder.toString();
    }

    private String textString(TerminalColor prev) {
        StringBuilder builder = new StringBuilder();

        // Check if foreground changed
        boolean fgChanged = false;
        if (textRGB != null) {
            fgChanged = !java.util.Arrays.equals(textRGB, prev.textRGB);
        } else if (prev.textRGB != null) {
            fgChanged = true;
        } else if (intTextColor != prev.intTextColor) {
            fgChanged = true;
        } else if (textColor != prev.textColor || intensity != prev.intensity) {
            fgChanged = intTextColor <= -1 && prev.intTextColor <= -1;
        }

        if (fgChanged) {
            if (textRGB != null) {
                builder.append(38).append(';').append(2).append(';')
                        .append(textRGB[0]).append(';').append(textRGB[1]).append(';').append(textRGB[2]);
            } else if (intTextColor > -1) {
                builder.append(38).append(';').append(5).append(';').append(intTextColor);
            } else {
                builder.append(intensity.getValue(Color.Type.FOREGROUND)).append(textColor.getValue());
            }
        }

        return builder.toString();
    }

    // ==================== Helper Methods ====================

    /**
     * Parse a hex color string to RGB array.
     */
    private static int[] parseHex(String hex) {
        if (hex == null || hex.isEmpty()) {
            return null;
        }
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
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
     * Convert RGB to nearest 256-color palette index.
     * Uses the 6x6x6 color cube (indices 16-231) or grayscale (232-255).
     */
    private static int rgbTo256(int r, int g, int b) {
        // Check if it's a grayscale color
        if (r == g && g == b) {
            if (r < 8) {
                return 16; // Black
            }
            if (r > 248) {
                return 231; // White
            }
            // Grayscale: 232-255 (24 shades)
            return 232 + (r - 8) / 10;
        }

        // Convert to 6x6x6 color cube (indices 16-231)
        int ri = (r < 48) ? 0 : (r < 115) ? 1 : (r - 35) / 40;
        int gi = (g < 48) ? 0 : (g < 115) ? 1 : (g - 35) / 40;
        int bi = (b < 48) ? 0 : (b < 115) ? 1 : (b - 35) / 40;

        return 16 + 36 * ri + 6 * gi + bi;
    }

    /**
     * Convert RGB to nearest basic ANSI color.
     */
    private static Color rgbToBasicColor(int r, int g, int b) {
        // Calculate luminance
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;

        // Check for near-grayscale
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int saturation = max - min;

        if (saturation < 50) {
            // Grayscale
            return luminance < 0.5 ? Color.BLACK : Color.WHITE;
        }

        // Find dominant color
        if (r >= g && r >= b) {
            // Red dominant
            if (g > b && g > 128) {
                return Color.YELLOW;
            }
            if (b > g && b > 128) {
                return Color.MAGENTA;
            }
            return Color.RED;
        } else if (g >= r && g >= b) {
            // Green dominant
            if (b > r && b > 128) {
                return Color.CYAN;
            }
            if (r > b && r > 128) {
                return Color.YELLOW;
            }
            return Color.GREEN;
        } else {
            // Blue dominant
            if (r > g && r > 128) {
                return Color.MAGENTA;
            }
            if (g > r && g > 128) {
                return Color.CYAN;
            }
            return Color.BLUE;
        }
    }

    /**
     * Determine intensity based on RGB brightness.
     */
    private static Color.Intensity rgbToIntensity(int r, int g, int b) {
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return luminance > 0.6 ? Color.Intensity.BRIGHT : Color.Intensity.NORMAL;
    }
}
