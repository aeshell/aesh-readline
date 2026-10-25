package org.aesh.terminal.detect;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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
        private TerminalCapabilities async;
        private FutureTask<TerminalCapabilities> full;

        ProbeFixture() {
            Assume.assumeFalse("the public async/full factories skip standalone queries in multiplexers",
                    new TerminalDetector().isInMultiplexer());
            saved = TerminalCapabilities.getInstance();
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
            assertEquals(1, available.get());
            assertEquals(1, opens.get());
            assertEquals(1, writes.get());
            assertEquals(1, closes.get());
        }

        @Override
        public void close() throws Exception {
            release.countDown();
            try {
                if (async != null) {
                    assertTrue("async fixture must finish before restoring globals", async.awaitColors(10, TimeUnit.SECONDS));
                }
                if (full != null) {
                    full.get(10, TimeUnit.SECONDS);
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
