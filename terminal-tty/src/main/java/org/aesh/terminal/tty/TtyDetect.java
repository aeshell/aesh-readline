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
import org.aesh.terminal.utils.PlatformContext;

/**
 * Utility for detecting whether file descriptors are connected to a terminal.
 * <p>
 * Each descriptor is examined independently: Windows probes the matching
 * standard handle with {@code GetConsoleMode}, POSIX runs {@code test -t}
 * for the descriptor. Both answer per-fd, so mixed redirection (e.g. tty
 * stdout with piped stdin) reports each stream truthfully.
 * <p>
 * No native access is needed on POSIX at any Java version. Where the
 * platform cannot answer per-fd (no {@code sh} available), detection falls
 * back to the whole-console {@code Console.isTerminal()} approximation and
 * finally the {@code System.console()} heuristic, in that order.
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
     * Each descriptor is examined on its own: Windows probes the matching
     * standard handle, POSIX runs {@code test -t} for the descriptor, so
     * mixed redirection reports each stream truthfully. Descriptors other
     * than 0-2, and negative values, always report false without spawning
     * anything. Where POSIX cannot run {@code sh}, the whole-console
     * {@code Console.isTerminal()} approximation applies instead.
     *
     * @param fd the file descriptor (0=stdin, 1=stdout, 2=stderr)
     * @return true if the file descriptor is connected to a terminal
     */
    public static boolean isTty(int fd) {
        return isTty(fd, PlatformContext.system());
    }

    /**
     * Check if the given file descriptor is connected to a terminal,
     * with the OS branch taken from captured platform facts.
     * <p>
     * Only the OS branch is captured: liveness itself (per-fd
     * {@code test -t}, console handles, {@code Console.isTerminal()}) is
     * always probed fresh, since redirection can change between calls.
     *
     * @param fd the file descriptor (0=stdin, 1=stdout, 2=stderr)
     * @param context the captured platform facts
     * @return true if the file descriptor is connected to a terminal
     */
    public static boolean isTty(int fd, PlatformContext context) {
        // On Windows, avoid System.console() entirely — it triggers the
        // JDK's internal JLine terminal initialization (JnaWinSysTerminal),
        // which starts a WindowsStreamPump thread that competes with our
        // own pump for ReadConsoleInputW events, causing lost keystrokes (#276).
        if (context.isWindows()) {
            Boolean winResult = tryWindowsConsoleHandle(fd);
            if (winResult != null) {
                return winResult;
            }
            // Fallback for Windows when native library is not available:
            // deny by default — ExternalTerminal handles pipes correctly,
            // and claiming a console we can't drive is dangerous.
            return false;
        }
        if (fd < 0) {
            return false;
        }
        // Exact per-fd answer wherever sh exists (all POSIX, all Java
        // versions, no native access needed).
        Boolean exact = tryTestT(fd);
        if (exact != null) {
            return exact;
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
     * Probe one descriptor with {@code test -t}, i.e. isatty(fd).
     * <p>
     * The tested descriptor must be inherited: the child answers for its
     * own fds, so the child descriptor under test is bound to the
     * parent's (INHERIT) while the other two stay piped and drained.
     * Default-PIPE stdio would make stdout/stderr probes test the pipe
     * instead of the terminal — always false, including for consoles.
     *
     * @param fd the file descriptor (non-negative)
     * @return TRUE/FALSE from the exit status, or null when sh is unavailable
     */
    private static Boolean tryTestT(int fd) {
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("sh", "-c", "test -t " + fd);
            if (fd == FD_STDIN) {
                builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            }
            if (fd == FD_STDOUT) {
                builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            }
            if (fd == FD_STDERR) {
                builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            }
            process = builder.start();
            // Drain piped streams (error text for bogus fds is tiny;
            // inherited ones read EOF instantly). Never merged: stray
            // text must not leak into an inherited user stream.
            drainQuietly(process.getInputStream());
            drainQuietly(process.getErrorStream());
            process.waitFor();
            return process.exitValue() == 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static void drainQuietly(java.io.InputStream in) {
        try {
            byte[] buf = new byte[64];
            while (in.read(buf) != -1) {
                // Discard; tiny.
            }
        } catch (Exception ignored) {
        }
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
     * getConsoleMode() for the standard input handle. Kept for the
     * stdin-specific probe; per-fd callers use {@link #tryWindowsConsoleHandle(int)}.
     * This avoids System.console() which triggers the JDK's
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
        return tryWindowsConsoleHandle(FD_STDIN);
    }

    /**
     * Try to detect a Windows console for one standard descriptor.
     * Descriptors other than stdin/stdout/stderr report false without
     * touching native code: GetStdHandle knows no other handles, and a
     * bogus fd must never report true.
     *
     * @param fd the file descriptor (0=stdin, 1=stdout, 2=stderr)
     * @return TRUE for a console handle, FALSE for pipes, redirects, and
     *         other descriptors, null if the native library is unavailable
     */
    private static Boolean tryWindowsConsoleHandle(int fd) {
        int stdHandle;
        if (fd == FD_STDIN) {
            stdHandle = WinConsoleNative.STD_INPUT_HANDLE;
        } else if (fd == FD_STDOUT) {
            stdHandle = WinConsoleNative.STD_OUTPUT_HANDLE;
        } else if (fd == FD_STDERR) {
            stdHandle = WinConsoleNative.STD_ERROR_HANDLE;
        } else {
            return Boolean.FALSE;
        }
        try {
            long handle = WinConsoleNative.getStdHandle(stdHandle);
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
