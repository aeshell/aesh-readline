package org.aesh.terminal.tty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Test;

/**
 * The {@code test -t} probe must stay bounded: a child that never exits
 * (observed headless on Windows, wedging augmentation behind
 * {@code isStdinTty}) degrades to unknown instead of hanging the caller.
 * See https://github.com/aeshell/aesh-readline/issues/376.
 */
public class TtyDetectTimeoutTest {

    @Test(timeout = 60000)
    public void testHangingChildReturnsNullBounded() {
        // `sleep` never exits within the test: the old unbounded
        // drain-then-waitFor would blow the test timeout (red); the
        // deadline-bounded probe returns null in about 5 seconds.
        long start = System.nanoTime();
        Boolean answer = TtyDetect.tryTestT(TtyDetect.FD_STDIN, new TtyDetect.ProbeBuilder() {
            @Override
            public ProcessBuilder build(int fd) {
                return new ProcessBuilder("sleep", "120");
            }
        });
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertNull(answer);
        assertTrue("probe took " + ms + "ms, expected under the deadline plus margin", ms < 30000);
    }

    @Test
    public void testExitZeroMeansTrue() {
        Assume.assumeTrue("sh not available", hasSh());
        assertEquals(Boolean.TRUE, TtyDetect.tryTestT(TtyDetect.FD_STDIN, new TtyDetect.ProbeBuilder() {
            @Override
            public ProcessBuilder build(int fd) {
                return new ProcessBuilder("sh", "-c", "exit 0");
            }
        }));
    }

    @Test
    public void testExitNonZeroMeansFalse() {
        Assume.assumeTrue("sh not available", hasSh());
        assertEquals(Boolean.FALSE, TtyDetect.tryTestT(TtyDetect.FD_STDIN, new TtyDetect.ProbeBuilder() {
            @Override
            public ProcessBuilder build(int fd) {
                return new ProcessBuilder("sh", "-c", "exit 3");
            }
        }));
    }

    private static boolean hasSh() {
        try {
            Process process = new ProcessBuilder("sh", "-c", "exit 0").start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            return finished && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
