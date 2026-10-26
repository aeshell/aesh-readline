package org.aesh.terminal.detect;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assume;
import org.junit.Test;

public class TerminalDetectionConcurrencyTest {

    @Test(timeout = 45000)
    public void testPendingManagedResultDoesNotJoinOrRestartDiscovery() throws Exception {
        FutureTask<TerminalDetection> request = null;
        try (ProbeFixture fixture = new ProbeFixture()) {
            final TerminalCapabilities pending = fixture.startAsync();
            fixture.awaitRead();
            assertFalse("fixture must still be pending", pending.awaitColors(0, TimeUnit.NANOSECONDS));

            request = start("managed-consumer", new Callable<TerminalDetection>() {
                @Override
                public TerminalDetection call() {
                    TerminalDetection owner = TerminalDetection.managed(pending);
                    assertSame(pending, owner.capabilities());
                    assertNull(owner.capabilities().backgroundRGB());
                    return owner;
                }
            });
            TerminalDetection owner = request.get(5, TimeUnit.SECONDS);
            assertFalse("reading must not complete the producer", pending.awaitColors(0, TimeUnit.NANOSECONDS));
            TerminalCapabilities unrelated = TerminalDetection.managed(null).capabilities();
            TerminalCapabilities.setInstance(unrelated);

            fixture.release.countDown();
            assertTrue("producer must finish after release", pending.awaitColors(10, TimeUnit.SECONDS));
            assertSame(pending, owner.capabilities());
            assertArrayEquals(new int[] { 0, 0, 0 }, owner.capabilities().backgroundRGB());
            assertArrayEquals(new int[] { 17, 34, 51 }, owner.capabilities().paletteColor(0));
            assertEquals(ModeSupport.SUPPORTED, owner.capabilities().synchronizedOutputSupport());
            assertSame("late producer must not replace the new default", unrelated, TerminalCapabilities.getInstance());
            fixture.assertOneRound();
        } finally {
            if (request != null) {
                request.cancel(true);
            }
        }
    }

    @Test(timeout = 45000)
    public void testScopedFactoriesDoNotWaitForUnrelatedFullDetection() throws Exception {
        final TerminalCapabilities supplied = TerminalDetection.environmentOnly(
                Collections.singletonMap("TERM", "xterm-256color")).capabilities();
        FutureTask<TerminalCapabilities> request = null;
        try (ProbeFixture fixture = new ProbeFixture()) {
            final FutureTask<TerminalCapabilities> full = fixture.startFull();
            fixture.awaitRead();

            request = start("independent-consumer", new Callable<TerminalCapabilities>() {
                @Override
                public TerminalCapabilities call() {
                    assertSame(supplied, TerminalDetection.managed(supplied).capabilities());
                    assertEquals("unknown", TerminalDetection.managed(null).capabilities().terminalName());
                    assertEquals("xterm", TerminalDetection.environmentOnly(
                            Collections.singletonMap("TERM", "xterm-256color")).capabilities().terminalName());
                    return TerminalDetection.environmentOnly().capabilities();
                }
            });
            assertNotNull(request.get(5, TimeUnit.SECONDS));
            assertFalse("full discovery must remain gated while scopes are created", full.isDone());
            fixture.release.countDown();
            assertArrayEquals(new int[] { 0, 0, 0 }, full.get(10, TimeUnit.SECONDS).backgroundRGB());
            fixture.assertOneRound();
        } finally {
            if (request != null) {
                request.cancel(true);
            }
        }
    }

