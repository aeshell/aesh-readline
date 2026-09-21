/*
 * Copyright 2026 Red Hat, Inc. and/or its affiliates.
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
package org.aesh.terminal.tty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.Device;
import org.aesh.terminal.Terminal;
import org.junit.Test;

/**
 * Tests for the legacy poll-based read loop in TerminalConnection.
 * <p>
 * Uses a stub Terminal with a fully controllable input stream (no real TTY
 * needed, works in CI). Covers the #288 regression: close() during a blocked
 * read must return promptly and stop the reader without touching the stream.
 */
public class TerminalConnectionLegacyLoopTest {

    /**
     * Input stream whose read() blocks forever on a latch and whose close()
     * deliberately does NOT unblock it — the shape of a tty fd read on macOS,
     * where close() cannot rescue a blocked reader.
     */
    static class NeverReadableStream extends InputStream {
        final AtomicInteger readCalls = new AtomicInteger();
        final AtomicInteger closeCalls = new AtomicInteger();
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public int available() {
            return 0;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            readCalls.incrementAndGet();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return -1;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public void close() {
            closeCalls.incrementAndGet();
        }
    }

    /**
     * Input stream with stageable bytes for suspend/resume testing.
     */
    static class StagedStream extends InputStream {
        final AtomicInteger readCalls = new AtomicInteger();
        volatile byte[] staged = new byte[0];

        @Override
        public int available() {
            return staged.length;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            readCalls.incrementAndGet();
            byte[] current = staged;
            if (current.length == 0) {
                return 0;
            }
            int count = Math.min(len, current.length);
            System.arraycopy(current, 0, b, off, count);
            byte[] rest = new byte[current.length - count];
            System.arraycopy(current, count, rest, 0, rest.length);
            staged = rest;
            return count;
        }

        @Override
        public int read() {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read <= 0 ? -1 : one[0] & 0xff;
        }
    }

    /**
     * Minimal Terminal stub routing input() to a controllable stream.
     * Reports no non-blocking read support so openBlocking() takes the
     * legacy loop.
     */
    static class StubTerminal implements Terminal {
        final InputStream input;

        StubTerminal(InputStream input) {
            this.input = input;
        }

        @Override
        public String getName() {
            return "test";
        }

        @Override
        public Terminal.SignalHandler handle(Signal signal, Terminal.SignalHandler handler) {
            return null;
        }

        @Override
        public void raise(Signal signal) {
        }

        @Override
        public InputStream input() {
            return input;
        }

        @Override
        public OutputStream output() {
            return new ByteArrayOutputStream();
        }

        @Override
        public boolean echo() {
            return false;
        }

        @Override
        public boolean echo(boolean echo) {
            return false;
        }

        @Override
        public Attributes getAttributes() {
            return new Attributes();
        }

        @Override
        public void setAttributes(Attributes attr) {
        }

        @Override
        public Size getSize() {
            return new Size(80, 24);
        }

        @Override
        public Device device() {
            return null;
        }

        @Override
        public void close() {
        }
    }

    @Test
    public void testCloseDuringBlockedReadReturnsPromptly() throws Exception {
        NeverReadableStream stream = new NeverReadableStream();
        TerminalConnection conn = new TerminalConnection(new StubTerminal(stream));

        Thread reader = new Thread(conn::openBlocking, "legacy-loop-reader");
        reader.setDaemon(true);
        reader.start();

        // Give the loop time to settle into polling (multiple 10ms intervals)
        Thread.sleep(100);
        assertTrue("Reader thread should be alive and polling", reader.isAlive());

        long start = System.currentTimeMillis();
        conn.close();
        long closeElapsed = System.currentTimeMillis() - start;
        assertTrue("close() must return promptly, took " + closeElapsed + "ms",
                closeElapsed < 5000);

        reader.join(5000);
        assertFalse("Reader thread must terminate after close() with no input arriving",
                reader.isAlive());
        assertEquals("close() must never touch the underlying stream",
                0, stream.closeCalls.get());
    }

    @Test
    public void testSuspendDoesNotConsume() throws Exception {
        StagedStream stream = new StagedStream();
        TerminalConnection conn = new TerminalConnection(new StubTerminal(stream));
        List<int[]> received = new ArrayList<>();
        conn.setStdinHandler(received::add);

        Thread reader = new Thread(conn::openBlocking, "legacy-loop-reader");
        reader.setDaemon(true);
        reader.start();

        conn.suspend();
        Thread.sleep(50);
        stream.staged = new byte[] { 'h', 'i' };
        Thread.sleep(150);

        assertEquals("Nothing must be consumed while suspended",
                0, stream.readCalls.get());

        conn.awake();
        long deadline = System.currentTimeMillis() + 5000;
        while (received.isEmpty() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals("Bytes must be delivered after awake()", 1, received.size());
        assertEquals("First byte must be 'h'", 'h', received.get(0)[0]);

        conn.close();
        reader.join(5000);
        assertFalse("Reader thread must terminate after close()", reader.isAlive());
    }
}
