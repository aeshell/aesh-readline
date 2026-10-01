package org.aesh.terminal.detect;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assume;
import org.junit.Test;

public class TerminalCapabilitiesTest {

    @Test
    public void testDetectReturnsNonNull() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        assertNotNull(caps);
        assertNotNull(caps.terminalName());
        assertNotNull(caps.imageProtocol());
        assertNotNull(caps.theme());
    }

    @Test
    public void testDetectColorConsistency() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        if (caps.supportsTrueColor()) {
            assertTrue(caps.supports256Colors());
            assertTrue(caps.supportsColor());
        }
        if (caps.supports256Colors()) {
            assertTrue(caps.supportsColor());
        }
    }

    @Test
    public void testSingleton() {
        TerminalCapabilities a = TerminalCapabilities.getInstance();
        TerminalCapabilities b = TerminalCapabilities.getInstance();
        assertSame(a, b);
    }

    @Test
    public void testSetInstance() {
        TerminalCapabilities custom = TerminalCapabilities.detect();
        TerminalCapabilities.setInstance(custom);
        assertSame(custom, TerminalCapabilities.getInstance());
        // Reset for other tests
        TerminalCapabilities.setInstance(null);
    }

    @Test
    public void testToString() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        String str = caps.toString();
        assertNotNull(str);
        assertTrue(str.contains("terminal="));
        assertTrue(str.contains("trueColor="));
        assertTrue(str.contains("imageProtocol="));
    }

    @Test
    public void testPaletteColorsEmptyBeforeQuery() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        assertNotNull(caps.paletteColors());
        assertTrue(caps.paletteColors().isEmpty());
        assertNull(caps.foregroundRGB());
        assertNull(caps.backgroundRGB());
    }

    @Test
    public void testImageProtocolValues() {
        assertEquals(4, ImageProtocol.values().length);
        assertNotNull(ImageProtocol.valueOf("NONE"));
        assertNotNull(ImageProtocol.valueOf("KITTY"));
        assertNotNull(ImageProtocol.valueOf("ITERM2"));
        assertNotNull(ImageProtocol.valueOf("SIXEL"));
    }

    @Test
    public void testDetectFullCachesInstance() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities.invalidate();
            TerminalCapabilities a = TerminalCapabilities.detectFull();
            TerminalCapabilities b = TerminalCapabilities.detectFull();
            assertSame("Repeat detectFull must return cached capabilities, not re-probe", a, b);
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testDetectAsyncSharesInstance() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities.invalidate();
            TerminalCapabilities a = TerminalCapabilities.detectAsync();
            TerminalCapabilities b = TerminalCapabilities.detectAsync();
            assertSame("Repeat detectAsync must share one background query", a, b);
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testInvalidateForcesReprobe() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities.invalidate();
            TerminalCapabilities a = TerminalCapabilities.detectFull();
            TerminalCapabilities.invalidate();
            TerminalCapabilities b = TerminalCapabilities.detectFull();
            assertNotSame("Post-invalidate detectFull must re-probe", a, b);
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testThemeEventWinsOverLateAsyncColorResponse() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities caps = TerminalCapabilities.detect();
            TerminalCapabilities.setInstance(caps);
            TerminalCapabilities.onThemeChanged(TerminalTheme.DARK);

            TerminalColorQuery late = new TerminalColorQuery();
            late.foreground = new int[] { 0, 0, 0 };
            late.background = new int[] { 255, 255, 255 };
            late.palette = Collections.singletonMap(0, new int[] { 20, 20, 20 });
            caps.applyColorResult(late);

            assertSame(caps, TerminalCapabilities.getInstance());
            assertEquals(TerminalTheme.DARK, caps.theme());
            assertNull("Old foreground must not reappear", caps.foregroundRGB());
            assertNull("Old background must not reappear", caps.backgroundRGB());
            assertEquals("Non-theme capabilities still update", 1, caps.paletteColors().size());
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testMeasuredWhiteOverridesDarkHint() throws Exception {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        setResolvedTheme(caps, TerminalTheme.DARK);

        TerminalColorQuery measured = new TerminalColorQuery();
        measured.background = new int[] { 255, 255, 255 };
        caps.applyColorResult(measured);

        assertEquals(TerminalTheme.LIGHT, caps.theme());
        assertArrayEquals(new int[] { 255, 255, 255 }, caps.backgroundRGB());
    }

    @Test
    public void testMeasuredBlackOverridesLightHint() throws Exception {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        setResolvedTheme(caps, TerminalTheme.LIGHT);

        TerminalColorQuery measured = new TerminalColorQuery();
        measured.background = new int[] { 0, 0, 0 };
        caps.applyColorResult(measured);

        assertEquals(TerminalTheme.DARK, caps.theme());
        assertArrayEquals(new int[] { 0, 0, 0 }, caps.backgroundRGB());
    }

    @Test
    public void testAbsentReplyKeepsEarlierHint() throws Exception {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        setResolvedTheme(caps, TerminalTheme.DARK);

        TerminalColorQuery partial = new TerminalColorQuery();
        partial.foreground = new int[] { 255, 255, 255 };
        caps.applyColorResult(partial);

        assertEquals(TerminalTheme.DARK, caps.theme());
        assertNull(caps.backgroundRGB());
        assertArrayEquals(new int[] { 255, 255, 255 }, caps.foregroundRGB());
    }

    /**
     * Preset a previously resolved theme, simulating an earlier
     * environment/config hint. Reflection mirrors the existing
     * theme-event test: the field has no other injectable seam.
     */
    private static void setResolvedTheme(TerminalCapabilities caps, TerminalTheme theme)
            throws Exception {
        Field field = TerminalCapabilities.class.getDeclaredField("resolvedTheme");
        field.setAccessible(true);
        field.set(caps, theme);
    }

    @Test
    public void testRgbReplyImpliesFullLadder() {
        // A terminal answering OSC color queries supports 24-bit output,
        // which includes the 256 palette and basic color by definition.
        TerminalCapabilities caps = TerminalCapabilities.detect();

        TerminalColorQuery measured = new TerminalColorQuery();
        measured.foreground = new int[] { 255, 255, 255 };
        caps.applyColorResult(measured);

        assertTrue(caps.supportsTrueColor());
        assertTrue(caps.supports256Colors());
        assertTrue(caps.supportsColor());
        assertNull("unprobed grapheme clustering must stay null, not false",
                caps.nativeGraphemeClustering());
    }

    @Test
    public void testLadderHoldsAcrossThemeEvent() {
        // Relations must hold in every state, independent of the real
        // environment this test happens to run in.
        TerminalCapabilities caps = TerminalCapabilities.detect();
        assertLadder(caps);

        TerminalColorQuery measured = new TerminalColorQuery();
        measured.background = new int[] { 0, 0, 0 };
        caps.applyColorResult(measured);
        assertLadder(caps);

        TerminalCapabilities.setInstance(caps);
        try {
            TerminalCapabilities.onThemeChanged(TerminalTheme.LIGHT);
            assertLadder(caps);
        } finally {
            TerminalCapabilities.setInstance(null);
        }
    }

    private static void assertLadder(TerminalCapabilities caps) {
        if (caps.supportsTrueColor()) {
            assertTrue("true color implies 256 colors", caps.supports256Colors());
        }
        if (caps.supports256Colors()) {
            assertTrue("256 colors imply basic color", caps.supportsColor());
        }
    }

    @Test
    public void testGetInstanceNeverReturnsNullDuringInvalidation() throws Exception {
        // Readers race an invalidator: the returned reference is captured
        // once, so clearing the shared field mid-call cannot null it.
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean stop = new AtomicBoolean();
            Thread[] readers = new Thread[4];
            for (int i = 0; i < readers.length; i++) {
                readers[i] = new Reader(stop, failure);
                readers[i].start();
            }
            for (int i = 0; i < 200000 && failure.get() == null; i++) {
                TerminalCapabilities.invalidate();
            }
            stop.set(true);
            for (Thread reader : readers) {
                reader.join(10000);
            }
            assertNull("getInstance() returned null under invalidation",
                    failure.getAndSet(null));
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    private static final class Reader extends Thread {
        private final AtomicBoolean stop;
        private final AtomicReference<Throwable> failure;

        Reader(AtomicBoolean stop, AtomicReference<Throwable> failure) {
            this.stop = stop;
            this.failure = failure;
        }

        @Override
        public void run() {
            try {
                while (!stop.get() && failure.get() == null) {
                    if (TerminalCapabilities.getInstance() == null) {
                        throw new AssertionError("getInstance() returned null");
                    }
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }
    }

    // ==================== Async-to-full upgrade (#346) ====================

    /** Transport serving queued canned replies while counting opens. */
    private static final class CountingTransport implements TerminalProbeTransport {
        final byte[][] responses;
        int opens;

        CountingTransport(byte[]... responses) {
            this.responses = responses;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TerminalProbeSession open() {
            final byte[] response = responses[Math.min(opens, responses.length - 1)];
            opens++;
            return new TerminalProbeSession() {
                private final InputStream in = new ByteArrayInputStream(response);

                @Override
                public void write(byte[] data) {
                }

                @Override
                public InputStream input() {
                    return in;
                }

                @Override
                public void close() {
                }
            };
        }
    }

    /**
     * Stream blocking its first read on a latch (30s cap), then serving
     * the payload. Lets a test hold an async probe mid-flight.
     */
    private static final class GatedStream extends InputStream {
        final CountDownLatch release;
        final byte[] payload;
        int pos;
        boolean released;

        GatedStream(CountDownLatch release, byte[] payload) {
            this.release = release;
            this.payload = payload.clone();
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (pos >= payload.length) {
                return -1;
            }
            if (!released) {
                try {
                    if (!release.await(30, TimeUnit.SECONDS)) {
                        return -1;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
                released = true;
            }
            int count = Math.min(len, payload.length - pos);
            System.arraycopy(payload, pos, b, off, count);
            pos += count;
            return count;
        }
    }

    private static byte[] probeBatch(boolean graphemeTrigger) {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[?2026;1$y");
        sb.append(graphemeTrigger ? "\033[?2027;0$y" : "\033[?2027;1$y");
        sb.append("\033[?63;1;2;4c");
        sb.append("\033]10;rgb:ffff/ffff/ffff\007");
        sb.append("\033]11;rgb:0000/0000/0000\007");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static TerminalProbeSession sessionOf(final InputStream in) {
        return new TerminalProbeSession() {
            @Override
            public void write(byte[] data) {
            }

            @Override
            public InputStream input() {
                return in;
            }

            @Override
            public void close() {
            }
        };
    }

    @Test
    public void testFullReusesCompletedAsync() throws Exception {
        Assume.assumeFalse("live query skipped in multiplexer",
                new TerminalDetector().isInMultiplexer());
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        CountingTransport transport = new CountingTransport(
                probeBatch(true), "\033[1;2R".getBytes(StandardCharsets.US_ASCII));
        try {
            TerminalCapabilities.setProbeTransport(transport);
            TerminalCapabilities.invalidate();
            TerminalCapabilities async = TerminalCapabilities.detectAsync();
            assertTrue("async background probe must finish",
                    async.awaitColors(10, TimeUnit.SECONDS));
            TerminalCapabilities full = TerminalCapabilities.detectFull();
            assertSame("full must upgrade the completed async instance, not re-probe",
                    async, full);
            assertEquals("no second color session may open", 2, transport.opens);
            assertArrayEquals(new int[] { 255, 255, 255 }, full.foregroundRGB());
            assertArrayEquals(new int[] { 0, 0, 0 }, full.backgroundRGB());
            assertEquals(ModeSupport.SUPPORTED, full.synchronizedOutputSupport());
            assertEquals(Boolean.TRUE, full.nativeGraphemeClustering());
            assertSame("upgraded instance must cache as full",
                    full, TerminalCapabilities.detectFull());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testFullJoinsInflightAsync() throws Exception {
        Assume.assumeFalse("live query skipped in multiplexer",
                new TerminalDetector().isInMultiplexer());
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        final int[] opens = new int[1];
        try {
            TerminalCapabilities.setProbeTransport(new TerminalProbeTransport() {
                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public TerminalProbeSession open() {
                    entered.countDown();
                    opens[0]++;
                    final InputStream in = new GatedStream(release, probeBatch(false));
                    return new TerminalProbeSession() {
                        @Override
                        public void write(byte[] data) {
                        }

                        @Override
                        public InputStream input() {
                            return in;
                        }

                        @Override
                        public void close() {
                        }
                    };
                }
            });
            TerminalCapabilities.invalidate();
            TerminalCapabilities async = TerminalCapabilities.detectAsync();
            assertTrue("background probe must open its session", entered.await(5, TimeUnit.SECONDS));
            Thread.sleep(500);
            assertEquals("no second session may open while async is in flight", 1, opens[0]);
            release.countDown();
            TerminalCapabilities full = TerminalCapabilities.detectFull();
            assertSame("full must return the joined async instance", async, full);
            assertArrayEquals(new int[] { 0, 0, 0 }, full.backgroundRGB());
        } finally {
            release.countDown();
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testFullAfterInvalidateReprobes() throws Exception {
        Assume.assumeFalse("live query skipped in multiplexer",
                new TerminalDetector().isInMultiplexer());
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        CountingTransport transport = new CountingTransport(probeBatch(false));
        try {
            TerminalCapabilities.setProbeTransport(transport);
            TerminalCapabilities.invalidate();
            TerminalCapabilities async = TerminalCapabilities.detectAsync();
            assertTrue(async.awaitColors(10, TimeUnit.SECONDS));
            TerminalCapabilities full = TerminalCapabilities.detectFull();
            assertEquals(1, transport.opens);
            TerminalCapabilities.invalidate();
            TerminalCapabilities second = TerminalCapabilities.detectFull();
            assertNotSame("post-invalidate full must re-probe", full, second);
            assertEquals(2, transport.opens);
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testInterruptedFullStillReturnsFull() throws Exception {
        Assume.assumeFalse("live query skipped in multiplexer",
                new TerminalDetector().isInMultiplexer());
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        CountDownLatch release = new CountDownLatch(1);
        final int[] opens = new int[1];
        try {
            TerminalCapabilities.setProbeTransport(new TerminalProbeTransport() {
                @Override
                public boolean isAvailable() {
                    return true;
                }

                @Override
                public TerminalProbeSession open() {
                    opens[0]++;
                    // First session stays gated so the joiner blocks in the
                    // upgrade wait; the interrupt fallback re-opens for a
                    // fresh instant probe.
                    InputStream in = opens[0] == 1
                            ? new GatedStream(release, probeBatch(false))
                            : new ByteArrayInputStream(probeBatch(false));
                    return sessionOf(in);
                }
            });
            TerminalCapabilities.invalidate();
            final TerminalCapabilities async = TerminalCapabilities.detectAsync();
            final AtomicReference<TerminalCapabilities> result = new AtomicReference<>();
            final AtomicBoolean interruptedSeen = new AtomicBoolean();
            Thread joiner = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        result.set(TerminalCapabilities.detectFull());
                    } finally {
                        interruptedSeen.set(Thread.currentThread().isInterrupted());
                    }
                }
            }, "full-joiner");
            joiner.setDaemon(true);
            joiner.start();
            Thread.sleep(500);
            // Interrupt first: the background probe is still gated, so the
            // latch cannot have fired and the wait must throw. Release
            // second so both the background drain and the fallback fresh
            // probe (which needs the session lock) proceed instantly.
            joiner.interrupt();
            release.countDown();
            joiner.join(10000);
            assertFalse("joining full must return", joiner.isAlive());
            assertNotNull(result.get());
            assertNotSame("interrupted join must fall back to fresh compute",
                    async, result.get());
            assertEquals("fallback performs exactly one fresh probe", 2, opens[0]);
            assertTrue("interrupt status must survive detectFull", interruptedSeen.get());
            assertSame("fallback must cache as full", result.get(), TerminalCapabilities.detectFull());
        } finally {
            release.countDown();
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }
}
