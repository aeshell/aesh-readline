package org.aesh.terminal.detect;

import static org.junit.Assert.*;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assume;
import org.junit.Test;

/** Public subprocess behavior, with execution and process-lifetime canaries. */
public class ProcessRunnerLifecycleTest {

    @Test(timeout = 45000)
    public void testFiniteStderrBeyondCaptureCapStillExits() throws Exception {
        assumeJvm();
        Path marker = Files.createTempFile("process-runner-", ".marker");
        try {
            ProcessRunner.Result result = ProcessRunner.execute(child(marker, "flood-stderr"), 10000);
            assertStopped(marker);
            String execution = new String(Files.readAllBytes(marker), StandardCharsets.US_ASCII);
            assertTrue("child fixture must actually start", execution.startsWith("started:flood-stderr\n"));
            assertFalse("finite output must not time out when capture is full", result.timedOut());
            assertEquals(0, result.exitCode());
            assertTrue("child must finish writing before it exits", execution.endsWith("finished\n"));
            assertEquals(ProcessRunner.DEFAULT_MAX_OUTPUT_BYTES, result.output().length);
        } finally {
            Files.deleteIfExists(marker);
        }
    }

    @Test(timeout = 60000)
    public void testExitedChildWithHeldDescendantPipeLeavesNoDrainer() throws Exception {
        assumeJvm();
        try (DescendantFixture fixture = new DescendantFixture()) {
            ProcessRunner.Result result = ProcessRunner.execute(fixture.command("fork-descendant"), 20000);
            assertStopped(fixture.marker);
            assertEquals("started:fork-descendant\nfinished\n",
                    new String(Files.readAllBytes(fixture.marker), StandardCharsets.US_ASCII));
            fixture.assertDescendantAlive();
            assertFalse(result.timedOut());
            assertEquals(0, result.exitCode());
            assertNoNewRunnerThreads(fixture.previous);
        }
    }

    @Test(timeout = 60000)
    public void testTimeoutReapsChildWithoutWaitingForDescendantPipe() throws Exception {
        assumeJvm();
        try (DescendantFixture fixture = new DescendantFixture()) {
            ProcessRunner.Result result = ProcessRunner.execute(fixture.command("fork-and-hold"), 3000);
            assertStopped(fixture.marker);
            assertEquals("started:fork-and-hold\n",
                    new String(Files.readAllBytes(fixture.marker), StandardCharsets.US_ASCII));
            fixture.assertDescendantAlive();
            assertTrue(result.timedOut());
            assertEquals(-1, result.exitCode());
            assertNoNewRunnerThreads(fixture.previous);
        }
    }

