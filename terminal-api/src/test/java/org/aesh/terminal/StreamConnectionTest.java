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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.aesh.terminal.tty.Size;
import org.junit.Test;

/**
 * Tests for {@link StreamConnection} (#292).
 */
public class StreamConnectionTest {

    private static Consumer<int[]> recorder(List<int[]> received) {
        return received::add;
    }

    private static void awaitSize(List<int[]> received, int size) throws InterruptedException {
        for (int i = 0; i < 500 && received.size() < size; i++) {
            Thread.sleep(10);
        }
        if (received.size() < size) {
            fail("Timed out waiting for input, received: " + received.size());
        }
    }

    @Test
    public void testRoundTrip() throws Exception {
        PipedOutputStream writer = new PipedOutputStream();
        PipedInputStream reader = new PipedInputStream(writer, 4096);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamConnection conn = new StreamConnection(StandardCharsets.UTF_8, reader, output);
        List<int[]> received = new ArrayList<>();
        try {
            conn.setStdinHandler(recorder(received));
            conn.openNonBlocking();
            writer.write("hello\n".getBytes(StandardCharsets.UTF_8));
            writer.flush();
            awaitSize(received, 1);
            assertArrayEquals("hello\n".codePoints().toArray(), received.get(0));
        } finally {
            conn.close();
            writer.close();
        }
    }

    @Test
    public void testSplitMultiByteSequence() throws Exception {
        // A multi-byte character split across reads must decode whole —
        // the hand-rolled bytes→String step this replaces corrupts it.
        PipedOutputStream writer = new PipedOutputStream();
        PipedInputStream reader = new PipedInputStream(writer, 4096);
        StreamConnection conn = new StreamConnection(StandardCharsets.UTF_8, reader,
                new ByteArrayOutputStream());
        List<int[]> received = new ArrayList<>();
        try {
            conn.setStdinHandler(recorder(received));
            conn.openNonBlocking();
            byte[] utf8 = "ä".getBytes(StandardCharsets.UTF_8);
            writer.write(utf8, 0, 1);
            writer.flush();
            Thread.sleep(100);
            writer.write(utf8, 1, 1);
            writer.flush();
            awaitSize(received, 1);
            assertArrayEquals(new int[] { 228 }, received.get(0));
        } finally {
            conn.close();
            writer.close();
        }
    }

    @Test
    public void testCloseStopsDelivery() throws Exception {
        PipedOutputStream writer = new PipedOutputStream();
        PipedInputStream reader = new PipedInputStream(writer, 4096);
        StreamConnection conn = new StreamConnection(StandardCharsets.UTF_8, reader,
                new ByteArrayOutputStream());
        List<int[]> received = new ArrayList<>();
        conn.setStdinHandler(recorder(received));
        conn.openNonBlocking();
        conn.close();
        conn.close();
        assertFalse(conn.reading());
        writer.write("late\n".getBytes(StandardCharsets.UTF_8));
        writer.flush();
        Thread.sleep(200);
        assertTrue("No input must be delivered after close", received.isEmpty());
        writer.close();
    }

    @Test
    public void testReadsStreamWithDefaultAvailable() throws Exception {
        InputStream input = new InputStream() {
            private int position;
            private final byte[] data = "ready".getBytes(StandardCharsets.UTF_8);

            @Override
            public int read() {
                return position < data.length ? data[position++] & 0xff : -1;
            }
        };
        StreamConnection conn = new StreamConnection(StandardCharsets.UTF_8, input,
                new ByteArrayOutputStream());
        List<int[]> received = new CopyOnWriteArrayList<>();
        try {
            conn.setStdinHandler(recorder(received));
            conn.openNonBlocking();
            awaitSize(received, 1);
            assertArrayEquals("ready".codePoints().toArray(), received.get(0));
        } finally {
            conn.close();
        }
    }

