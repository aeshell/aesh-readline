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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
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
 * POSIX selection policy (#399, #400): where the descriptor entry already
 * classifies the fd (pipes, redirections, terminals visible through the
 * filesystem), a readlink answer comes first and skips every probe.
 * Otherwise, on supported hosts (Linux/macOS on x86_64/aarch64) with
 * Java 22+ and native access enabled, one in-process {@code isatty}
 * downcall answers and a completed call is final, including valid
 * negatives — no subprocess runs. Only when neither can answer (older
 * Java, denied native access, unsupported host, unknown descriptor)
 * does the bounded {@code test -t} probe run. Cygwin/MSYS and native
 * Windows keep their existing branches and never consult these paths.
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
     * Each descriptor is examined on its own: a readlink answer comes
     * first where the platform exposes one, then the in-process
     * {@code isatty} fast path where available and otherwise
     * {@code test -t} for the descriptor, so mixed redirection reports
     * each stream truthfully. Descriptors other than 0-2, and negative
     * values, always report false without spawning anything. Where POSIX
     * cannot run {@code sh}, the whole-console {@code Console.isTerminal()}
     * approximation applies instead.
     *
     * @param fd the file descriptor (0=stdin, 1=stdout, 2=stderr)
     * @return true if the file descriptor is connected to a terminal
     */
    public static boolean isTty(int fd) {
        PlatformContext context = PlatformContext.system();
        Boolean link = tryReadlinkTty(fd, context);
        if (link != null) {
            return link;
        }
        return isTty(fd, context);
    }

    /**
     * Check if the given file descriptor is connected to a terminal,
     * with the OS branch taken from captured platform facts.
     * <p>
     * Only the OS branch is captured: liveness itself (readlink answers,
     * per-fd {@code test -t}, console handles, {@code Console.isTerminal()})
     * is always probed fresh, since redirection can change between calls.
     *
     * @param fd the file descriptor (0=stdin, 1=stdout, 2=stderr)
     * @param context the captured platform facts
     * @return true if the file descriptor is connected to a terminal
     */
    public static boolean isTty(int fd, PlatformContext context) {
        Boolean link = tryReadlinkTty(fd, context);
        if (link != null) {
            return link;
        }
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
     * Answer a standard descriptor from its /proc (or /dev/fd) symlink,
     * without spawning (#400).
     * <p>
     * A single {@code readlink} classifies the common shapes: pipes,
     * sockets and {@code /dev/null} are never terminals, pty slaves
     * always are, regular files never are. Anything else — unknown
     * devices, serial consoles, unreadable links, unsupported platforms
     * — declines with null and the caller falls through to the FFM and
     * shell probes unchanged. Only answers when the answer is certain;
     * never guesses.
     *
     * @param fd the file descriptor (0=stdin, 1=stdout, 2=stderr)
     * @param context the captured platform facts
     * @return TRUE/FALSE for a classified shape, or null when unknown
     */
    static Boolean tryReadlinkTty(int fd, PlatformContext context) {
        if (fd < FD_STDIN || fd > FD_STDERR) {
            return null;
        }
        // Cross-runtime descriptors stay out: a POSIX view of a Windows
        // or Cygwin handle answers nothing about its own runtime (#399).
        if (context.isWindows()) {
            return null;
        }
        String dir;
        if (context.isLinux()) {
            dir = "/proc/self/fd/";
        } else if (context.isMac()) {
            dir = "/dev/fd/";
        } else {
            return null;
        }
        String target;
        try {
            target = Files.readSymbolicLink(Paths.get(dir + fd)).toString();
        } catch (Exception e) {
            return null;
        }
        // A link to another fd entry resolves to the concrete target with
        // one extra hop (e.g. through /dev/stdin); anything odd declines.
        if (target.startsWith("/proc/self/fd/") || target.startsWith("/dev/fd/")) {
            try {
                target = Files.readSymbolicLink(Paths.get(target)).toString();
            } catch (Exception e) {
                return null;
            }
        }
        if (target.startsWith("pipe:") || target.startsWith("socket:")) {
            return Boolean.FALSE;
        }
        if (target.equals("/dev/null")) {
            return Boolean.FALSE;
        }
        boolean statOk = true;
        boolean regular = false;
        boolean directory = false;
        boolean other = false;
        try {
            BasicFileAttributes attrs = Files.readAttributes(Paths.get(target), BasicFileAttributes.class);
            regular = attrs.isRegularFile();
            directory = attrs.isDirectory();
            other = attrs.isOther();
        } catch (Exception e) {
            statOk = false;
        }
        return classifyLinkTarget(target, statOk, regular, directory, other);
    }

    /**
     * Classify one symlink target. Pure function of its arguments so the
     * table is unit-testable without touching the filesystem.
     * <p>
     * Only certain shapes answer: anonymously piped, socket-bound and
     * null descriptors are never terminals; a pty slave behind a
     * confirmed non-regular, non-directory target always is. Everything
     * else — serial consoles, /dev/tty itself, stray devices, dangling
     * links, unreadable targets — is UNKNOWN (null) and falls through to
     * the regular probes. A wrong answer here would be worse than a
     * spawn, so the unknown set is deliberately wide.
     *
     * @param target the readlink result, or null
     * @param statOk whether the target statted successfully
     * @param regular whether the final target is a regular file
     * @param directory whether the final target is a directory
     * @param other whether the final target is neither file, dir nor link
     * @return TRUE/FALSE for certain shapes, null when unknown
     */
    static Boolean classifyLinkTarget(String target, boolean statOk, boolean regular,
            boolean directory, boolean other) {
        if (target == null) {
            return null;
        }
        if (target.startsWith("pipe:") || target.startsWith("socket:")) {
            return Boolean.FALSE;
        }
        if (target.equals("/dev/null")) {
            return Boolean.FALSE;
        }
        if (!statOk) {
            return null;
        }
        if (regular || directory) {
            // Neither regular files nor directories are terminal devices.
            return Boolean.FALSE;
        }
        if (target.startsWith("/dev/pts/")) {
            // A pty slave is a terminal by definition. Anything mounted
            // over the path that is not a device (files, directories) is
            // handled above; what remains is the device itself.
            return Boolean.TRUE;
        }
        if (target.startsWith("/dev/tty") || target.equals("/dev/console")) {
            // Serial consoles, /dev/tty itself, the system console: real
            // terminals the table does not claim — let the probes answer.
            return null;
        }
        if (other) {
            // FIFOs, stray character devices, anything else that is
            // definitely not a terminal device behind the above allowlist.
            return Boolean.FALSE;
        }
        return null;
    }

    /**
     * Probe several descriptors with one {@code test -t} child (#400).
     * <p>
     * Each tested descriptor is inherited (the child answers for its own
     * fds, so every descriptor under test is bound to the parent's) while
     * the rest stay piped and drained. Answers travel in the exit code,
     * two bits at a time: bit {@code i} set means {@code test -t} failed
     * for {@code fds[i]}. No output is produced, so inheriting stdout and
     * stderr together is safe — the child's exit status is the whole
     * verdict. Same bounded deadline and reap discipline as the
     * single-fd probe; anything else (no sh, timeout, unparseable code)
     * declines with null and the caller falls back to individual probes.
     *
     * @param fds the descriptors to test, each 0-2
     * @return per-fd TRUE/FALSE in input order, or null when unavailable
     */
    static Boolean[] tryCombinedTestT(int[] fds) {
        if (fds == null || fds.length == 0 || fds.length > 3) {
            return null;
        }
        for (int fd : fds) {
            if (fd < FD_STDIN || fd > FD_STDERR) {
                return null;
            }
        }
        StringBuilder script = new StringBuilder("c=0;");
        for (int i = 0; i < fds.length; i++) {
            script.append("test -t ").append(fds[i]).append(" || c=$((c | ").append(1 << i)
                    .append("));");
        }
        script.append("exit $c");
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", script.toString());
        if (contains(fds, FD_STDIN)) {
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        }
        if (contains(fds, FD_STDOUT)) {
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        }
        if (contains(fds, FD_STDERR)) {
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        }
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
        final int code;
        try {
            code = process.exitValue();
        } catch (Exception e) {
            return null;
        }
        if (code < 0 || code >= (1 << fds.length)) {
            return null;
        }
        Boolean[] answers = new Boolean[fds.length];
        for (int i = 0; i < fds.length; i++) {
            answers[i] = (code & (1 << i)) == 0 ? Boolean.TRUE : Boolean.FALSE;
        }
        return answers;
    }

    private static boolean contains(int[] fds, int fd) {
        for (int f : fds) {
            if (f == fd) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the combined shell probe is worth trying for this platform.
     * <p>
     * Combining only pays when the shell path would spawn: native Windows
     * answers per handle without spawning, and an available FFM binding
     * answers per fd in-process. Everywhere else the first of stdout/stderr
     * may answer both with one child. Cygwin probes through its POSIX
     * runtime first, so it combines too.
     *
     * @param context the captured platform facts
     * @return true when one shell child may answer both descriptors
     */
    static boolean shouldCombine(PlatformContext context) {
        if (context.isCygwin()) {
            return true;
        }
        if (context.isWindows()) {
            return false;
        }
        return !(context.isFfmPosixSupported() && PosixTtyProbe.isNativeAccessEnabled());
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
     * <p>
     * When stderr is also uncomputed and the platform would spawn for it,
     * one shell child answers both descriptors (#400) and both caches are
     * filled together. Otherwise this probes stdout alone.
     *
     * @return true if stdout is a terminal (not piped/redirected)
     */
    public static boolean isStdoutTty() {
        if (cachedStdout == -1) {
            return isStdoutTty(PlatformContext.system());
        }
        return cachedStdout == 1;
    }

    /**
     * Standard-output check on a captured context, with the production
     * probes. Combines when {@link #shouldCombine(PlatformContext)} allows.
     *
     * @param context the captured platform facts
     * @return true if stdout is a terminal (not piped/redirected)
     */
    static boolean isStdoutTty(PlatformContext context) {
        fillReadlinkStdCaches(context);
        Function<int[], Boolean[]> combined = shouldCombine(context) ? TtyDetect::tryCombinedTestT : null;
        return isStdoutTty(context, PosixTtyProbe::tryIsTty, f -> tryTestT(f), combined);
    }

    /**
     * Standard-output check with injectable POSIX probes, for tests.
     * Call-scoped like {@link #isTty(int, PlatformContext, IntFunction, IntFunction)}:
     * production passes the real helper, the real {@code test -t} probe,
     * and the real combined probe (or null when combining would not pay);
     * tests pass counted, scripted, or throwing answers.
     *
     * @param context the captured platform facts
     * @param nativeProbe the FFM isatty answer (null = unavailable)
     * @param shellProbe the {@code test -t} answer (null = unavailable)
     * @param combinedProbe the one-child answer for stdout+stderr in order
     *        (null = unavailable), or null to probe stdout alone
     * @return true if stdout is a terminal (not piped/redirected)
     */
    static boolean isStdoutTty(PlatformContext context, IntFunction<Boolean> nativeProbe,
            IntFunction<Boolean> shellProbe, Function<int[], Boolean[]> combinedProbe) {
        if (cachedStdout == -1) {
            if (cachedStderr == -1 && combinedProbe != null) {
                Boolean[] both = combinedProbe.apply(new int[] { FD_STDOUT, FD_STDERR });
                if (both != null && both.length == 2 && both[0] != null && both[1] != null) {
                    cachedStdout = both[0] ? 1 : 0;
                    cachedStderr = both[1] ? 1 : 0;
                    return cachedStdout == 1;
                }
            }
            cachedStdout = isTty(FD_STDOUT, context, nativeProbe, shellProbe) ? 1 : 0;
        }
        return cachedStdout == 1;
    }

    /**
     * Check if standard error is connected to a terminal.
     * Result is cached after the first call.
     * <p>
     * When stdout is also uncomputed and the platform would spawn for it,
     * one shell child answers both descriptors (#400) and both caches are
     * filled together. Otherwise this probes stderr alone.
     *
     * @return true if stderr is a terminal (not piped/redirected)
     */
    public static boolean isStderrTty() {
        if (cachedStderr == -1) {
            return isStderrTty(PlatformContext.system());
        }
        return cachedStderr == 1;
    }

    /**
     * Standard-error check on a captured context, with the production
     * probes. Combines when {@link #shouldCombine(PlatformContext)} allows.
     *
     * @param context the captured platform facts
     * @return true if stderr is a terminal (not piped/redirected)
     */
    static boolean isStderrTty(PlatformContext context) {
        fillReadlinkStdCaches(context);
        Function<int[], Boolean[]> combined = shouldCombine(context) ? TtyDetect::tryCombinedTestT : null;
        return isStderrTty(context, PosixTtyProbe::tryIsTty, f -> tryTestT(f), combined);
    }

    /**
     * Fill the stdout/stderr caches from certain readlink answers, before
     * any probe that could spawn or initialize native bindings runs.
     * Answers nothing on its own when the descriptors stay unclassified.
     *
     * @param context the captured platform facts
     */
    private static void fillReadlinkStdCaches(PlatformContext context) {
        if (cachedStdout == -1) {
            Boolean link = tryReadlinkTty(FD_STDOUT, context);
            if (link != null) {
                cachedStdout = link ? 1 : 0;
            }
        }
        if (cachedStderr == -1) {
            Boolean link = tryReadlinkTty(FD_STDERR, context);
            if (link != null) {
                cachedStderr = link ? 1 : 0;
            }
        }
    }

    /**
     * Standard-error check with injectable POSIX probes, for tests.
     * Call-scoped like {@link #isStdoutTty(PlatformContext, IntFunction, IntFunction, Function)}:
     * production passes the real probes, tests pass counted, scripted, or
     * throwing answers.
     *
     * @param context the captured platform facts
     * @param nativeProbe the FFM isatty answer (null = unavailable)
     * @param shellProbe the {@code test -t} answer (null = unavailable)
     * @param combinedProbe the one-child answer for stdout+stderr in order
     *        (null = unavailable), or null to probe stderr alone
     * @return true if stderr is a terminal (not piped/redirected)
     */
    static boolean isStderrTty(PlatformContext context, IntFunction<Boolean> nativeProbe,
            IntFunction<Boolean> shellProbe, Function<int[], Boolean[]> combinedProbe) {
        if (cachedStderr == -1) {
            if (cachedStdout == -1 && combinedProbe != null) {
                Boolean[] both = combinedProbe.apply(new int[] { FD_STDOUT, FD_STDERR });
                if (both != null && both.length == 2 && both[0] != null && both[1] != null) {
                    cachedStdout = both[0] ? 1 : 0;
                    cachedStderr = both[1] ? 1 : 0;
                    return cachedStderr == 1;
                }
            }
            cachedStderr = isTty(FD_STDERR, context, nativeProbe, shellProbe) ? 1 : 0;
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
