/*
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Canonical extraction for terminal query replies.
 * <p>
 * The framer ({@link TerminalReplyFramer}) decides which bytes form
 * complete frames; this class reads values out of them. Both standalone
 * probing and live-connection queries ({@code ANSI.parse*} in
 * terminal-api delegates here) share these rules, so a framing fix
 * lands once instead of diverging per path again.
 * <p>
 * All functions take the accumulated response text and tolerate
 * surrounding input: they scan for their own shapes and return null
 * (or {@code NO_RESPONSE}) when no complete valid frame is present.
 * Partial frames never parse — callers re-run on more bytes.
 */
public final class TerminalReplyParser {

    private static final char BEL = '\007';

    /**
     * Device attributes from a DA1 reply: class plus feature codes.
     */
    public static final class TerminalDeviceAttributes {
        /** Device class (conformance level). */
        public final int deviceClass;
        /** Feature codes in reply order. */
        public final List<Integer> features;

        /**
         * Create device attributes.
         *
         * @param deviceClass the device class
         * @param features the feature codes
         */
        public TerminalDeviceAttributes(int deviceClass, List<Integer> features) {
            this.deviceClass = deviceClass;
            this.features = Collections.unmodifiableList(new ArrayList<>(features));
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TerminalDeviceAttributes)) {
                return false;
            }
            TerminalDeviceAttributes that = (TerminalDeviceAttributes) other;
            return deviceClass == that.deviceClass && features.equals(that.features);
        }

        @Override
        public int hashCode() {
            return Objects.hash(deviceClass, features);
        }

        @Override
        public String toString() {
            return "DA1[class=" + deviceClass + ",features=" + features + "]";
        }
    }

    /**
     * Parse an OSC color response for a given code and optional parameter.
     *
     * @param response the raw terminal response
     * @param oscCode the OSC code (4, 10, 11)
     * @param oscParam the parameter index (-1 for none, 0-255 for palette)
     * @return RGB array, or null when no complete valid reply is present
     */
    public static int[] oscColor(String response, int oscCode, int oscParam) {
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

            // Stop at this response's own terminator: the earliest of
            // BEL and ST. A later reply's terminator must not extend
            // this response's color content, and an unterminated reply
            // is still arriving: parsers never invent data (#316).
            int end = response.indexOf(BEL, rgbStart);
            int stEnd = response.indexOf("\033\\", rgbStart);
            if (end < 0 || (stEnd >= 0 && stEnd < end)) {
                end = stEnd;
            }
            if (end < 0) {
                return null;
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
                rgb[i] = value;
            }
            return rgb;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Parse one DECRQM mode report out of a possibly batched response.
     * Each CSI frames independently, so interleaved replies (DA1 first,
     * malformed reports) never poison a later valid one. The last valid
     * report for the mode wins; anything else leaves prior state alone.
     *
     * @param response the accumulated response text
     * @param mode the mode number (e.g. 2026, 2027)
     * @return the support state, {@code NO_RESPONSE} when no valid
     *         report for the mode is present
     */
    public static ModeSupport modeSupport(String response, int mode) {
        ModeSupport found = ModeSupport.NO_RESPONSE;
        if (response == null) {
            return found;
        }
        String marker = "\033[?" + mode + ";";
        int pos = 0;
        while (true) {
            // Find this mode's CSI ...
            int start = response.indexOf(marker, pos);
            if (start < 0) {
                return found;
            }
            // ... scan to this CSI's own final byte (0x40-0x7E).
            int end = start + marker.length();
            while (end < response.length() && !isCsiFinal(response.charAt(end))) {
                end++;
            }
            if (end < response.length() && response.charAt(end) == 'y'
                    && end > start + marker.length() && response.charAt(end - 1) == '$') {
                // The marker already consumed "mode;": parts[0] is Ps,
                // extras ignored, mirroring the two-part legacy scan.
                String[] parts = response.substring(start + marker.length(), end - 1).split(";");
                if (parts.length >= 1) {
                    try {
                        int ps = Integer.parseInt(parts[0].trim());
                        // Ps: 1=set, 2=reset(recognized), 3=permanently set
                        // imply support; 0/4 imply the opposite. Reserved
                        // values convey nothing and leave prior state alone.
                        if (ps >= 1 && ps <= 3) {
                            found = ModeSupport.SUPPORTED;
                        } else if (ps == 0 || ps == 4) {
                            found = ModeSupport.NOT_SUPPORTED;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            pos = end + 1;
        }
    }

    /**
     * Parse a DA1 reply out of a possibly batched response. Only a
     * sequence terminated by {@code c} is DA1: other CSI responses
     * sharing the {@code ESC[?} prefix (DECRPM reports) are skipped
     * without consuming the following device attributes.
     *
     * @param response the accumulated response text
     * @return the attributes, or null when no valid DA1 frame is present
     */
    public static TerminalDeviceAttributes deviceAttributes(String response) {
        if (response == null) {
            return null;
        }
        int pos = 0;
        while (true) {
            int start = response.indexOf("\033[?", pos);
            if (start < 0) {
                return null;
            }
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
                    return null;
                }
                try {
                    int deviceClass = Integer.parseInt(parts[0].trim());
                    List<Integer> features = new ArrayList<>();
                    for (int i = 1; i < parts.length; i++) {
                        String part = parts[i].trim();
                        if (!part.isEmpty()) {
                            features.add(Integer.parseInt(part));
                        }
                    }
                    return new TerminalDeviceAttributes(deviceClass, features);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            pos = end + 1;
        }
    }

    /**
     * Parse cursor position out of a possibly batched response: the
     * first complete valid CPR frame wins. Stale garbage never poisons
     * a later well-formed frame: any byte outside digits-and-separator
     * abandons the frame and scanning resyncs at the next {@code ESC[}.
     *
     * @param response the accumulated response text
     * @return {@code {row, col}}, or null when no complete valid frame
     *         is present
     */
    public static int[] cursorPosition(String response) {
        if (response == null) {
            return null;
        }
        boolean started = false;
        boolean gotSep = false;
        int fieldDigits = 0;
        int col = 0;
        int row = 0;
        for (int i = 0; i < response.length(); i++) {
            char c = response.charAt(i);
            if (c == '\033' && i + 1 < response.length() && response.charAt(i + 1) == '[') {
                started = true;
                gotSep = false;
                fieldDigits = 0;
                col = 0;
                row = 0;
                i++;
            } else if (started) {
                if (c == 'R') {
                    if (gotSep && fieldDigits > 0) {
                        return new int[] { row, col };
                    }
                    started = false;
                } else if (c == ';') {
                    if (gotSep || fieldDigits == 0) {
                        started = false;
                    } else {
                        gotSep = true;
                        fieldDigits = 0;
                    }
                } else if (c >= '0' && c <= '9') {
                    int digit = c - '0';
                    if (gotSep) {
                        if (col > (Integer.MAX_VALUE - digit) / 10) {
                            started = false;
                        } else {
                            col = col * 10 + digit;
                            fieldDigits++;
                        }
                    } else {
                        if (row > (Integer.MAX_VALUE - digit) / 10) {
                            started = false;
                        } else {
                            row = row * 10 + digit;
                            fieldDigits++;
                        }
                    }
                } else {
                    started = false;
                }
            }
        }
        return null;
    }

    private static boolean isCsiFinal(char c) {
        return c >= '@' && c <= '~';
    }

    private TerminalReplyParser() {
    }
}
