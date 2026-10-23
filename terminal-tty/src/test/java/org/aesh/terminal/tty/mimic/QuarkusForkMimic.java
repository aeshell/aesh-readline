package org.aesh.terminal.tty.mimic;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.Connection;
import org.aesh.terminal.tty.TerminalConnection;
import org.aesh.terminal.tty.TtyDetect;
import org.aesh.terminal.utils.OSUtils;

/**
 * Mimics the Quarkus forked dev-mode console setup on headless Windows.
 * <p>
 * Quarkus {@code quarkus:dev} builds a {@code TerminalConnection} on
 * {@code System.in/out} synchronously on the augmentation thread
 * ({@code ConsoleProcessor.setupConsole} installs an Aesh console whose
 * constructor opens the reader and queries the terminal size). Quarkus CI
 * runs its Windows steps under Git Bash, so every forked JVM inherits a
 * POSIX-style {@code PWD}, which trips the Cygwin/MSYS fallback in
 * {@code OSUtils.detectCygwinOrMsys} and admits the provider chain with no
 * console attached — the environment behind quarkusio/quarkus#57716 and
 * quarkusio/quarkus#57434, where forked dev-mode JVMs intermittently go
 * silent before the boot banner and HTTP polling times out.
 * <p>
 * This program replays exactly that synchronous surface —
 * {@code supportsAnsi()}, {@code size()}, {@code openNonBlocking()} —
 * from several threads in parallel before any TTY status checks, followed
 * by sequential warm attempts. Each parallel round starts behind a barrier
 * and has one watchdog deadline. On timeout the watchdog dumps stacks before
 * interrupting anything and halts the child JVM, bypassing stuck shutdown hooks.
 * <p>
 * Not a surefire test: it runs as a standalone {@code main()} so the CI job
 * controls stdin precisely (inherited vs never-EOF pipe). See
 * {@code windows-quarkus-mimic} in {@code .github/workflows/main.yml} and
 * issue #375.
 */
public final class QuarkusForkMimic {

    private static final int EXIT_TIMEOUT = 2;

    private QuarkusForkMimic() {
    }

