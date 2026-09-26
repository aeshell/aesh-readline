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

import org.aesh.terminal.tty.impl.WinConsoleNative;
import org.aesh.terminal.utils.OSUtils;

/**
 * Utility for detecting whether file descriptors are connected to a terminal.
 * <p>
 * On Java 22+, uses {@code Console.isTerminal()} for detection without
 * triggering FFM restricted method warnings. On older Java versions, falls
 * back to the {@code System.console() != null} heuristic.
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
     * On Java 22+, this uses {@code Console.isTerminal()} which avoids
     * loading FFM bindings and triggering restricted method warnings.
     * On older Java versions, falls back to heuristics based on
     * {@code System.console()}.
     *
     * @param fd the file descriptor (0=stdin, 1=stdout, 2=stderr)
     * @return true if the file descriptor is connected to a terminal
     */
    public static boolean isTty(int fd) {
        // On Windows, avoid System.console() entirely — it triggers the
        // JDK's internal JLine terminal initialization (JnaWinSysTerminal),
        // which starts a WindowsStreamPump thread that competes with our
        // own pump for ReadConsoleInputW events, causing lost keystrokes (#276).
        if (OSUtils.IS_WINDOWS) {
            Boolean winResult = tryWindowsConsoleHandle();
            if (winResult != null) {
                return winResult;
            }
            // Fallback for Windows when native library is not available:
            // deny by default — ExternalTerminal handles pipes correctly,
            // and claiming a console we can't drive is dangerous.
            return false;
        }
        // Try Console.isTerminal() first (Java 22+, no FFM needed).
        Boolean consoleResult = tryConsoleIsTerminal();
        if (consoleResult != null) {
            return consoleResult;
        }
        // Fallback: System.console() != null heuristic (pre-Java 22)
        return System.console() != null;
    }

    /**
     * Try Console.isTerminal() (Java 22+). Returns null if not available.
     */
    private static Boolean tryConsoleIsTerminal() {
        Console console = System.console();
        if (console == null) {
            return Boolean.FALSE;
        }
        try {
            Method isTerminal = Console.class.getMethod("isTerminal");
            return (Boolean) isTerminal.invoke(console);
        } catch (NoSuchMethodException e) {
            // Pre-Java 22: Console.isTerminal() doesn't exist
            return null;
        } catch (Exception e) {
            return null;
        }
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
     * Try to detect a Windows console via WinConsoleNative.getStdHandle() +
     * getConsoleMode(). This avoids System.console() which triggers the JDK's
     * internal JLine terminal (JnaWinSysTerminal + WindowsStreamPump) that
     * competes for ReadConsoleInputW events (#276).
     * <p>
     * Called directly (no reflection): the base-layer JNI declarations and
     * the Java 22 FFM overlay share the same signatures, so the call
     * resolves to whichever variant the runtime loads. WinConsoleNative's
     * static init is side-effect-free (loads JNI DLL or creates FFM
     * downcall handles — no threads, no pumps); any linkage failure
     * surfaces as an Error caught below.
     *
     * @return TRUE if a valid console handle exists, FALSE if piped/redirected,
     *         null if the native library is not available
     */
    private static Boolean tryWindowsConsoleHandle() {
        try {
            long handle = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
            if (handle == WinConsoleNative.INVALID_HANDLE) {
                return Boolean.FALSE;
            }
            // getConsoleMode returns -1 on failure (pipe/redirected)
            return WinConsoleNative.getConsoleMode(handle) != -1;
        } catch (Throwable e) {
            LOGGER.log(Level.FINE, "WinConsoleNative not available for TTY detection", e);
            return null;
        }
    }

}
