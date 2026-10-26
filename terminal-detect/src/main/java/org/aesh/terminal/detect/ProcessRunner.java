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
package org.aesh.terminal.detect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

/**
 * Subprocess capture with one normal-work budget for launch, execution,
 * and output draining.
 * <p>
 * Used by theme, terminfo, and PTY helper commands. Standard error merges
 * into standard output. Output past the capture cap is discarded while
 * the child is running. The owned drainer does not wait for EOF after
 * the child exits: a descendant may still hold the pipe open.
 * <p>
 * Timeout and interruption request forcible termination, with a separate
 * cleanup allowance of at most five seconds for the drainer and child
 * together. This preserves the existing cleanup safety allowance; it is
 * not a hard end-to-end enrichment deadline. Process creation and platform
 * pipe operations cannot be forcibly interrupted by this Java 8 interface.
 * Run permitted subprocess work off latency-critical caller threads.
 * <p>
 * Java 8, zero dependencies beyond {@code java.base}.
 */
public final class ProcessRunner {

    /** Default normal-work budget (ms) for launch, drain, and wait combined. */
    public static final long DEFAULT_TIMEOUT_MS = 5000;

    private static final long CLEANUP_NANOS = TimeUnit.SECONDS.toNanos(5);
    private static final long DRAIN_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(1);

    /**
     * Maximum captured output bytes. Larger outputs are truncated;
     * probe commands answer in kilobytes.
     */
    public static final int DEFAULT_MAX_OUTPUT_BYTES = 1024 * 1024;

    /**
     * Result of one execution: exit status plus captured output.
     * Distinguishes "no result" (timeout, spawn failure) from "command
     * failed" (nonzero exit with output) instead of collapsing both
     * to null or an empty string.
     */
    public static final class Result {
        private final int exitCode;
        private final boolean timedOut;
        private final byte[] output;

        /**
         * Create a result. The output array is retained as passed.
         *
         * @param exitCode the process exit code, or -1 when the process
         *        did not produce a complete normal-work result
         * @param timedOut true when the normal-work budget expired first
         * @param output the captured merged output bytes
         */
        public Result(int exitCode, boolean timedOut, byte[] output) {
            this.exitCode = exitCode;
            this.timedOut = timedOut;
            this.output = output;
        }

        /**
         * Get the process exit code.
         *
         * @return the exit code, or -1 when no complete result is available
         */
        public int exitCode() {
            return exitCode;
        }

        /**
         * Check whether the normal-work budget expired before capture completed.
         *
         * @return true on timeout
         */
        public boolean timedOut() {
            return timedOut;
        }

        /**
         * Get the captured merged stdout/stderr bytes, possibly
         * truncated at the output cap.
         *
         * @return the output bytes
         */
        public byte[] output() {
            return output;
        }

        /**
         * Decode the captured output.
         *
         * @param charset the charset to decode with
         * @return the decoded output
         */
        public String text(Charset charset) {
            return new String(output, charset);
        }
    }

    /**
     * Test seam: replaces process spawning with canned results.
     * Package-visible so fixture tests count invocations without
     * spawning anything; production always uses the direct path.
     */
    interface RunnerTransport {
        Result run(ProcessBuilder starter, long timeoutMs) throws IOException, InterruptedException;
    }

    private static volatile RunnerTransport installed;

    static void setInstalled(RunnerTransport transport) {
        installed = transport;
    }

    private ProcessRunner() {
    }

    /**
     * Execute a command with the default normal-work budget.
     *
     * @param command the command and arguments
     * @return the result
     * @throws IOException if the process cannot start or I/O fails
     * @throws InterruptedException if interrupted while waiting
     */
    public static Result execute(String... command) throws IOException, InterruptedException {
        return execute(DEFAULT_TIMEOUT_MS, command);
    }

    /**
     * Execute a command with a normal-work budget and bounded cleanup.
     *
     * @param timeoutMs the normal-work budget in milliseconds; see class-level cleanup limits
     * @param command the command and arguments
     * @return the result
     * @throws IOException if the process cannot start or I/O fails
     * @throws InterruptedException if interrupted while waiting
     */
    public static Result execute(long timeoutMs, String... command)
            throws IOException, InterruptedException {
        return execute(new ProcessBuilder(command), timeoutMs);
    }

