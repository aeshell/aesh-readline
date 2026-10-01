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
package org.aesh.terminal.tty;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.aesh.terminal.ConnectionLifecycleTestBase;

/**
 * Lifecycle matrix adoption for {@link TerminalConnection} over the
 * legacy poll-based read loop (#355).
 * <p>
 * The loop polls instead of blocking, so close() only flips flags and
 * the reader always stops; the borrowed stream is never touched. The
 * raw leg pins the split-screen bypass through a current region.
 */
public class TerminalConnectionLifecycleTest
        extends ConnectionLifecycleTestBase<TerminalConnection> {

    private TerminalConnectionLegacyLoopTest.StagedStream staged;
    private ByteArrayOutputStream captured;
    private final List<int[]> recorded = new ArrayList<>();

    @Override
    protected TerminalConnection newConnection() throws Exception {
        staged = new TerminalConnectionLegacyLoopTest.StagedStream();
        captured = new ByteArrayOutputStream();
        final ByteArrayOutputStream shared = captured;
        TerminalConnectionLegacyLoopTest.StubTerminal stub = new TerminalConnectionLegacyLoopTest.StubTerminal(staged) {
            @Override
            public OutputStream output() {
                return shared;
            }
        };
        synchronized (recorded) {
            recorded.clear();
        }
        TerminalConnection connection = new TerminalConnection(stub);
        connection.setStdinHandler(new Consumer<int[]>() {
            @Override
            public void accept(int[] ints) {
                synchronized (recorded) {
                    recorded.add(ints.clone());
                }
            }
        });
        return connection;
    }

    @Override
    protected void closeConnection(TerminalConnection connection) {
        connection.close();
    }

    @Override
    protected void startReading(TerminalConnection connection) {
        connection.openBlocking();
    }

    @Override
    protected void feedInput(TerminalConnection connection, byte[] data) {
        byte[] previous = staged.staged;
        byte[] combined = new byte[previous.length + data.length];
        System.arraycopy(previous, 0, combined, 0, previous.length);
        System.arraycopy(data, 0, combined, previous.length, data.length);
        staged.staged = combined;
    }

    @Override
    protected byte[] drainDelivered(TerminalConnection connection) {
        StringBuilder sb = new StringBuilder();
        synchronized (recorded) {
            for (int[] chunk : recorded) {
                for (int cp : chunk) {
                    sb.appendCodePoint(cp);
                }
            }
            recorded.clear();
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void writeOutput(TerminalConnection connection, String text) {
        connection.write(text);
    }

    @Override
    protected byte[] drainOutput(TerminalConnection connection) {
        synchronized (captured) {
            byte[] bytes = captured.toByteArray();
            captured.reset();
            return bytes;
        }
    }

    @Override
    protected void assertRawBypass(TerminalConnection connection) throws Exception {
        org.aesh.terminal.tty.SplitScreen split = connection.splitScreen(0.5);
        try {
            connection.setCurrentRegion(split.bottomRegion());
            int mark;
            synchronized (captured) {
                mark = captured.size();
            }
            connection.writeRaw("x");
            byte[] delta;
            synchronized (captured) {
                byte[] bytes = captured.toByteArray();
                delta = java.util.Arrays.copyOfRange(bytes, mark, bytes.length);
            }
            assertEquals("raw sink must bypass region routing exactly once",
                    "x", new String(delta, StandardCharsets.UTF_8));
        } finally {
            connection.setCurrentRegion(null);
        }
    }
}
