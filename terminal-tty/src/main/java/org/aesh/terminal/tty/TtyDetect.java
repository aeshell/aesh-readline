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
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.detect.ProcessRunner;
import org.aesh.terminal.tty.impl.WinConsoleNative;
import org.aesh.terminal.utils.PlatformContext;

/**
 * Utility for detecting whether file descriptors are connected to a terminal.
 * <p>
 * Each descriptor is examined independently: Windows probes the matching
 * standard handle with {@code GetConsoleMode}, POSIX answers per-fd (fast
 * path or {@code test -t}), and Cygwin/MSYS runs {@code test -t} for the
 * descriptor. Both answer per-fd, so mixed redirection (e.g. tty
 * stdout with piped stdin) reports each stream truthfully.
 * <p>
 * POSIX selection policy (#399): on supported hosts (Linux/macOS on
 * x86_64/aarch64) with Java 22+ and native access enabled, one in-process
 * {@code isatty} downcall answers first and a completed call is final,
 * including valid negatives — no subprocess runs. Only a missing
 * capability (older Java, denied native access, unsupported host) falls
 * through to the bounded {@code test -t} probe. Cygwin/MSYS and native
 * Windows keep their existing branches and never consult the fast path.
 * Where the platform cannot answer per-fd (no {@code sh} available),
 * detection falls back to the whole-console {@code Console.isTerminal()}
 * approximation and finally the {@code System.console()} heuristic, in
 * that order.
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

    /**
     * Lazy logger holder: creating the logger runs the JDK's
     * {@code LoggerFinder} ServiceLoader scan over the whole class path,
     * so it must not happen in {@code <clinit>} — the first
     * {@code isTty} call on an application startup path would otherwise
     * pay ~50ms before any logging is needed. The logger's only use is
     * an exceptional Windows-native-fallback path, so normal startup
     * never initializes this class. Initialization-on-demand is
     * thread-safe by the JVM's class-init guarantees.
     */
    private static final class Log {
        static final Logger LOGGER = Logger.getLogger(TtyDetect.class.getName());
    }

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
     * standard handle, POSIX tries the in-process {@code isatty} fast path
     * where available and otherwise {@code test -t} for the descriptor, so
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
        return isTty(fd, context, PosixTtyProbe::tryIsTty, f -> tryTestT(f));
    }

    /**
     * Selection with injectable POSIX probes, for tests.
     * <p>
     * Call-scoped: each function answers one selection and is never
     * stored. Production passes the real FFM helper and the real
     * {@code test -t} probe; tests pass counted, scripted, or throwing
     * answers to prove call counts and routing without timing
     * assertions or subprocesses. Not public API: no selection knob,
     * no installed or mutable transport.
     *
     * @param fd the file descriptor (0=stdin, 1=stdout, 2=stderr)
     * @param context the captured platform facts
     * @param nativeProbe the FFM isatty answer (null = unavailable)
     * @param shellProbe the {@code test -t} answer (null = unavailable)
     * @return true if the file descriptor is connected to a terminal
     */
    static boolean isTty(int fd, PlatformContext context,
            IntFunction<Boolean> nativeProbe, IntFunction<Boolean> shellProbe) {
        if (fd < FD_STDIN || fd > FD_STDERR) {
            return false;
        }
        // Cygwin/mintty descriptors may be pipe-backed Windows handles.
        // Check the descriptor through its POSIX runtime, not through
        // GetConsoleMode. In particular, stdin being a PTY does not prove
        // that stdout is one: redirection must still suppress ANSI output.
        if (context.isCygwin()) {
            Boolean exact = shellProbe.apply(fd);
            if (exact != null) {
                return exact;
            }
        }
        // On Windows, avoid System.console() entirely — it triggers the
        // JDK's internal JLine terminal initialization (JnaWinSysTerminal),
        // which starts a WindowsStreamPump thread that competes with our
        // own pump for ReadConsoleInputW events, causing lost keystrokes (#276).
        if (context.isWindows()) {
            return isWindowsConsole(fd);
        }
        // Fast path on supported POSIX hosts: one in-process isatty
        // instead of a `sh -c "test -t"` child (#399). A completed call
        // answers for the descriptor itself, including valid negatives;
        // only a missing capability (null) falls through to the shell.
        if (context.isFfmPosixSupported()) {
            Boolean fast = nativeProbe.apply(fd);
            if (fast != null) {
                return fast;
            }
        }
        // Exact per-fd answer wherever sh exists (all POSIX, all Java
        // versions, no native access needed).
        Boolean exact = shellProbe.apply(fd);
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
     * Check for a native Windows console on one standard descriptor.
     * <p>
     * Unlike {@link #isTty(int)}, this never accepts a Cygwin/MSYS PTY.
     * Native Windows providers need this transport-specific proof even
     * when the process runs inside a POSIX shell. Probed fresh each time;
     * unavailable native access and nonstandard descriptors return false.
     *
     * @param fd the standard descriptor, 0-2
     * @return true only when GetConsoleMode succeeds
     */
    public static boolean isWindowsConsole(int fd) {
        return Boolean.TRUE.equals(tryWindowsConsoleHandle(fd));
    }

    /**
     * Builds the {@code test -t} probe subprocess for one fd.
     * Separated so tests can substitute hanging or scripted children.
     * Production binds the tested fd to INHERIT (the child must answer
     * for OUR descriptor) and pipes the rest.
     */
    interface ProbeBuilder {
        ProcessBuilder build(int fd);
    }

    /**
     * Probe one descriptor with {@code test -t}, i.e. isatty(fd).
     * <p>
     * The tested descriptor must be inherited: the child answers for its
     * own fds, so the child descriptor under test is bound to the
     * parent's (INHERIT) while the other two stay piped and drained.
     * Default-PIPE stdio would make stdout/stderr probes test the pipe
     * instead of the terminal — always false, including for consoles.
     * <p>
     * The probe runs under a bounded deadline: a child that never exits
     * (seen headless on Windows, where it wedged augmentation behind this
     * call) degrades to unknown instead of hanging the caller. Streams are
     * never merged: an INHERIT binding is ground truth under test.
     *
     * @param fd the file descriptor (non-negative)
     * @return TRUE/FALSE from the exit status, or null when sh is unavailable
     *         or the probe exceeds its deadline
     */
    private static Boolean tryTestT(int fd) {
        return tryTestT(fd, TtyDetect::defaultProbeBuilder);
    }

    static Boolean tryTestT(int fd, ProbeBuilder starter) {
        final ProcessBuilder builder;
        try {
            builder = starter.build(fd);
        } catch (Exception e) {
            return null;
        }
        // Bounded drain + wait + reap. An exiting child resolves in
        // milliseconds; a stuck one (seen headless on Windows, wedging
        // augmentation behind this call) degrades to unknown instead of
        // hanging the caller the way the old unbounded drain-then-waitFor
        // did. No stream merging: every INHERIT binding is ground truth
        // under test (merging stderr into a piped stdout would probe the
        // pipe, not the descriptor).
        final Process process;
        try {
            process = builder.start();
        } catch (Exception e) {
            return null;
        }
        Thread outDrainer = drainAsync(process.getInputStream());
        Thread errDrainer = drainAsync(process.getErrorStream());
        final boolean finished;
        try {
            finished = process.waitFor(ProcessRunner.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return null;
        }
        if (!finished) {
            process.destroyForcibly();
            closeQuietly(process.getInputStream());
            closeQuietly(process.getErrorStream());
            return null;
        }
        joinQuietly(outDrainer);
        joinQuietly(errDrainer);
        try {
            return process.exitValue() == 0;
        } catch (Exception e) {
            return null;
        }
    }

    private static Thread drainAsync(final java.io.InputStream in) {
        Thread drainer = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buf = new byte[8192];
                try {
                    while (in.read(buf) != -1) {
                        // Discard; the probe answers by exit status only.
                    }
                } catch (java.io.IOException ignored) {
                    // Destroyed or closed: partial output stands.
                }
            }
        }, "tty-probe-drain");
        drainer.setDaemon(true);
        drainer.start();
        return drainer;
    }

    private static void joinQuietly(Thread drainer) {
        try {
            drainer.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(java.io.InputStream in) {
        try {
            in.close();
        } catch (Exception ignored) {
        }
    }

    private static ProcessBuilder defaultProbeBuilder(int fd) {
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
        return builder;
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
     * Read the cached stdin result without probing. Package-visible so tests
     * pin fd facts without reflection (native-image needs no registration).
     *
     * @return -1 when uncomputed, 0 when not a terminal, 1 when a terminal
     */
    static int cachedStdinForTests() {
        return cachedStdin;
    }

    /**
     * Pin the cached stdin result. Package-visible so tests pin fd facts
     * without reflection (native-image needs no registration).
     *
     * @param value -1 to restore uncomputed, 0 for not a terminal, 1 for a terminal
     */
    static void cachedStdinForTests(int value) {
        cachedStdin = value;
    }

    /**
     * Read the cached stdout result without probing. Package-visible so tests
     * pin fd facts without reflection (native-image needs no registration).
     *
     * @return -1 when uncomputed, 0 when not a terminal, 1 when a terminal
     */
    static int cachedStdoutForTests() {
        return cachedStdout;
    }

    /**
     * Pin the cached stdout result. Package-visible so tests pin fd facts
     * without reflection (native-image needs no registration).
     *
     * @param value -1 to restore uncomputed, 0 for not a terminal, 1 for a terminal
     */
    static void cachedStdoutForTests(int value) {
        cachedStdout = value;
    }

    /**
     * Read the cached stderr result without probing. Package-visible so tests
     * pin fd facts without reflection (native-image needs no registration).
     *
     * @return -1 when uncomputed, 0 when not a terminal, 1 when a terminal
     */
    static int cachedStderrForTests() {
        return cachedStderr;
    }

    /**
     * Pin the cached stderr result. Package-visible so tests pin fd facts
     * without reflection (native-image needs no registration).
     *
     * @param value -1 to restore uncomputed, 0 for not a terminal, 1 for a terminal
     */
    static void cachedStderrForTests(int value) {
        cachedStderr = value;
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
     * resolves to whichever variant the runtime loads. Loading is lazy
     * (first native call, never class touch) and side-effect-free apart
     * from the load itself (no threads, no pumps); any linkage failure
     * surfaces as an Error caught below.
     *
     * @return TRUE if a valid console handle exists, FALSE if piped/redirected,
     *         null if the native library is not available
     */
    static Boolean tryWindowsConsoleHandle() {
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
            Log.LOGGER.log(Level.FINE, "WinConsoleNative not available for TTY detection", e);
            return null;
        }
    }

}
