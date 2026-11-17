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
package org.aesh.terminal.tty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.aesh.terminal.utils.OSUtils;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.Test;

/**
 * Per-fd TTY detection: each descriptor must answer for itself.
 * <p>
 * The ground-truth legs agree with {@code test -t} in whatever environment
 * runs them (PTY or pipes), so they need no fixture terminal. The mixed
 * redirection matrix spawns a child JVM under {@code script} (real PTY)
 * with controlled stdin/stdout/stderr, plus one direct (no-PT Y) case with
 * all stdio on files.
 * <p>
 * Every matrix case runs twice: with the FFM fast path explicitly enabled
 * ({@code --enable-native-access=ALL-UNNAMED}) and explicitly denied
 * ({@code --enable-native-access=java.base} with inherited native-access
 * options scrubbed). The child proves which binding ran on its identity
 * line; on Java 22+ the overlay must load in both modes, so a shadowed
 * overlay or broken MRJAR fails instead of silently covering fallback.
 * The child also prints its own {@code test -t} agreement line for the
 * exact same inherited descriptors, and every verdict travels on a report
 * file ({@code TTY_FD_PROBE_REPORT}) — the only channel that survives
 * redirected or closed stderr.
 * <p>
 * The child classpath prefers packaged jars (production MRJAR layout) and
 * falls back to exploded classes only when jars are absent or stale; the
 * resolved layout is asserted against the child's actual code source. A
 * stale jar never tests old code: jar content older than the compiled
 * classes resolves exploded instead.
 */
public class TtyDetectPerFdTest {

    /** Native-access modes every matrix case runs in. */
    private enum NativeMode {
        ENABLED,
        DISABLED
    }

    /** One parsed child verdict triple. */
    private static final class Verdict {
        String tty;
        String shell;
        boolean overlay;
        boolean nativeEnabled;
        String lib;
        boolean packaged;
    }

    /** Resolved child classpath plus the layout that won. */
    private static final class ChildClassPath {
        final String spec;
        final boolean packaged;

        ChildClassPath(String spec, boolean packaged) {
            this.spec = spec;
            this.packaged = packaged;
        }
    }

    /** Background pipe drainer with a bounded join. */
    private static final class Drain {
        private final ByteArrayOutputStream captured = new ByteArrayOutputStream();
        private final Thread thread;

