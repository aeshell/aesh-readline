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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.aesh.terminal.detect.ImageProtocol;
import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.detect.TerminalDetection;
import org.aesh.terminal.detect.TerminalProbeSession;
import org.aesh.terminal.detect.TerminalProbeTransport;
import org.aesh.terminal.detect.TerminalTheme;
import org.aesh.terminal.utils.ColorDepth;
import org.aesh.terminal.utils.TerminalColorCapability;
import org.junit.Assume;
import org.junit.Test;

/**
 * Endpoint-scoped detection attachment on connections (#381).
 * <p>
 * Attached facts supply the theme and measured colors of
 * {@code colorCapability}, fill an unknown-device image-protocol gap,
 * and decline the live DA1 fallback when the attached source is
 * unknown. Unattached connections keep the legacy answers bit for bit.
 * The live-query test uses an injected fake transport; no test touches
 * a real terminal.
 */
public class AttachedDetectionTest {

    private static StreamConnection remote(final Device device) {
        return new StreamConnection(StandardCharsets.UTF_8,
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream()) {
            @Override
            public Device device() {
                return device;
            }

            @Override
            public boolean supportsAnsi() {
                return true;
            }
        };
    }

    private static byte[] measuredBatch() {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[?2026;1$y");
        // Mode 2027 reported supported: the grapheme phase is skipped,
        // so one session serves exactly one open, write, and close.
        sb.append("\033[?2027;1$y");
        sb.append("\033[?63;1;2;4c");
        sb.append("\033]10;rgb:ffff/ffff/ffff\007");
        sb.append("\033]11;rgb:0000/0000/0000\007");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static final class MeasuredSession implements TerminalProbeSession {
        private final ByteArrayInputStream input;
        int closes;

        MeasuredSession(byte[] reply) {
            this.input = new ByteArrayInputStream(reply);
        }

        @Override
        public void write(byte[] data) {
        }

        @Override
        public ByteArrayInputStream input() {
            return input;
        }

        @Override
        public void close() {
            closes++;
        }
    }

    /** Counts automatic image-protocol queries without answering them. */
    private static final class CountingFeatures extends TerminalFeatures {
        int imageQueries;

        CountingFeatures(Connection connection) {
            super(connection);
        }

        @Override
        public ImageProtocol queryImageProtocol(long timeoutMs) {
            imageQueries++;
            return super.queryImageProtocol(timeoutMs);
        }
    }

    /**
     * Live standalone queries are skipped in multiplexers, which would
     * leave the fixture result empty. TerminalDetector is package-private
     * to terminal-detect, so this pins the same two ambient markers here
     * instead of reaching across the module boundary.
     */
    private static void assumeNoMultiplexer() {
        Map<String, String> ambient = System.getenv();
        String tmux = ambient.get("TMUX");
        String term = ambient.get("TERM");
        boolean multiplexed = (tmux != null && !tmux.isEmpty())
                || (term != null && (term.toLowerCase().startsWith("tmux")
                        || term.toLowerCase().startsWith("screen")));
        Assume.assumeFalse("live query skipped in multiplexer", multiplexed);
    }

    @Test
    public void testAttachedMeasuredRgbFlowsIntoColorCapability() {
        assumeNoMultiplexer();
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        final byte[] reply = measuredBatch();
        final MeasuredSession[] session = new MeasuredSession[1];
        TerminalCapabilities.setProbeTransport(new TerminalProbeTransport() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public TerminalProbeSession open() {
                session[0] = new MeasuredSession(reply);
                return session[0];
            }
        });
        TerminalCapabilities.invalidate();
        StreamConnection connection = remote(new BaseDevice("linux"));
        try {
            TerminalCapabilities measured = TerminalCapabilities.detectFull();
            assertArrayEquals(new int[] { 0, 0, 0 }, measured.backgroundRGB());
            assertEquals(1, session[0].closes);
            connection.attachDetection(TerminalDetection.managed(measured));
            TerminalColorCapability capability = connection.terminal().colorCapability();
            assertEquals(TerminalTheme.DARK, capability.getTheme());
            assertArrayEquals(new int[] { 0, 0, 0 }, capability.getBackgroundRGB());
            assertArrayEquals(new int[] { 255, 255, 255 }, capability.getForegroundRGB());
            assertEquals("measured true color upgrades the 8-color baseline",
                    ColorDepth.TRUE_COLOR, capability.getColorDepth());
        } finally {
            connection.close();
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testAttachedHintsOverrideAmbientTheme() {
        Map<String, String> env = new HashMap<>();
        env.put("TERM", "xterm-256color");
        env.put("COLORFGBG", "0;15");
        StreamConnection connection = remote(new BaseDevice("linux"));
        try {
            connection.attachDetection(TerminalDetection.environmentOnly(env));
            TerminalColorCapability capability = connection.terminal().colorCapability();
            assertEquals(TerminalTheme.LIGHT, capability.getTheme());
            assertNull(capability.getBackgroundRGB());
            assertEquals("the endpoint 256-color hint upgrades the baseline",
                    ColorDepth.COLORS_256, capability.getColorDepth());
        } finally {
            connection.close();
        }
    }

    @Test
    public void testAttachedUnknownSuppressesImageQuery() {
        StreamConnection connection = remote(new BaseDevice("linux"));
        CountingFeatures features = new CountingFeatures(connection);
        try {
            connection.attachDetection(TerminalDetection.managed(null));
            assertEquals(ImageProtocol.NONE, features.getImageProtocol());
            assertEquals("attached-but-unknown must decline the live DA1 fallback",
                    0, features.imageQueries);
        } finally {
            connection.close();
        }
    }

    @Test
    public void testAttachedHintFillsUnknownDeviceGap() {
        StreamConnection connection = remote(new BaseDevice("linux"));
        CountingFeatures features = new CountingFeatures(connection);
        try {
            Map<String, String> env = new HashMap<>();
            env.put("TERM", "kitty");
            connection.attachDetection(TerminalDetection.environmentOnly(env));
            assertEquals(ImageProtocol.KITTY, features.getImageProtocol());
            assertEquals("an endpoint hint answers without a live query",
                    0, features.imageQueries);
        } finally {
            connection.close();
        }
    }

    @Test
    public void testUnattachedKeepsLegacyImageFallback() {
        StreamConnection connection = remote(new BaseDevice("linux"));
        CountingFeatures features = new CountingFeatures(connection);
        try {
            assertEquals(ImageProtocol.NONE, features.getImageProtocol());
            assertEquals("the DA1 fallback still runs when unattached",
                    1, features.imageQueries);
        } finally {
            connection.close();
        }
    }

    @Test
    public void testUnattachedKeepsLegacyTheme() {
        StreamConnection connection = remote(new BaseDevice("linux"));
        try {
            assertEquals(TerminalColorCapability.detectThemeFromEnvironment(),
                    connection.terminal().colorCapability().getTheme());
        } finally {
            connection.close();
        }
    }
}
