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
package org.aesh.terminal;

import static org.junit.Assert.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.junit.Test;

/**
 * Tests for Mode 2026 (synchronized output) support across
 * TerminalType, Device, and Connection.
 *
 * @author Stale Pedersen
 */
public class SynchronizedOutputTest {

    // ==================== TerminalType Tests ====================

    @Test
    public void testKittySupportsSynchronizedOutput() {
        assertTrue("Kitty should support Mode 2026",
                Device.TerminalType.KITTY.supportsSynchronizedOutput());
    }

    @Test
    public void testGhosttySupportsSynchronizedOutput() {
        assertTrue("Ghostty should support Mode 2026",
                Device.TerminalType.GHOSTTY.supportsSynchronizedOutput());
    }

    @Test
    public void testWeztermSupportsSynchronizedOutput() {
        assertTrue("WezTerm should support Mode 2026",
                Device.TerminalType.WEZTERM.supportsSynchronizedOutput());
    }

    @Test
    public void testFootSupportsSynchronizedOutput() {
        assertTrue("Foot should support Mode 2026",
                Device.TerminalType.FOOT.supportsSynchronizedOutput());
    }

    @Test
    public void testContourSupportsSynchronizedOutput() {
        assertTrue("Contour should support Mode 2026",
                Device.TerminalType.CONTOUR.supportsSynchronizedOutput());
    }

    @Test
    public void testIterm2SupportsSynchronizedOutput() {
        assertTrue("iTerm2 should support Mode 2026",
                Device.TerminalType.ITERM2.supportsSynchronizedOutput());
    }

    @Test
    public void testMinttySupportsSynchronizedOutput() {
        assertTrue("Mintty should support Mode 2026",
                Device.TerminalType.MINTTY.supportsSynchronizedOutput());
    }

    @Test
    public void testXtermDoesNotSupportSynchronizedOutput() {
        assertFalse("xterm should not support Mode 2026",
                Device.TerminalType.XTERM.supportsSynchronizedOutput());
    }

    @Test
    public void testGnomeTerminalDoesNotSupportSynchronizedOutput() {
        assertFalse("GNOME Terminal should not support Mode 2026",
                Device.TerminalType.GNOME_TERMINAL.supportsSynchronizedOutput());
    }

    @Test
    public void testKonsoleDoesNotSupportSynchronizedOutput() {
        assertFalse("Konsole should not support Mode 2026",
                Device.TerminalType.KONSOLE.supportsSynchronizedOutput());
    }

    @Test
    public void testAlacrittyDoesNotSupportSynchronizedOutput() {
        assertFalse("Alacritty should not support Mode 2026",
                Device.TerminalType.ALACRITTY.supportsSynchronizedOutput());
    }

    @Test
    public void testLinuxConsoleDoesNotSupportSynchronizedOutput() {
        assertFalse("Linux console should not support Mode 2026",
                Device.TerminalType.LINUX_CONSOLE.supportsSynchronizedOutput());
    }

    @Test
    public void testTmuxDoesNotSupportSynchronizedOutput() {
        assertFalse("tmux should not support Mode 2026",
                Device.TerminalType.TMUX.supportsSynchronizedOutput());
    }

    @Test
    public void testScreenDoesNotSupportSynchronizedOutput() {
        assertFalse("GNU Screen should not support Mode 2026",
                Device.TerminalType.SCREEN.supportsSynchronizedOutput());
    }

    @Test
    public void testUnknownDoesNotSupportSynchronizedOutput() {
        assertFalse("Unknown terminal should not support Mode 2026",
                Device.TerminalType.UNKNOWN.supportsSynchronizedOutput());
    }

    @Test
    public void testVscodeDoesNotSupportSynchronizedOutput() {
        assertFalse("VSCode should not support Mode 2026",
                Device.TerminalType.VSCODE.supportsSynchronizedOutput());
    }

    @Test
    public void testJetbrainsDoesNotSupportSynchronizedOutput() {
        assertFalse("JetBrains should not support Mode 2026",
                Device.TerminalType.JETBRAINS.supportsSynchronizedOutput());
    }

    // ==================== Connection Query Tests ====================

