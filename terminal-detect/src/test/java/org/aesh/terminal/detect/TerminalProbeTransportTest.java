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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Assume;
import org.junit.Test;

/**
 * Tests for injectable probe transports ({@link TerminalProbeTransport}).
 * All I/O goes through fakes — no /dev/tty or stty is touched.
 */
public class TerminalProbeTransportTest {

    /** Session serving one canned response, capturing writes, tracking close. */
    private static final class FakeProbeSession implements TerminalProbeSession {
        private final byte[] response;
        private final ByteArrayOutputStream written = new ByteArrayOutputStream();
        private boolean closed;

        FakeProbeSession(byte[] response) {
            this.response = response;
        }

        @Override
        public void write(byte[] data) {
            written.write(data, 0, data.length);
        }

        @Override
        public InputStream input() {
            return new ByteArrayInputStream(response);
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
            // First open serves the color query, second the grapheme probe
            // (DA1 received + 2027 unsupported triggers it in detectFull).
            TerminalCapabilities.setProbeTransport(
                    new FakeProbeTransport(true, colorResponse(), bytes("\033[1;3R")));
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
}
