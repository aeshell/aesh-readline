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
 * Tests that OSC 133 shell integration sequences (A, B, C) are
 * automatically emitted around prompt and on Enter, and that the
 * NO_SHELL_INTEGRATION flag suppresses them.
 */
public class ShellIntegrationTest {

    @Test
    public void testOsc133SequencesSentAroundPrompt() {
        List<String> output = new ArrayList<>();
        MockShellIntegrationConnection conn = new MockShellIntegrationConnection(output);
        Readline readline = new Readline();

        readline.readline(conn, new Prompt(": "), s -> {
        }, null, null, null, null,
                new EnumMap<>(ReadlineFlag.class));

        assertTrue("OSC 133;A (Prompt Start) should be sent before prompt",
                output.stream().anyMatch(s -> s.contains(ANSI.OSC_133_PROMPT_START)));
        assertTrue("OSC 133;B (Prompt End) should be sent after prompt",
                output.stream().anyMatch(s -> s.contains(ANSI.OSC_133_PROMPT_END)));

        // Verify ordering: A before B
        int indexA = -1;
        int indexB = -1;
        for (int i = 0; i < output.size(); i++) {
            if (output.get(i).contains(ANSI.OSC_133_PROMPT_START) && indexA == -1)
                indexA = i;
            if (output.get(i).contains(ANSI.OSC_133_PROMPT_END) && indexB == -1)
                indexB = i;
        }
        assertTrue("Prompt Start (A) should appear before Prompt End (B)",
                indexA >= 0 && indexB >= 0 && indexA < indexB);
    }

    @Test
    public void testOsc133CommandStartSentOnEnter() {
        List<String> output = new ArrayList<>();
        MockShellIntegrationConnection conn = new MockShellIntegrationConnection(output);
        Readline readline = new Readline();

        readline.readline(conn, new Prompt(": "), s -> {
        }, null, null, null, null,
                new EnumMap<>(ReadlineFlag.class));

        // Simulate pressing Enter to complete readline
        conn.simulateInput(new int[] { '\r' });

        assertTrue("OSC 133;C (Command Start) should be sent on Enter",
                output.stream().anyMatch(s -> s.contains(ANSI.OSC_133_COMMAND_START)));
    }

    @Test
    public void testOsc133NotSentWithFlag() {
        List<String> output = new ArrayList<>();
        MockShellIntegrationConnection conn = new MockShellIntegrationConnection(output);
        Readline readline = new Readline();

        EnumMap<ReadlineFlag, Integer> flags = new EnumMap<>(ReadlineFlag.class);
        flags.put(ReadlineFlag.NO_SHELL_INTEGRATION, 0);

        readline.readline(conn, new Prompt(": "), s -> {
        }, null, null, null, null, flags);

        // Simulate pressing Enter
        conn.simulateInput(new int[] { '\r' });

        assertFalse("OSC 133;A should NOT be sent when NO_SHELL_INTEGRATION flag is set",
                output.stream().anyMatch(s -> s.contains(ANSI.OSC_133_PROMPT_START)));
        assertFalse("OSC 133;B should NOT be sent when NO_SHELL_INTEGRATION flag is set",
                output.stream().anyMatch(s -> s.contains(ANSI.OSC_133_PROMPT_END)));
        assertFalse("OSC 133;C should NOT be sent when NO_SHELL_INTEGRATION flag is set",
                output.stream().anyMatch(s -> s.contains(ANSI.OSC_133_COMMAND_START)));
    }

    @Test
    public void testConnectionWriteCommandFinished() {
        List<String> output = new ArrayList<>();
        MockShellIntegrationConnection conn = new MockShellIntegrationConnection(output);

        conn.terminal().writeCommandFinished();
        assertTrue("writeCommandFinished() should emit OSC 133;D",
                output.stream().anyMatch(s -> s.contains(ANSI.OSC_133_COMMAND_FINISHED)));

        output.clear();
        conn.terminal().writeCommandFinished(0);
        assertTrue("writeCommandFinished(0) should emit OSC 133;D;0",
                output.stream().anyMatch(s -> s.contains(ANSI.osc133CommandFinished(0))));

        output.clear();
        conn.terminal().writeCommandFinished(1);
        assertTrue("writeCommandFinished(1) should emit OSC 133;D;1",
                output.stream().anyMatch(s -> s.contains(ANSI.osc133CommandFinished(1))));
    }

    /**
     * A mock Connection that captures all output for verification.
     */
    private static class MockShellIntegrationConnection implements Connection {
        private final List<String> output;
        private Consumer<int[]> stdinHandler;
        private Consumer<Size> sizeHandler;
        private Consumer<Signal> signalHandler;
        private Consumer<Void> closeHandler;
        private Attributes attributes = new Attributes();

        MockShellIntegrationConnection(List<String> output) {
            this.output = output;
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

        @Override
        public TerminalFeatures terminal() {
            return new TerminalFeatures(this) {
                @Override
                public boolean supportsShellIntegration() {
                    return true;
                }
            };
        }

        public void simulateInput(int[] data) {
            if (stdinHandler != null) {
                stdinHandler.accept(data);
            }
        }
    }
}
