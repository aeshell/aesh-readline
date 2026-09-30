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
package org.aesh.terminal.tty.impl;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.junit.Test;

/**
 * Tests for owned-input ExternalTerminal pumps (#341).
 * <p>
 * The borrowed pump only reads when {@code available()} reports bytes, so
 * streams with the default zero-returning {@code available()} are silently
 * starved and their EOF never observed. An owned input is read with
 * blocking {@code read()} calls instead: zero-available streams deliver,
 * EOF arrives promptly, and {@code close()} cancels a blocked read by
 * closing the owned stream.
 */
public class ExternalTerminalOwnedInputTest {

    /**
     * Stream with the default zero-returning {@code available()} that still
     * delivers bytes via {@code read()}: staged payloads, blocks while
     * empty, EOF once finished and drained, fails reads once closed.
     */
    static class ZeroAvailableStream extends InputStream {
        private final Object monitor = new Object();
        private byte[] staged;
        private int pos;
        private boolean eof;
        private boolean closed;
        int closeCalls;

        void stage(byte[] data) {
            synchronized (monitor) {
                staged = data.clone();
                pos = 0;
                monitor.notifyAll();
            }
        }

        void finish() {
            synchronized (monitor) {
                eof = true;
                monitor.notifyAll();
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            synchronized (monitor) {
                while (!closed && !eof && (staged == null || pos >= staged.length)) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IOException("interrupted while waiting for input", e);
                    }
                }
                if (closed) {
                    throw new IOException("stream closed");
                }
                if (staged != null && pos < staged.length) {
                    int count = Math.min(len, staged.length - pos);
                    System.arraycopy(staged, pos, b, off, count);
                    pos += count;
                    return count;
                }
                return -1;
            }
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int read = read(one, 0, 1);
            return read < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public void close() {
            synchronized (monitor) {
                closed = true;
                closeCalls++;
                monitor.notifyAll();
            }
        }
    }

    private static Thread findPumpThread(ExternalTerminal terminal) {
        String name = terminal.toString() + " input pump thread";
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (name.equals(thread.getName())) {
                return thread;
            }
        }
        return null;
    }

    private static Thread awaitPumpThread(ExternalTerminal terminal) throws Exception {
        Thread pump = null;
        long deadline = System.currentTimeMillis() + 5000;
        while (pump == null && System.currentTimeMillis() < deadline) {
            pump = findPumpThread(terminal);
            if (pump == null) {
                Thread.sleep(50);
            }
        }
        assertTrue("pump thread should be running", pump != null && pump.isAlive());
        return pump;
    }

    @Test
    public void testOwnedModeDeliversZeroAvailableStream() throws Exception {
        ZeroAvailableStream master = new ZeroAvailableStream();
        assertEquals("fixture must keep the default available()",
                0, master.available());
        ExternalTerminal terminal = new ExternalTerminal(
                "test", "test", master, new ByteArrayOutputStream(), true);
        try {
            master.stage(new byte[] { 'h', 'i' });
            byte[] received = new byte[2];
            int off = 0;
            long deadline = System.currentTimeMillis() + 10000;
            while (off < received.length && System.currentTimeMillis() < deadline) {
                int available = terminal.input().available();
                if (available > 0) {
                    int read = terminal.input().read(received, off, received.length - off);
                    if (read < 0) {
                        break;
                    }
                    off += read;
                } else {
                    Thread.sleep(10);
                }
            }
            assertEquals("owned pump must deliver zero-available input",
                    received.length, off);
            assertArrayEquals(new byte[] { 'h', 'i' }, received);
        } finally {
            terminal.close();
        }
    }

    @Test
    public void testOwnedModeObservesEof() throws Exception {
        ZeroAvailableStream master = new ZeroAvailableStream();
        ExternalTerminal terminal = new ExternalTerminal(
                "test", "test", master, new ByteArrayOutputStream(), true);
        try {
            master.stage(new byte[] { 'h', 'i' });
            master.finish();
            ByteArrayOutputStream received = new ByteArrayOutputStream();
            boolean eof = false;
            long deadline = System.currentTimeMillis() + 10000;
            while (!eof && System.currentTimeMillis() < deadline) {
                int available;
                try {
                    available = terminal.input().available();
                } catch (IOException pipeClosed) {
                    break;
                }
                if (available > 0) {
                    byte[] buf = new byte[available];
                    int read = terminal.input().read(buf);
                    if (read < 0) {
                        eof = true;
                    } else {
                        received.write(buf, 0, read);
                    }
                } else {
                    Thread.sleep(10);
                }
            }
            assertArrayEquals("bytes before EOF must be delivered",
                    new byte[] { 'h', 'i' }, received.toByteArray());
            assertEquals("owned pump must observe master EOF promptly",
                    -1, terminal.input().read());
        } finally {
            terminal.close();
        }
    }

    @Test
    public void testOwnedCloseDuringBlockedRead() throws Exception {
        ZeroAvailableStream master = new ZeroAvailableStream();
        ExternalTerminal terminal = new ExternalTerminal(
                "test", "test", master, new ByteArrayOutputStream(), true);
        Thread pump = awaitPumpThread(terminal);
        // Let the pump settle into the blocked read.
        Thread.sleep(300);
        terminal.close();
        pump.join(5000);
        assertFalse("owned close() must stop a pump blocked in read()",
                pump.isAlive());
        assertTrue("owned close() must close the owned stream",
                master.closeCalls > 0);
    }

    @Test
    public void testBorrowedModeIgnoresZeroAvailableStream() throws Exception {
        ZeroAvailableStream master = new ZeroAvailableStream();
        ExternalTerminal terminal = new ExternalTerminal(
                "test", "test", master, new ByteArrayOutputStream());
        try {
            master.stage(new byte[] { 'h', 'i' });
            // Far beyond the 10ms poll interval: nothing may arrive, ever.
            Thread.sleep(500);
            assertEquals("borrowed pump must not read zero-available streams",
                    0, terminal.input().available());
        } finally {
            terminal.close();
        }
    }

    @Test
    public void testSlavePipeReportsReadiness() throws Exception {
        PipedInputStream masterIn = new PipedInputStream(1024);
        PipedOutputStream feed = new PipedOutputStream(masterIn);
        ExternalTerminal terminal = new ExternalTerminal(
                "test", "test", masterIn, new ByteArrayOutputStream());
        try {
            feed.write('x');
            feed.flush();
            boolean ready = false;
            long deadline = System.currentTimeMillis() + 5000;
            while (!ready && System.currentTimeMillis() < deadline) {
                if (terminal.input().available() > 0) {
                    ready = true;
                } else {
                    Thread.sleep(10);
                }
            }
            assertTrue("in-repo terminal inputs must report readiness via available()",
                    ready);
            assertEquals('x', terminal.input().read());
        } finally {
            feed.close();
            terminal.close();
        }
    }

    @Test
    public void testWinExternalTerminalOwnedParity() throws Exception {
        ZeroAvailableStream master = new ZeroAvailableStream();
        WinExternalTerminal terminal = new WinExternalTerminal(
                "test", "test", master, new ByteArrayOutputStream(), true);
        try {
            master.stage(new byte[] { 'w' });
            boolean ready = false;
            long deadline = System.currentTimeMillis() + 10000;
            while (!ready && System.currentTimeMillis() < deadline) {
                if (terminal.input().available() > 0) {
                    ready = true;
                } else {
                    Thread.sleep(10);
                }
            }
            assertTrue("owned WinExternalTerminal must deliver zero-available input",
                    ready);
            assertEquals('w', terminal.input().read());
        } finally {
            terminal.close();
        }
    }
}
