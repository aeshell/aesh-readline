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
package org.aesh.terminal.tty;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.aesh.terminal.TestBase;
import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.detect.TerminalDetection;
import org.aesh.terminal.detect.TerminalProbeSession;
import org.aesh.terminal.detect.TerminalProbeTransport;
import org.aesh.terminal.detect.TerminalTheme;
import org.aesh.terminal.tty.TerminalConnectionLegacyLoopTest.StagedStream;
import org.aesh.terminal.tty.TerminalConnectionLegacyLoopTest.StubTerminal;
import org.aesh.terminal.utils.TerminalColorCapability;
import org.junit.Assume;
import org.junit.Test;

/**
 * Setup validation for explicit-stream local connections (#381).
 * <p>
 * Explicit pipes, files, and EOF-stdin layouts must construct without
 * consulting fd state for capability answers: attached facts flow
 * regardless of layout, and unattached answers stay self-consistent.
 * Repeated open/close cycles must not accumulate reader workers.
 */
public class TerminalConnectionSetupTest {

    private static final String READER_NAME = "Aesh InputStream Reader";

    private static byte[] measuredBatch() {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[?2026;1$y");
        sb.append("\033[?2027;1$y");
        sb.append("\033[?63;1;2;4c");
        sb.append("\033]10;rgb:ffff/ffff/ffff\007");
        sb.append("\033]11;rgb:0000/0000/0000\007");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
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
    public void testExplicitStreamsUseAttachedFacts() throws Exception {
        assumeNoMultiplexer();
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        final byte[] reply = measuredBatch();
        TerminalCapabilities.setProbeTransport(new TerminalProbeTransport() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public TerminalProbeSession open() {
                return new TerminalProbeSession() {
                    @Override
                    public void write(byte[] data) {
                    }

                    @Override
                    public java.io.InputStream input() {
                        return new ByteArrayInputStream(reply);
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        });
        TerminalCapabilities.invalidate();
        PipedOutputStream stdinWriter = new PipedOutputStream();
        PipedInputStream stdinReader = new PipedInputStream(stdinWriter, 4096);
        ByteArrayOutputStream stdoutCapture = new ByteArrayOutputStream();
        TerminalConnection connection = new TerminalConnection(StandardCharsets.UTF_8,
                stdinReader, stdoutCapture);
        try {
            TerminalCapabilities measured = TerminalCapabilities.detectFull();
            assertArrayEquals(new int[] { 0, 0, 0 }, measured.backgroundRGB());
            connection.attachDetection(TerminalDetection.managed(measured));
            assertEquals(TerminalTheme.DARK,
                    connection.terminal().colorCapability().getTheme());
            assertArrayEquals(new int[] { 0, 0, 0 },
                    connection.terminal().colorCapability().getBackgroundRGB());
        } finally {
            connection.close();
            stdinWriter.close();
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testEofStdinLayoutDoesNotBlockSetup() throws Exception {
        ByteArrayOutputStream stdoutCapture = new ByteArrayOutputStream();
        TerminalConnection connection = new TerminalConnection(StandardCharsets.UTF_8,
                new ByteArrayInputStream(new byte[0]), stdoutCapture);
        try {
            assertEquals(TerminalColorCapability.detectThemeFromEnvironment(),
                    connection.terminal().colorCapability().getTheme());
            connection.openNonBlocking();
            connection.close();
        } finally {
            connection.close();
        }
    }

    @Test(timeout = 120000)
    public void testOpenCloseCyclesAccumulateNoReaders() throws Exception {
        int baseline = TestBase.countThreadsByPrefix(READER_NAME);
        for (int i = 0; i < 10; i++) {
            StagedStream staged = new StagedStream();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            final ByteArrayOutputStream shared = captured;
            StubTerminal stub = new StubTerminal(staged) {
                @Override
                public OutputStream output() {
                    return shared;
                }
            };
            TerminalConnection connection = new TerminalConnection(stub);
            connection.openNonBlocking();
            connection.close();
        }
        TestBase.awaitThreadsByPrefix(READER_NAME, baseline, 15000);
    }
}