    @Test(timeout = 45000)
    public void testSharedHintsDoNotWaitForFullDetectionIo() throws Exception {
        FutureTask<TerminalCapabilities> reader = null;
        try (ProbeFixture fixture = new ProbeFixture()) {
            FutureTask<TerminalCapabilities> full = fixture.startFull();
            fixture.awaitRead();
            reader = start("shared-hints-reader", new Callable<TerminalCapabilities>() {
                @Override
                public TerminalCapabilities call() {
                    return TerminalCapabilities.getInstance();
                }
            });
            TerminalCapabilities pending = reader.get(5, TimeUnit.SECONDS);
            assertFalse("full discovery must still be gated", full.isDone());
            assertNull("pending hints must not fabricate colors", pending.backgroundRGB());
            assertFalse("the published result must track pending discovery", pending.awaitColors(0, TimeUnit.NANOSECONDS));

            fixture.release.countDown();
            assertSame("completion must fill the same published result", pending, full.get(10, TimeUnit.SECONDS));
            assertArrayEquals(new int[] { 0, 0, 0 }, pending.backgroundRGB());
            fixture.assertOneRound();
        } finally {
            if (reader != null) {
                reader.cancel(true);
            }
        }
    }

    @Test(timeout = 45000)
    public void testAsyncCallerReusesPendingSynchronousRound() throws Exception {
        FutureTask<TerminalCapabilities> request = null;
        try (ProbeFixture fixture = new ProbeFixture()) {
            FutureTask<TerminalCapabilities> full = fixture.startFull();
            fixture.awaitRead();
            request = start("async-round-consumer", new Callable<TerminalCapabilities>() {
                @Override
                public TerminalCapabilities call() {
                    return TerminalCapabilities.detectAsync();
                }
            });
            TerminalCapabilities pending = request.get(5, TimeUnit.SECONDS);
            assertFalse("async must not wait for synchronous I/O", full.isDone());
            assertSame(pending, TerminalCapabilities.getInstance());
            assertFalse(pending.awaitColors(0, TimeUnit.NANOSECONDS));
            fixture.release.countDown();
            assertSame(pending, full.get(10, TimeUnit.SECONDS));
            assertSame(pending, TerminalCapabilities.detectAsync());
            assertSame(pending, TerminalCapabilities.detectFull());
            fixture.assertOneRound();
        } finally {
            if (request != null) {
                request.cancel(true);
            }
        }
    }

    @Test(timeout = 45000)
    public void testThemeEventDuringFullIoWinsOverLateColors() throws Exception {
        FutureTask<TerminalCapabilities> update = null;
        try (ProbeFixture fixture = new ProbeFixture()) {
            FutureTask<TerminalCapabilities> full = fixture.startFull();
            fixture.awaitRead();
            update = start("theme-event", new Callable<TerminalCapabilities>() {
                @Override
                public TerminalCapabilities call() {
                    TerminalCapabilities.onThemeChanged(TerminalTheme.LIGHT);
                    return TerminalCapabilities.getInstance();
                }
            });
            TerminalCapabilities pending = update.get(5, TimeUnit.SECONDS);
            assertFalse("theme publication must not wait for I/O", full.isDone());
            assertEquals(TerminalTheme.LIGHT, pending.theme());
            fixture.release.countDown();
            assertSame(pending, full.get(10, TimeUnit.SECONDS));
            assertEquals("late RGB must not replace the event", TerminalTheme.LIGHT, pending.theme());
            assertNull(pending.backgroundRGB());
            assertNull(pending.foregroundRGB());
            assertArrayEquals(new int[] { 17, 34, 51 }, pending.paletteColor(0));
            assertEquals(ModeSupport.SUPPORTED, pending.synchronizedOutputSupport());
            fixture.assertOneRound();
        } finally {
            if (update != null) {
                update.cancel(true);
            }
        }
    }

