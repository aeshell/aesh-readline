package org.aesh.terminal.tty.mimic;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
 * sequentially and from several threads in parallel (parallel augmentation),
 * each attempt guarded by a watchdog. On timeout it prints every stack trace
 * and exits nonzero, so a hanging run names the culprit in the CI log.
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
        enableFineLogging();
        printEnvironment(System.out);
        if ("sequential".equals(mode) || "both".equals(mode)) {
            runSequential(iterations, perBuildTimeoutSec);
        }
        if ("parallel".equals(mode) || "both".equals(mode)) {
            runParallel(threads, 10, perBuildTimeoutSec);
        }
        System.out.println("MIMIC-OK all builds completed within their watchdog");
    }

    /**
     * One Quarkus-style console setup: build a connection on the system
     * streams, run the capability check, the synchronous size query and the
     * reader start that {@code AeshConsole} performs. Deliberately never
     * closed: Quarkus never closes the dev-mode console until JVM death, and
     * the reader threads are daemon.
     *
     * @param id sequential build number for log correlation
     * @return one-line description of the resulting connection
     * @throws Exception on failure
     */
    static String buildOnce(int id) throws Exception {
        long start = System.nanoTime();
        final String[] outcome = new String[1];
        new TerminalConnection(new Consumer<Connection>() {
            @Override
            public void accept(Connection connection) {
                boolean ansi = connection.supportsAnsi();
                Object size = connection.size();
                connection.openNonBlocking();
                outcome[0] = "ansi=" + ansi + " size=" + size;
            }
        });
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        return "build " + id + " took " + ms + "ms " + outcome[0];
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

    private static void runParallel(int threads, int perThread, long perBuildTimeoutSec) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads, daemonThreads("mimic-build"));
        List<Callable<String>> tasks = new ArrayList<Callable<String>>();
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < perThread; i++) {
                final int id = t * perThread + i;
                tasks.add(new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        return buildOnce(id);
                    }
                });
            }
        }
        // Generous global deadline: healthy parallel runs finish in seconds.
        // A hang trips here, dumps threads and exits nonzero.
        long globalTimeoutSec = Math.max(180, perThread * perBuildTimeoutSec);
        List<Future<String>> futures = pool.invokeAll(tasks, globalTimeoutSec, TimeUnit.SECONDS);
        pool.shutdownNow();
        int done = 0;
        for (Future<String> future : futures) {
            if (future.isDone() && !future.isCancelled()) {
                System.out.println("MIMIC-OK parallel " + future.get());
                done++;
            }
        }
        if (done < tasks.size()) {
            System.out.println("MIMIC-TIMEOUT parallel phase: " + done + " of " + tasks.size()
                    + " builds finished within " + globalTimeoutSec + "s; dumping threads");
            dumpAllStacks(System.out);
            System.out.flush();
            System.exit(EXIT_TIMEOUT);
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
            try {
                return future.get(timeoutSec, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                System.out.println("MIMIC-TIMEOUT " + label + " exceeded " + timeoutSec + "s; dumping threads");
                dumpAllStacks(System.out);
                System.out.flush();
                System.exit(EXIT_TIMEOUT);
                throw new AssertionError("unreachable");
            }
        } finally {
            guard.shutdownNow();
        }
    }

    static void printEnvironment(final PrintStream out) throws Exception {
        out.println("MIMIC-ENV os.name=" + System.getProperty("os.name")
                + " java.version=" + System.getProperty("java.version"));
        out.println("MIMIC-ENV PWD=" + System.getenv("PWD")
                + " MSYSTEM=" + System.getenv("MSYSTEM")
                + " TERM=" + System.getenv("TERM")
                + " TERM_PROGRAM=" + System.getenv("TERM_PROGRAM")
                + " CYGWIN=" + System.getenv("CYGWIN"));
        out.println("MIMIC-ENV IS_WINDOWS=" + OSUtils.IS_WINDOWS + " IS_CYGWIN=" + OSUtils.IS_CYGWIN);
        // The TTY probes themselves spawn subprocesses on some platforms, so
        // guard them like everything else: a stuck probe is a finding, not a hang.
        Boolean stdinTty = runGuarded(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return Boolean.valueOf(TtyDetect.isStdinTty());
            }
        }, 60, "isStdinTty probe");
        Boolean stdoutTty = runGuarded(new Callable<Boolean>() {
            @Override
            public Boolean call() {
                return Boolean.valueOf(TtyDetect.isStdoutTty());
            }
        }, 60, "isStdoutTty probe");
        out.println("MIMIC-ENV stdinTty=" + stdinTty + " stdoutTty=" + stdoutTty);
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
