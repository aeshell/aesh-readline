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
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.junit.Test;

/**
 * Tests for the ExternalTerminal pump lifecycle.
 * <p>
 * The pump must never block indefinitely: close() has to stop it even on
 * uninterruptible, unclosable streams like System.in, otherwise the JVM
 * cannot shut down (Surefire's 30s kill timeout, #269). A PipedInputStream
 * with nothing written reproduces the stuck System.in shape headlessly.
 */
public class ExternalTerminalPumpTest {

    private static Thread findPumpThread(ExternalTerminal terminal) {
        String name = terminal.toString() + " input pump thread";
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (name.equals(thread.getName())) {
                return thread;
            }
        }
        return null;
    }

    @Test
    public void testCloseStopsIdlePump() throws Exception {
        PipedInputStream masterIn = new PipedInputStream(1024);
        // Connect a writer so available() works (unconnected PipedInputStream
        // would throw on read), but never write — simulates System.in with
        // no console input. The pump polls available()==0, sleeps, repeats.
        PipedOutputStream feed = new PipedOutputStream(masterIn);
        ExternalTerminal terminal = new ExternalTerminal("test", "test", masterIn, new ByteArrayOutputStream());

        Thread pump = null;
        long startedWaiting = System.currentTimeMillis();
        while (pump == null && System.currentTimeMillis() - startedWaiting < 5000) {
            pump = findPumpThread(terminal);
            if (pump == null) {
                Thread.sleep(50);
            }
        }
        assertTrue("Pump thread should be running", pump != null && pump.isAlive());

        terminal.close();
        pump.join(5000);
        assertFalse("Pump thread must die after close(), even with no input ever arriving",
                pump.isAlive());
        feed.close();
    }

    @Test
    public void testPumpDeliversData() throws Exception {
        PipedInputStream masterIn = new PipedInputStream(1024);
        PipedOutputStream feed = new PipedOutputStream(masterIn);
        ExternalTerminal terminal = new ExternalTerminal("test", "test", masterIn, new ByteArrayOutputStream());
        try {
            byte[] sent = new byte[] { 'h', 'i' };
            feed.write(sent);
            feed.flush();

            byte[] received = new byte[2];
            int off = 0;
            long deadline = System.currentTimeMillis() + 5000;
            while (off < received.length && System.currentTimeMillis() < deadline) {
                int avail = terminal.input().available();
                if (avail > 0) {
                    int read = terminal.input().read(received, off, received.length - off);
                    if (read < 0) {
                        break;
                    }
                    off += read;
                } else {
                    Thread.sleep(10);
                }
            }
            assertEquals("Pump must deliver all bytes", received.length, off);
            assertArrayEquals(sent, received);
        } finally {
            feed.close();
            terminal.close();
        }
    }

    @Test
    public void testCloseSignalsEofToReader() throws Exception {
        // When the terminal is closed, the slave pipe closes, and readers
        // on terminal.input() get EOF (-1). This is how TerminalConnection
        // discovers shutdown — independent of the master input stream state.
        PipedInputStream masterIn = new PipedInputStream(1024);
        PipedOutputStream feed = new PipedOutputStream(masterIn);
        ExternalTerminal terminal = new ExternalTerminal("test", "test", masterIn, new ByteArrayOutputStream());
        try {
            terminal.close();
            int eof = terminal.input().read();
            assertEquals("close() must signal EOF to readers on terminal.input()", -1, eof);
        } finally {
            feed.close();
        }
    }
}
