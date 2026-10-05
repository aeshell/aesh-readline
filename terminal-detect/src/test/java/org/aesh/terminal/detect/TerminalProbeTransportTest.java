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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assume;
import org.junit.Test;

/**
 * Tests for injectable probe transports ({@link TerminalProbeTransport}).
 * All I/O goes through fakes — no /dev/tty or stty is touched.
 */
public class TerminalProbeTransportTest {

    /** Session serving one canned response, capturing writes, tracking close. */
    private static final class FakeProbeSession implements TerminalProbeSession {
        private final InputStream response;
        private final ByteArrayOutputStream written = new ByteArrayOutputStream();
        private boolean closed;

        FakeProbeSession(byte[] response) {
            this(new ByteArrayInputStream(response));
        }

        FakeProbeSession(InputStream response) {
            this.response = response;
        }

        @Override
        public void write(byte[] data) {
            written.write(data, 0, data.length);
        }

        @Override
        public InputStream input() {
            return response;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** Transport serving queued canned responses, one per open(). */
    private static final class FakeProbeTransport implements TerminalProbeTransport {
        private final boolean available;
        private final byte[][] responses;
        private int opens;
        private FakeProbeSession lastSession;

        FakeProbeTransport(boolean available, byte[]... responses) {
            this.available = available;
            this.responses = responses;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public TerminalProbeSession open() {
            byte[] response = responses[Math.min(opens, responses.length - 1)];
            opens++;
            lastSession = new FakeProbeSession(response);
            return lastSession;
        }
    }

    private static final class ThrowingProbeTransport implements TerminalProbeTransport {
        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TerminalProbeSession open() throws IOException {
            throw new IOException("no console");
        }
    }

    /** A response stream delivered in small reads, with an optional timeout. */
    private static final class ChunkedInputStream extends InputStream {
        private final byte[] response;
        private final boolean timeout;
        private int offset;

        ChunkedInputStream(byte[] response, boolean timeout) {
            this.response = response;
            this.timeout = timeout;
        }

        @Override
        public int read() {
            return offset < response.length ? response[offset++] & 0xff : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (offset == response.length) {
                return timeout ? 0 : -1;
            }
            int count = Math.min(2, Math.min(len, response.length - offset));
            System.arraycopy(response, offset, b, off, count);
            offset += count;
            return count;
        }
    }

    private static final class StreamProbeTransport implements TerminalProbeTransport {
        final FakeProbeSession session;

        StreamProbeTransport(InputStream response) {
            session = new FakeProbeSession(response);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TerminalProbeSession open() {
            return session;
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }

    /**
     * Canned full response: DA1 class 63 with sixel, 2026 supported,
     * 2027 not recognized, white fg, black bg, 16-entry palette, 256-color.
     */
    private static byte[] colorResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[?2026;1$y");
        sb.append("\033[?2027;0$y");
        sb.append("\033[?63;1;2;4c");
        sb.append("\033]10;rgb:ffff/ffff/ffff\007");
        sb.append("\033]11;rgb:0000/0000/0000\007");
        for (int i = 0; i <= 15; i++) {
            sb.append("\033]4;").append(i).append(";rgb:1111/2222/3333\007");
        }
        sb.append("\033]4;255;rgb:eeee/eeee/eeee\007");
        return bytes(sb.toString());
    }

    /**
     * Canned response with a white background: DA1 class 63, 2026
     * supported, 2027 not recognized, white bg. Mirrors
     * {@link #colorResponse()} except for OSC 11.
     */
    private static byte[] whiteBackgroundResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[?2026;1$y");
        sb.append("\033[?2027;0$y");
        sb.append("\033[?63;1;2;4c");
        sb.append("\033]10;rgb:0000/0000/0000\007");
        sb.append("\033]11;rgb:ffff/ffff/ffff\007");
        return bytes(sb.toString());
    }

    @Test
    public void testQueryParsesCannedResponse() {
        FakeProbeTransport transport = new FakeProbeTransport(true, colorResponse());
        TerminalColorQuery result = TerminalColorQuery.query(transport);
        assertNotNull(result);
        assertArrayEquals(new int[] { 255, 255, 255 }, result.foreground);
        assertArrayEquals(new int[] { 0, 0, 0 }, result.background);
        assertNotNull(result.palette);
        assertEquals(16, result.palette.size());
        assertArrayEquals(new int[] { 0x11, 0x22, 0x33 }, result.palette.get(7));
        assertTrue(result.supports256);
        assertEquals(ModeSupport.SUPPORTED, result.mode2026);
        assertEquals(ModeSupport.NOT_SUPPORTED, result.mode2027);
        assertTrue(result.da1Received);
        assertTrue(result.supportsSixel);
        // Query bytes went through the session, session was closed
        assertTrue(Arrays.equals(TerminalColorQuery.buildColorQuery(),
                transport.lastSession.written.toByteArray()));
        assertTrue(transport.lastSession.closed);
    }

    @Test
    public void testQueryUnavailableTransportReturnsNull() {
        FakeProbeTransport transport = new FakeProbeTransport(false, colorResponse());
        assertNull(TerminalColorQuery.query(transport));
        assertEquals(0, transport.opens);
    }

    @Test
    public void testQueryThrowingTransportReturnsNull() {
        assertNull(TerminalColorQuery.query(new ThrowingProbeTransport()));
    }

    @Test
    public void testQueryEmptyResponseReturnsNull() {
        FakeProbeTransport transport = new FakeProbeTransport(true, new byte[0]);
        assertNull(TerminalColorQuery.query(transport));
        assertTrue(transport.lastSession.closed);
    }

    @Test
    public void testGraphemeProbeClustered() {
        FakeProbeTransport transport = new FakeProbeTransport(true, bytes("\033[1;3R"));
        assertTrue(TerminalColorQuery.probeGraphemeClustering(transport));
        // Probe bytes first, cursor-restore bytes after, on the same session
        byte[] written = transport.lastSession.written.toByteArray();
        byte[] probe = TerminalColorQuery.buildGraphemeProbe();
        assertTrue(written.length > probe.length);
        assertTrue(Arrays.equals(probe, Arrays.copyOf(written, probe.length)));
        assertTrue(transport.lastSession.closed);
    }

    @Test
    public void testGraphemeProbeWide() {
        FakeProbeTransport transport = new FakeProbeTransport(true, bytes("\033[1;5R"));
        assertFalse(TerminalColorQuery.probeGraphemeClustering(transport));
    }

    @Test
    public void testGraphemeProbeEmptyReturnsFalse() {
        FakeProbeTransport transport = new FakeProbeTransport(true, new byte[0]);
        assertFalse(TerminalColorQuery.probeGraphemeClustering(transport));
    }

    /**
     * Session serving the color batch first, then the CPR only after the
     * grapheme probe is written — the honest shape of a terminal answering
     * each query after receiving it. A bulk-reading color phase must not
     * consume the CPR early.
     */
    private static final class PhasedProbeSession implements TerminalProbeSession {
        private final byte[] color;
        private final byte[] cpr;
        private final ByteArrayOutputStream written = new ByteArrayOutputStream();
        private final InputStream in = new InputStream() {
            @Override
            public int read() {
                byte[] one = new byte[1];
                int n = read(one, 0, 1);
                return n < 0 ? -1 : one[0] & 0xff;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (writes <= 1) {
                    if (colorPos >= color.length) {
                        return 0;
                    }
                    int count = Math.min(len, color.length - colorPos);
                    System.arraycopy(color, colorPos, b, off, count);
                    colorPos += count;
                    return count;
                }
                if (cprPos >= cpr.length) {
                    return -1;
                }
                int count = Math.min(len, cpr.length - cprPos);
                System.arraycopy(cpr, cprPos, b, off, count);
                cprPos += count;
                return count;
            }
        };
        private int writes;
        private int colorPos;
        private int cprPos;
        private boolean closed;

        PhasedProbeSession(byte[] color, byte[] cpr) {
            this.color = color.clone();
            this.cpr = cpr.clone();
        }

        @Override
        public void write(byte[] data) {
            writes++;
            written.write(data, 0, data.length);
        }

        @Override
        public InputStream input() {
            return in;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class PhasedProbeTransport implements TerminalProbeTransport {
        final PhasedProbeSession session;
        int opens;

        PhasedProbeTransport(byte[] color, byte[] cpr) {
            session = new PhasedProbeSession(color, cpr);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TerminalProbeSession open() {
            opens++;
            return session;
        }
    }

    @Test
    public void testQueryFullRunsBothPhasesInOneSession() {
        PhasedProbeTransport transport = new PhasedProbeTransport(
                colorResponse(), bytes("\033[1;2R"));
        TerminalColorQuery result = TerminalColorQuery.queryFull(transport);
        assertNotNull(result);
        assertArrayEquals(new int[] { 0, 0, 0 }, result.background);
        assertEquals(Boolean.TRUE, result.graphemeClustering);
        assertEquals("color and grapheme must share one raw-mode session", 1, transport.opens);
        byte[] written = transport.session.written.toByteArray();
        byte[] colorQuery = TerminalColorQuery.buildColorQuery();
        byte[] probe = TerminalColorQuery.buildGraphemeProbe();
        byte[] restore = bytes("\0338\033[K");
        assertEquals(colorQuery.length + probe.length + restore.length, written.length);
        assertTrue(Arrays.equals(colorQuery, Arrays.copyOf(written, colorQuery.length)));
        assertTrue(Arrays.equals(probe,
                Arrays.copyOfRange(written, colorQuery.length, colorQuery.length + probe.length)));
        assertTrue(Arrays.equals(restore,
                Arrays.copyOfRange(written, colorQuery.length + probe.length, written.length)));
        assertTrue(transport.session.closed);
    }

    @Test
    public void testQueryFullSkipsGraphemeWhenMode2027Supported() {
        // Same full batch as colorResponse() but with Mode 2027
        // supported: no cursor-position probe may run.
        String supported = new String(colorResponse(), StandardCharsets.US_ASCII)
                .replace("?2027;0$y", "?2027;1$y");
        FakeProbeTransport transport = new FakeProbeTransport(true, bytes(supported));
        TerminalColorQuery result = TerminalColorQuery.queryFull(transport);
        assertNotNull(result);
        assertNull(result.graphemeClustering);
        assertEquals(1, transport.opens);
        assertTrue(Arrays.equals(TerminalColorQuery.buildColorQuery(),
                transport.lastSession.written.toByteArray()));
        assertTrue(transport.lastSession.closed);
    }

    @Test
    public void testColorResponseSplitAcrossReads() {
        StreamProbeTransport transport = new StreamProbeTransport(
                new ChunkedInputStream(colorResponse(), false));

        TerminalColorQuery result = TerminalColorQuery.query(transport);

        assertNotNull(result);
        assertArrayEquals(new int[] { 255, 255, 255 }, result.foreground);
        assertArrayEquals(new int[] { 0, 0, 0 }, result.background);
        assertEquals(ModeSupport.SUPPORTED, result.mode2026);
        assertTrue(transport.session.closed);
        assertArrayEquals(TerminalColorQuery.buildColorQuery(), transport.session.written.toByteArray());
    }

    @Test
    public void testDa1FirstResponseSplitAcrossReads() {
        // DA1 arriving before the mode reports, delivered two bytes at
        // a time: each CSI is framed independently, so the device class
        // must not consume the following DECRPM replies (#307).
        StringBuilder sb = new StringBuilder();
        sb.append("\033[?62;4c");
        sb.append("\033[?2026;1$y");
        sb.append("\033[?2027;1$y");
        sb.append("\033]11;rgb:0000/0000/0000\007");
        StreamProbeTransport transport = new StreamProbeTransport(
                new ChunkedInputStream(bytes(sb.toString()), false));

        TerminalColorQuery result = TerminalColorQuery.query(transport);

        assertNotNull(result);
        assertTrue(result.da1Received);
        assertEquals(ModeSupport.SUPPORTED, result.mode2026);
        assertEquals(ModeSupport.SUPPORTED, result.mode2027);
        assertArrayEquals(new int[] { 0, 0, 0 }, result.background);
        assertTrue(transport.session.closed);
    }

    @Test
    public void testMixedTerminatorsSplitAcrossReads() {
        // ST-terminated foreground followed by BEL-terminated
        // background, delivered two bytes at a time: framing happens
        // after reassembly, so the split cannot join the replies (#306).
        StringBuilder sb = new StringBuilder();
        sb.append("\033[?2026;1$y");
        sb.append("\033[?2027;0$y");
        sb.append("\033[?63;1;2;4c");
        sb.append("\033]10;rgb:ffff/ffff/ffff\033\\");
        sb.append("\033]11;rgb:0000/0000/0000\007");
        StreamProbeTransport transport = new StreamProbeTransport(
                new ChunkedInputStream(bytes(sb.toString()), false));

        TerminalColorQuery result = TerminalColorQuery.query(transport);

        assertNotNull(result);
        assertArrayEquals(new int[] { 255, 255, 255 }, result.foreground);
        assertArrayEquals(new int[] { 0, 0, 0 }, result.background);
        assertTrue(transport.session.closed);
    }

    @Test
    public void testTimeoutEndsPartialColorResponseAndRestoresSession() {
        StreamProbeTransport transport = new StreamProbeTransport(
                new ChunkedInputStream(bytes("\033]11;rgb:ffff/ff"), true));

        TerminalColorQuery result = TerminalColorQuery.query(transport);

        assertNotNull(result);
        assertNull("Incomplete OSC 11 must not report a color", result.background);
        assertTrue("Raw-mode session must close after timeout", transport.session.closed);
    }

    @Test
    public void testTimeoutWithoutBytesReturnsNoResult() {
        StreamProbeTransport transport = new StreamProbeTransport(new ChunkedInputStream(new byte[0], true));

        assertNull(TerminalColorQuery.query(transport));
        assertTrue(transport.session.closed);
    }

    @Test
    public void testCustomTransportReplacesBuiltinExclusively() {
        // An injected-but-unavailable transport skips probing without
        // falling back to /dev/tty (which could steal embedder input).
        try {
            TerminalColorQuery.setTransport(new FakeProbeTransport(false, colorResponse()));
            assertNull(TerminalColorQuery.query());
            assertFalse(TerminalColorQuery.probeGraphemeClustering());
        } finally {
            TerminalColorQuery.setTransport(null);
        }
    }

    @Test
    public void testSetProbeTransportWiresIntoDetectFull() {
        // detectFull skips live queries under tmux/screen by design
        Assume.assumeFalse("live query skipped in multiplexer",
                new TerminalDetector().isInMultiplexer());
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            // One session serves the color batch, then the CPR once the
            // grapheme probe is written (DA1 received + 2027 unsupported
            // triggers it in detectFull).
            TerminalCapabilities.setProbeTransport(
                    new PhasedProbeTransport(colorResponse(), bytes("\033[1;3R")));
            TerminalCapabilities.invalidate();
            TerminalCapabilities caps = TerminalCapabilities.detectFull();
            assertArrayEquals(new int[] { 0, 0, 0 }, caps.backgroundRGB());
            assertArrayEquals(new int[] { 255, 255, 255 }, caps.foregroundRGB());
            assertEquals(16, caps.paletteColors().size());
            assertEquals(ModeSupport.SUPPORTED, caps.synchronizedOutputSupport());
            assertEquals(ModeSupport.NOT_SUPPORTED, caps.graphemeClusterSupport());
            assertEquals(Boolean.TRUE, caps.nativeGraphemeClustering());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testAsyncProbesGraphemeClustering() throws Exception {
        // Mode 2027 unsupported but terminal clusters flag emoji: the
        // background async probe must measure TRUE, not leave null.
        Assume.assumeFalse("live query skipped in multiplexer",
                new TerminalDetector().isInMultiplexer());
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            // One session serves the color batch, then the CPR once the
            // grapheme probe is written (DA1 received + 2027 unsupported
            // triggers it).
            TerminalCapabilities.setProbeTransport(
                    new PhasedProbeTransport(colorResponse(), bytes("\033[1;3R")));
            TerminalCapabilities.invalidate();
            TerminalCapabilities async = TerminalCapabilities.detectAsync();
            assertTrue(async.awaitColors(2, TimeUnit.SECONDS));
            assertEquals(Boolean.TRUE, async.nativeGraphemeClustering());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testAsyncWithoutReplyLeavesGraphemeUnknown() throws Exception {
        // No reply means no probe ran: null, not a fabricated negative.
        Assume.assumeFalse("live query skipped in multiplexer",
                new TerminalDetector().isInMultiplexer());
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities.setProbeTransport(new FakeProbeTransport(true, new byte[0]));
            TerminalCapabilities.invalidate();
            TerminalCapabilities async = TerminalCapabilities.detectAsync();
            assertTrue(async.awaitColors(2, TimeUnit.SECONDS));
            assertNull(async.nativeGraphemeClustering());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testFullAndAsyncAgreeOnMeasuredTheme() throws Exception {
        // The same white-background reply through both entry points must
        // yield the same theme: measured RGB outranks earlier hints.
        Assume.assumeFalse("live query skipped in multiplexer",
                new TerminalDetector().isInMultiplexer());
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            // One session serves the color batch, then the CPR once the
            // grapheme probe is written (DA1 received + 2027 unsupported
            // triggers it in detectFull).
            TerminalCapabilities.setProbeTransport(
                    new PhasedProbeTransport(whiteBackgroundResponse(), bytes("\033[1;3R")));
            TerminalCapabilities.invalidate();
            TerminalCapabilities full = TerminalCapabilities.detectFull();
            assertArrayEquals(new int[] { 255, 255, 255 }, full.backgroundRGB());
            assertEquals(TerminalTheme.LIGHT, full.theme());

            // Fresh transport: the fake serves one response per open and
            // the full phase already consumed the color and grapheme
            // replies above.
            TerminalCapabilities.setProbeTransport(
                    new FakeProbeTransport(true, whiteBackgroundResponse(), bytes("\033[1;3R")));
            TerminalCapabilities.invalidate();
            TerminalCapabilities async = TerminalCapabilities.detectAsync();
            assertTrue(async.awaitColors(2, TimeUnit.SECONDS));
            assertArrayEquals(new int[] { 255, 255, 255 }, async.backgroundRGB());
            assertEquals(TerminalTheme.LIGHT, async.theme());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testFullAndAsyncAgreeWithoutReply() throws Exception {
        // No reply: both modes fall back to the same hints.
        Assume.assumeFalse("live query skipped in multiplexer",
                new TerminalDetector().isInMultiplexer());
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities.setProbeTransport(new FakeProbeTransport(true, new byte[0]));
            TerminalCapabilities.invalidate();
            TerminalCapabilities full = TerminalCapabilities.detectFull();

            TerminalCapabilities.invalidate();
            TerminalCapabilities async = TerminalCapabilities.detectAsync();
            assertTrue(async.awaitColors(2, TimeUnit.SECONDS));

            assertEquals(full.theme(), async.theme());
            assertNull(async.backgroundRGB());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testConcurrentColorQueriesDoNotOverlapRawSessions() throws Exception {
        assertSerializedSessions(false);
    }

    @Test
    public void testGraphemeProbeWaitsForColorSession() throws Exception {
        assertSerializedSessions(true);
    }

    private static void assertSerializedSessions(boolean graphemeSecond) throws Exception {
        ContendedProbeTransport transport = new ContendedProbeTransport();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = new Thread(new ProbeTask(transport, false, failure));
        Thread second = new Thread(new ProbeTask(transport, graphemeSecond, failure));
        first.setDaemon(true);
        second.setDaemon(true);
        try {
            first.start();
            assertTrue("First probe must enter its read", transport.firstRead.await(2, TimeUnit.SECONDS));
            second.start();
            assertTrue("Second probe must attempt to run", transport.secondReady.await(2, TimeUnit.SECONDS));
            assertFalse("A second session must wait until raw mode is restored",
                    transport.secondOpen.await(200, TimeUnit.MILLISECONDS));
        } finally {
            transport.releaseFirst.countDown();
            first.join(3000);
            second.join(3000);
        }
        assertFalse("Probe worker must finish", first.isAlive() || second.isAlive());
        assertNull("Probe worker failed", failure.get());
        assertFalse("Raw-mode sessions must never overlap", transport.overlapped.get());
        assertEquals(2, transport.opens.get());
        assertEquals(0, transport.active.get());
    }

    private static final class ProbeTask implements Runnable {
        private final TerminalProbeTransport transport;
        private final boolean grapheme;
        private final AtomicReference<Throwable> failure;

        ProbeTask(TerminalProbeTransport transport, boolean grapheme, AtomicReference<Throwable> failure) {
            this.transport = transport;
            this.grapheme = grapheme;
            this.failure = failure;
        }

        @Override
        public void run() {
            try {
                if (grapheme) {
                    TerminalColorQuery.probeGraphemeClustering(transport);
                } else {
                    TerminalColorQuery.query(transport);
                }
            } catch (Throwable t) {
                failure.set(t);
            }
        }
    }

    private static final class ContendedProbeTransport implements TerminalProbeTransport {
        private final AtomicInteger checks = new AtomicInteger();
        final AtomicInteger opens = new AtomicInteger();
        final AtomicInteger active = new AtomicInteger();
        final AtomicBoolean overlapped = new AtomicBoolean();
        final CountDownLatch firstRead = new CountDownLatch(1);
        final CountDownLatch secondReady = new CountDownLatch(1);
        final CountDownLatch secondOpen = new CountDownLatch(1);
        final CountDownLatch releaseFirst = new CountDownLatch(1);

        @Override
        public boolean isAvailable() {
            if (checks.incrementAndGet() == 2) {
                secondReady.countDown();
            }
            return true;
        }

        @Override
        public TerminalProbeSession open() {
            int opened = opens.incrementAndGet();
            if (active.incrementAndGet() > 1) {
                overlapped.set(true);
            }
            if (opened == 2) {
                secondOpen.countDown();
            }
            return new ContendedProbeSession(this, opened == 1);
        }
    }

    private static final class ContendedProbeSession implements TerminalProbeSession {
        private final ContendedProbeTransport transport;
        private final boolean first;

        ContendedProbeSession(ContendedProbeTransport transport, boolean first) {
            this.transport = transport;
            this.first = first;
        }

        @Override
        public void write(byte[] data) {
        }

        @Override
        public InputStream input() {
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    if (first) {
                        transport.firstRead.countDown();
                        try {
                            if (!transport.releaseFirst.await(3, TimeUnit.SECONDS)) {
                                throw new IOException("First probe was never released");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted while waiting for the first probe", e);
                        }
                    }
                    return -1;
                }
            };
        }

        @Override
        public void close() {
            transport.active.decrementAndGet();
        }
    }

    // ==================== Probe completion without redundant reads (#345) ====================

    /**
     * Single-shot stream: the whole reply arrives in one read, and any
     * second read fails the test — the probe must complete on matching
     * replies instead of reading into the transport timeout.
     */
    private static final class FailOnExtraReadStream extends InputStream {
        private final byte[] payload;
        private int pos;
        private int bulkReads;

        FailOnExtraReadStream(byte[] payload) {
            this.payload = payload.clone();
        }

        @Override
        public int read() {
            return pos < payload.length ? payload[pos++] & 0xff : -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            bulkReads++;
            if (bulkReads > 1) {
                fail("probe must complete on the matching reply without an extra read");
            }
            if (pos >= payload.length) {
                return -1;
            }
            int count = Math.min(len, payload.length - pos);
            System.arraycopy(payload, pos, b, off, count);
            pos += count;
            return count;
        }
    }

    /**
     * Batch without DECRPM: DA1 fence + the 19 OSC replies, as sent by
     * terminals without DECRQM support.
     */
    private static byte[] fenceOnlyBatch() {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[?63;1;2;4c");
        sb.append("\033]10;rgb:ffff/ffff/ffff\007");
        sb.append("\033]11;rgb:0000/0000/0000\007");
        for (int i = 0; i <= 15; i++) {
            sb.append("\033]4;").append(i).append(";rgb:1111/2222/3333\007");
        }
        sb.append("\033]4;255;rgb:eeee/eeee/eeee\007");
        return bytes(sb.toString());
    }

    @Test
    public void testGraphemeCompletesWithoutExtraRead() {
        StreamProbeTransport transport = new StreamProbeTransport(
                new FailOnExtraReadStream(bytes("\033[1;2R")));
        assertTrue("clustered CPR must parse true", TerminalColorQuery.probeGraphemeClustering(transport));
        assertTrue(transport.session.closed);
    }

    @Test
    public void testColorBatchCompletesAtFenceWithoutExtraRead() {
        StreamProbeTransport transport = new StreamProbeTransport(
                new FailOnExtraReadStream(fenceOnlyBatch()));
        TerminalColorQuery result = TerminalColorQuery.query(transport);
        assertNotNull("fence-only batch must parse", result);
        assertTrue(result.da1Received);
        assertArrayEquals(new int[] { 0, 0, 0 }, result.background);
        assertEquals(16, result.palette.size());
        assertTrue(result.supports256);
        assertTrue(transport.session.closed);
    }

    @Test
    public void testFragmentedCprAcrossReads() {
        // CPR split mid-frame: framing happens after reassembly, so the
        // partial chunk must not complete early or get stuck.
        StreamProbeTransport transport = new StreamProbeTransport(
                new ChunkedInputStream(bytes("\033[24;8" + "0R"), false));
        assertFalse("col 80 must parse false", TerminalColorQuery.probeGraphemeClustering(transport));
        assertTrue(transport.session.closed);
    }

    @Test
    public void testFragmentedFenceOnlyBatch() {
        // Fence plus fragmentation combined: DA1 lands early in the
        // stream, OSC replies trickle two bytes at a time.
        StreamProbeTransport transport = new StreamProbeTransport(
                new ChunkedInputStream(fenceOnlyBatch(), false));
        TerminalColorQuery result = TerminalColorQuery.query(transport);
        assertNotNull(result);
        assertTrue(result.da1Received);
        assertArrayEquals(new int[] { 0, 0, 0 }, result.background);
        assertTrue(transport.session.closed);
    }
}
