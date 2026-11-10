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
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.aesh.terminal.tty.impl.WinConsoleNative;

/**
 * Point-in-time terminal size reads without a terminal session.
 * <p>
 * POSIX order is an in-process {@code ioctl(TIOCGWINSZ)} on a fresh
 * {@code O_RDONLY /dev/tty} descriptor (Java 22+ with native access),
 * then a bounded standalone {@code stty size} parse, then empty.
 * The {@code stty} path never touches raw mode: it runs
 * {@code stty size} redirected from the tty, unlike the probe
 * sessions in {@link SttyProbeTransport}. Windows reads the output
 * console screen buffer through the same {@code WinConsoleNative}
 * facade the probe transports use (FFM overlay on 22+, JNI below).
 * No result is cached: {@code SIGWINCH} can change the answer
 * between calls.
 * <p>
 * Every failure declines to empty, never throws.
 * <p>
 * Java 8, zero dependencies beyond {@code java.base}.
 */
final class TerminalSizeQuery {

    static final File DEV_TTY = new File("/dev/tty");

    static final long STTY_TIMEOUT_MS = 2000;

    private static volatile Class<?> cachedFfmSizeClass;
    private static volatile Method cachedFfmSizeQuery;

    private TerminalSizeQuery() {
    }

    /**
     * Query the controlling terminal size for the current OS.
     *
     * @return the size, or empty when unavailable
     */
    static Optional<TerminalSize> query() {
        return query(System.getProperty("os.name", ""));
    }

    /**
     * Query with an explicit OS name. Package-visible so tests pin
     * the branch without inheriting the developer machine.
     *
     * @param osName the OS name, as from {@code os.name}
     * @return the size, or empty when unavailable
     */
    static Optional<TerminalSize> query(String osName) {
        String lower = osName == null ? "" : osName.toLowerCase();
        if (lower.contains("win")) {
            return queryWindowsSize();
        }
        int[] dims = queryFfmSize();
        if (dims == null) {
            dims = querySttySize();
        }
        return toSize(dims);
    }

    /**
     * Wrap raw dimensions, rejecting anything unmeasurable.
     *
     * @param dims {@code [width, height]}, or null
     * @return the size, or empty
     */
    static Optional<TerminalSize> toSize(int[] dims) {
        if (dims == null || dims.length != 2) {
            return Optional.empty();
        }
        int width = dims[0];
        int height = dims[1];
        if (width <= 0 || height <= 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(new TerminalSize(width, height));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * Try the in-process ioctl path (Java 22+ overlay) reflectively.
     * Returns null on pre-22 runtimes, without native access, or when
     * the query declines — the stty fallback then applies.
     *
     * @return {@code [width, height]}, or null
     */
    static int[] queryFfmSize() {
        try {
            Class<?> clazz = cachedFfmSizeClass;
            Method query = cachedFfmSizeQuery;
            if (clazz == null || query == null) {
                clazz = Class.forName(
                        "org.aesh.terminal.detect.FfmTerminalSize");
                query = clazz.getDeclaredMethod("query");
                query.setAccessible(true);
                cachedFfmSizeClass = clazz;
                cachedFfmSizeQuery = query;
            }
            return (int[]) query.invoke(null);
        } catch (LinkageError ignored) {
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Try the Windows console screen-buffer path through the
     * {@code WinConsoleNative} facade (FFM overlay on 22+, JNI
     * below). Reads the output handle: with redirected stdout the
     * handle may be a pipe, in which case the call declines.
     *
     * @return the size, or empty
     */
    static Optional<TerminalSize> queryWindowsSize() {
        try {
            long output = WinConsoleNative.getStdHandle(
                    WinConsoleNative.STD_OUTPUT_HANDLE);
            if (output == WinConsoleNative.INVALID_HANDLE) {
                return Optional.empty();
            }
            return toSize(WinConsoleNative.getConsoleSize(output));
        } catch (UnsatisfiedLinkError
                | ExceptionInInitializerError
                | RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Try the bounded {@code stty size} fallback.
     *
     * @return {@code [width, height]}, or null
     */
    static int[] querySttySize() {
        return querySttySize(DEV_TTY, "stty", STTY_TIMEOUT_MS);
    }

    /**
     * Run {@code stty size} against an explicit tty and command.
     * Package-visible so tests substitute a fixture command without
     * touching a real terminal.
     *
     * @param tty the terminal device file
     * @param command the stty-compatible executable
     * @param timeoutMs the normal-work budget in milliseconds
     * @return {@code [width, height]}, or null
     */
    static int[] querySttySize(File tty, String command,
            long timeoutMs) {
        if (tty == null || !tty.exists()) {
            return null;
        }
        try {
            ProcessBuilder starter = new ProcessBuilder(
                    command, "size").redirectInput(tty);
            ProcessRunner.Result result = ProcessRunner.execute(
                    starter, timeoutMs);
            if (result == null || result.timedOut()
                    || result.exitCode() != 0) {
                return null;
            }
            byte[] output = result.output();
            if (output == null) {
                return null;
            }
            return parseSttySize(
                    new String(output, StandardCharsets.US_ASCII));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * Parse {@code stty size} output ({@code "rows cols"}).
     * Package-visible for direct unit tests.
     *
     * @param output the raw command output
     * @return {@code [width, height]}, or null when unparseable
     */
    static int[] parseSttySize(String output) {
        if (output == null) {
            return null;
        }
        String trimmed = output.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length != 2) {
            return null;
        }
        try {
            int rows = Integer.parseInt(parts[0]);
            int cols = Integer.parseInt(parts[1]);
            if (rows <= 0 || cols <= 0) {
                return null;
            }
            return new int[] { cols, rows };
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
