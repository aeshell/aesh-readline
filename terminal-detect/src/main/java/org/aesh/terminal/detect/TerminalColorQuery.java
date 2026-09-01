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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Direct terminal color queries via OSC escape sequences.
 * Uses /dev/tty and stty for raw terminal I/O without depending on terminal-api.
 */
final class TerminalColorQuery {

    private static final File DEV_TTY = new File("/dev/tty");
    // OSC 10/11: foreground/background
    // OSC 4;N: palette color N
    // Query param is "?" terminated by BEL
    private static final char BEL = '\007';

    int[] foreground;
    int[] background;
    Map<Integer, int[]> palette;
    boolean supports256;
    boolean supportsSixel;
    int da1DeviceClass = -1;
    List<Integer> da1Features;

    TerminalColorQuery() {
    }

    static TerminalColorQuery query() {
        if (!DEV_TTY.exists() || !DEV_TTY.canRead() || !DEV_TTY.canWrite()) {
            return null;
        }

        // Try JNI-based path first (avoids stty subprocesses)
        if (TerminalNative.isAvailable()) {
            TerminalColorQuery result = queryJni();
            if (result != null) {
                return result;
            }
        }

        return queryStty();
    }

    /**
     * JNI-based query path: uses TerminalNative for all I/O.
     * Opens /dev/tty once, does tcgetattr/tcsetattr + write/read on the
     * same fd, avoiding any FileInputStream/FileOutputStream overhead.
     */
    private static TerminalColorQuery queryJni() {
        int fd = TerminalNative.openTty();
        if (fd < 0) {
            return null;
        }

        try {
            byte[] savedTermios = TerminalNative.tcgetattr(fd);
            if (savedTermios == null) {
                return null;
            }

            try {
                // Set raw query mode. VTIME=5 is set by setRawQueryMode but the
                // native readWithTimeout() uses poll() for timing instead.
                // VTIME is harmless — it only affects bare read() without poll().
                byte[] rawTermios = savedTermios.clone();
                TermiosHelper.setRawQueryMode(rawTermios);

                if (TerminalNative.tcsetattr(fd, 2 /* TCSAFLUSH */, rawTermios) != 0) {
                    return null;
                }

                // Build query batch
                StringBuilder queries = new StringBuilder();
                queries.append("\033[c"); // DA1 query
                queries.append("\033]10;?").append(BEL);
                queries.append("\033]11;?").append(BEL);
                for (int i = 0; i <= 15; i++) {
                    queries.append("\033]4;").append(i).append(";?").append(BEL);
                }
                queries.append("\033]4;255;?").append(BEL);

                // Write and read on the same fd — no extra file opens
                if (TerminalNative.writeAll(fd, queries.toString().getBytes(StandardCharsets.ISO_8859_1)) < 0) {
                    return null;
                }

                // Read responses with poll()-based timeout (500ms max wait).
                // Exits early when all 20 response terminators are found.
                byte[] responseBytes = TerminalNative.readWithTimeout(fd, 500, 20);
                if (responseBytes == null || responseBytes.length == 0) {
                    return null;
                }

                String response = new String(responseBytes);
                return parseResponse(response);

            } finally {
                // Restore saved terminal state
                TerminalNative.tcsetattr(fd, 2 /* TCSAFLUSH */, savedTermios);
            }
        } finally {
            TerminalNative.closeFd(fd);
        }
    }

    /**
     * stty subprocess-based query path (fallback).
     */
    static TerminalColorQuery queryStty() {
        String savedState = sttyGet();
        if (savedState == null) {
            return null;
        }

        try {
            sttyRaw();
            return doQuery();
        } finally {
            sttyRestore(savedState);
        }
    }

    /**
     * Sends OSC/DA1 queries via FileOutputStream and reads responses via
     * FileInputStream. Used by the stty fallback path.
     */
    private static TerminalColorQuery doQuery() {
        try {
            // Build batch: DA1 + OSC 10 (fg) + OSC 11 (bg) + OSC 4 for 0-15 + color 255
            StringBuilder queries = new StringBuilder();
            queries.append("\033[c"); // DA1 query
            queries.append("\033]10;?").append(BEL);
            queries.append("\033]11;?").append(BEL);
            for (int i = 0; i <= 15; i++) {
                queries.append("\033]4;").append(i).append(";?").append(BEL);
            }
            queries.append("\033]4;255;?").append(BEL);

            try (FileOutputStream ttyOut = new FileOutputStream(DEV_TTY)) {
                ttyOut.write(queries.toString().getBytes(StandardCharsets.ISO_8859_1));
                ttyOut.flush();
            }

            // 20 expected terminators: 1 DA1 + 19 OSC responses
            String response = readResponse(20);
            if (response == null || response.isEmpty()) {
                return null;
            }

            return parseResponse(response);
        } catch (IOException ignored) {
            return null;
        }
    }