    @Test
    public void testCloseUnblocksOpenBlockingWithoutOwningInput() throws Exception {
        final CountDownLatch enteredRead = new CountDownLatch(1);
        final CountDownLatch releaseRead = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        final boolean[] streamClosed = { false };
        InputStream input = new InputStream() {
            private boolean delivered;

            @Override
            public int read() {
                enteredRead.countDown();
                while (true) {
                    try {
                        releaseRead.await();
                        if (delivered) {
                            return -1;
                        }
                        delivered = true;
                        return 'x';
                    } catch (InterruptedException ignored) {
                        // Simulates a caller-owned stream that cannot be cancelled.
                    }
                }
            }

            @Override
            public void close() {
                streamClosed[0] = true;
            }
        };
        final StreamConnection conn = new StreamConnection(StandardCharsets.UTF_8, input,
                new ByteArrayOutputStream());
        List<int[]> received = new CopyOnWriteArrayList<>();
        conn.setStdinHandler(recorder(received));
        conn.setCloseHandler(ignored -> closed.countDown());
        Thread waiter = new Thread(new Runnable() {
            @Override
            public void run() {
                conn.openBlocking();
            }
        });
        try {
            waiter.start();
            assertTrue("Reader must enter read()", enteredRead.await(2, TimeUnit.SECONDS));
            conn.close();
            waiter.join(1000);
            assertFalse("close must unblock openBlocking()", waiter.isAlive());
            assertEquals(0, closed.getCount());
            assertFalse("Input belongs to the caller", streamClosed[0]);
        } finally {
            conn.close();
            releaseRead.countDown();
            waiter.join(2000);
        }
        for (int i = 0; i < 100 && conn.reading(); i++) {
            Thread.sleep(10);
        }
        assertFalse("Reader must exit after caller releases the stream", conn.reading());
        assertTrue("No bytes may be dispatched after close", received.isEmpty());
    }

    private static class FailingInputStream extends java.io.InputStream {
        private final CountDownLatch broken = new CountDownLatch(1);

        @Override
        public int read() throws java.io.IOException {
            try {
                broken.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("interrupted", e);
            }
            throw new java.io.IOException("boom");
        }

        void breakIt() {
            broken.countDown();
        }
    }

    @Test
    public void testDeathHookOnBrokenStream() throws Exception {
        FailingInputStream input = new FailingInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamConnection conn = new StreamConnection(StandardCharsets.UTF_8, input, output);
        AtomicReference<Throwable> death = new AtomicReference<>();
        try {
            conn.setReaderDeathHook(death::set);
            conn.setStdinHandler(recorder(new ArrayList<>()));
            conn.openNonBlocking();
            input.breakIt();
            for (int i = 0; i < 500 && death.get() == null; i++) {
                Thread.sleep(10);
            }
            assertTrue("Death hook must fire when the stream breaks",
                    death.get() instanceof java.io.IOException);
        } finally {
            conn.close();
        }
    }

    @Test
    public void testWriterCloseDeliversPendingAndStaysSilent() throws Exception {
        // Clean writer close: pending bytes are delivered before EOF, and
        // the death hook stays silent (no failure occurred).
        PipedOutputStream writer = new PipedOutputStream();
        PipedInputStream reader = new PipedInputStream(writer, 4096);
        StreamConnection conn = new StreamConnection(StandardCharsets.UTF_8, reader,
                new ByteArrayOutputStream());
        List<int[]> received = new ArrayList<>();
        AtomicReference<Throwable> death = new AtomicReference<>();
        try {
            conn.setReaderDeathHook(death::set);
            conn.setStdinHandler(recorder(received));
            conn.openNonBlocking();
            writer.write("go\n".getBytes(StandardCharsets.UTF_8));
            writer.flush();
            writer.close();
            awaitSize(received, 1);
            assertArrayEquals("go\n".codePoints().toArray(), received.get(0));
            for (int i = 0; i < 100 && conn.reading(); i++) {
                Thread.sleep(10);
            }
            assertNull("Clean writer close must not fire the death hook", death.get());
            assertFalse("Reader must stop on EOF", conn.reading());
        } finally {
            conn.close();
        }
    }

    @Test
    public void testFlagsSizeAndOutputEncoding() throws Exception {
        PipedOutputStream writer = new PipedOutputStream();
        PipedInputStream reader = new PipedInputStream(writer, 4096);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        StreamConnection conn = new StreamConnection(StandardCharsets.UTF_8, reader, output);
        try {
            assertFalse(conn.supportsAnsi());
            assertTrue(conn.isInteractive());
            assertEquals(120, conn.size().getWidth());
            conn.setSize(new Size(100, 30));
            assertEquals(100, conn.size().getWidth());
            assertEquals(StandardCharsets.UTF_8, conn.inputEncoding());
            assertEquals(StandardCharsets.UTF_8, conn.outputEncoding());
            conn.stdoutHandler().accept("héllo".codePoints().toArray());
            assertArrayEquals("héllo".getBytes(StandardCharsets.UTF_8), output.toByteArray());
        } finally {
            conn.close();
            writer.close();
        }
    }
}