    /**
     * Execute a preconfigured process builder with a normal-work budget.
     * <p>
     * Callers keep builder customization (environment, redirections);
     * this method owns launch, capture, and cleanup. Launch time is charged
     * once it returns. Timeout or interruption uses the separate cleanup
     * allowance described on this class, not a fresh allowance per phase.
     *
     * @param starter the configured process builder
     * @param timeoutMs the normal-work budget in milliseconds; see class-level cleanup limits
     * @return the result
     * @throws IOException if the process cannot start or I/O fails
     * @throws InterruptedException if interrupted while waiting
     */
    public static Result execute(ProcessBuilder starter, long timeoutMs)
            throws IOException, InterruptedException {
        RunnerTransport transport = installed;
        if (transport != null) {
            return transport.run(starter, timeoutMs);
        }
        long started = System.nanoTime();
        long budget = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        AtomicBoolean stop = new AtomicBoolean();
        starter.redirectErrorStream(true);
        Process process = starter.start();
        Thread drainer = null;
        try {
            drainer = new Thread(new Runnable() {
                @Override
                public void run() {
                    drain(process, captured, stop);
                }
            }, "process-runner-drain");
            drainer.setDaemon(true);
            drainer.start();
            boolean finished = process.waitFor(remaining(started, budget), TimeUnit.NANOSECONDS);
            stop.set(true);
            drainer.interrupt();
            if (finished && joinWithin(drainer, started, budget)) {
                return new Result(process.exitValue(), false, captured.toByteArray());
            }
            cleanup(process, drainer, stop);
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("interrupted during subprocess cleanup");
            }
            return new Result(-1, true, captured.toByteArray());
        } catch (InterruptedException e) {
            cleanupAfterFailure(process, drainer, stop, e);
            Thread.currentThread().interrupt();
            throw e;
        } catch (RuntimeException | Error e) {
            cleanupAfterFailure(process, drainer, stop, e);
            throw e;
        }
    }

    private static void drain(Process process, ByteArrayOutputStream captured, AtomicBoolean stop) {
        try {
            InputStream input = process.getInputStream();
            byte[] buffer = new byte[8192];
            while (!stop.get() || captured.size() < DEFAULT_MAX_OUTPUT_BYTES) {
                // Observe exit before checking readiness, so a child's final
                // write cannot land between an empty read and the exit check.
                boolean ending = stop.get();
                int count = readAvailable(input, buffer);
                if (count > 0) {
                    int room = DEFAULT_MAX_OUTPUT_BYTES - captured.size();
                    if (room > 0) {
                        captured.write(buffer, 0, Math.min(count, room));
                    }
                } else if (ending) {
                    return;
                } else {
                    LockSupport.parkNanos(DRAIN_POLL_NANOS);
                }
            }
        } catch (IOException ignored) {
            // The process/JVM may close a pipe concurrently. Keep partial output.
        }
    }

    private static int readAvailable(InputStream input, byte[] buffer) throws IOException {
        // Exclude the JVM's exit-time pipe drain between readiness and read.
        // No other reader can consume these bytes while this monitor is held.
        synchronized (input) {
            int available = input.available();
            return available > 0 ? input.read(buffer, 0, Math.min(available, buffer.length)) : 0;
        }
    }

    private static long remaining(long started, long budget) {
        long elapsed = System.nanoTime() - started;
        return budget <= 0 || elapsed >= budget ? 0 : budget - elapsed;
    }

    private static boolean joinWithin(Thread thread, long started, long budget) throws InterruptedException {
        while (thread != null && thread.isAlive()) {
            long left = remaining(started, budget);
            if (left == 0) {
                return false;
            }
            thread.join(TimeUnit.NANOSECONDS.toMillis(left), (int) (left % 1000000));
        }
        return true;
    }

    private static void cleanup(Process process, Thread drainer, AtomicBoolean stop) throws IOException {
        long started = System.nanoTime();
        boolean interrupted = Thread.interrupted();
        stop.set(true);
        if (drainer != null) {
            drainer.interrupt();
        }
        try {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            while (true) {
                try {
                    if (!joinWithin(drainer, started, CLEANUP_NANOS)
                            || !process.waitFor(remaining(started, CLEANUP_NANOS), TimeUnit.NANOSECONDS)) {
                        throw new IOException("subprocess cleanup did not finish within its allowance");
                    }
                    return;
                } catch (InterruptedException e) {
                    // Finish this owned cleanup attempt without resetting its budget.
                    interrupted = true;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void cleanupAfterFailure(Process process, Thread drainer, AtomicBoolean stop, Throwable primary) {
        try {
            cleanup(process, drainer, stop);
        } catch (IOException | RuntimeException | Error cleanupFailure) {
            if (cleanupFailure != primary) {
                primary.addSuppressed(cleanupFailure);
            }
        }
    }
}
