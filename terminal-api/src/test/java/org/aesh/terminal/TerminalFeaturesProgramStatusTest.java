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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.ProgramStatus;
import org.junit.Test;

/**
 * Tests for explicit OSC 7501 writers on TerminalFeatures.
 * Writers emit one complete sequence with no implicit queries.
 */
public class TerminalFeaturesProgramStatusTest {

    @Test
    public void testWriteProgramStatusEmitsOneSequence() {
        RecordingConnection connection = new RecordingConnection();
        ProgramStatus status = ProgramStatus.builder(ProgramStatus.State.WORKING)
                .app("brew")
                .message("Installing updates")
                .build();

        Connection returned = connection.terminal().writeProgramStatus(status);

        assertSame(connection, returned);
        assertEquals(1, connection.written().size());
        assertEquals(status.toSequence(), connection.written().get(0));
    }

    @Test
    public void testWriteProgramStatusNullWritesNothing() {
        RecordingConnection connection = new RecordingConnection();
        try {
            connection.terminal().writeProgramStatus(null);
            fail("null status must be rejected");
        } catch (NullPointerException expected) {
            assertTrue(connection.written().isEmpty());
        }
    }

    @Test
    public void testClearProgramStatus() {
        RecordingConnection connection = new RecordingConnection();

        Connection returned = connection.terminal().clearProgramStatus("us-east");

        assertSame(connection, returned);
        assertEquals(1, connection.written().size());
        assertEquals(ProgramStatus.clearSequence("us-east"), connection.written().get(0));
    }

    @Test
    public void testClearProgramStatusInvalidWritesNothing() {
        RecordingConnection connection = new RecordingConnection();
        try {
            connection.terminal().clearProgramStatus("");
            fail("empty id must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(connection.written().isEmpty());
        }
    }

    @Test
    public void testClearAllProgramStatus() {
        RecordingConnection connection = new RecordingConnection();

        connection.terminal().clearAllProgramStatus();

        assertEquals(1, connection.written().size());
        assertEquals(ProgramStatus.clearAllSequence(), connection.written().get(0));
    }

    @Test
    public void testProgramStatusHintAbsentByDefault() {
        RecordingConnection connection = new RecordingConnection();
        assertEquals(null, connection.device().getStringCapability(
                org.aesh.terminal.tty.Capability.program_status));
        assertEquals(false, connection.terminal().hasProgramStatusHint());
    }

    @Test
    public void testProgramStatusHintPresent() {
        RecordingConnection connection = new RecordingConnection(new BaseDevice("pst-term") {
            @Override
            public String getStringCapability(org.aesh.terminal.tty.Capability capability) {
                if (capability == org.aesh.terminal.tty.Capability.program_status) {
                    return "\\E]7501;%p1%s\\E\\\\";
                }
                return super.getStringCapability(capability);
            }
        });
        assertEquals(true, connection.terminal().hasProgramStatusHint());
    }

    private static final String ACK_BEL = "" + (char) 27 + "]7501;?" + (char) 7;
    private static final String ACK_ST = "" + (char) 27 + "]7501;?" + (char) 27 + (char) 92;
    private static final String DA1_REPLY = "" + (char) 27 + "[?1;2c";

    private static Boolean queryWithReply(RecordingConnection connection, String reply, long timeoutMs)
            throws Exception {
        Consumer<int[]> before = connection.stdinHandler();
        Thread responder = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.awaitLease(before, 5000);
                    connection.simulateInput(reply);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        responder.start();
        try {
            return connection.terminal().queryProgramStatusSupport(timeoutMs);
        } finally {
            responder.join(5000);
        }
    }

    @Test
    public void testSupportQuerySendsProbePlusFence() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        assertEquals(Boolean.TRUE, queryWithReply(connection, ACK_BEL + DA1_REPLY, 2000));
        assertEquals(1, connection.written().size());
        assertEquals(org.aesh.terminal.utils.ANSI.OSC_7501_QUERY
                + org.aesh.terminal.utils.ANSI.DA1_QUERY, connection.written().get(0));
    }

