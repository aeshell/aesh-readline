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
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * FFM-based variant of TerminalColorQuery (Java 22+).
 * <p>
 * Replaces stty subprocess calls with direct {@code tcgetattr}/{@code tcsetattr}
 * via the Foreign Function &amp; Memory API. All parsing logic is identical to
 * the base Java 8+ version.
 * <p>
 * Requires {@code --enable-native-access=ALL-UNNAMED} at runtime.
 */
final class TerminalColorQuery {

    private static final File DEV_TTY = new File("/dev/tty");
    private static final char BEL = '\007';

    // ==================== FFM Bindings ====================

    private static final boolean IS_MACOS;
    private static final boolean IS_SUPPORTED_PLATFORM;
    static {
        String osName = System.getProperty("os.name", "").toLowerCase();
        IS_MACOS = osName.startsWith("mac") || osName.contains("darwin");
        boolean isLinux = osName.contains("linux");
        IS_SUPPORTED_PLATFORM = IS_MACOS || isLinux;
    }

    // termios struct sizes (Linux and macOS only — other POSIX systems
    // may have different layouts and must fall back to stty)
    private static final long TERMIOS_SIZE = IS_MACOS ? 72 : 60;
    private static final long LFLAG_OFFSET = IS_MACOS ? 24 : 12;
    private static final long IFLAG_OFFSET = 0;
    private static final long CC_OFFSET = IS_MACOS ? 32 : 17;

    // Local flag bitmasks
    private static final long ECHO   = IS_MACOS ? 0x00000008L : 0x00000008L;
    private static final long ICANON = IS_MACOS ? 0x00000100L : 0x00000002L;

    // Input flag bitmasks
    private static final long IXON = IS_MACOS ? 0x00000200L : 0x00000400L;

    // c_cc indices for VMIN and VTIME
    private static final int VMIN_INDEX  = IS_MACOS ? 16 : 6;
    private static final int VTIME_INDEX = IS_MACOS ? 17 : 5;

    private static final int TCSAFLUSH = 2;

    // nfds_t is unsigned int (4 bytes) on macOS, unsigned long (8 bytes) on Linux
    private static final ValueLayout NFDS_LAYOUT = IS_MACOS ? ValueLayout.JAVA_INT : ValueLayout.JAVA_LONG;

    // struct pollfd offsets
    private static final long POLLFD_SIZE = 8;
    private static final long POLLFD_FD = 0;
    private static final long POLLFD_EVENTS = 4;
    private static final long POLLFD_REVENTS = 6;
    private static final short POLLIN = 0x0001;

    private static final MethodHandle OPEN;
    private static final MethodHandle CLOSE;
    private static final MethodHandle TCGETATTR;
    private static final MethodHandle TCSETATTR;
    private static final MethodHandle WRITE;
    private static final MethodHandle READ;
    private static final MethodHandle POLL;

    private static final boolean FFM_AVAILABLE;

    static {
        boolean available = false;
        MethodHandle openH = null, closeH = null, tcgetH = null, tcsetH = null;
        MethodHandle writeH = null, readH = null, pollH = null;
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup stdlib = linker.defaultLookup();

            openH = linker.downcallHandle(
                    stdlib.find("open").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

            closeH = linker.downcallHandle(
                    stdlib.find("close").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));

            tcgetH = linker.downcallHandle(
                    stdlib.find("tcgetattr").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

            tcsetH = linker.downcallHandle(
                    stdlib.find("tcsetattr").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                            ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

            writeH = linker.downcallHandle(
                    stdlib.find("write").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

            readH = linker.downcallHandle(
                    stdlib.find("read").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));

            pollH = linker.downcallHandle(
                    stdlib.find("poll").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS,
                            NFDS_LAYOUT, ValueLayout.JAVA_INT));

            available = true;
        } catch (Throwable ignored) {
            // FFM not usable, will fall back to stty
        }
        OPEN = openH;
        CLOSE = closeH;
        TCGETATTR = tcgetH;
        TCSETATTR = tcsetH;
        WRITE = writeH;
        READ = readH;
        POLL = pollH;
        FFM_AVAILABLE = available;
    }

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

