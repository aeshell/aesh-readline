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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assume;
import org.junit.Test;

/**
 * Lifecycle tests for the shared subprocess runner (#357).
 * <p>
 * Normal work has one budget; owned cleanup has its separate safety
 * allowance. Stderr merges without pipe deadlock, capture is bounded
 * without stopping drainage, and interruption survives. These shell
 * cases run on POSIX JVM/native tests; ProcessRunnerLifecycleTest adds
 * cross-platform child-JVM execution and lifetime markers.
 */
public class ProcessRunnerTest {

    private static boolean hasSh() {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return false;
        }
        File sh = new File("/bin/sh");
        return sh.isFile() && sh.canExecute();
    }

    @Test
    public void testSlowCommandTimesOutAndReaps() throws Exception {
        Assume.assumeTrue("needs POSIX sh", hasSh());
        long start = System.currentTimeMillis();
        ProcessRunner.Result result = ProcessRunner.execute(500, "/bin/sh", "-c", "sleep 30");
        long elapsed = System.currentTimeMillis() - start;
        assertTrue("timed out result expected", result.timedOut());
        assertEquals(-1, result.exitCode());
        assertTrue("deadline must bound the call, took " + elapsed + "ms",
                elapsed < 10000);
    }

    @Test
    public void testInfiniteOutputTruncatedAtCap() throws Exception {
        Assume.assumeTrue("needs POSIX sh", hasSh());
        ProcessRunner.Result result = ProcessRunner.execute(2000,
                "/bin/sh", "-c", "while true; do echo flood; done");
        assertTrue("infinite output must hit the deadline", result.timedOut());
        assertTrue("output must stay bounded, got " + result.output().length,
                result.output().length <= ProcessRunner.DEFAULT_MAX_OUTPUT_BYTES);
    }

    @Test
    public void testFiniteOutputBeyondCapIsDrained() throws Exception {
        Assume.assumeTrue("needs POSIX sh", hasSh());
        ProcessRunner.Result result = ProcessRunner.execute(10000, "/bin/sh", "-c",
                "printf 'runner-started\\n'; i=0; while [ $i -lt 3072 ]; do "
                        + "printf '%01024d' 0 >&2; i=$((i+1)); done");
        assertTrue("fixture must execute before capture fills",
                result.text(StandardCharsets.US_ASCII).startsWith("runner-started\n"));
        assertFalse("finite output must finish even after the capture cap", result.timedOut());
        assertEquals(0, result.exitCode());
        assertEquals(ProcessRunner.DEFAULT_MAX_OUTPUT_BYTES, result.output().length);
    }

    @Test
    public void testLargeStderrMerged() throws Exception {
        Assume.assumeTrue("needs POSIX sh", hasSh());
        ProcessRunner.Result result = ProcessRunner.execute(
                "/bin/sh", "-c", "i=0; while [ $i -lt 20000 ]; do echo errline >&2; i=$((i+1)); done");
        assertFalse(result.timedOut());
        assertEquals(0, result.exitCode());
        String text = result.text(StandardCharsets.UTF_8);
        assertTrue("merged stderr must be captured", text.contains("errline"));
        assertEquals("all 20000 lines must arrive", 20000, text.split("errline", -1).length - 1);
    }

    @Test
    public void testNonzeroExit() throws Exception {
        Assume.assumeTrue("needs POSIX sh", hasSh());
        ProcessRunner.Result result = ProcessRunner.execute("/bin/sh", "-c", "exit 3");
        assertFalse(result.timedOut());
        assertEquals(3, result.exitCode());
    }

    @Test
    public void testInterruptPreserved() throws Exception {
        Assume.assumeTrue("needs POSIX sh", hasSh());
        final AtomicReference<Throwable> outcome = new AtomicReference<>();
        final AtomicBoolean interruptedSeen = new AtomicBoolean();
        Thread runner = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessRunner.execute(30000, "/bin/sh", "-c", "sleep 30");
                    outcome.compareAndSet(null, new AssertionError("must not return normally"));
                } catch (InterruptedException e) {
                    interruptedSeen.set(Thread.currentThread().isInterrupted());
                    outcome.compareAndSet(null, e);
                } catch (Throwable t) {
                    outcome.compareAndSet(null, t);
                }
            }
        }, "process-runner-interrupt");
        runner.setDaemon(true);
        runner.start();
        Thread.sleep(500);
        runner.interrupt();
        runner.join(10000);
        assertFalse("interrupted run must return", runner.isAlive());
        assertTrue("InterruptedException expected, got " + outcome.get(),
                outcome.get() instanceof InterruptedException);
        assertTrue("interrupt status must survive", interruptedSeen.get());
    }

    @Test
    public void testMissingBinaryThrows() {
        try {
            ProcessRunner.execute("definitely-not-a-command-357");
            fail("missing binary must throw");
        } catch (IOException expected) {
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("must not be interrupted");
        }
    }
}
