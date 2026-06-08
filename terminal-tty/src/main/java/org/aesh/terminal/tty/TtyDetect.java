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

import java.io.Console;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility for detecting whether file descriptors are connected to a terminal.
 * <p>
 * On Java 22+, uses FFM to call POSIX {@code isatty()} for accurate per-fd
 * detection. On older Java versions, falls back to {@code System.console()}
 * and {@code Console.isTerminal()} (Java 22+) as heuristics.
 * <p>
 * Typical usage:
 *
 * <pre>
 * if (TtyDetect.isStdinTty()) {
 *     // Interactive mode — show prompt, enable completion
 * } else {
 *     // Piped/redirected — read commands from stdin
 * }
 *
 * if (!TtyDetect.isStdoutTty()) {
 *     // Output is piped — disable colors, use machine-readable format
 * }
 * </pre>
 */
public final class TtyDetect {

    private static final Logger LOGGER = Logger.getLogger(TtyDetect.class.getName());

    /** File descriptor for standard input. */
    public static final int FD_STDIN = 0;
    /** File descriptor for standard output. */
    public static final int FD_STDOUT = 1;
    /** File descriptor for standard error. */
    public static final int FD_STDERR = 2;

    // Cached results (computed once)
    private static volatile int cachedStdin = -1; // -1 = not computed, 0 = false, 1 = true
    private static volatile int cachedStdout = -1;
    private static volatile int cachedStderr = -1;

    private TtyDetect() {
    }

    /**
     * Check if the given file descriptor is connected to a terminal.
     * <p>
     * On Java 22+ with POSIX systems, this uses the native {@code isatty()} function
     * via FFM for accurate detection. On older Java versions or unsupported platforms,
     * falls back to heuristics based on {@code System.console()}.
     *
     * @param fd the file descriptor (0=stdin, 1=stdout, 2=stderr)
     * @return true if the file descriptor is connected to a terminal
     */
    public static boolean isTty(int fd) {
        // Try FFM-based isatty first (Java 22+, POSIX)
        Boolean result = tryNativeIsatty(fd);
        if (result != null) {
            return result;
        }
        // Fallback: use System.console() heuristic
        return fallbackIsTty(fd);
    }

    /**
     * Check if standard input is connected to a terminal.
     * Result is cached after the first call.
     *
     * @return true if stdin is a terminal (interactive input)
     */
    public static boolean isStdinTty() {
        if (cachedStdin == -1) {
            cachedStdin = isTty(FD_STDIN) ? 1 : 0;
        }
        return cachedStdin == 1;
    }

    /**
     * Check if standard output is connected to a terminal.
     * Result is cached after the first call.
     *
     * @return true if stdout is a terminal (not piped/redirected)
     */
    public static boolean isStdoutTty() {
        if (cachedStdout == -1) {
            cachedStdout = isTty(FD_STDOUT) ? 1 : 0;
        }
        return cachedStdout == 1;
    }

    /**
     * Check if standard error is connected to a terminal.
     * Result is cached after the first call.
     *
     * @return true if stderr is a terminal (not piped/redirected)
     */
    public static boolean isStderrTty() {
        if (cachedStderr == -1) {
            cachedStderr = isTty(FD_STDERR) ? 1 : 0;
        }
        return cachedStderr == 1;
    }

    /**
     * Try native isatty() via FFM (Java 22+).
     * Returns null if FFM is not available.
     */
    private static Boolean tryNativeIsatty(int fd) {
        try {
            // Use reflection to call LibC.isatty() which is only available
            // in the java22 multi-release overlay
            Class<?> libC = Class.forName("org.aesh.terminal.tty.impl.LibC");
            Method isatty = libC.getDeclaredMethod("isatty", int.class);
            isatty.setAccessible(true);
            return (Boolean) isatty.invoke(null, fd);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "FFM isatty not available, using fallback", e);
            return null;
        }
    }

    /**
     * Fallback TTY detection using System.console() and Console.isTerminal().
     */
    private static boolean fallbackIsTty(int fd) {
        Console console = System.console();
        if (console == null) {
            return false;
        }
        // Console.isTerminal() was introduced in Java 22
        try {
            Method isTerminal = Console.class.getMethod("isTerminal");
            return (boolean) isTerminal.invoke(console);
        } catch (NoSuchMethodException e) {
            // Pre-Java 22: System.console() != null is our best guess
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to invoke Console.isTerminal()", e);
            return console != null;
        }
    }
}