        if (FFM_AVAILABLE && IS_SUPPORTED_PLATFORM) {
            return queryFfm();
        }
        return queryStty();
    }

    // ==================== FFM-based query path ====================

    private static TerminalColorQuery queryFfm() {
        try (Arena arena = Arena.ofConfined()) {
            // Open /dev/tty
            MemorySegment devTtyPath = arena.allocateFrom("/dev/tty");
            int fd = (int) OPEN.invokeExact(devTtyPath, 0x0002); // O_RDWR
            if (fd < 0) {
                return queryStty(); // fallback
            }

            try {
                // Save current termios
                MemorySegment savedTermios = arena.allocate(TERMIOS_SIZE, 8);
                if ((int) TCGETATTR.invokeExact(fd, savedTermios) != 0) {
                    return queryStty(); // fallback
                }

                try {
                    // Set raw mode: copy saved, modify flags
                    MemorySegment rawTermios = arena.allocate(TERMIOS_SIZE, 8);
                    MemorySegment.copy(savedTermios, 0, rawTermios, 0, TERMIOS_SIZE);

                    // Clear ECHO and ICANON in c_lflag
                    long lflag = getFlag(rawTermios, LFLAG_OFFSET);
                    lflag &= ~(ECHO | ICANON);
                    setFlag(rawTermios, LFLAG_OFFSET, lflag);

                    // Clear IXON in c_iflag
                    long iflag = getFlag(rawTermios, IFLAG_OFFSET);
                    iflag &= ~IXON;
                    setFlag(rawTermios, IFLAG_OFFSET, iflag);

                    // Set VMIN=0, VTIME=5 (500ms timeout)
                    rawTermios.set(ValueLayout.JAVA_BYTE, CC_OFFSET + VMIN_INDEX, (byte) 0);
                    rawTermios.set(ValueLayout.JAVA_BYTE, CC_OFFSET + VTIME_INDEX, (byte) 5);

                    if ((int) TCSETATTR.invokeExact(fd, TCSAFLUSH, rawTermios) != 0) {
                        return queryStty(); // fallback
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

                    // Write queries directly on the fd
                    byte[] queryBytes = queries.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                    MemorySegment writeBuf = arena.allocateFrom(ValueLayout.JAVA_BYTE, queryBytes);
                    long written = (long) WRITE.invokeExact(fd, writeBuf, (long) queryBytes.length);
                    if (written < 0) {
                        return queryStty(); // fallback
                    }

                    // Read responses using poll() — exits early when all 20 terminators found
                    String response = readWithPoll(arena, fd, 500, 20);
                    if (response == null || response.isEmpty()) {
                        return null;
                    }
                    return parseResponse(response);

                } finally {
                    // Restore saved termios
                    TCSETATTR.invokeExact(fd, TCSAFLUSH, savedTermios);
                }
            } finally {
                CLOSE.invokeExact(fd);
            }
        } catch (Throwable t) {
            // FFM failed, fall back to stty
            return queryStty();
        }
    }

    // ==================== stty-based query path (fallback) ====================

    private static TerminalColorQuery queryStty() {
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

    // ==================== Shared query/parse logic ====================

    private static TerminalColorQuery doQuery() {
        try {
            StringBuilder queries = new StringBuilder();
            queries.append("\033[c"); // DA1 query
            queries.append("\033]10;?").append(BEL);
            queries.append("\033]11;?").append(BEL);
            for (int i = 0; i <= 15; i++) {
                queries.append("\033]4;").append(i).append(";?").append(BEL);
            }
            queries.append("\033]4;255;?").append(BEL);

            try (FileOutputStream ttyOut = new FileOutputStream(DEV_TTY)) {
                ttyOut.write(queries.toString().getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
                ttyOut.flush();
            }

            String response = readResponse(20);
            if (response == null || response.isEmpty()) {
                return null;
            }

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
        } catch (IOException ignored) {
            return null;
        }
    }

    /**
     * Reads terminal responses from fd using poll() with timeout.
     * Exits early when all expected response terminators are found.
     */
    private static String readWithPoll(Arena arena, int fd, int timeoutMs,
                                        int expectedResponses) throws Throwable {
        MemorySegment pollfd = arena.allocate(POLLFD_SIZE, 4);
        MemorySegment readBuf = arena.allocate(4096);
        StringBuilder sb = new StringBuilder();
        int remaining = timeoutMs;

        boolean gotFirstData = false;

        while (remaining > 0) {
            pollfd.set(ValueLayout.JAVA_INT, POLLFD_FD, fd);
            pollfd.set(ValueLayout.JAVA_SHORT, POLLFD_EVENTS, POLLIN);
            pollfd.set(ValueLayout.JAVA_SHORT, POLLFD_REVENTS, (short) 0);

            // Use full remaining timeout until first data, then short follow-up
            int pollTime = gotFirstData ? Math.min(remaining, 20) : remaining;
            int rc = pollCall(pollfd, pollTime);

            if (rc < 0) continue; // EINTR
            if (rc == 0) {
                remaining -= pollTime;
                continue;
            }

            long n = (long) READ.invokeExact(fd, readBuf, 4096L);
            if (n <= 0) break;

            gotFirstData = true;
            byte[] bytes = new byte[(int) n];
            MemorySegment.copy(readBuf, ValueLayout.JAVA_BYTE, 0, bytes, 0, (int) n);
            sb.append(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1));

            // Check if we have all expected responses
            if (countTerminators(sb) >= expectedResponses) break;

            // Short follow-up timeout for remaining data
            remaining = 20;
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    /**
     * Platform-correct poll() call — nfds_t is int on macOS, long on Linux.
     */
    private static int pollCall(MemorySegment pollfd, int timeoutMs) throws Throwable {
        if (IS_MACOS) {
            return (int) POLL.invokeExact(pollfd, 1, timeoutMs);
        } else {
            return (int) POLL.invokeExact(pollfd, 1L, timeoutMs);
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

    // ==================== FFM flag helpers ====================

    private static long getFlag(MemorySegment termios, long offset) {
        if (IS_MACOS) {
            return termios.get(ValueLayout.JAVA_LONG, offset);
        } else {
            return Integer.toUnsignedLong(termios.get(ValueLayout.JAVA_INT, offset));
        }
    }

    private static void setFlag(MemorySegment termios, long offset, long value) {
        if (IS_MACOS) {
            termios.set(ValueLayout.JAVA_LONG, offset, value);
        } else {
            termios.set(ValueLayout.JAVA_INT, offset, (int) value);
        }
    }

    // ==================== stty subprocess methods ====================

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
                sb.append(new String(buf, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1));
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
                sb.append(new String(buf, 0, n, java.nio.charset.StandardCharsets.ISO_8859_1));
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
                count++;
                inCsi = false;
            } else if (inCsi && !Character.isDigit(c) && c != ';' && c != '?') {
                inCsi = false;
            }
        }
        return count;
    }

    // ==================== OSC Response Parsing ====================

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

    static void parseDA1Response(String response, TerminalColorQuery result) {
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