    /**
     * Run the mimic.
     *
     * @param args mode (sequential, parallel, both), iterations,
     *        parallel threads, per-build watchdog seconds
     * @throws Exception on unexpected failure
     */
    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "both";
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 20;
        int threads = args.length > 2 ? Integer.parseInt(args[2]) : 8;
        long perBuildTimeoutSec = args.length > 3 ? Long.parseLong(args[3]) : 60;
        if (!"sequential".equals(mode) && !"parallel".equals(mode) && !"both".equals(mode)) {
            throw new IllegalArgumentException("mode must be sequential, parallel, or both");
        }
        if (iterations <= 0 || threads <= 0 || perBuildTimeoutSec < 0) {
            throw new IllegalArgumentException("iterations/threads must be positive and timeout nonnegative");
        }
        enableFineLogging();
        printEnvironment(System.out);
        if ("parallel".equals(mode) || "both".equals(mode)) {
            System.out.println("MIMIC-PHASE parallel-cold-first");
            runParallel(threads, 10, perBuildTimeoutSec);
        }
        if ("sequential".equals(mode) || "both".equals(mode)) {
            System.out.println("MIMIC-PHASE sequential-" + ("both".equals(mode) ? "warm" : "cold-first"));
            runSequential(iterations, perBuildTimeoutSec);
        }
        String markers = System.getenv("AESH_TTY_PROBE_MARKER_DIR");
        if (markers != null) {
            verifyProbeMarkers(new File(markers));
        }
        System.out.println("MIMIC-PHASE cached-tty-status");
        printTtyStatus(System.out);
        System.out.println("MIMIC-OK all builds completed within their watchdog");
    }

    /**
     * One Quarkus-style console setup: build a connection on the system
     * streams, run the capability check, the synchronous size query and the
     * reader start that {@code AeshConsole} performs. Close afterwards so
     * repeated attempts do not accumulate reader workers. Setup time is
     * reported separately from close; the watchdog bounds both.
     *
     * @param id sequential build number for log correlation
     * @return one-line description of the resulting connection
     * @throws Exception on failure
     */
    static String buildOnce(int id) throws Exception {
        long start = System.nanoTime();
        final String[] outcome = new String[1];
        TerminalConnection connection = new TerminalConnection(new Consumer<Connection>() {
            @Override
            public void accept(Connection connection) {
                boolean ansi = connection.supportsAnsi();
                Object size = connection.size();
                connection.openNonBlocking();
                outcome[0] = "ansi=" + ansi + " size=" + size;
            }
        });
        try {
            long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            return "build " + id + " took " + ms + "ms " + outcome[0];
        } finally {
            connection.close();
        }
    }

    private static void runSequential(int iterations, long perBuildTimeoutSec) throws Exception {
        for (int i = 0; i < iterations; i++) {
            final int id = i;
            String outcome = runGuarded(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return buildOnce(id);
                }
            }, perBuildTimeoutSec, "sequential build " + id);
            System.out.println("MIMIC-OK " + outcome);
        }
    }

    private static void runParallel(final int threads, int perThread, long perBuildTimeoutSec) throws Exception {
        final ExecutorService pool = Executors.newFixedThreadPool(threads, daemonThreads("mimic-build"));
        try {
            for (int round = 0; round < perThread; round++) {
                final int firstId = round * threads;
                List<String> outcomes = runGuarded(new Callable<List<String>>() {
                    @Override
                    public List<String> call() throws Exception {
                        final CountDownLatch ready = new CountDownLatch(threads);
                        final CountDownLatch start = new CountDownLatch(1);
                        List<Future<String>> futures = new ArrayList<Future<String>>();
                        for (int t = 0; t < threads; t++) {
                            final int id = firstId + t;
                            futures.add(pool.submit(new Callable<String>() {
                                @Override
                                public String call() throws Exception {
                                    ready.countDown();
                                    start.await();
                                    return buildOnce(id);
                                }
                            }));
                        }
                        ready.await();
                        start.countDown();
                        List<String> results = new ArrayList<String>();
                        for (Future<String> future : futures) {
                            results.add(future.get());
                        }
                        return results;
                    }
                }, perBuildTimeoutSec, "parallel round " + round);
                for (String outcome : outcomes) {
                    System.out.println("MIMIC-OK parallel " + outcome);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Run one callable with a hard deadline. On timeout print every stack
     * trace and exit, so CI captures the hung location.
     *
     * @param <T> result type
     * @param work the work to guard
     * @param timeoutSec deadline in seconds
     * @param label log label for this attempt
     * @return the callable result
     * @throws Exception from the callable itself
     */
    static <T> T runGuarded(Callable<T> work, long timeoutSec, String label) throws Exception {
        ExecutorService guard = Executors.newSingleThreadExecutor(daemonThreads("mimic-guard"));
        Future<T> future = guard.submit(work);
        try {
            return future.get(timeoutSec, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            System.out.println("MIMIC-TIMEOUT " + label + " exceeded " + timeoutSec + "s; dumping threads");
            dumpAllStacks(System.out);
            System.out.flush();
            Runtime.getRuntime().halt(EXIT_TIMEOUT);
            throw new AssertionError("unreachable");
        } finally {
            guard.shutdownNow();
        }
    }

    static void printEnvironment(PrintStream out) {
        out.println("MIMIC-ENV os.name=" + System.getProperty("os.name")
                + " java.version=" + System.getProperty("java.version"));
        out.println("MIMIC-ENV PWD=" + System.getenv("PWD")
                + " MSYSTEM=" + System.getenv("MSYSTEM")
                + " TERM=" + System.getenv("TERM")
                + " TERM_PROGRAM=" + System.getenv("TERM_PROGRAM")
                + " CYGWIN=" + System.getenv("CYGWIN"));
    }

    private static void printTtyStatus(PrintStream out) {
        out.println("MIMIC-ENV IS_WINDOWS=" + OSUtils.IS_WINDOWS + " IS_CYGWIN=" + OSUtils.IS_CYGWIN);
        // Construction must reach the probes with cold caches. Only inspect
        // the cached answers afterwards, never as an environment preflight.
        out.println("MIMIC-ENV stdinTty=" + TtyDetect.isStdinTty() + " stdoutTty=" + TtyDetect.isStdoutTty());
    }

    static void verifyProbeMarkers(File directory) throws IOException {
        File[] markers = directory.listFiles();
        if (markers == null) {
            throw new IOException("probe fixture marker directory is unavailable: " + directory);
        }
        for (int fd = 0; fd <= 1; fd++) {
            int launches = 0;
            for (File marker : markers) {
                if (!marker.getName().endsWith("-" + fd + ".started")) {
                    continue;
                }
                String text = new String(Files.readAllBytes(marker.toPath()), StandardCharsets.US_ASCII);
                if (!text.equals("fd=" + fd + "\n")) {
                    throw new IllegalStateException("invalid probe fixture marker: " + marker);
                }
                launches++;
            }
            if (launches == 0) {
                throw new IllegalStateException("probe fixture did not execute for fd " + fd + " in " + directory);
            }
            System.out.println("MIMIC-FIXTURE fd=" + fd + " launches=" + launches);
        }
    }

    static void dumpAllStacks(PrintStream out) {
        Map<Thread, StackTraceElement[]> all = Thread.getAllStackTraces();
        List<Thread> threads = new ArrayList<Thread>(all.keySet());
        Collections.sort(threads, new Comparator<Thread>() {
            @Override
            public int compare(Thread left, Thread right) {
                return left.getName().compareTo(right.getName());
            }
        });
        for (Thread thread : threads) {
            out.println("\"" + thread.getName() + "\" state=" + thread.getState()
                    + (thread.isDaemon() ? " daemon" : ""));
            StackTraceElement[] trace = all.get(thread);
            if (trace != null) {
                for (StackTraceElement element : trace) {
                    out.println("    at " + element);
                }
            }
        }
    }

    private static void enableFineLogging() {
        // Scope to our packages only: enabling ALL on the root logger also
        // unleashes the JDK's own FINER tracing, notably a full synthetic
        // stack per ProcessBuilder.start() that buries the log. The handler
        // level still gates emission, so org.aesh FINE (e.g. which provider
        // won the chain) flows while java.* stays at its default level.
        Logger.getLogger("org.aesh").setLevel(Level.ALL);
        Logger root = Logger.getLogger("");
        for (Handler handler : root.getHandlers()) {
            handler.setLevel(Level.ALL);
        }
    }

    private static ThreadFactory daemonThreads(final String prefix) {
        return new ThreadFactory() {
            private final AtomicInteger count = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, prefix + "-" + count.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
    }
}