    @Test
    public void testQuerySynchronizedOutput_Supported() throws Exception {
        MockConnection connection = new MockConnection();

        Thread responseThread = new Thread(() -> {
            try {
                // Wait until querySynchronizedOutput() installs its stdin handler
                long deadline = System.currentTimeMillis() + 1000;
                while (connection.stdinHandler() == null && System.currentTimeMillis() < deadline) {
                    Thread.sleep(5);
                }
                // Terminal responds: Mode 2026 is reset (recognized but disabled)
                String response = "\u001B[?2026;2$y";
                connection.simulateInput(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        responseThread.start();

        Boolean result = connection.terminal().querySynchronizedOutput(500);
        responseThread.join(1000);

        assertNotNull("Should get a response from terminal", result);
        assertTrue("Ps=2 means terminal recognizes Mode 2026", result);
    }

    @Test
    public void testQuerySynchronizedOutput_NotSupported() throws Exception {
        MockConnection connection = new MockConnection();

        Thread responseThread = new Thread(() -> {
            try {
                // Wait until querySynchronizedOutput() installs its stdin handler
                long deadline = System.currentTimeMillis() + 1000;
                while (connection.stdinHandler() == null && System.currentTimeMillis() < deadline) {
                    Thread.sleep(5);
                }
                // Terminal responds: Mode 2026 is not recognized
                String response = "\u001B[?2026;0$y";
                connection.simulateInput(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        responseThread.start();

        Boolean result = connection.terminal().querySynchronizedOutput(500);
        responseThread.join(1000);

        assertNotNull("Should get a response from terminal", result);
        assertFalse("Ps=0 means terminal does not recognize Mode 2026", result);
    }

    @Test
    public void testQuerySynchronizedOutput_Timeout() {
        MockConnection connection = new MockConnection();

        // No response simulated - should timeout
        Boolean result = connection.terminal().querySynchronizedOutput(100);

        assertNull("Should return null on timeout", result);
    }

    @Test
    public void testEnableSynchronizedOutput_SendsCorrectSequence() {
        List<String> sentOutput = new ArrayList<>();
        MockConnection connection = new MockConnection() {
            @Override
            public Consumer<int[]> stdoutHandler() {
                return codePoints -> {
                    StringBuilder sb = new StringBuilder();
                    for (int cp : codePoints) {
                        sb.appendCodePoint(cp);
                    }
                    sentOutput.add(sb.toString());
                };
            }
        };

        connection.terminal().enableSynchronizedOutput();

        assertFalse("Should have sent output", sentOutput.isEmpty());
        assertTrue("Should have sent Mode 2026 enable sequence",
                sentOutput.stream().anyMatch(s -> s.contains("\u001B[?2026h")));
    }

    @Test
    public void testDisableSynchronizedOutput_SendsCorrectSequence() {
        List<String> sentOutput = new ArrayList<>();
        MockConnection connection = new MockConnection() {
            @Override
            public Consumer<int[]> stdoutHandler() {
                return codePoints -> {
                    StringBuilder sb = new StringBuilder();
                    for (int cp : codePoints) {
                        sb.appendCodePoint(cp);
                    }
                    sentOutput.add(sb.toString());
                };
            }
        };

        connection.terminal().disableSynchronizedOutput();

        assertFalse("Should have sent output", sentOutput.isEmpty());
        assertTrue("Should have sent Mode 2026 disable sequence",
                sentOutput.stream().anyMatch(s -> s.contains("\u001B[?2026l")));
    }

    @Test
    public void testSupportsSynchronizedOutput_NullDevice() {
        MockConnection connection = new MockConnection() {
            @Override
            public Device device() {
                return null;
            }
        };

        assertFalse("Should return false when device is null",
                connection.terminal().supportsSynchronizedOutput());
    }

    @Test
    public void testSupportsSynchronizedOutput_NoAnsi() {
        MockConnection connection = new MockConnection() {
            @Override
            public boolean supportsAnsi() {
                return false;
            }
        };

        assertFalse("Should return false when ANSI not supported",
                connection.terminal().supportsSynchronizedOutput());
    }

    // ==================== Mock Connection ====================

    private static class MockConnection implements Connection {
        private Consumer<int[]> stdinHandler;
        private Consumer<Size> sizeHandler;
        private Consumer<Signal> signalHandler;
        private Consumer<Void> closeHandler;
        private Attributes attributes = new Attributes();
        private final List<String> outputBuffer = new ArrayList<>();

        @Override
        public Device device() {
            return new BaseDevice("xterm-256color") {
                @Override
                public boolean supportsOscQueries() {
                    return true;
                }
            };
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
                outputBuffer.add(sb.toString());
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
        public boolean reading() {
            return true;
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

        public void simulateInput(String input) {
            if (stdinHandler != null) {
                stdinHandler.accept(input.codePoints().toArray());
            }
        }
    }
}