    @Test(timeout = 45000)
    public void testInvalidationStartsNextRoundWithoutWaitingForRetiredIo() throws Exception {
        try (ProbeFixture fixture = new ProbeFixture()) {
            FutureTask<TerminalCapabilities> full = fixture.startFull();
            fixture.awaitRead();
            FutureTask<TerminalCapabilities> replacement = start("next-generation", new Callable<TerminalCapabilities>() {
                @Override
                public TerminalCapabilities call() {
                    TerminalCapabilities.invalidate();
                    return fixture.startAsync();
                }
            });
            try {
                TerminalCapabilities next = replacement.get(5, TimeUnit.SECONDS);
                assertFalse("retired I/O must remain gated", full.isDone());
                assertSame(next, TerminalCapabilities.getInstance());
                assertFalse(next.awaitColors(0, TimeUnit.NANOSECONDS));
                fixture.release.countDown();
                assertSame("full caller must join the current generation", next, full.get(10, TimeUnit.SECONDS));
                assertTrue(next.awaitColors(10, TimeUnit.SECONDS));
                assertArrayEquals(new int[] { 0, 0, 0 }, next.backgroundRGB());
                fixture.assertRounds(2);
            } finally {
                fixture.release.countDown();
                // The request can start a producer, so finish it before the
                // fixture restores shared state even on a regression failure.
                replacement.get(10, TimeUnit.SECONDS);
            }
        }
    }

    @Test(timeout = 45000)
    public void testJoinedFailureIsNotCachedAsSuccessOrRetried() throws Exception {
        final RuntimeException failure = new IllegalStateException("fixture discovery failure");
        FutureTask<TerminalCapabilities> joined = null;
        try (ProbeFixture fixture = new ProbeFixture(failure)) {
            FutureTask<TerminalCapabilities> full = fixture.startFull();
            fixture.awaitRead();
            joined = start("failed-round-joiner", new Callable<TerminalCapabilities>() {
                @Override
                public TerminalCapabilities call() {
                    return TerminalCapabilities.detectFull();
                }
            });
            fixture.release.countDown();
            assertFailure(full, failure);
            assertFailure(joined, failure);
            try {
                TerminalCapabilities.detectFull();
                fail("failed discovery must not become cached success");
            } catch (RuntimeException expected) {
                assertSame(failure, expected);
            }
            assertTrue("failed attempt must release completion waiters",
                    TerminalCapabilities.getInstance().awaitColors(0, TimeUnit.NANOSECONDS));
            fixture.assertOneRound();
        } finally {
            if (joined != null) {
                joined.cancel(true);
            }
        }
    }

    @Test(timeout = 45000)
    public void testRetiredFullCannotOverwriteSuppliedCompleteDefault() throws Exception {
        final TerminalCapabilities replacement;
        try (ProbeFixture seed = new ProbeFixture()) {
            FutureTask<TerminalCapabilities> complete = seed.startFull();
            seed.awaitRead();
            seed.release.countDown();
            replacement = complete.get(10, TimeUnit.SECONDS);
            seed.assertOneRound();
        }
        FutureTask<TerminalCapabilities> update = null;
        try (ProbeFixture fixture = new ProbeFixture()) {
            FutureTask<TerminalCapabilities> full = fixture.startFull();
            fixture.awaitRead();
            update = start("supplied-default", new Callable<TerminalCapabilities>() {
                @Override
                public TerminalCapabilities call() {
                    TerminalCapabilities.setInstance(replacement);
                    return TerminalCapabilities.getInstance();
                }
            });
            assertSame(replacement, update.get(5, TimeUnit.SECONDS));
            assertFalse("retired full round must still be gated", full.isDone());
            fixture.release.countDown();
            assertSame("late full completion must honor replacement", replacement, full.get(10, TimeUnit.SECONDS));
            assertSame(replacement, TerminalCapabilities.getInstance());
            fixture.assertOneRound();
        } finally {
            if (update != null) {
                update.cancel(true);
            }
        }
    }

    private static void assertFailure(FutureTask<?> task, Throwable expected) throws Exception {
        try {
            task.get(10, TimeUnit.SECONDS);
            fail("discovery must report its failure");
        } catch (ExecutionException failure) {
            assertSame(expected, failure.getCause());
        }
    }

