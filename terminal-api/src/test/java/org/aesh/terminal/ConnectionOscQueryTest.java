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
import org.aesh.terminal.tty.Point;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.junit.Test;

/**
 * Tests for the TerminalFeatures.queryOsc() method and related color query methods.
 * These tests verify that OSC queries work correctly and responses are captured
 * properly (addressing issue #94).
 *
 * @author Ståle Pedersen
 */
public class ConnectionOscQueryTest {

    /**
     * Test that queryOsc correctly builds and sends OSC queries.
     */
    @Test
    public void testQueryOscBuildsCorrectQuery() {
        List<String> sentQueries = new ArrayList<>();
        MockConnection connection = new MockConnection() {
            @Override
            public Consumer<int[]> stdoutHandler() {
                return codePoints -> {
                    StringBuilder sb = new StringBuilder();
                    for (int cp : codePoints) {
                        sb.appendCodePoint(cp);
                    }
                    sentQueries.add(sb.toString());
                };
            }
        };

        // Trigger a query (will timeout since no response)
        connection.terminal().queryOsc(10, "?", 50, input -> null);

        assertEquals(1, sentQueries.size());
        assertEquals("\u001B]10;?\u0007", sentQueries.get(0));
    }

    /**
     * Test that queryForegroundColor correctly parses a valid response.
     * This tests the scenario from issue #94 - the response should be
     * captured and returned, not echoed to terminal.
     */
    @Test
    public void testQueryForegroundColorReturnsValue() throws Exception {
        MockConnection connection = new MockConnection();
        Consumer<int[]> originalHandler = connection.stdinHandler();

        Thread responseThread = new Thread(() -> {
            try {
                connection.awaitHandlerChange(originalHandler, 1000);
                String response = "\u001B]10;rgb:FFFF/8080/0000\u0007";
                connection.simulateInput(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        responseThread.start();

        int[] rgb = connection.terminal().queryForegroundColor(500);

        responseThread.join(1000);

        // Verify the response was captured and returned
        assertNotNull("queryForegroundColor should return the parsed RGB values", rgb);
        assertEquals(3, rgb.length);
        assertEquals(255, rgb[0]); // FFFF >> 8 = 255
        assertEquals(128, rgb[1]); // 8080 >> 8 = 128
        assertEquals(0, rgb[2]); // 0000 >> 8 = 0
    }

    /**
     * Test that queryBackgroundColor correctly parses a valid response.
     */
    @Test
    public void testQueryBackgroundColorReturnsValue() throws Exception {
        MockConnection connection = new MockConnection();
        Consumer<int[]> originalHandler = connection.stdinHandler();

        Thread responseThread = new Thread(() -> {
            try {
                connection.awaitHandlerChange(originalHandler, 1000);
                String response = "\u001B]11;rgb:2828/2828/2828\u0007";
                connection.simulateInput(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        responseThread.start();

        int[] rgb = connection.terminal().queryBackgroundColor(500);
        responseThread.join(1000);

        assertNotNull("queryBackgroundColor should return the parsed RGB values", rgb);
        assertEquals(40, rgb[0]); // 2828 >> 8 = 40
        assertEquals(40, rgb[1]);
        assertEquals(40, rgb[2]);
    }

    /**
     * Test that queryCursorColor correctly parses a valid response.
     */
    @Test
    public void testQueryCursorColorReturnsValue() throws Exception {
        MockConnection connection = new MockConnection();
        Consumer<int[]> originalHandler = connection.stdinHandler();

        Thread responseThread = new Thread(() -> {
            try {
                connection.awaitHandlerChange(originalHandler, 1000);
                String response = "\u001B]12;rgb:0000/FFFF/0000\u0007";
                connection.simulateInput(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        responseThread.start();

        int[] rgb = connection.terminal().queryCursorColor(500);
        responseThread.join(1000);

        assertNotNull("queryCursorColor should return the parsed RGB values", rgb);
        assertEquals(0, rgb[0]);
        assertEquals(255, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    /**
     * Test that query returns null on timeout when no response.
     */
    @Test
    public void testQueryReturnsNullOnTimeout() {
        MockConnection connection = new MockConnection();

        long start = System.currentTimeMillis();
        int[] rgb = connection.terminal().queryForegroundColor(100);
        long elapsed = System.currentTimeMillis() - start;

        assertNull("Should return null when no response received", rgb);
        assertTrue("Should wait for timeout", elapsed >= 90);
    }

    /**
     * Test that the response is captured by the stdin handler and not
     * passed to any other handler (addressing issue #94).
     */
    @Test
    public void testResponseNotPassedToOriginalHandler() throws Exception {
        MockConnection connection = new MockConnection();
        List<String> originalHandlerReceived = new ArrayList<>();

        Consumer<int[]> originalHandler = input -> {
            StringBuilder sb = new StringBuilder();
            for (int cp : input) {
                sb.appendCodePoint(cp);
            }
            originalHandlerReceived.add(sb.toString());
        };
        connection.setStdinHandler(originalHandler);

        Thread responseThread = new Thread(() -> {
            try {
                connection.awaitHandlerChange(originalHandler, 1000);
                String response = "\u001B]10;rgb:FFFF/FFFF/FFFF\u0007";
                connection.simulateInput(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        responseThread.start();

        int[] rgb = connection.terminal().queryForegroundColor(500);
        responseThread.join(1000);

        assertNotNull("Query should succeed", rgb);
        // The original handler should be restored after the query
        // but should not have received the OSC response
        assertTrue("Original handler should not receive OSC response during query",
                originalHandlerReceived.isEmpty());
    }

    /**
     * Test generic queryOsc with custom parser.
     */
    @Test
    public void testGenericQueryOscWithCustomParser() throws Exception {
        MockConnection connection = new MockConnection();
        Consumer<int[]> originalHandler = connection.stdinHandler();

        Thread responseThread = new Thread(() -> {
            try {
                connection.awaitHandlerChange(originalHandler, 1000);
                String response = "\u001B]52;c;SGVsbG8gV29ybGQ=\u0007";
                connection.simulateInput(response);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        responseThread.start();

        String result = connection.terminal().queryOsc(52, "c;?", 500, input -> {
            StringBuilder sb = new StringBuilder();
            for (int cp : input) {
                sb.appendCodePoint(cp);
            }
            String str = sb.toString();
            if (str.contains(";") && str.contains("\u001B]52")) {
                // Extract base64 content
                int start = str.indexOf(";c;") + 3;
                int end = str.indexOf('\u0007', start);
                if (end < 0)
                    end = str.indexOf("\u001B\\", start);
                if (end > start) {
                    return str.substring(start, end);
                }
            }
            return null;
        });

        responseThread.join(1000);

        assertEquals("SGVsbG8gV29ybGQ=", result);
    }

    /**
     * A mock connection for testing OSC queries.
     */
    /**
     * Test that an OSC reply split across reads at any point still
     * matches (issue #316). Each transport chunks differently; the
     * query must not depend on chunk boundaries.
     */
    @Test
    public void testSplitOscResponseMatchesAtEverySplitPoint() throws Exception {
        String response = "" + (char) 27 + "]10;rgb:FFFF/8080/0000" + (char) 7;
        for (int split = 1; split < response.length(); split++) {
            MockConnection connection = new MockConnection();
            Consumer<int[]> originalHandler = connection.stdinHandler();
            String first = response.substring(0, split);
            String second = response.substring(split);

            Thread responseThread = new Thread(() -> {
                try {
                    connection.awaitHandlerChange(originalHandler, 1000);
                    connection.simulateInput(first);
                    Thread.sleep(20);
                    connection.simulateInput(second);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            responseThread.start();

            int[] rgb = connection.terminal().queryForegroundColor(500);
            responseThread.join(2000);

            assertNotNull("split at " + split + " must still match", rgb);
            assertArrayEquals(new int[] { 255, 128, 0 }, rgb);
        }
    }

    /**
     * Test that a cursor position reply split across reads still
     * matches, at every split point.
     */
    @Test
    public void testSplitCursorPositionResponse() throws Exception {
        String response = "" + (char) 27 + "[24;80R";
        for (int split = 1; split < response.length(); split++) {
            MockConnection connection = new MockConnection();
            Consumer<int[]> originalHandler = connection.stdinHandler();
            String first = response.substring(0, split);
            String second = response.substring(split);

            Thread responseThread = new Thread(() -> {
                try {
                    connection.awaitHandlerChange(originalHandler, 1000);
                    connection.simulateInput(first);
                    Thread.sleep(20);
                    connection.simulateInput(second);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            responseThread.start();

            Point pos = connection.terminal().getCursorPosition();
            responseThread.join(2000);

            assertNotNull("split at " + split + " must still match", pos);
            assertEquals(80, pos.x());
            assertEquals(24, pos.y());
        }
    }

    /**
     * Test that multiple replies arriving in one read still parse.
     */
    @Test
    public void testMultipleRepliesInOneRead() throws Exception {
        MockConnection connection = new MockConnection();
        Consumer<int[]> originalHandler = connection.stdinHandler();

        Thread responseThread = new Thread(() -> {
            try {
                connection.awaitHandlerChange(originalHandler, 1000);
                String reply = "" + (char) 27 + "]10;rgb:FFFF/8080/0000" + (char) 7;
                connection.simulateInput(reply + reply);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        responseThread.start();

        int[] rgb = connection.terminal().queryForegroundColor(500);
        responseThread.join(1000);

        assertNotNull(rgb);
        assertArrayEquals(new int[] { 255, 128, 0 }, rgb);
    }

    /**
     * Test that unrelated input arriving during a query survives its
     * timeout: nothing matched, so every buffered byte is handed back
     * to the restored handler instead of dropped.
     */
    @Test
    public void testTimeoutForwardsUnrelatedInput() throws Exception {
        MockConnection connection = new MockConnection();
        List<String> originalHandlerReceived = new ArrayList<>();
        Consumer<int[]> originalHandler = input -> {
            StringBuilder sb = new StringBuilder();
            for (int cp : input) {
                sb.appendCodePoint(cp);
            }
            originalHandlerReceived.add(sb.toString());
        };
        connection.setStdinHandler(originalHandler);

        Thread inputThread = new Thread(() -> {
            try {
                connection.awaitHandlerChange(originalHandler, 1000);
                connection.simulateInput("hello");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        inputThread.start();

        int[] rgb = connection.terminal().queryForegroundColor(100);
        inputThread.join(1000);

        assertNull("no reply was sent", rgb);
        assertEquals("unrelated input must survive the timeout",
                1, originalHandlerReceived.size());
        assertEquals("hello", originalHandlerReceived.get(0));
        assertTrue("handler must be restored after timeout",
                connection.stdinHandler() == originalHandler);
    }

    private static class MockConnection implements Connection {
        private volatile Consumer<int[]> stdinHandler;
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
            return true; // Mock is always "reading" for test purposes
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

        public void awaitHandlerChange(Consumer<int[]> originalHandler, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (stdinHandler == originalHandler && System.currentTimeMillis() < deadline) {
                Thread.sleep(1);
            }
        }

        public void simulateInput(String input) {
            if (stdinHandler != null) {
                stdinHandler.accept(input.codePoints().toArray());
            }
        }

        public List<String> getOutputBuffer() {
            return outputBuffer;
        }
    }
}