        Drain(final InputStream in) {
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    byte[] buf = new byte[8192];
                    try {
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            captured.write(buf, 0, n);
                        }
                    } catch (IOException ignored) {
                        // Destroyed or closed: partial output stands.
                    }
                }
            }, "tty-matrix-drain");
            thread.setDaemon(true);
            thread.start();
        }

        String join(String what) {
            try {
                thread.join(TimeUnit.SECONDS.toMillis(DRAIN_TIMEOUT_SECONDS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted draining " + what);
            }
            if (thread.isAlive()) {
                fail("draining " + what + " did not finish " + DRAIN_TIMEOUT_SECONDS
                        + "s after child exit");
            }
            try {
                return captured.toString("UTF-8");
            } catch (Exception e) {
                fail("cannot decode " + what + ": " + e);
                return null;
            }
        }

        String partial() {
            try {
                return captured.toString("UTF-8");
            } catch (Exception e) {
                return "";
            }
        }
    }

    private static final long CHILD_TIMEOUT_SECONDS = 60;
    private static final long DRAIN_TIMEOUT_SECONDS = 15;

    private static final AtomicInteger ptyMatrixRuns = new AtomicInteger();
    private static final AtomicInteger directMatrixRuns = new AtomicInteger();

    private static boolean isNativeImage() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    private static boolean isJava22OrLater() {
        String spec = System.getProperty("java.specification.version", "8");
        try {
            if (spec.startsWith("1.")) {
                return false;
            }
            return Integer.parseInt(spec) >= 22;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean hasPosixSh() {
        return !OSUtils.IS_WINDOWS
                && (new File("/bin/sh").canExecute() || new File("/usr/bin/sh").canExecute());
    }

    private static void assumePosixSh() {
        Assume.assumeFalse("POSIX shell probing needs non-Windows", OSUtils.IS_WINDOWS);
        Assume.assumeTrue("no executable POSIX shell",
                new File("/bin/sh").canExecute() || new File("/usr/bin/sh").canExecute());
    }

    /**
     * Run {@code test -t} directly: the ground truth TtyDetect must agree with.
     * The tested descriptor is inherited, or the child would probe a pipe.
     */
    private static boolean shellGroundTruth(int fd) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", "test -t " + fd);
        if (fd == 0) {
            builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
        }
        if (fd == 1) {
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        }
        if (fd == 2) {
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        }
        Process process = builder.start();
        byte[] buf = new byte[64];
        while (process.getInputStream().read(buf) != -1) {
            // Drain; tiny.
        }
        while (process.getErrorStream().read(buf) != -1) {
            // Drain; tiny.
        }
        process.waitFor(10, TimeUnit.SECONDS);
        return process.exitValue() == 0;
    }

    @Test
    public void testAgreesWithShellGroundTruth() throws Exception {
        assumePosixSh();
        for (int fd = 0; fd <= 2; fd++) {
            assertEquals("fd " + fd + " must match test -t",
                    shellGroundTruth(fd), TtyDetect.isTty(fd));
        }
    }

    @Test
    public void testInvalidFdsAlwaysFalse() {
        // No environment, native image, or platform may report these true.
        // No subprocess runs for negatives; bogus positives fail here.
        assertFalse(TtyDetect.isTty(-1));
        assertFalse(TtyDetect.isTty(99));
        assertFalse(TtyDetect.isTty(999));
        assertFalse(TtyDetect.isTty(Integer.MAX_VALUE));
    }

    private static Boolean scriptUsable;

    private static boolean isMacScript() {
        return OSUtils.IS_OSX;
    }

    private static boolean probeScript(List<String> probe) {
        try {
            Process process = new ProcessBuilder(probe).redirectErrorStream(true).start();
            Drain drain = new Drain(process.getInputStream());
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            drain.join("script probe");
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean canRunScriptMatrix() {
        if (OSUtils.IS_WINDOWS || isNativeImage()) {
            return false;
        }
        if (scriptUsable != null) {
            return scriptUsable;
        }
        scriptUsable = false;
        // Probe the host-OS spelling first, then the other one: whichever
        // actually runs a trivial command wins. A misparsed spelling that
        // exits 0 without a PTY cannot slip through — the matrix verdicts
        // themselves prove PTY-ness via the shell agreement line.
        List<String> primary = isMacScript()
                ? Arrays.asList("script", "-q", "/dev/null", "true")
                : Arrays.asList("script", "-qec", "true", "/dev/null");
        List<String> fallback = isMacScript()
                ? Arrays.asList("script", "-qec", "true", "/dev/null")
                : Arrays.asList("script", "-q", "/dev/null", "true");
        scriptUsable = probeScript(primary) || probeScript(fallback);
        return scriptUsable;
    }

    /**
     * Quote one argv for embedding in {@code sh -c '...'}. Paths are never
     * assumed space-free: every argument is single-quoted.
     */
    private static String quoteShellArg(String arg) {
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    private static String joinShell(List<String> argv) {
        StringBuilder out = new StringBuilder();
        for (String arg : argv) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(quoteShellArg(arg));
        }
        return out.toString();
    }

    private static String javaBin() {
        String extension = System.getProperty("os.name", "").startsWith("Windows") ? ".exe" : "";
        return System.getProperty("java.home") + "/bin/java" + extension;
    }

    private static File newestJar(File dir, String prefix) {
        if (!dir.isDirectory()) {
            return null;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return null;
        }
        File best = null;
        for (File file : files) {
            String name = file.getName().toLowerCase();
            if (!file.isFile() || !name.startsWith(prefix) || !name.endsWith(".jar")) {
                continue;
            }
            if (name.contains("sources") || name.contains("tests") || name.contains("javadoc")) {
                continue;
            }
            if (best == null || file.lastModified() > best.lastModified()) {
                best = file;
            }
        }
        return best;
    }

    private static long newestClassMtime(File classesDir) {
        if (!classesDir.isDirectory()) {
            return -1;
        }
        long newest = 0;
        List<File> stack = new ArrayList<File>();
        stack.add(classesDir);
        while (!stack.isEmpty()) {
            File current = stack.remove(stack.size() - 1);
            File[] files = current.listFiles();
            if (files == null) {
                continue;
            }
            for (File file : files) {
                if (file.isDirectory()) {
                    stack.add(file);
                } else if (file.getName().endsWith(".class") && file.lastModified() > newest) {
                    newest = file.lastModified();
                }
            }
        }
        return newest;
    }

    private static boolean requirePackaged() {
        return Boolean.getBoolean("aesh.matrix.requirePackaged");
    }

    /**
     * Whether the FFM overlay loads for one matrix child. Packaged jars
     * always carry the versioned overlay on Java 22+; exploded test-classes
     * only when the overlay copy survived (the native compatibility profile
     * deletes it for old-GraalVM testing, and then the base stub declines).
     */
    private static boolean expectedOverlay(boolean packaged) {
        if (!isJava22OrLater()) {
            return false;
        }
        if (packaged) {
            return true;
        }
        return new File("target/test-classes/org/aesh/terminal/tty/PosixTtyProbe.class").isFile();
    }

    /**
     * Resolve the child classpath. Packaged main jars win when present and
     * newer than the compiled classes (so a stale jar never tests old code);
     * otherwise exploded output dirs are used. The probe itself always runs
     * from test-classes, which is current by construction at test time.
     */
    private static ChildClassPath childClassPath() {
        File testClasses = new File("target/test-classes");
        File ttyClasses = new File("target/classes");
        File apiClasses = new File("../terminal-api/target/classes");
        if (!testClasses.isDirectory()) {
            fail("target/test-classes missing; rebuild before running the matrix");
        }
        File ttyJar = newestJar(new File("target"), "terminal-tty-");
        File apiJar = newestJar(new File("../terminal-api/target"), "terminal-api-");
        List<String> entries = new ArrayList<String>();
        boolean packaged;
        if (ttyJar != null && ttyJar.lastModified() >= newestClassMtime(ttyClasses)) {
            entries.add(ttyJar.getAbsolutePath());
            packaged = true;
        } else {
            entries.add(testClasses.getAbsolutePath());
            entries.add(ttyClasses.getAbsolutePath());
            packaged = false;
        }
        if (apiJar != null && apiJar.lastModified() >= newestClassMtime(apiClasses)) {
            entries.add(apiJar.getAbsolutePath());
        } else if (apiClasses.isDirectory()) {
            entries.add(apiClasses.getAbsolutePath());
        } else {
            fail("cannot resolve terminal-api (neither packaged jar nor classes dir)");
        }
        if (packaged) {
            entries.add(testClasses.getAbsolutePath());
        }
        if (requirePackaged() && !packaged) {
            fail("acceptance run requires packaged jars but resolved the exploded layout");
        }
        StringBuilder spec = new StringBuilder();
        for (String entry : entries) {
            if (spec.length() > 0) {
                spec.append(File.pathSeparator);
            }
            spec.append(entry);
        }
        return new ChildClassPath(spec.toString(), packaged);
    }

    /**
     * Child argv as a list (no shell): java, explicit native-access flags on
     * Java 22+, packaged-or-exploded classpath, probe main, probe args.
     * Pre-22 runtimes take no native-access flag (their launcher rejects it)
     * and always decline to the shell.
     */
    private static List<String> childArgv(NativeMode mode, ChildClassPath cp, String... probeArgs) {
        List<String> command = new ArrayList<String>();
        command.add(javaBin());
        if (isJava22OrLater()) {
            command.add(mode == NativeMode.ENABLED
                    ? "--enable-native-access=ALL-UNNAMED"
                    : "--enable-native-access=java.base");
        }
        command.add("-cp");
        command.add(cp.spec);
        command.add("org.aesh.terminal.tty.TtyFdProbe");
        command.addAll(Arrays.asList(probeArgs));
        return command;
    }

    private static String tail(String output, int max) {
        if (output.length() <= max) {
            return output;
        }
        return "[truncated] ..." + output.substring(output.length() - max);
    }

    private static void scrubDisabledEnv(ProcessBuilder builder, NativeMode mode) {
        if (mode == NativeMode.DISABLED) {
            // The test JVM may run with blanket native access; the denied
            // child must not inherit any grant through the environment.
            builder.environment().remove("JAVA_TOOL_OPTIONS");
            builder.environment().remove("JDK_JAVA_OPTIONS");
            builder.environment().remove("_JAVA_OPTIONS");
        }
    }

    /**
     * Wait out one matrix child with its capture bounded together: the drain
     * thread starts before the wait, and both share the deadline. A child
     * that exceeds its lifetime fails the test — it never turns into an
     * assumption skip.
     */
    private static void awaitChild(Process process, Drain drain, String describe, NativeMode mode) {
        boolean finished = false;
        try {
            finished = process.waitFor(CHILD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            fail("interrupted waiting for matrix child " + describe);
        }
        if (!finished) {
            process.destroyForcibly();
            try {
                drain.thread.join(TimeUnit.SECONDS.toMillis(5));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            fail("matrix child timed out after " + CHILD_TIMEOUT_SECONDS + "s (" + describe + ", "
                    + mode + "):\n" + tail(drain.partial(), 2000));
        }
    }

    private static String readReport(File report, String describe, NativeMode mode, String output) {
        try {
            return new String(Files.readAllBytes(report.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("matrix child wrote no report file (" + describe + ", " + mode
                    + "); combined output:\n" + tail(output, 2000));
            return null;
        }
    }

    private static Verdict parseVerdict(String content, String describe, NativeMode mode) {
        String tty = null;
        String shell = null;
        String identity = null;
        for (String line : content.split("\\r?\\n")) {
            if (line.startsWith("0:")) {
                tty = line.trim();
            } else if (line.startsWith("shell 0:")) {
                shell = line.substring("shell ".length()).trim();
            } else if (line.startsWith("identity ")) {
                identity = line.trim();
            }
        }
        if (tty == null || shell == null || identity == null) {
            fail("probe report incomplete (" + describe + ", " + mode + "):\n" + tail(content, 2000));
        }
        Verdict verdict = new Verdict();
        verdict.tty = tty;
        verdict.shell = shell;
        String[] tokens = identity.split(" ");
        if (tokens.length != 4 || !tokens[1].startsWith("overlay=") || !tokens[2].startsWith("native=")
                || !tokens[3].startsWith("lib=")) {
            fail("probe identity malformed (" + describe + ", " + mode + "): " + identity);
        }
        verdict.overlay = Boolean.parseBoolean(tokens[1].substring("overlay=".length()));
        verdict.nativeEnabled = Boolean.parseBoolean(tokens[2].substring("native=".length()));
        verdict.lib = tokens[3].substring("lib=".length());
        return verdict;
    }

    /**
     * Assert one matrix verdict: the tty answers equal the known expectation,
     * the independent shell line agrees on the same descriptors, and the
     * identity line proves the expected binding ran from the expected layout.
     */
    private static void assertVerdict(NativeMode mode, Verdict verdict, String expectedTty,
            String expectedShell) {
        String where = "(mode=" + mode + ", packaged=" + verdict.packaged + ", lib=" + verdict.lib
                + ")";
        assertEquals("tty verdict " + where, expectedTty, verdict.tty);
        assertEquals("shell agreement " + where, expectedShell, verdict.shell);
        boolean overlayExpected = expectedOverlay(verdict.packaged);
        assertEquals("overlay must load exactly when the layout carries it " + where
                + "; a base-stub shadow is a failure, not fallback coverage", overlayExpected,
                verdict.overlay);
        if (!overlayExpected) {
            assertFalse("no native access without the overlay " + where, verdict.nativeEnabled);
        } else {
            assertEquals("native flag must match mode " + where, mode == NativeMode.ENABLED,
                    verdict.nativeEnabled);
        }
        if (verdict.packaged) {
            assertTrue("packaged layout must load PosixTtyProbe from a jar, was: " + verdict.lib
                    + " " + where, verdict.lib.endsWith(".jar") && !verdict.lib.contains("test-classes"));
        } else if (isJava22OrLater()) {
            assertTrue("exploded layout must load the overlay copy from test-classes, was: "
                    + verdict.lib + " " + where, verdict.lib.contains("test-classes"));
        } else {
            // Pre-22 there is no overlay copy in test-classes; the base
            // stub loads from the classes directory itself.
            assertTrue("exploded pre-22 layout must load the base stub from the classes dir, was: "
                    + verdict.lib + " " + where,
                    verdict.lib.endsWith("/") && !verdict.lib.contains("test-classes"));
        }
    }

    private static File tempReport(String prefix) throws Exception {
        File report = File.createTempFile(prefix, ".log");
        report.deleteOnExit();
        return report;
    }

    /**
     * Run one PTY case: the child java command runs inside a {@code sh -c}
     * under {@code script}, with the given shell-code suffix for
     * redirections or pipes.
     */
    private static void runPtyCase(NativeMode mode, String suffix, String expectedTty,
            String expectedShell, String... probeArgs) throws Exception {
        ChildClassPath cp = childClassPath();
        String shellCode = joinShell(childArgv(mode, cp, probeArgs)) + suffix;
        // Follow the proven shape: util-linux script runs `sh -c '...'`;
        // the inner single quotes are escaped for the outer layer. macOS
        // takes the same shell code as a direct sh -c argv, no outer
        // quoting needed.
        List<String> argv;
        if (isMacScript()) {
            argv = Arrays.asList("script", "-q", "/dev/null", "sh", "-c", shellCode);
        } else {
            argv = Arrays.asList("script", "-qec", "sh -c '" + shellCode.replace("'", "'\\''") + "'",
                    "/dev/null");
        }
        String describe = "pty [" + shellCode + "]";
        ProcessBuilder builder = new ProcessBuilder(argv);
        builder.redirectErrorStream(true);
        File report = tempReport("tty-pty-");
        builder.environment().put(TtyFdProbe.REPORT_ENV, report.getAbsolutePath());
        scrubDisabledEnv(builder, mode);
        Process process = builder.start();
        Drain drain = new Drain(process.getInputStream());
        awaitChild(process, drain, describe, mode);
        String output = drain.join("matrix child " + describe);
        Verdict verdict = parseVerdict(readReport(report, describe, mode, output), describe, mode);
        verdict.packaged = cp.packaged;
        assertVerdict(mode, verdict, expectedTty, expectedShell);
        ptyMatrixRuns.incrementAndGet();
    }

    /**
     * Run the no-PT Y case: the child runs directly with all three standard
     * descriptors on files. No script, no shell, no quoting: pure argv plus
     * {@code ProcessBuilder} redirections.
     */
    private static void runDirectCase(NativeMode mode, String expected) throws Exception {
        ChildClassPath cp = childClassPath();
        File stdin = File.createTempFile("tty-stdin-", ".txt");
        File stdout = File.createTempFile("tty-stdout-", ".txt");
        File stderr = File.createTempFile("tty-stderr-", ".txt");
        stdin.deleteOnExit();
        stdout.deleteOnExit();
        stderr.deleteOnExit();
        ProcessBuilder builder = new ProcessBuilder(childArgv(mode, cp));
        builder.redirectInput(stdin);
        builder.redirectOutput(stdout);
        builder.redirectError(stderr);
        File report = tempReport("tty-direct-");
        builder.environment().put(TtyFdProbe.REPORT_ENV, report.getAbsolutePath());
        scrubDisabledEnv(builder, mode);
        String describe = "direct all-files";
        Process process = builder.start();
        Drain drain = new Drain(process.getInputStream());
        awaitChild(process, drain, describe, mode);
        String output = drain.join("direct matrix child");
        Verdict verdict = parseVerdict(readReport(report, describe, mode, output), describe, mode);
        verdict.packaged = cp.packaged;
        assertVerdict(mode, verdict, expected, expected);
        directMatrixRuns.incrementAndGet();
    }

    @AfterClass
    public static void requireMatrixExecution() {
        if (Boolean.TRUE.equals(scriptUsable)) {
            assertTrue("PTY matrix must execute at least one case on script-capable CI",
                    ptyMatrixRuns.get() > 0);
        }
        if (hasPosixSh()) {
            assertTrue("direct matrix must execute at least one case on POSIX CI",
                    directMatrixRuns.get() > 0);
        }
    }

    /** Marker file recording every {@code sh} invocation of a child. */
    private static final String SPAWN_MARKER_ENV = "TTY_SPAWN_MARKER";

    private static File writeMarkerSh(File bindir, File marker, File realSh) throws Exception {
        File sh = new File(bindir, "sh");
        String script = "#!" + realSh.getAbsolutePath() + "\n"
                + "echo \"$@\" >> " + quoteShellArg(marker.getAbsolutePath()) + "\n"
                + "exec " + quoteShellArg(realSh.getAbsolutePath()) + " \"$@\"\n";
        Files.write(sh.toPath(), script.getBytes(StandardCharsets.UTF_8));
        if (!sh.setExecutable(true)) {
            fail("cannot make marker sh executable: " + sh);
        }
        return sh;
    }

    private static String readMarker(File marker, String describe) throws Exception {
        try {
            return new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("cannot read spawn marker (" + describe + "): " + e);
            return null;
        }
    }

    /**
     * Run the no-spawn case: the child runs directly on files (no PTY, no
     * shell in the harness), with a marker {@code sh} first on its PATH and
     * its own agreement grandchildren skipped. Only {@code TtyDetect}'s own
     * subprocess use can reach the marker.
     */
    private static void runNoSpawnCase(NativeMode mode, File pathPrefix, File marker) throws Exception {
        ChildClassPath cp = childClassPath();
        File stdin = File.createTempFile("tty-stdin-", ".txt");
        File stdout = File.createTempFile("tty-stdout-", ".txt");
        File stderr = File.createTempFile("tty-stderr-", ".txt");
        stdin.deleteOnExit();
        stdout.deleteOnExit();
        stderr.deleteOnExit();
        ProcessBuilder builder = new ProcessBuilder(childArgv(mode, cp));
        builder.redirectInput(stdin);
        builder.redirectOutput(stdout);
        builder.redirectError(stderr);
        File report = tempReport("tty-nosspawn-");
        builder.environment().put(TtyFdProbe.REPORT_ENV, report.getAbsolutePath());
        builder.environment().put(TtyFdProbe.SKIP_SHELL_ENV, "1");
        builder.environment().put(SPAWN_MARKER_ENV, marker.getAbsolutePath());
        String path = builder.environment().get("PATH");
        builder.environment().put("PATH",
                pathPrefix.getAbsolutePath() + File.pathSeparator + (path == null ? "" : path));
        scrubDisabledEnv(builder, mode);
        String describe = "no-spawn " + mode;
        Process process = builder.start();
        Drain drain = new Drain(process.getInputStream());
        awaitChild(process, drain, describe, mode);
        String output = drain.join("no-spawn matrix child");
        Verdict verdict = parseVerdict(readReport(report, describe, mode, output), describe, mode);
        verdict.packaged = cp.packaged;
        assertVerdict(mode, verdict, "0:false 1:false 2:false 999:false",
                "0:null 1:null 2:null 999:null");
        directMatrixRuns.incrementAndGet();
    }

    private static void runPtyBothModes(String suffix, String expectedTty, String expectedShell,
            String... probeArgs) throws Exception {
        for (NativeMode mode : NativeMode.values()) {
            runPtyCase(mode, suffix, expectedTty, expectedShell, probeArgs);
        }
    }

    @Test
    public void testAllPtyReportsAllTrue() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        runPtyBothModes("", "0:true 1:true 2:true 999:false", "0:true 1:true 2:true 999:false");
    }

    @Test
    public void testRedirectedStdinKeepsPtyStdout() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        File input = File.createTempFile("tty-stdin", ".txt");
        input.deleteOnExit();
        runPtyBothModes(" < " + quoteShellArg(input.getAbsolutePath()),
                "0:false 1:true 2:true 999:false", "0:false 1:true 2:true 999:false");
    }

    @Test
    public void testRedirectedStdoutReportsNonTty() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        File output = File.createTempFile("tty-stdout", ".txt");
        output.deleteOnExit();
        runPtyBothModes(" > " + quoteShellArg(output.getAbsolutePath()),
                "0:true 1:false 2:true 999:false", "0:true 1:false 2:true 999:false");
    }

    @Test
    public void testPipedStdoutReportsNonTty() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        runPtyBothModes(" | cat", "0:true 1:false 2:true 999:false",
                "0:true 1:false 2:true 999:false");
    }

    @Test
    public void testRedirectedStderrReportsNonTty() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        File error = File.createTempFile("tty-stderr", ".txt");
        error.deleteOnExit();
        // The verdict survives on the report file, not on redirected stderr.
        runPtyBothModes(" 2> " + quoteShellArg(error.getAbsolutePath()),
                "0:true 1:true 2:false 999:false", "0:true 1:true 2:false 999:false");
    }

    @Test
    public void testClosedStderrReportsNonTty() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        // Only the report file carries the verdict past a closed stderr.
        // The runner's own descriptors are untouched: the close happens in
        // the inner shell of an isolated child.
        runPtyBothModes(" 2>&-", "0:true 1:true 2:false 999:false",
                "0:true 1:true 2:false 999:false");
    }

    @Test
    public void testFilesWithoutControllingTerminalReportNonTty() throws Exception {
        assumePosixSh();
        for (NativeMode mode : NativeMode.values()) {
            runDirectCase(mode, "0:false 1:false 2:false 999:false");
        }
    }

    @Test
    public void testNativeNegativePerformsNoSubprocess() throws Exception {
        // A valid native negative must perform no subprocess: prove it with
        // a marker sh first on the child's PATH. The child's own agreement
        // grandchildren are skipped, so only TtyDetect's own `test -t` use
        // can write to the marker. The denied mode is the control proving
        // the fixture detects spawns (exactly the three standard fds).
        assumePosixSh();
        Assume.assumeTrue("no-spawn proof needs the Java 22+ FFM overlay", isJava22OrLater());
        File realSh = new File("/bin/sh").canExecute() ? new File("/bin/sh")
                : new File("/usr/bin/sh");
        File bindir = Files.createTempDirectory("tty-nosspawn-").toFile();
        bindir.deleteOnExit();
        File marker = File.createTempFile("tty-spawn-marker-", ".log");
        marker.deleteOnExit();
        writeMarkerSh(bindir, marker, realSh).deleteOnExit();
        runNoSpawnCase(NativeMode.ENABLED, bindir, marker);
        assertEquals("enabled FFM path must not spawn sh; marker:\n" + readMarker(marker, "enabled"),
                "", readMarker(marker, "enabled"));
        runNoSpawnCase(NativeMode.DISABLED, bindir, marker);
        String[] invocations = readMarker(marker, "disabled").trim().split("\\r?\\n");
        assertEquals("denied fallback must spawn exactly the three standard fds; marker:\n"
                + readMarker(marker, "disabled"), 3, invocations.length);
        for (String invocation : invocations) {
            assertTrue("marker must show test -t use, was: " + invocation,
                    invocation.contains("test -t"));
        }
    }

    @Test
    public void testCygwinContextUsesPosixDescriptorProof() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        runPtyBothModes("", "0:true 1:true 2:true 999:false", "0:true 1:true 2:true 999:false",
                "cygwin");
    }

    @Test
    public void testCygwinRedirectedStdoutStaysNonTty() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        File output = File.createTempFile("tty-stdout", ".txt");
        output.deleteOnExit();
        runPtyBothModes(" > " + quoteShellArg(output.getAbsolutePath()),
                "0:true 1:false 2:true 999:false", "0:true 1:false 2:true 999:false", "cygwin");
    }

    @Test
    public void testCygwinPipedStdoutStaysNonTty() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        runPtyBothModes(" | cat", "0:true 1:false 2:true 999:false",
                "0:true 1:false 2:true 999:false", "cygwin");
    }

    @Test
    public void testNativeWindowsContextDoesNotAcceptPosixDescriptors() throws Exception {
        Assume.assumeTrue("needs script(1) for a real PTY", canRunScriptMatrix());
        // The Windows transport deliberately disagrees with the POSIX
        // descriptors: GetConsoleMode knows no console here, while test -t
        // truthfully reports the PTY. Assert both sides explicitly.
        runPtyBothModes("", "0:false 1:false 2:false 999:false",
                "0:true 1:true 2:true 999:false", "windows");
    }
}
