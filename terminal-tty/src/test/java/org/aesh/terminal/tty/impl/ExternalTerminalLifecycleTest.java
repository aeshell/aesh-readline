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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;

import org.aesh.terminal.ConnectionLifecycleTestBase;
import org.junit.Test;

/**
 * Lifecycle matrix adoption for {@link ExternalTerminal} (#355).
 * <p>
 * The borrowed pipe is polled, never parked in: close interrupts the
 * idle sleep and the pump always stops. All headless.
 */
public class ExternalTerminalLifecycleTest extends ConnectionLifecycleTestBase<ExternalTerminal> {

    private PipedOutputStream feed;
    private ByteArrayOutputStream written;

    @Override
    protected ExternalTerminal newConnection() throws Exception {
        PipedInputStream masterIn = new PipedInputStream(4096);
        feed = new PipedOutputStream(masterIn);
        written = new ByteArrayOutputStream();
        return new ExternalTerminal("test", "test", masterIn, written);
    }

    @Override
    protected void closeConnection(ExternalTerminal connection) throws Exception {
        connection.close();
    }

    @Override
    protected void startReading(ExternalTerminal connection) {
        // The pump starts in the constructor; nothing to do.
    }

    @Override
    protected void feedInput(ExternalTerminal connection, byte[] data) throws Exception {
        feed.write(data);
        feed.flush();
    }

    @Override
    protected byte[] drainDelivered(ExternalTerminal connection) throws Exception {
        InputStream in = connection.input();
        ByteArrayOutputStream delivered = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (in.available() > 0) {
            int n = in.read(buf);
            if (n < 0) {
                break;
            }
            delivered.write(buf, 0, n);
        }
        return delivered.toByteArray();
    }

    @Override
    protected void writeOutput(ExternalTerminal connection, String text) throws Exception {
        connection.output().write(text.getBytes(StandardCharsets.UTF_8));
        connection.output().flush();
    }

    @Override
    protected byte[] drainOutput(ExternalTerminal connection) {
        synchronized (written) {
            byte[] bytes = written.toByteArray();
            written.reset();
            return bytes;
        }
    }

    @Test
    public void testPumpStopsOnClose() throws Exception {
        ExternalTerminal terminal = newConnection();
        try {
            // Thread registration may lag thread start (notably in
            // native images): poll boundedly instead of scanning once.
            Thread pump = null;
            long deadline = System.currentTimeMillis() + 5000;
            while (pump == null && System.currentTimeMillis() < deadline) {
                pump = findPumpThread(terminal);
                if (pump == null) {
                    Thread.sleep(50);
                }
            }
            assertTrue("pump must run while open", pump != null && pump.isAlive());
            closeConnection(terminal);
            pump.join(10000);
            assertFalse("pump must stop after close", pump.isAlive());
        } finally {
            closeConnection(terminal);
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

}
