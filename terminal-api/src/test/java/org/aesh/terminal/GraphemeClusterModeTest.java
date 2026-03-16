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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.junit.Test;

/**
 * Tests for Mode 2027 (grapheme cluster mode) support across
 * TerminalType, Device, and Connection.
 *
 * @author Ståle Pedersen
 */
public class GraphemeClusterModeTest {

    // ==================== TerminalType Tests ====================

    @Test
    public void testGhosttySupportsGraphemeClusterMode() {
        assertTrue("Ghostty should support Mode 2027",
                Device.TerminalType.GHOSTTY.supportsGraphemeClusterMode());
    }

    @Test
    public void testWeztermSupportsGraphemeClusterMode() {
        assertTrue("WezTerm should support Mode 2027",
                Device.TerminalType.WEZTERM.supportsGraphemeClusterMode());
    }

    @Test
    public void testKittySupportsGraphemeClusterMode() {
        assertTrue("Kitty should support Mode 2027",
                Device.TerminalType.KITTY.supportsGraphemeClusterMode());
    }

    @Test
    public void testContourSupportsGraphemeClusterMode() {
        assertTrue("Contour should support Mode 2027",
                Device.TerminalType.CONTOUR.supportsGraphemeClusterMode());
    }

    @Test
    public void testFootSupportsGraphemeClusterMode() {
        assertTrue("Foot should support Mode 2027",
                Device.TerminalType.FOOT.supportsGraphemeClusterMode());
    }

    @Test
    public void testXtermDoesNotSupportGraphemeClusterMode() {
        assertFalse("xterm should not support Mode 2027",
                Device.TerminalType.XTERM.supportsGraphemeClusterMode());
    }

    @Test
    public void testLinuxConsoleDoesNotSupportGraphemeClusterMode() {
        assertFalse("Linux console should not support Mode 2027",
                Device.TerminalType.LINUX_CONSOLE.supportsGraphemeClusterMode());
    }

    @Test
    public void testTmuxDoesNotSupportGraphemeClusterMode() {
        assertFalse("tmux should not support Mode 2027",
                Device.TerminalType.TMUX.supportsGraphemeClusterMode());
    }

    @Test
    public void testScreenDoesNotSupportGraphemeClusterMode() {
        assertFalse("GNU Screen should not support Mode 2027",
                Device.TerminalType.SCREEN.supportsGraphemeClusterMode());
    }

    @Test
    public void testUnknownDoesNotSupportGraphemeClusterMode() {
        assertFalse("Unknown terminal should not support Mode 2027",
                Device.TerminalType.UNKNOWN.supportsGraphemeClusterMode());
    }

    @Test
    public void testVscodeDoesNotSupportGraphemeClusterMode() {
        assertFalse("VSCode should not support Mode 2027",
                Device.TerminalType.VSCODE.supportsGraphemeClusterMode());
    }

    @Test
    public void testJetbrainsDoesNotSupportGraphemeClusterMode() {
        assertFalse("JetBrains should not support Mode 2027",
                Device.TerminalType.JETBRAINS.supportsGraphemeClusterMode());
    }

    @Test
    public void testAlacrittyDoesNotSupportGraphemeClusterMode() {
        assertFalse("Alacritty should not support Mode 2027",
                Device.TerminalType.ALACRITTY.supportsGraphemeClusterMode());
    }

    @Test
    public void testIterm2DoesNotSupportGraphemeClusterMode() {
        assertFalse("iTerm2 should not support Mode 2027",
                Device.TerminalType.ITERM2.supportsGraphemeClusterMode());
    }

    // ==================== Connection Query Tests ====================

    @Test
    public void testQueryGraphemeClusterMode_Supported() throws Exception {
        MockConnection connection = new MockConnection();

        CountDownLatch queryStarted = new CountDownLatch(1);
        Thread responseThread = new Thread(() -> {
            try {
                queryStarted.await(1, TimeUnit.SECONDS);
                Thread.sleep(20);
                // Terminal responds: Mode 2027 is reset (recognized but disabled)
                String response = "\u001B[?2027;2$y";
                connection.simulateInput(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        responseThread.start();

        queryStarted.countDown();
        Boolean result = connection.terminal().queryGraphemeClusterMode(500);
        responseThread.join(1000);

        assertNotNull("Should get a response from terminal", result);
        assertTrue("Ps=2 means terminal recognizes Mode 2027", result);
    }

    @Test
    public void testQueryGraphemeClusterMode_NotSupported() throws Exception {
        MockConnection connection = new MockConnection();

        CountDownLatch queryStarted = new CountDownLatch(1);
        Thread responseThread = new Thread(() -> {
            try {
                queryStarted.await(1, TimeUnit.SECONDS);
                Thread.sleep(20);
                // Terminal responds: Mode 2027 is not recognized
                String response = "\u001B[?2027;0$y";
                connection.simulateInput(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        responseThread.start();

        queryStarted.countDown();
        Boolean result = connection.terminal().queryGraphemeClusterMode(500);
        responseThread.join(1000);

        assertNotNull("Should get a response from terminal", result);
        assertFalse("Ps=0 means terminal does not recognize Mode 2027", result);
    }

    @Test
    public void testQueryGraphemeClusterMode_Timeout() {
        MockConnection connection = new MockConnection();

        // No response simulated - should timeout
        Boolean result = connection.terminal().queryGraphemeClusterMode(100);

        assertNull("Should return null on timeout", result);
    }

    @Test
    public void testEnableGraphemeClusterMode_SendsCorrectSequence() {
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

        connection.terminal().enableGraphemeClusterMode();

        assertFalse("Should have sent output", sentOutput.isEmpty());
        assertTrue("Should have sent Mode 2027 enable sequence",
                sentOutput.stream().anyMatch(s -> s.contains("\u001B[?2027h")));
    }

    @Test
    public void testDisableGraphemeClusterMode_SendsCorrectSequence() {
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

        connection.terminal().disableGraphemeClusterMode();

        assertFalse("Should have sent output", sentOutput.isEmpty());
        assertTrue("Should have sent Mode 2027 disable sequence",
                sentOutput.stream().anyMatch(s -> s.contains("\u001B[?2027l")));
    }

    @Test
    public void testSupportsGraphemeClusterMode_NullDevice() {
        MockConnection connection = new MockConnection() {
            @Override
            public Device device() {
                return null;
            }
        };

        assertFalse("Should return false when device is null",
                connection.terminal().supportsGraphemeClusterMode());
    }

    @Test
    public void testSupportsGraphemeClusterMode_NoAnsi() {
        MockConnection connection = new MockConnection() {
            @Override
            public boolean supportsAnsi() {
                return false;
            }
        };

        assertFalse("Should return false when ANSI not supported",
                connection.terminal().supportsGraphemeClusterMode());
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
                    // Ensure tests do not depend on host environment OSC heuristics
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