    @Test(timeout = 90000)
    public void testRepeatedInterruptReapsChildAndPreservesStatus() throws Exception {
        assumeJvm();
        for (int attempt = 0; attempt < 3; attempt++) {
            final AtomicReference<Throwable> outcome = new AtomicReference<>();
            final AtomicBoolean interrupted = new AtomicBoolean();
            Thread caller = null;
            try (DescendantFixture fixture = new DescendantFixture()) {
                final ProcessBuilder command = fixture.command("fork-and-hold");
                caller = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            ProcessRunner.execute(command, 30000);
                            outcome.set(new AssertionError("interrupted execution returned normally"));
                        } catch (InterruptedException e) {
                            interrupted.set(Thread.currentThread().isInterrupted());
                            outcome.set(e);
                        } catch (Throwable e) {
                            outcome.set(e);
                        }
                    }
                }, "process-runner-cancellation");
                caller.setDaemon(true);
                caller.start();
                fixture.awaitDescendant();
                caller.interrupt();
                caller.join(10000);
                assertFalse("interrupted caller must finish", caller.isAlive());
                assertTrue("expected InterruptedException, got " + outcome.get(),
                        outcome.get() instanceof InterruptedException);
                assertTrue("interrupt status must be preserved", interrupted.get());
                assertStopped(fixture.marker);
                fixture.assertDescendantAlive();
                assertNoNewRunnerThreads(fixture.previous);
            } finally {
                if (caller != null) {
                    caller.interrupt();
                    caller.join(5000);
                }
            }
        }
    }

    private static final class DescendantFixture implements AutoCloseable {
        final Path directory = Files.createTempDirectory("process-descendant-");
        final Path marker = directory.resolve("child.marker");
        final Path descendant = directory.resolve("descendant.marker");
        final Path stop = directory.resolve("stop");
        final Set<Long> previous = runnerThreads();

        DescendantFixture() throws Exception {
        }

        ProcessBuilder command(String mode) {
            return child(marker, mode, descendant.toString(), stop.toString());
        }

        void awaitDescendant() throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!Files.exists(descendant) || Files.size(descendant) == 0) {
                if (System.nanoTime() >= deadline) {
                    fail("descendant fixture never reached its startup marker");
                }
                Thread.sleep(10);
            }
            assertDescendantAlive();
        }

        void assertDescendantAlive() throws Exception {
            assertTrue("descendant must actually start", Files.size(descendant) > 0);
            try (RandomAccessFile file = new RandomAccessFile(descendant.toFile(), "rw");
                    FileLock lock = file.getChannel().tryLock()) {
                assertNull("the descendant must still hold the inherited pipe", lock);
            }
        }

        @Override
        public void close() throws Exception {
            // Explicit fixture cleanup, not a claim the runner kills arbitrary
            // grandchildren. Java 8 has no portable process-tree termination.
            Files.write(stop, new byte[] { 1 });
            try {
                if (Files.exists(descendant)) {
                    assertStopped(descendant);
                }
                if (Files.exists(marker)) {
                    assertStopped(marker);
                }
                assertNoNewRunnerThreads(previous);
            } finally {
                Files.deleteIfExists(marker);
                Files.deleteIfExists(descendant);
                Files.deleteIfExists(stop);
                Files.deleteIfExists(directory);
            }
        }
    }

    private static ProcessBuilder child(Path marker, String mode, String... extra) {
        String executable = System.getProperty("os.name", "").startsWith("Windows") ? "java.exe" : "java";
        File java = new File(new File(System.getProperty("java.home"), "bin"), executable);
        List<String> command = new ArrayList<>(Arrays.asList(java.getAbsolutePath(), "-cp",
                System.getProperty("java.class.path"), ProcessRunnerChild.class.getName(), marker.toString(), mode));
        command.addAll(Arrays.asList(extra));
        return new ProcessBuilder(command);
    }

    private static Set<Long> runnerThreads() {
        int size = 64;
        Thread[] threads;
        int count;
        do {
            threads = new Thread[size];
            count = Thread.enumerate(threads);
            size *= 2;
        } while (count == threads.length);
        Set<Long> ids = new HashSet<>();
        String runner = ProcessRunner.class.getName();
        for (int i = 0; i < count; i++) {
            for (StackTraceElement frame : threads[i].getStackTrace()) {
                if (frame.getClassName().equals(runner) || frame.getClassName().startsWith(runner + "$")) {
                    ids.add(threads[i].getId());
                    break;
                }
            }
        }
        return ids;
    }

    private static void assertNoNewRunnerThreads(Set<Long> previous) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        Set<Long> remaining;
        do {
            remaining = runnerThreads();
            remaining.removeAll(previous);
            if (remaining.isEmpty()) {
                return;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        fail("runner left new drainers alive: " + remaining);
    }

    private static void assertStopped(Path marker) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        do {
            try (RandomAccessFile file = new RandomAccessFile(marker.toFile(), "rw");
                    FileLock lock = file.getChannel().tryLock()) {
                if (lock != null) {
                    return;
                }
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        fail("runner returned while the child still held its process-lifetime lock");
    }

    private static void assumeJvm() {
        Assume.assumeTrue("child-JVM execution is verified by JVM suites, not the native test classpath",
                System.getProperty("org.graalvm.nativeimage.imagecode") == null);
    }
}
