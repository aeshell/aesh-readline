package org.aesh.terminal.tty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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

    @Test(timeout = 45000)
    public void testHangingChildReturnsNullBounded() throws Exception {
        long start = System.nanoTime();
        assertNull(probe("hold"));
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue("probe took " + ms + "ms, expected under the deadline plus margin", ms < 20000);
    }

    @Test(timeout = 20000)
    public void testExitZeroMeansTrue() throws Exception {
        assertEquals(Boolean.TRUE, probe("0"));
    }

    @Test(timeout = 20000)
    public void testExitNonZeroMeansFalse() throws Exception {
        assertEquals(Boolean.FALSE, probe("3"));
    }

    private static Boolean probe(String outcome) throws Exception {
        // These fixtures need the JVM test classpath. Do not report native
        // coverage when the native test binary cannot launch that classpath.
        Assume.assumeTrue("child-JVM fixture is verified by the JVM suites",
                System.getProperty("org.graalvm.nativeimage.imagecode") == null);
        Path marker = Files.createTempFile("tty-probe-", ".marker");
        try {
            String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
            File java = new File(new File(System.getProperty("java.home"), "bin"), executable);
            final ProcessBuilder builder = new ProcessBuilder(java.getAbsolutePath(), "-cp",
                    System.getProperty("java.class.path"), ProbeChild.class.getName(), marker.toString(), outcome);
            Boolean answer = TtyDetect.tryTestT(TtyDetect.FD_STDIN, new TtyDetect.ProbeBuilder() {
                @Override
                public ProcessBuilder build(int fd) {
                    return builder;
                }
            });
            assertChildStartedAndStopped(marker);
            return answer;
        } finally {
            Files.deleteIfExists(marker);
        }
    }

    private static void assertChildStartedAndStopped(Path marker) throws Exception {
        assertTrue("fixture never reached its startup marker", Files.size(marker) > 0);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            try (RandomAccessFile file = new RandomAccessFile(marker.toFile(), "rw");
                    FileLock lock = file.getChannel().tryLock()) {
                if (lock != null) {
                    assertEquals(8, file.length());
                    byte[] content = new byte[8];
                    file.readFully(content);
                    assertEquals("started\n", new String(content, StandardCharsets.US_ASCII));
                    return;
                }
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        fail("probe returned while the fixture still held its process-lifetime lock");
    }
}
