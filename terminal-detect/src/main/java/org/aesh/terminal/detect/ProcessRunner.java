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

/**
 * Subprocess execution with a single deadline covering process start,
 * output drain, and wait.
 * <p>
 * Every probe subprocess (theme and registry queries, stty, infocmp)
 * funnels through here instead of hand-rolled start/drain/wait loops,
 * which could block draining stdout before a timed wait or deadlock on
 * a full stderr pipe. Standard error merges into standard output, so
 * one drainer thread always keeps the pipe flowing; output past the
 * cap is discarded rather than accumulated. On timeout or interruption
 * the process is destroyed forcibly and reaped before returning.
 * <p>
 * Java 8, zero dependencies beyond {@code java.base}.
 */
public final class ProcessRunner {

    /** Default deadline (ms) for start, drain, and wait combined. */
    public static final long DEFAULT_TIMEOUT_MS = 5000;

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
         *        never exited normally
         * @param timedOut true when the deadline expired first
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
         * @return the exit code, or -1 when the process never exited normally
         */
        public int exitCode() {
            return exitCode;
        }

        /**
         * Check whether the deadline expired before the process exited.
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
     * Execute a command with the default deadline.
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
     * Execute a command with a deadline.
     *
     * @param timeoutMs the deadline in milliseconds for start, drain, and wait combined
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
     * Execute a preconfigured process builder with a deadline.
     * <p>
     * Callers keep builder customization (environment, redirections);
     * this method owns the lifecycle: start, drain, bounded wait, and
     * forcible destroy plus reap on timeout or interruption.
     *
     * @param starter the configured process builder
     * @param timeoutMs the deadline in milliseconds for start, drain, and wait combined
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
        starter.redirectErrorStream(true);
        Process process = starter.start();
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        Thread drainer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    InputStream in = process.getInputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        int room = DEFAULT_MAX_OUTPUT_BYTES - captured.size();
                        if (room <= 0) {
                            break;
                        }
                        captured.write(buf, 0, Math.min(n, room));
                    }
                } catch (IOException ignored) {
                    // Stream closed by destroy: partial output stands.
                }
            }
        }, "process-runner-drain");
        drainer.setDaemon(true);
        drainer.start();
        boolean finished;
        try {
            finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            process.destroyForcibly();
            joinDrainer(drainer);
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException nested) {
                Thread.currentThread().interrupt();
            }
            Thread.currentThread().interrupt();
            throw e;
        }
        if (!finished) {
            process.destroyForcibly();
            joinDrainer(drainer);
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return new Result(-1, true, captured.toByteArray());
        }
        joinDrainer(drainer);
        return new Result(process.exitValue(), false, captured.toByteArray());
    }

    private static void joinDrainer(Thread drainer) {
        try {
            drainer.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
