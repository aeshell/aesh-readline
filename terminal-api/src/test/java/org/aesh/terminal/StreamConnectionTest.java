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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
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

    private static class FailingInputStream extends java.io.InputStream {
        private volatile boolean broken;

        @Override
        public int available() throws java.io.IOException {
            if (broken) {
                throw new java.io.IOException("boom");
            }
            return 0;
        }

        @Override
        public int read() {
            return -1;
        }

        void breakIt() {
            broken = true;
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