    @Test
    public void testSupportQueryStTerminated() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        assertEquals(Boolean.TRUE, queryWithReply(connection, ACK_ST + DA1_REPLY, 2000));
    }

    @Test
    public void testSupportQueryDa1First() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        assertEquals(Boolean.FALSE, queryWithReply(connection, DA1_REPLY + ACK_BEL, 2000));
    }

    @Test
    public void testSupportQueryTimeoutIsUnknown() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        assertEquals(null, connection.terminal().queryProgramStatusSupport(50));
        assertEquals(null, connection.terminal().programStatusSupport());
    }

    @Test
    public void testSupportResultCached() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        assertEquals(Boolean.TRUE, queryWithReply(connection, ACK_BEL + DA1_REPLY, 2000));
        connection.written().clear();
        assertEquals(Boolean.TRUE, connection.terminal().queryProgramStatusSupport(300));
        assertTrue("cached answer must not query again", connection.written().isEmpty());
    }

    @Test
    public void testSupportTimeoutNotCached() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        assertEquals(null, connection.terminal().queryProgramStatusSupport(50));
        assertEquals(Boolean.TRUE, queryWithReply(connection, ACK_BEL + DA1_REPLY, 2000));
    }

    @Test
    public void testSupportQueryPreservesKeyboardInput() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        final List<int[]> received = new ArrayList<>();
        connection.setStdinHandler(new Consumer<int[]>() {
            @Override
            public void accept(int[] codePoints) {
                received.add(codePoints);
            }
        });
        assertEquals(Boolean.TRUE, queryWithReply(connection, "hi" + ACK_BEL + DA1_REPLY, 2000));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < received.size(); i++) {
            int[] chunk = received.get(i);
            for (int j = 0; j < chunk.length; j++) {
                text.appendCodePoint(chunk[j]);
            }
        }
        assertEquals("hi", text.toString());
    }

    @Test
    public void testSupportQueryPreservesReplyShapedKeys() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        final List<int[]> received = new ArrayList<>();
        connection.setStdinHandler(new Consumer<int[]>() {
            @Override
            public void accept(int[] codePoints) {
                received.add(codePoints);
            }
        });
        String shiftF3 = "" + (char) 27 + "[1;2R";
        assertEquals(Boolean.TRUE, queryWithReply(connection, shiftF3 + ACK_BEL + DA1_REPLY, 2000));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < received.size(); i++) {
            int[] chunk = received.get(i);
            for (int j = 0; j < chunk.length; j++) {
                text.appendCodePoint(chunk[j]);
            }
        }
        assertEquals(shiftF3, text.toString());
    }

    @Test
    public void testSupportQueryKeepsArrivalOrder() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        final List<int[]> received = new ArrayList<>();
        connection.setStdinHandler(new Consumer<int[]>() {
            @Override
            public void accept(int[] codePoints) {
                received.add(codePoints);
            }
        });
        String up = "" + (char) 27 + "[A";
        assertEquals(Boolean.TRUE, queryWithReply(connection, up + "x" + ACK_BEL + DA1_REPLY, 2000));
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < received.size(); i++) {
            int[] chunk = received.get(i);
            for (int j = 0; j < chunk.length; j++) {
                text.appendCodePoint(chunk[j]);
            }
        }
        assertEquals(up + "x", text.toString());
    }

    @Test
    public void testSupportQueryKeepsFragmentedTail() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        final List<int[]> received = new ArrayList<>();
        connection.setStdinHandler(new Consumer<int[]>() {
            @Override
            public void accept(int[] codePoints) {
                received.add(codePoints);
            }
        });
        Consumer<int[]> before = connection.stdinHandler();
        Thread responder = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.awaitLease(before, 5000);
                    connection.simulateInput(ACK_BEL + (char) 27 + "[1;");
                    Thread.sleep(20);
                    connection.simulateInput("2R");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        responder.start();
        Boolean result;
        try {
            result = connection.terminal().queryProgramStatusSupport(2000);
        } finally {
            responder.join(5000);
        }
        assertEquals(Boolean.TRUE, result);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < received.size(); i++) {
            int[] chunk = received.get(i);
            for (int j = 0; j < chunk.length; j++) {
                text.appendCodePoint(chunk[j]);
            }
        }
        assertEquals("" + (char) 27 + "[1;2R", text.toString());
    }

    @Test
    public void testSupportQueryKeepsPostResultInput() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        final List<int[]> received = new ArrayList<>();
        connection.setStdinHandler(new Consumer<int[]>() {
            @Override
            public void accept(int[] codePoints) {
                received.add(codePoints);
            }
        });
        Consumer<int[]> before = connection.stdinHandler();
        Thread responder = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.awaitLease(before, 5000);
                    connection.simulateInput(ACK_BEL + DA1_REPLY);
                    connection.simulateInput("typed");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        responder.start();
        Boolean result;
        try {
            result = connection.terminal().queryProgramStatusSupport(2000);
        } finally {
            responder.join(5000);
        }
        assertEquals(Boolean.TRUE, result);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < received.size(); i++) {
            int[] chunk = received.get(i);
            for (int j = 0; j < chunk.length; j++) {
                text.appendCodePoint(chunk[j]);
            }
        }
        assertEquals("typed", text.toString());
    }

    @Test
    public void testSupportQueryFragmentedAck() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        Consumer<int[]> before = connection.stdinHandler();
        Thread responder = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.awaitLease(before, 5000);
                    connection.simulateInput("" + (char) 27 + "]7501");
                    Thread.sleep(20);
                    connection.simulateInput(";?" + (char) 7 + DA1_REPLY);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
        responder.start();
        Boolean result;
        try {
            result = connection.terminal().queryProgramStatusSupport(2000);
        } finally {
            responder.join(5000);
        }
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    public void testSupportCacheIsPerConnection() throws Exception {
        RecordingConnection first = new RecordingConnection();
        assertEquals(Boolean.TRUE, queryWithReply(first, ACK_BEL + DA1_REPLY, 2000));
        RecordingConnection second = new RecordingConnection();
        assertEquals(null, second.terminal().programStatusSupport());
    }

    @Test
    public void testOverlappingQueriesShareOneProbe() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        TerminalFeatures features = connection.terminal();
        Consumer<int[]> before = connection.stdinHandler();
        final java.util.concurrent.atomic.AtomicReference<Boolean> firstResult = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<Boolean> secondResult = new java.util.concurrent.atomic.AtomicReference<>();
        Thread first = new Thread(new Runnable() {
            @Override
            public void run() {
                firstResult.set(features.queryProgramStatusSupport(2000));
            }
        });
        first.start();
        connection.awaitLease(before, 5000);
        Thread second = new Thread(new Runnable() {
            @Override
            public void run() {
                secondResult.set(features.queryProgramStatusSupport(2000));
            }
        });
        second.start();
        second.join(1000);
        connection.simulateInput(ACK_BEL + DA1_REPLY);
        first.join(5000);
        second.join(5000);
        assertEquals(Boolean.TRUE, firstResult.get());
        assertEquals(Boolean.TRUE, secondResult.get());
        assertEquals(Boolean.TRUE, features.programStatusSupport());
        assertEquals("one probe serves both callers", 1, connection.written().size());
    }

    @Test
    public void testHandlerlessQueuedInputSurvivesQuery() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        connection.feedDecoder("queued");
        assertEquals(Boolean.TRUE, queryWithReply(connection, ACK_BEL + DA1_REPLY, 2000));
        final List<int[]> received = new ArrayList<>();
        connection.setStdinHandler(new Consumer<int[]>() {
            @Override
            public void accept(int[] codePoints) {
                received.add(codePoints);
            }
        });
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < received.size(); i++) {
            int[] chunk = received.get(i);
            for (int j = 0; j < chunk.length; j++) {
                text.appendCodePoint(chunk[j]);
            }
        }
        assertEquals("queued", text.toString());
    }

    @Test
    public void testHandlerlessQueuedInputSurvivesTimeout() throws Exception {
        RecordingConnection connection = new RecordingConnection();
        connection.feedDecoder("queued");
        assertEquals(null, connection.terminal().queryProgramStatusSupport(50));
        final List<int[]> received = new ArrayList<>();
        connection.setStdinHandler(new Consumer<int[]>() {
            @Override
            public void accept(int[] codePoints) {
                received.add(codePoints);
            }
        });
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < received.size(); i++) {
            int[] chunk = received.get(i);
            for (int j = 0; j < chunk.length; j++) {
                text.appendCodePoint(chunk[j]);
            }
        }
        assertEquals("queued", text.toString());
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

        void awaitLease(Consumer<int[]> previous, long timeoutMs) throws InterruptedException {
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (stdinHandler() == previous && System.currentTimeMillis() < deadline) {
                Thread.sleep(1);
            }
        }

        void simulateInput(String text) {
            Consumer<int[]> handler = stdinHandler();
            if (handler != null) {
                handler.accept(text.codePoints().toArray());
            }
        }

        void feedDecoder(String text) {
            eventDecoder.accept(text.codePoints().toArray());
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