    /**
     * Parses a raw terminal response string into a TerminalColorQuery result.
     */
    private static TerminalColorQuery parseResponse(String response) {
        TerminalColorQuery result = new TerminalColorQuery();
        parseDA1Response(response, result);
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

    private static String sttyGet() {
        Process p = null;
        try {
            p = new ProcessBuilder("stty", "-g")
                    .redirectInput(DEV_TTY)
                    .redirectErrorStream(true)
                    .start();
            byte[] buf = new byte[256];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = p.getInputStream().read(buf)) != -1) {
                sb.append(new String(buf, 0, n, StandardCharsets.ISO_8859_1));
            }
            p.waitFor();
            return p.exitValue() == 0 ? sb.toString().trim() : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (p != null)
                p.destroy();
        }
    }

    private static void sttyRaw() {
        Process p = null;
        try {
            p = new ProcessBuilder("stty", "-echo", "-icanon", "-ixon", "min", "0", "time", "5")
                    .redirectInput(DEV_TTY)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (Exception ignored) {
        } finally {
            if (p != null)
                p.destroy();
        }
    }

    private static void sttyRestore(String savedState) {
        Process p = null;
        try {
            p = new ProcessBuilder("stty", savedState)
                    .redirectInput(DEV_TTY)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
        } catch (Exception ignored) {
        } finally {
            if (p != null)
                p.destroy();
        }
    }

    private static String readResponse(int expectedResponses) throws IOException {
        try (FileInputStream ttyIn = new FileInputStream(DEV_TTY)) {
            byte[] buf = new byte[4096];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = ttyIn.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, StandardCharsets.ISO_8859_1));
                if (countTerminators(sb) >= expectedResponses) {
                    break;
                }
            }
            return sb.toString();
        }
    }

    private static int countTerminators(StringBuilder sb) {
        int count = 0;
        boolean inCsi = false;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == BEL) {
                count++;
                inCsi = false;
            } else if (c == '\033') {
                if (i + 1 < sb.length() && sb.charAt(i + 1) == '\\') {
                    count++;
                    i++;
                } else if (i + 1 < sb.length() && sb.charAt(i + 1) == '[') {
                    inCsi = true;
                    i++;
                }
            } else if (inCsi && c == 'c') {
                // DA1 response: ESC[?...c
                count++;
                inCsi = false;
            } else if (inCsi && !Character.isDigit(c) && c != ';' && c != '?') {
                inCsi = false;
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

            int end = response.indexOf(BEL, rgbStart);
            if (end < 0) {
                end = response.indexOf("\033\\", rgbStart);
            }
            if (end < 0) {
                end = response.length();
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
                rgb[i] = Math.min(255, Math.max(0, value));
            }
            return rgb;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ==================== DA1 Response Parsing ====================

    private static final int DA1_FEATURE_SIXEL = 4;

    /**
     * Parse a DA1 (Primary Device Attributes) response.
     * Format: ESC[?{class};{feat1};{feat2};...c
     * Feature code 4 = Sixel graphics support.
     */
    static void parseDA1Response(String response, TerminalColorQuery result) {
        // Find ESC[? ... c
        int start = response.indexOf("\033[?");
        if (start < 0) {
            return;
        }
        int end = response.indexOf('c', start + 3);
        if (end < 0) {
            return;
        }

        String params = response.substring(start + 3, end);
        String[] parts = params.split(";");
        if (parts.length == 0) {
            return;
        }

        try {
            result.da1DeviceClass = Integer.parseInt(parts[0].trim());
            result.da1Features = new ArrayList<>();
            for (int i = 1; i < parts.length; i++) {
                int feature = Integer.parseInt(parts[i].trim());
                result.da1Features.add(feature);
                if (feature == DA1_FEATURE_SIXEL) {
                    result.supportsSixel = true;
                }
            }
        } catch (NumberFormatException ignored) {
        }
    }
}
