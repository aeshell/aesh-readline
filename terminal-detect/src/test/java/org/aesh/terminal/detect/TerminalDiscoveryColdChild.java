package org.aesh.terminal.detect;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** No TerminalCapabilities factory or cache preflight before the worker gate. */
public final class TerminalDiscoveryColdChild {

    private TerminalDiscoveryColdChild() {
    }

    public static void main(String[] args) throws Exception {
        final int workers = 8;
        final CountDownLatch ready = new CountDownLatch(workers);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch calling = new CountDownLatch(workers);
        final CountDownLatch done = new CountDownLatch(workers);
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final TerminalCapabilities[] results = new TerminalCapabilities[workers];
        ColdTransport fixture = new ColdTransport();
        // Fixture setup uses the existing query seam directly so it does not
        // initialize the facade before the public factory race.
        TerminalColorQuery.setTransport(fixture);
        for (int i = 0; i < workers; i++) {
            final int index = i;
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    ready.countDown();
                    try {
                        if (!start.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("start gate timed out");
                        }
                        calling.countDown();
                        if ((index & 1) == 0) {
                            results[index] = TerminalCapabilities.detectFull();
                        } else {
                            TerminalCapabilities async = TerminalCapabilities.detectAsync();
                            if (!async.awaitColors(10, TimeUnit.SECONDS)) {
                                throw new AssertionError("async completion timed out");
                            }
                            results[index] = TerminalCapabilities.detectFull();
                            if (results[index] != async) {
                                throw new AssertionError("full did not reuse async result");
                            }
                        }
                    } catch (Throwable e) {
                        failure.compareAndSet(null, e);
                    } finally {
                        done.countDown();
                    }
                }
            }, "cold-discovery-" + i);
            thread.setDaemon(true);
            thread.start();
        }
        try {
            if (!ready.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("workers did not become ready");
            }
            System.out.println("barrier-release");
            start.countDown();
            if (!calling.await(10, TimeUnit.SECONDS) || !fixture.readEntered.await(10, TimeUnit.SECONDS)) {
                throw new AssertionError("cold workers or fixture did not execute");
            }
        } finally {
            start.countDown();
            fixture.release.countDown();
        }
        if (!done.await(10, TimeUnit.SECONDS) || failure.get() != null) {
            throw new AssertionError("cold discovery failed", failure.get());
        }
        for (TerminalCapabilities result : results) {
            if (result == null || result != results[0] || result.backgroundRGB() == null
                    || result.synchronizedOutputSupport() != ModeSupport.SUPPORTED) {
                throw new AssertionError("workers did not share a completed measured result");
            }
        }
        if (fixture.opens.get() != 1 || fixture.writes.get() != 1 || fixture.closes.get() != 1) {
            throw new AssertionError("duplicate or unclosed cold sessions");
        }
        System.out.println("cold-mixed workers=8 opens=1 writes=1 closes=1");
    }

    private static final class ColdTransport implements TerminalProbeTransport {
        final CountDownLatch readEntered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger opens = new AtomicInteger();
        final AtomicInteger writes = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TerminalProbeSession open() {
            opens.incrementAndGet();
            String esc = Character.toString((char) 27);
            String bel = Character.toString((char) 7);
            byte[] reply = (esc + "[?2026;1$y" + esc + "[?2027;1$y" + esc + "[?63;1;2;4c"
                    + esc + "]10;rgb:ffff/ffff/ffff" + bel + esc + "]11;rgb:0000/0000/0000" + bel)
                    .getBytes(StandardCharsets.US_ASCII);
            final InputStream input = new ByteArrayInputStream(reply) {
                @Override
                public int read(byte[] buffer, int offset, int length) {
                    readEntered.countDown();
                    try {
                        if (!release.await(15, TimeUnit.SECONDS)) {
                            throw new AssertionError("read gate timed out");
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
    }
}
