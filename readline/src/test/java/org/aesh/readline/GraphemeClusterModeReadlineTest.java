/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
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
package org.aesh.readline;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.function.Consumer;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.TerminalFeatures;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.ANSI;
import org.junit.Test;

/**
 * Tests that Mode 2027 (grapheme cluster segmentation) is automatically
 * enabled/disabled during readline start/finish based on terminal support
 * and the NO_GRAPHEME_CLUSTER_MODE flag.
 *
 * @author Ståle Pedersen
 */
public class GraphemeClusterModeReadlineTest {

    @Test
    public void testMode2027EnabledForSupportingTerminal() {
        List<String> output = new ArrayList<>();
        MockGraphemeConnection conn = new MockGraphemeConnection(output, true);
        Readline readline = new Readline();

        readline.readline(conn, new Prompt(": "), s -> {
        }, null, null, null, null,
                new EnumMap<>(ReadlineFlag.class));

        assertTrue("Mode 2027 enable sequence should be sent during start()",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2027_ENABLE)));
    }

    @Test
    public void testMode2027DisabledOnFinish() {
        List<String> output = new ArrayList<>();
        MockGraphemeConnection conn = new MockGraphemeConnection(output, true);
        Readline readline = new Readline();

        readline.readline(conn, new Prompt(": "), s -> {
        }, null, null, null, null,
                new EnumMap<>(ReadlineFlag.class));

        // Simulate pressing Enter to finish
        conn.simulateInput(new int[] { '\r' });

        assertTrue("Mode 2027 disable sequence should be sent during finish()",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2027_DISABLE)));
    }

    @Test
    public void testMode2027NotSentWithFlag() {
        List<String> output = new ArrayList<>();
        MockGraphemeConnection conn = new MockGraphemeConnection(output, true);
        Readline readline = new Readline();

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        flags.put(ReadlineFlag.NO_GRAPHEME_CLUSTER_MODE, 0);

        readline.readline(conn, new Prompt(": "), s -> {
        }, null, null, null, null, flags);

        // Simulate pressing Enter to complete the readline interaction
        conn.simulateInput(new int[] { '\r' });

        assertFalse("Mode 2027 enable sequence should NOT be sent when flag is set",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2027_ENABLE)));
        assertFalse("Mode 2027 disable sequence should NOT be sent when flag is set",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2027_DISABLE)));
    }

    @Test
    public void testMode2027NotSentForUnsupportingTerminal() {
        List<String> output = new ArrayList<>();
        MockGraphemeConnection conn = new MockGraphemeConnection(output, false);
        Readline readline = new Readline();

        readline.readline(conn, new Prompt(": "), s -> {
        }, null, null, null, null,
                new EnumMap<>(ReadlineFlag.class));

        // Simulate pressing Enter to finish the readline interaction
        conn.simulateInput(new int[] { '\r' });

        assertFalse("Mode 2027 enable sequence should NOT be sent for unsupporting terminal",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2027_ENABLE)));
        assertFalse("Mode 2027 disable sequence should NOT be sent for unsupporting terminal",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2027_DISABLE)));
    }

    /**
     * A mock Connection that captures all output and can be configured to
     * support or not support grapheme cluster mode.
     */
    private static class MockGraphemeConnection implements Connection {
        private final List<String> output;
        private final boolean graphemeSupport;
        private Consumer<int[]> stdinHandler;
        private Consumer<Size> sizeHandler;
        private Consumer<Signal> signalHandler;
        private Consumer<Void> closeHandler;
        private Attributes attributes = new Attributes();

        MockGraphemeConnection(List<String> output, boolean graphemeSupport) {
            this.output = output;
            this.graphemeSupport = graphemeSupport;
        }

        @Override
        public TerminalFeatures terminal() {
            return new TerminalFeatures(this) {
                @Override
                public boolean supportsGraphemeClusterMode() {
                    return graphemeSupport;
                }
            };
        }

        @Override
        public Device device() {
            return new BaseDevice("xterm-256color");
        }

        @Override
        public Size size() {
            return new Size(80, 24);
        }

        @Override
        public Consumer<Size> sizeHandler() {
            return sizeHandler;
        }

        @Override
        public void setSizeHandler(Consumer<Size> handler) {
            this.sizeHandler = handler;
        }

        @Override
        public Consumer<Signal> signalHandler() {
            return signalHandler;
        }

        @Override
        public void setSignalHandler(Consumer<Signal> handler) {
            this.signalHandler = handler;
        }

        @Override
        public Consumer<int[]> stdinHandler() {
            return stdinHandler;
        }

        @Override
        public void setStdinHandler(Consumer<int[]> handler) {
            this.stdinHandler = handler;
        }

        @Override
        public Consumer<int[]> stdoutHandler() {
            return codePoints -> {
                StringBuilder sb = new StringBuilder();
                for (int cp : codePoints) {
                    sb.appendCodePoint(cp);
                }
                output.add(sb.toString());
            };
        }

        @Override
        public void setCloseHandler(Consumer<Void> handler) {
            this.closeHandler = handler;
        }

        @Override
        public Consumer<Void> closeHandler() {
            return closeHandler;
        }

        @Override
        public void close() {
            if (closeHandler != null) {
                closeHandler.accept(null);
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
        public Attributes attributes() {
            return attributes;
        }

        @Override
        public void setAttributes(Attributes attr) {
            this.attributes = attr;
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

        public void simulateInput(int[] data) {
            if (stdinHandler != null) {
                stdinHandler.accept(data);
            }
        }
    }
}
