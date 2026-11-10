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
package org.aesh.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.detect.TerminalProbeSession;
import org.aesh.terminal.detect.TerminalProbeTransport;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Size;
import org.junit.Assume;
import org.junit.Test;

/**
 * Tests for progressive keyboard enablement on TerminalFeatures.
 * Seeded through a canned probe transport plus {@code detectFull},
 * mirroring the production path: detection learns the bitmask,
 * enablement reads it back. Multiplexer-gated legs assume a
 * non-multiplexer host like the other live-query tests.
 */
public class TerminalFeaturesKeyboardTest {

    @Test
    public void testIsInMultiplexerMarkers() {
        assertTrue(TerminalFeatures.isInMultiplexer("123", "xterm-256color"));
        assertTrue(TerminalFeatures.isInMultiplexer(null, "screen-256color"));
        assertTrue(TerminalFeatures.isInMultiplexer(null, "tmux-256color"));
        assertTrue(TerminalFeatures.isInMultiplexer("", "screen"));
        assertFalse(TerminalFeatures.isInMultiplexer(null, "xterm-256color"));
        assertFalse(TerminalFeatures.isInMultiplexer("", "xterm-256color"));
        assertFalse(TerminalFeatures.isInMultiplexer(null, null));
        assertFalse(TerminalFeatures.isInMultiplexer(null, ""));
    }

