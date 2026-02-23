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
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.ANSI;
import org.junit.Test;

/**
 * Tests that Mode 2026 (synchronized output) BSU/ESU sequences are
 * automatically sent around prompt draw and buffer redraws based on
 * terminal support and the NO_SYNCHRONIZED_OUTPUT flag.
 *
 * @author Stale Pedersen
 */
public class SynchronizedOutputReadlineTest {

    @Test
    public void testBsuEsuSentAroundPromptForSupportingTerminal() {
        List<String> output = new ArrayList<>();
        MockSyncConnection conn = new MockSyncConnection(output, true);
        Readline readline = new Readline();

        readline.readline(conn, new Prompt(": "), s -> {
        }, null, null, null, null,
                new EnumMap<>(ReadlineFlag.class));

        assertTrue("BSU (Mode 2026 enable) should be sent during start()",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2026_ENABLE)));
        assertTrue("ESU (Mode 2026 disable) should be sent during start()",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2026_DISABLE)));
    }

    @Test
    public void testBsuEsuNotSentWithFlag() {
        List<String> output = new ArrayList<>();
        MockSyncConnection conn = new MockSyncConnection(output, true);
        Readline readline = new Readline();

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        flags.put(ReadlineFlag.NO_SYNCHRONIZED_OUTPUT, 0);

        readline.readline(conn, new Prompt(": "), s -> {
        }, null, null, null, null, flags);

        // Simulate pressing Enter to complete the readline interaction
        conn.simulateInput(new int[] { '\r' });

        assertFalse("BSU should NOT be sent when NO_SYNCHRONIZED_OUTPUT flag is set",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2026_ENABLE)));
        assertFalse("ESU should NOT be sent when NO_SYNCHRONIZED_OUTPUT flag is set",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2026_DISABLE)));
    }

    @Test
    public void testBsuEsuNotSentForUnsupportingTerminal() {
        List<String> output = new ArrayList<>();
        MockSyncConnection conn = new MockSyncConnection(output, false);
        Readline readline = new Readline();

        readline.readline(conn, new Prompt(": "), s -> {
        }, null, null, null, null,
                new EnumMap<>(ReadlineFlag.class));

        // Simulate pressing Enter to finish the readline interaction
        conn.simulateInput(new int[] { '\r' });

        assertFalse("BSU should NOT be sent for unsupporting terminal",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2026_ENABLE)));
        assertFalse("ESU should NOT be sent for unsupporting terminal",
                output.stream().anyMatch(s -> s.contains(ANSI.MODE_2026_DISABLE)));
    }

    /**
     * A mock Connection that captures all output and can be configured to
     * support or not support synchronized output.
     */
    private static class MockSyncConnection implements Connection {
        private final List<String> output;
        private final boolean syncSupport;
        private Consumer<int[]> stdinHandler;
        private Consumer<Size> sizeHandler;
        private Consumer<Signal> signalHandler;
        private Consumer<Void> closeHandler;
        private Attributes attributes = new Attributes();

        MockSyncConnection(List<String> output, boolean syncSupport) {
            this.output = output;
            this.syncSupport = syncSupport;
        }

        @Override
        public boolean supportsSynchronizedOutput() {
            return syncSupport;
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
        public Consumer<Size> getSizeHandler() {
            return sizeHandler;
        }

        @Override
        public void setSizeHandler(Consumer<Size> handler) {
            this.sizeHandler = handler;
        }

        @Override
        public Consumer<Signal> getSignalHandler() {
            return signalHandler;
        }

        @Override
        public void setSignalHandler(Consumer<Signal> handler) {
            this.signalHandler = handler;
        }

        @Override
        public Consumer<int[]> getStdinHandler() {
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
        public Consumer<Void> getCloseHandler() {
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
        public Attributes getAttributes() {
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
