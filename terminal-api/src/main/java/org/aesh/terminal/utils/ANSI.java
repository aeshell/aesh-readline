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

import java.util.Arrays;

import org.aesh.terminal.DeviceAttributes;
import org.aesh.terminal.tty.Point;

/**
 * Utility class to provide ANSI codes for different operations
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class ANSI {

    private static final int TAB = 4;

    /** ANSI escape sequence start. */
    public static final String START = "\u001B[";
    /** ANSI escape code for black foreground text. */
    public static final String BLACK_TEXT = "\u001B[0;30m";
    /** ANSI escape code for red foreground text. */
    public static final String RED_TEXT = "\u001B[0;31m";
    /** ANSI escape code for green foreground text. */
    public static final String GREEN_TEXT = "\u001B[0;32m";
    /** ANSI escape code for yellow foreground text. */
    public static final String YELLOW_TEXT = "\u001B[0;33m";
    /** ANSI escape code for blue foreground text. */
    public static final String BLUE_TEXT = "\u001B[0;34m";
    /** ANSI escape code for magenta foreground text. */
    public static final String MAGENTA_TEXT = "\u001B[0;35m";
    /** ANSI escape code for cyan foreground text. */
    public static final String CYAN_TEXT = "\u001B[0;36m";
    /** ANSI escape code for white foreground text. */
    public static final String WHITE_TEXT = "\u001B[0;37m";
    /** ANSI escape code for default foreground text color. */
    public static final String DEFAULT_TEXT = "\u001B[0;39m";

    /** ANSI escape code for black background. */
    public static final String BLACK_BG = "\u001B[0;40m";
    /** ANSI escape code for red background. */
    public static final String RED_BG = "\u001B[0;41m";
    /** ANSI escape code for green background. */
    public static final String GREEN_BG = "\u001B[0;42m";
    /** ANSI escape code for yellow background. */
    public static final String YELLOW_BG = "\u001B[0;43m";
    /** ANSI escape code for blue background. */
    public static final String BLUE_BG = "\u001B[0;44m";
    /** ANSI escape code for magenta background. */
    public static final String MAGENTA_BG = "\u001B[0;45m";
    /** ANSI escape code for cyan background. */
    public static final String CYAN_BG = "\u001B[0;46m";
    /** ANSI escape code for white background. */
    public static final String WHITE_BG = "\u001B[0;47m";
    /** ANSI escape code for default background color. */
    public static final String DEFAULT_BG = "\u001B[0;49m";
    /** ANSI escape code to switch to alternate screen buffer. */
    public static final String ALTERNATE_BUFFER = InfoCmpHelper.getCurrentTranslatedCapability("smcup", "\u001B[?1049h");
    /** ANSI escape code to switch back to main screen buffer. */
    public static final String MAIN_BUFFER = InfoCmpHelper.getCurrentTranslatedCapability("rmcup", "\u001B[?1049l");
    /** ANSI escape code to invert foreground and background colors. */
    public static final String INVERT_BACKGROUND = InfoCmpHelper.getCurrentTranslatedCapability("smso", "\u001B[7m");
    /** ANSI escape code to restore normal foreground and background colors. */
    public static final String NORMAL_BACKGROUND = InfoCmpHelper.getCurrentTranslatedCapability("rmso", "\u001B[27m");
    /** ANSI escape code to reset all text attributes to default. */
    public static final String RESET = "\u001B[0m";
    /** ANSI escape code to enable bold text. */
    public static final String BOLD = InfoCmpHelper.getCurrentTranslatedCapability("bold", "\u001B[0;1m");
    /** ANSI escape code to disable bold text. */
    public static final String BOLD_OFF = "\u001B[0;22m";
    /** ANSI escape code to enable underlined text. */
    public static final String UNDERLINE = InfoCmpHelper.getCurrentTranslatedCapability("smul", "\u001B[0;4m");
    /** ANSI escape code to disable underlined text. */
    public static final String UNDERLINE_OFF = InfoCmpHelper.getCurrentTranslatedCapability("rmul", "\u001B[0;24m");
    /** ANSI escape code to enable blinking text. */
    public static final String BLINK = InfoCmpHelper.getCurrentTranslatedCapability("blink", "\u001B[5m");
    /** ANSI escape code to disable blinking text. */
    public static final String BLINK_OFF = "\u001B[25m";
    /** ANSI escape sequence to move cursor to start of line. */
    public static final int[] CURSOR_START = new int[] { 27, '[', 'G' };
    /** ANSI escape sequence to erase the entire current line. */
    public static final int[] ERASE_WHOLE_LINE = new int[] { 27, '[', '2', 'K' };
    /** ANSI escape code to query cursor row position. */
    public static final String CURSOR_ROW = "\u001B[6n";
    /** ANSI escape sequence to clear the entire screen. */
    public static final int[] CLEAR_SCREEN = InfoCmpHelper.getCurrentTranslatedCapability("clear", "\u001B[2J").codePoints()
            .toArray();
    /** ANSI escape code to save current cursor position. */
    public static final String CURSOR_SAVE = InfoCmpHelper.getCurrentTranslatedCapability("sc", "\u001B[s");
    /** ANSI escape code to restore previously saved cursor position. */
    public static final String CURSOR_RESTORE = InfoCmpHelper.getCurrentTranslatedCapability("rc", "\u001B[u");
    /** ANSI escape code to hide the cursor. */
    public static final String CURSOR_HIDE = "\u001B[?25l";
    /** ANSI escape code to show the cursor. */
    public static final String CURSOR_SHOW = "\u001B[?25h";
    /** ANSI escape sequence to erase from cursor to end of line. */
    public static final int[] ERASE_LINE_FROM_CURSOR = new int[] { 27, '[', 'K' };
    /** ANSI escape sequence to move cursor up one line. */
    public static final int[] MOVE_LINE_UP = new int[] { 27, '[', '1', 'A' };
    /** ANSI escape sequence to move cursor down one line. */
    public static final int[] MOVE_LINE_DOWN = new int[] { 27, '[', '1', 'B' };

    /** ANSI escape code to enable light (reverse video) background mode. */
    public static final String LIGHT_BG = "\u001B[?5h";
    /** ANSI escape code to enable dark (normal) background mode. */
    public static final String DARK_BG = "\u001B[?5l";

    /** OSC (Operating System Command) escape sequence start. */
    public static final String OSC_START = "\u001B]";
    /** BEL character, used as OSC terminator. */
    public static final String BEL = "\u0007";
    /** ST (String Terminator), alternate OSC terminator. */
    public static final String ST = "\u001B\\";

    /** OSC code for palette color query/set. */
    public static final int OSC_PALETTE = 4;
    /** OSC code for foreground color query/set. */
    public static final int OSC_FOREGROUND = 10;
    /** OSC code for background color query/set. */
    public static final int OSC_BACKGROUND = 11;
    /** OSC code for cursor color query/set. */
    public static final int OSC_CURSOR_COLOR = 12;

    private ANSI() {
    }

    /**
     * Return a ansified string based on param
     *
     * @param out string
     * @return ansified string
     */
    public static int[] printAnsi(String out) {
        return printAnsi(out.toCharArray());
    }

    /**
     * Return a ansified string based on param
     *
     * @param out what will be ansified
     * @return ansified string
     */
    public static int[] printAnsi(char... out) {
        int[] ansi = new int[out.length + 2];
        ansi[0] = 27;
        ansi[1] = '[';
        int counter = 0;
        for (char anOut : out) {
            if (anOut == '\t') {
                Arrays.fill(ansi, counter + 2, counter + 2 + TAB, ' ');
                counter += TAB - 1;
            } else
                ansi[counter + 2] = anOut;

            counter++;
        }
        return ansi;
    }

    /**
     * Parse cursor position response and return the actual cursor position.
     *
     * @param input the ANSI cursor position response sequence
     * @return a Point containing the column and row of the cursor
     */
    public static Point getActualCursor(int[] input) {
        boolean started = false;
        boolean gotSep = false;
        int col = 0;
        int row = 0;

        //read until we get a 'R'
        for (int i = 0; i < input.length - 1; i++) {
            if (started) {
                if (input[i] == 82)
                    break;
                else if (input[i] == 59) // we got a ';' which is the separator
                    gotSep = true;
                else {
                    if (gotSep) {
                        char c = (char) input[i];
                        col *= 10;
                        col += ((int) c & 0xF);
                    } else {
                        char c = (char) input[i];
                        row *= 10;
                        row += ((int) c & 0xF);
                    }
                }
            }
            //search for the beginning which starts with esc,[
            else if (input[i] == 27 && i < input.length - 1 && input[i + 1] == 91) {
                started = true;
                i++;
            }
        }

        return new Point(col, row);
    }

    /**
     * Create ANSI escape sequence to move cursor up by specified rows.
     *
     * @param rows number of rows to move up
     * @return ANSI escape sequence as int array
     */
    public static int[] moveRowsUp(int rows) {
        return moveInDirection(rows, 'A');
    }

    /**
     * Create ANSI escape sequence to move cursor down by specified rows.
     *
     * @param rows number of rows to move down
     * @return ANSI escape sequence as int array
     */
    public static int[] moveRowsDown(int rows) {
        return moveInDirection(rows, 'B');
    }

    /**
     * Create ANSI escape sequence to move cursor right by specified columns.
     *
     * @param rows number of columns to move right
     * @return ANSI escape sequence as int array
     */
    public static int[] moveColumnsRight(int rows) {
        return moveInDirection(rows, 'C');
    }

    /**
     * Create ANSI escape sequence to move cursor left by specified columns.
     *
     * @param rows number of columns to move left
     * @return ANSI escape sequence as int array
     */
    public static int[] moveColumnsLeft(int rows) {
        return moveInDirection(rows, 'D');
    }

    private static int[] moveInDirection(int value, char direction) {
        if (value < 10) {
            int[] out = new int[4];
            out[0] = 27; // esc
            out[1] = '['; // [
            out[2] = 48 + value;
            out[3] = direction;
            return out;
        } else {
            int[] asciiColumn = intToAsciiInts(value);
            int[] out = new int[3 + asciiColumn.length];
            out[0] = 27; // esc
            out[1] = '['; // [
            System.arraycopy(asciiColumn, 0, out, 2, asciiColumn.length);
            out[out.length - 1] = direction;
            return out;
        }
    }

    /**
     * we assume that value is > 0
     *
     * @param value int value (non ascii value)
     * @return ascii represented int value
     */
    private static int[] intToAsciiInts(int value) {
        int length = getAsciiSize(value);
        int[] asciiValue = new int[length];

        if (length == 1) {
            asciiValue[0] = 48 + value;
        } else {
            while (length > 0) {
                length--;
                int num = value % 10;
                asciiValue[length] = 48 + num;
                value = value / 10;
            }
        }
        return asciiValue;
    }

    private static int getAsciiSize(int value) {
        if (value < 10)
            return 1;
        //very simple way of getting the length
        if (value > 9 && value < 99)
            return 2;
        else if (value > 99 && value < 999)
            return 3;
        else if (value > 999 && value < 9999)
            return 4;
        else
            return 5;
    }

    /**
     * Build an OSC (Operating System Command) query string.
     * <p>
     * OSC format: ESC ] Ps ; Pt BEL
     * Where Ps is the OSC code and Pt is the parameter.
     *
     * @param oscCode the OSC code (e.g., 10 for foreground, 11 for background)
     * @param param the parameter (e.g., "?" for query)
     * @return the OSC query string
     */
    public static String buildOscQuery(int oscCode, String param) {
        return OSC_START + oscCode + ";" + param + BEL;
    }

    /**
     * Build an OSC query string with an additional index parameter.
     * <p>
     * This is used for OSC codes that require an index, such as OSC 4 (palette colors).
     * <p>
     * OSC format: ESC ] Ps ; Pn ; Pt BEL
     * Where Ps is the OSC code, Pn is the index/parameter, and Pt is the query.
     *
     * @param oscCode the OSC code (e.g., 4 for palette color)
     * @param index the index parameter (e.g., palette color index 0-255)
     * @param param the parameter (e.g., "?" for query)
     * @return the OSC query string
     */
    public static String buildOscQuery(int oscCode, int index, String param) {
        return OSC_START + oscCode + ";" + index + ";" + param + BEL;
    }

    /**
     * Parse an OSC color response.
     * <p>
     * Expected format: ESC ] {oscCode} ; rgb:RRRR/GGGG/BBBB {ST}
     * Where:
     * <ul>
     * <li>ESC is 0x1B (27)</li>
     * <li>oscCode is the OSC code (e.g., 10 for foreground, 11 for background)</li>
     * <li>RRRR, GGGG, BBBB are 4-digit or 2-digit hex values</li>
     * <li>ST is either BEL (0x07) or ESC \ (0x1B 0x5C)</li>
     * </ul>
     * <p>
     * For OSC codes with parameters (like OSC 4 palette colors), use
     * {@link #parseOscColorResponse(int[], int, int)} instead.
     *
     * @param input the input sequence as code points
     * @param oscCode the expected OSC code in response
     * @return RGB array [r, g, b] (0-255 each), or null if parsing failed
     */
    public static int[] parseOscColorResponse(int[] input, int oscCode) {
        return parseOscColorResponse(input, oscCode, -1);
    }

    /**
     * Parse an OSC color response with an optional parameter.
     * <p>
     * This method handles OSC codes that include a parameter, such as OSC 4
     * (palette colors) which includes a color index.
     * <p>
     * Expected formats:
     * <ul>
     * <li>Without parameter: ESC ] {code} ; rgb:RRRR/GGGG/BBBB {ST}</li>
     * <li>With parameter: ESC ] {code} ; {param} ; rgb:RRRR/GGGG/BBBB {ST}</li>
     * </ul>
     *
     * @param input the input sequence as code points
     * @param oscCode the expected OSC code in response
     * @param oscParam the expected parameter (e.g., palette index for OSC 4),
     *        or -1 to not require a specific parameter
     * @return RGB array [r, g, b] (0-255 each), or null if parsing failed
     */
    public static int[] parseOscColorResponse(int[] input, int oscCode, int oscParam) {
        if (input == null || input.length < 10) {
            return null;
        }

        // Convert to string for easier parsing
        StringBuilder sb = new StringBuilder();
        for (int c : input) {
            sb.appendCodePoint(c);
        }
        String response = sb.toString();

        // Build the pattern to search for
        String oscMarker = "\u001B]" + oscCode + ";";
        int start = response.indexOf(oscMarker);
        if (start < 0) {
            // Try alternate format with just ']'
            oscMarker = "]" + oscCode + ";";
            start = response.indexOf(oscMarker);
            if (start >= 0 && start > 0 && response.charAt(start - 1) == '\u001B') {
                start--;
                oscMarker = "\u001B" + oscMarker;
            } else if (start < 0) {
                return null;
            }
        }

        // Move past the OSC marker
        int searchStart = start + oscMarker.length();

        // If a specific parameter is expected, verify it's present
        if (oscParam >= 0) {
            String paramMarker = oscParam + ";";
            if (!response.substring(searchStart).startsWith(paramMarker)) {
                return null;
            }
            searchStart += paramMarker.length();
        }

        // Find rgb: from current position
        // Handle case where there might be an unexpected parameter before rgb:
        int rgbStart = response.indexOf("rgb:", searchStart);
        if (rgbStart < 0) {
            return null;
        }

        // Verify rgb: comes before any terminator
        int belPos = response.indexOf('\u0007', searchStart);
        int stPos = response.indexOf("\u001B\\", searchStart);
        int terminatorPos = -1;
        if (belPos >= 0 && stPos >= 0) {
            terminatorPos = Math.min(belPos, stPos);
        } else if (belPos >= 0) {
            terminatorPos = belPos;
        } else if (stPos >= 0) {
            terminatorPos = stPos;
        }

        if (terminatorPos >= 0 && rgbStart > terminatorPos) {
            return null;
        }

        rgbStart += 4; // skip "rgb:"

        // Find the terminator (BEL or ESC \)
        int end = response.indexOf('\u0007', rgbStart);
        if (end < 0) {
            end = response.indexOf("\u001B\\", rgbStart);
        }
        if (end < 0) {
            end = response.length();
        }

        String rgbPart = response.substring(rgbStart, end);

        // Parse RRRR/GGGG/BBBB
        String[] parts = rgbPart.split("/");
        if (parts.length != 3) {
            return null;
        }

        try {
            int[] rgb = new int[3];
            for (int i = 0; i < 3; i++) {
                String hex = parts[i].trim();
                int value;
                if (hex.length() == 4) {
                    // 4-digit hex (e.g., FFFF), take high byte
                    value = Integer.parseInt(hex, 16) >> 8;
                } else if (hex.length() == 2) {
                    // 2-digit hex
                    value = Integer.parseInt(hex, 16);
                } else {
                    return null;
                }
                rgb[i] = Math.min(255, Math.max(0, value));
            }
            return rgb;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== RGB to ANSI Color Conversion ====================

    /**
     * Convert RGB to nearest 256-color palette index.
     * <p>
     * The 256-color palette consists of:
     * <ul>
     * <li>0-15: Standard and bright ANSI colors</li>
     * <li>16-231: 6x6x6 color cube</li>
     * <li>232-255: 24-shade grayscale ramp</li>
     * </ul>
     * <p>
     * This method maps to the color cube (16-231) or grayscale (232-255).
     * For standard ANSI colors (0-15), use {@link #rgbToAnsiColor(int, int, int)}.
     *
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @return the nearest 256-color palette index (16-255)
     */
    public static int rgbTo256Color(int r, int g, int b) {
        // Clamp values to valid range
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

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
     * Convert RGB to nearest basic ANSI foreground color code.
     * <p>
     * Returns a foreground color code in the range 30-37 (normal) or 90-97 (bright),
     * automatically determining brightness based on the RGB luminance.
     *
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @return ANSI foreground color code (30-37 or 90-97)
     */
    public static int rgbToAnsiColor(int r, int g, int b) {
        boolean bright = rgbIsBright(r, g, b);
        return rgbToAnsiColor(r, g, b, bright);
    }

    /**
     * Convert RGB to nearest basic ANSI foreground color code with explicit brightness.
     * <p>
     * Returns a foreground color code:
     * <ul>
     * <li>30-37: Normal colors (black, red, green, yellow, blue, magenta, cyan, white)</li>
     * <li>90-97: Bright colors</li>
     * </ul>
     *
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @param bright true for bright variant (90-97), false for normal (30-37)
     * @return ANSI foreground color code
     */
    public static int rgbToAnsiColor(int r, int g, int b, boolean bright) {
        int baseCode = rgbToBasicColorCode(r, g, b);
        return bright ? baseCode + 60 : baseCode;
    }

    /**
     * Convert RGB to nearest basic ANSI background color code.
     * <p>
     * Returns a background color code in the range 40-47 (normal) or 100-107 (bright),
     * automatically determining brightness based on the RGB luminance.
     *
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @return ANSI background color code (40-47 or 100-107)
     */
    public static int rgbToAnsiBackgroundColor(int r, int g, int b) {
        boolean bright = rgbIsBright(r, g, b);
        return rgbToAnsiBackgroundColor(r, g, b, bright);
    }

    /**
     * Convert RGB to nearest basic ANSI background color code with explicit brightness.
     * <p>
     * Returns a background color code:
     * <ul>
     * <li>40-47: Normal colors</li>
     * <li>100-107: Bright colors</li>
     * </ul>
     *
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @param bright true for bright variant (100-107), false for normal (40-47)
     * @return ANSI background color code
     */
    public static int rgbToAnsiBackgroundColor(int r, int g, int b, boolean bright) {
        int baseCode = rgbToBasicColorCode(r, g, b);
        int bgCode = baseCode + 10; // 30-37 -> 40-47
        return bright ? bgCode + 60 : bgCode;
    }

    /**
     * Get the basic ANSI color code (30-37) for an RGB value.
     * <p>
     * Maps RGB to one of the 8 basic colors:
     * <ul>
     * <li>30: Black</li>
     * <li>31: Red</li>
     * <li>32: Green</li>
     * <li>33: Yellow</li>
     * <li>34: Blue</li>
     * <li>35: Magenta</li>
     * <li>36: Cyan</li>
     * <li>37: White</li>
     * </ul>
     *
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @return basic ANSI color code (30-37)
     */
    public static int rgbToBasicColorCode(int r, int g, int b) {
        // Clamp values
        r = Math.max(0, Math.min(255, r));
        g = Math.max(0, Math.min(255, g));
        b = Math.max(0, Math.min(255, b));

        // Calculate luminance for grayscale detection
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;

        // Check for near-grayscale
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        int saturation = max - min;

        if (saturation < 50) {
            // Grayscale - return black or white
            return luminance < 0.5 ? 30 : 37; // BLACK or WHITE
        }

        // Find dominant color
        if (r >= g && r >= b) {
            // Red dominant
            if (g > b && g > 128) {
                return 33; // YELLOW
            }
            if (b > g && b > 128) {
                return 35; // MAGENTA
            }
            return 31; // RED
        } else if (g >= r && g >= b) {
            // Green dominant
            if (b > r && b > 128) {
                return 36; // CYAN
            }
            if (r > b && r > 128) {
                return 33; // YELLOW
            }
            return 32; // GREEN
        } else {
            // Blue dominant
            if (r > g && r > 128) {
                return 35; // MAGENTA
            }
            if (g > r && g > 128) {
                return 36; // CYAN
            }
            return 34; // BLUE
        }
    }

    /**
     * Determine if an RGB color should use bright ANSI variant.
     *
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @return true if the color is bright enough for bright ANSI variant
     */
    public static boolean rgbIsBright(int r, int g, int b) {
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
        return luminance > 0.6;
    }

    /**
     * Get the RGB values for a 256-color palette index.
     * <p>
     * This is the inverse of {@link #rgbTo256Color(int, int, int)}.
     *
     * @param index the 256-color palette index (0-255)
     * @return RGB array [r, g, b] (0-255 each)
     */
    public static int[] color256ToRgb(int index) {
        if (index < 0 || index > 255) {
            throw new IllegalArgumentException("Index must be 0-255, got: " + index);
        }

        // Standard colors 0-15 (approximate)
        if (index < 16) {
            return STANDARD_COLORS[index];
        }

        // Color cube 16-231
        if (index < 232) {
            int idx = index - 16;
            int r = (idx / 36) % 6;
            int g = (idx / 6) % 6;
            int b = idx % 6;
            return new int[] {
                    r == 0 ? 0 : 55 + r * 40,
                    g == 0 ? 0 : 55 + g * 40,
                    b == 0 ? 0 : 55 + b * 40
            };
        }

        // Grayscale 232-255
        int gray = 8 + (index - 232) * 10;
        return new int[] { gray, gray, gray };
    }

    // Standard 16 ANSI colors (approximate RGB values)
    private static final int[][] STANDARD_COLORS = {
            { 0, 0, 0 }, // 0: Black
            { 128, 0, 0 }, // 1: Red
            { 0, 128, 0 }, // 2: Green
            { 128, 128, 0 }, // 3: Yellow
            { 0, 0, 128 }, // 4: Blue
            { 128, 0, 128 }, // 5: Magenta
            { 0, 128, 128 }, // 6: Cyan
            { 192, 192, 192 }, // 7: White
            { 128, 128, 128 }, // 8: Bright Black (Gray)
            { 255, 0, 0 }, // 9: Bright Red
            { 0, 255, 0 }, // 10: Bright Green
            { 255, 255, 0 }, // 11: Bright Yellow
            { 0, 0, 255 }, // 12: Bright Blue
            { 255, 0, 255 }, // 13: Bright Magenta
            { 0, 255, 255 }, // 14: Bright Cyan
            { 255, 255, 255 } // 15: Bright White
    };

    // ==================== Device Attributes (DA1/DA2) ====================

    /** DA1 (Primary Device Attributes) query sequence. */
    public static final String DA1_QUERY = "\u001B[c";

    /** DA2 (Secondary Device Attributes) query sequence. */
    public static final String DA2_QUERY = "\u001B[>c";

    /**
     * Parse a DA1 (Primary Device Attributes) response.
     * <p>
     * Expected format: ESC [ ? Ps ; Ps ; ... c
     * <p>
     * Where the first Ps is the device class/conformance level and
     * subsequent Ps values are feature parameters.
     *
     * @param input the input sequence as code points
     * @return DeviceAttributes parsed from DA1, or null if parsing failed
     */
    public static DeviceAttributes parseDA1Response(int[] input) {
        if (input == null || input.length < 4) {
            return null;
        }

        // Convert to string for easier parsing
        StringBuilder sb = new StringBuilder();
        for (int c : input) {
            sb.appendCodePoint(c);
        }
        String response = sb.toString();

        // Look for DA1 response pattern: ESC [ ? ... c
        int start = response.indexOf("\u001B[?");
        if (start < 0) {
            return null;
        }

        // Find the terminating 'c'
        int end = response.indexOf('c', start);
        if (end < 0) {
            return null;
        }

        // Extract the parameters between "?" and "c"
        String params = response.substring(start + 3, end);
        if (params.isEmpty()) {
            return null;
        }

        // Parse semicolon-separated parameters
        String[] parts = params.split(";");
        if (parts.length == 0) {
            return null;
        }

        try {
            // First parameter is device class
            int deviceClass = Integer.parseInt(parts[0].trim());

            // Remaining parameters are feature codes
            java.util.Set<Integer> features = new java.util.HashSet<>();
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i].trim();
                if (!part.isEmpty()) {
                    features.add(Integer.parseInt(part));
                }
            }

            return new DeviceAttributes(deviceClass, features);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse a DA2 (Secondary Device Attributes) response.
     * <p>
     * Expected format: ESC [ > Pp ; Pv ; Pc c
     * <p>
     * Where:
     * <ul>
     * <li>Pp is the terminal type (0=VT100, 1=VT220, etc.)</li>
     * <li>Pv is the firmware version</li>
     * <li>Pc is the ROM cartridge registration number</li>
     * </ul>
     *
     * @param input the input sequence as code points
     * @return DeviceAttributes parsed from DA2, or null if parsing failed
     */
    public static DeviceAttributes parseDA2Response(int[] input) {
        if (input == null || input.length < 4) {
            return null;
        }

        // Convert to string for easier parsing
        StringBuilder sb = new StringBuilder();
        for (int c : input) {
            sb.appendCodePoint(c);
        }
        String response = sb.toString();

        // Look for DA2 response pattern: ESC [ > ... c
        int start = response.indexOf("\u001B[>");
        if (start < 0) {
            return null;
        }

        // Find the terminating 'c'
        int end = response.indexOf('c', start);
        if (end < 0) {
            return null;
        }

        // Extract the parameters between ">" and "c"
        String params = response.substring(start + 3, end);
        if (params.isEmpty()) {
            return null;
        }

        // Parse semicolon-separated parameters
        String[] parts = params.split(";");

        try {
            int typeCode = parts.length > 0 && !parts[0].trim().isEmpty()
                    ? Integer.parseInt(parts[0].trim())
                    : -1;
            int version = parts.length > 1 && !parts[1].trim().isEmpty()
                    ? Integer.parseInt(parts[1].trim())
                    : -1;
            int rom = parts.length > 2 && !parts[2].trim().isEmpty()
                    ? Integer.parseInt(parts[2].trim())
                    : -1;

            DeviceAttributes.TerminalType termType = DeviceAttributes.TerminalType.fromCode(typeCode);

            return new DeviceAttributes(-1, null, termType, version, rom);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse both DA1 and DA2 responses from a combined input.
     * <p>
     * This is useful when both queries are sent together and responses
     * arrive in sequence.
     *
     * @param input the input sequence containing both responses
     * @return DeviceAttributes with merged DA1 and DA2 data, or null if parsing failed
     */
    public static DeviceAttributes parseDAResponse(int[] input) {
        DeviceAttributes da1 = parseDA1Response(input);
        DeviceAttributes da2 = parseDA2Response(input);

        if (da1 != null && da2 != null) {
            return da1.merge(da2);
        } else if (da1 != null) {
            return da1;
        } else {
            return da2;
        }
    }

}