    @Test
    public void testSupportsPredicateMirrorsFlags() {
        assumeNoMultiplexer();
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            seedShared(5, true);
            assertTrue(new RecordingConnection().terminal().supportsProgressiveKeyboard());
            seedShared(0, true);
            assertFalse(new RecordingConnection().terminal().supportsProgressiveKeyboard());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
        TerminalCapabilities.setInstance(TerminalCapabilities.detect());
        try {
            assertFalse("unprobed bitmask must not count as support",
                    new RecordingConnection().terminal().supportsProgressiveKeyboard());
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testEnablePushesWhenReported() {
        assumeNoMultiplexer();
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities caps = seedShared(5, true);
            assertEquals(5, caps.kittyKeyboardFlags());

            RecordingConnection connection = new RecordingConnection();
            assertTrue(connection.terminal().enableProgressiveKeyboard());
            assertEquals(1, connection.written().size());
            assertEquals("push must be mode-2 OR-in, not a bare set",
                    "\033[=1;2u", connection.written().get(0));
            assertTrue(connection.keyboardEnhancements());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testEnableDeclinesWhenUnprobed() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            TerminalCapabilities.setInstance(TerminalCapabilities.detect());
            RecordingConnection connection = new RecordingConnection();
            assertFalse(connection.terminal().enableProgressiveKeyboard());
            assertTrue("unprobed terminal must receive no bytes",
                    connection.written().isEmpty());
            assertFalse(connection.keyboardEnhancements());
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testEnableDeclinesWhenAbsent() {
        assumeNoMultiplexer();
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            seedShared(-1, true);
            RecordingConnection connection = new RecordingConnection();
            assertFalse(connection.terminal().enableProgressiveKeyboard());
            assertTrue(connection.written().isEmpty());
            assertFalse(connection.keyboardEnhancements());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testEnableDeclinesUnsupportedFlags() {
        assumeNoMultiplexer();
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            seedShared(16, true);
            RecordingConnection connection = new RecordingConnection();
            assertFalse("flag 16 alone must not push disambiguate-only mode",
                    connection.terminal().enableProgressiveKeyboard());
            assertTrue(connection.written().isEmpty());
            assertFalse(connection.keyboardEnhancements());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testEnableTwicePushesOnce() {
        assumeNoMultiplexer();
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            seedShared(7, true);
            RecordingConnection connection = new RecordingConnection();
            assertTrue(connection.terminal().enableProgressiveKeyboard());
            assertTrue(connection.terminal().enableProgressiveKeyboard());
            assertEquals("repeat enable must not grow the terminal stack",
                    1, connection.written().size());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testDisableWithoutEnableWritesNothing() {
        RecordingConnection connection = new RecordingConnection();
        connection.terminal().disableProgressiveKeyboard();
        assertTrue(connection.written().isEmpty());
    }

    @Test
    public void testDisableAfterEnablePopsOnce() {
        assumeNoMultiplexer();
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        try {
            seedShared(5, true);
            RecordingConnection connection = new RecordingConnection();
            assertTrue(connection.terminal().enableProgressiveKeyboard());
            connection.terminal().disableProgressiveKeyboard();
            assertEquals(2, connection.written().size());
            assertEquals("\033[=1;2u", connection.written().get(0));
            assertEquals("pop must unwind exactly one stack entry",
                    "\033[<u", connection.written().get(1));
            assertFalse(connection.keyboardEnhancements());
            connection.terminal().disableProgressiveKeyboard();
            assertEquals("repeat disable must not pop again",
                    2, connection.written().size());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    /**
     * Seed the shared instance through a canned probe batch plus
     * {@code detectFull}, the same path production uses. A measured
     * background resolves the theme, so no platform subprocess runs
     * regardless of the host environment.
     *
     * @param kitty the keyboard flags to report, or -1 for no reply
     * @param background whether to include a background OSC reply
     * @return the detected capabilities
     */
    private static TerminalCapabilities seedShared(int kitty, boolean background) {
        StringBuilder batch = new StringBuilder("\033[?64;1c");
        if (kitty >= 0) {
            batch.append("\033[?").append(kitty).append("u");
        }
        if (background) {
            batch.append("\033]11;rgb:0000/0000/0000\007");
        }
        TerminalCapabilities.setProbeTransport(new CannedTransport(batch.toString()));
        TerminalCapabilities.invalidate();
        return TerminalCapabilities.detectFull();
    }

    private static void assumeNoMultiplexer() {
        String tmux = System.getenv("TMUX");
        Assume.assumeTrue("live query skipped in multiplexer",
                tmux == null || tmux.isEmpty());
        String term = System.getenv("TERM");
        Assume.assumeTrue("live query skipped in multiplexer", term == null
                || (!term.toLowerCase().startsWith("tmux")
                        && !term.toLowerCase().startsWith("screen")));
    }

    private static final class CannedTransport implements TerminalProbeTransport {
        private final byte[] response;

        CannedTransport(String response) {
            this.response = response.getBytes(StandardCharsets.US_ASCII);
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TerminalProbeSession open() {
            final InputStream in = new ByteArrayInputStream(response);
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
    }

    private static class RecordingConnection extends AbstractConnection {
        private final List<String> written = new ArrayList<>();
        private final Device device;

        RecordingConnection() {
            this(new BaseDevice("xterm-256color"));
        }

        RecordingConnection(Device device) {
            this.device = device;
            attributes = new Attributes();
            eventDecoder = new EventDecoder(attributes);
            reading = true;
        }

        List<String> written() {
            return written;
        }

        @Override
        public Device device() {
            return device;
        }

        @Override
        public Size size() {
            return new Size(80, 24);
        }

        @Override
        public Consumer<int[]> stdoutHandler() {
            return codePoints -> {
                StringBuilder sb = new StringBuilder();
                for (int cp : codePoints) {
                    sb.appendCodePoint(cp);
                }
                written.add(sb.toString());
            };
        }

        @Override
        public void close() {
            if (closeHandler() != null) {
                closeHandler().accept(null);
            }
        }

        @Override
        public void openBlocking() {
        }

        @Override
        public void openNonBlocking() {
        }

        @Override
        public boolean put(Capability capability, Object... params) {
            return false;
        }

        @Override
        public Charset inputEncoding() {
            return Charset.defaultCharset();
        }

        @Override
        public Charset outputEncoding() {
            return Charset.defaultCharset();
        }

        @Override
        public boolean supportsAnsi() {
            return true;
        }
    }
}