    private static <T> FutureTask<T> start(String name, Callable<T> operation) {
        FutureTask<T> task = new FutureTask<>(operation);
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.start();
        return task;
    }

    /** Existing gated/counted transport pattern, without a real terminal. */
    private static final class ProbeFixture implements TerminalProbeTransport, AutoCloseable {
        private final TerminalCapabilities saved;
        private final CountDownLatch readEntered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        private final AtomicInteger available = new AtomicInteger();
        private final AtomicInteger opens = new AtomicInteger();
        private final AtomicInteger writes = new AtomicInteger();
        private final AtomicInteger closes = new AtomicInteger();
        private final byte[] reply;
        private final RuntimeException failure;
        private TerminalCapabilities async;
        private FutureTask<TerminalCapabilities> full;

        ProbeFixture() {
            this(null);
        }

        ProbeFixture(RuntimeException failure) {
            Assume.assumeFalse("the public async/full factories skip standalone queries in multiplexers",
                    new TerminalDetector().isInMultiplexer());
            saved = TerminalCapabilities.getInstance();
            this.failure = failure;
            String esc = Character.toString((char) 27);
            String bel = Character.toString((char) 7);
            reply = (esc + "[?2026;1$y" + esc + "[?2027;1$y" + esc + "[?63;1;2;4c"
                    + esc + "]10;rgb:ffff/ffff/ffff" + bel + esc + "]11;rgb:0000/0000/0000" + bel
                    + esc + "]4;0;rgb:1111/2222/3333" + bel).getBytes(StandardCharsets.US_ASCII);
            TerminalCapabilities.setProbeTransport(this);
            TerminalCapabilities.invalidate();
        }

        TerminalCapabilities startAsync() {
            async = TerminalCapabilities.detectAsync();
            return async;
        }

        FutureTask<TerminalCapabilities> startFull() {
            full = start("full-producer", new Callable<TerminalCapabilities>() {
                @Override
                public TerminalCapabilities call() {
                    return TerminalCapabilities.detectFull();
                }
            });
            return full;
        }

        void awaitRead() throws InterruptedException {
            assertTrue("the fixture must execute its gated read", readEntered.await(5, TimeUnit.SECONDS));
        }

        @Override
        public boolean isAvailable() {
            available.incrementAndGet();
            return true;
        }

        @Override
        public TerminalProbeSession open() {
            opens.incrementAndGet();
            final InputStream input = new ByteArrayInputStream(reply) {
                @Override
                public int read(byte[] buffer, int offset, int length) {
                    readEntered.countDown();
                    try {
                        if (!release.await(30, TimeUnit.SECONDS)) {
                            throw new AssertionError("fixture release timed out");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("fixture interrupted", e);
                    }
                    if (failure != null) {
                        throw failure;
                    }
                    return super.read(buffer, offset, length);
                }
            };
            return new TerminalProbeSession() {
                @Override
                public void write(byte[] data) {
                    writes.incrementAndGet();
                }

                @Override
                public InputStream input() {
                    return input;
                }

                @Override
                public void close() {
                    closes.incrementAndGet();
                }
            };
        }

        void assertOneRound() {
            assertRounds(1);
        }

        void assertRounds(int expected) {
            assertEquals(expected, available.get());
            assertEquals(expected, opens.get());
            assertEquals(expected, writes.get());
            assertEquals(expected, closes.get());
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            try {
                if (async != null) {
                    assertTrue("async fixture must finish before restoring globals", async.awaitColors(10, TimeUnit.SECONDS));
                }
                if (full != null) {
                    try {
                        full.get(10, TimeUnit.SECONDS);
                    } catch (ExecutionException expected) {
                        if (failure == null || expected.getCause() != failure) {
                            throw expected;
                        }
                    }
                }
            } finally {
                if (full != null) {
                    full.cancel(true);
                }
                TerminalCapabilities.setProbeTransport(null);
                TerminalCapabilities.setInstance(saved);
            }
        }
    }
}
