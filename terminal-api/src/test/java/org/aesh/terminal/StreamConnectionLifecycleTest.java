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

import java.io.ByteArrayOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.Test;

/**
 * Lifecycle matrix adoption for {@link StreamConnection} (#355).
 * <p>
 * The borrowed pipe is never closed by the connection: reads block in
 * an uninterruptible wait, so the reader may outlive close and only
 * the promptness of close itself is asserted there.
 */
public class StreamConnectionLifecycleTest extends ConnectionLifecycleTestBase<StreamConnection> {

    private PipedOutputStream feed;
    private ByteArrayOutputStream written;
    private final List<int[]> recorded = new ArrayList<>();

    @Override
    protected StreamConnection newConnection() throws Exception {
        PipedInputStream masterIn = new PipedInputStream(4096);
        feed = new PipedOutputStream(masterIn);
        synchronized (recorded) {
            recorded.clear();
        }
        written = new ByteArrayOutputStream();
        StreamConnection connection = new StreamConnection(StandardCharsets.UTF_8,
                masterIn, written);
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
    protected void closeConnection(StreamConnection connection) {
        connection.close();
    }

    @Override
    protected void startReading(StreamConnection connection) {
        connection.openBlocking();
    }

    @Override
    protected boolean readerStopsOnClose() {
        // Pipe reads wait uninterruptibly; the daemon reader outlives
        // close until its owner releases the stream. Close itself must
        // still return promptly without touching the borrowed pipe.
        return false;
    }

    @Override
    protected void feedInput(StreamConnection connection, byte[] data) throws Exception {
        feed.write(data);
        feed.flush();
    }

    @Override
    protected byte[] drainDelivered(StreamConnection connection) {
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
    protected void writeOutput(StreamConnection connection, String text) throws Exception {
        connection.write(text);
    }

    @Override
    protected byte[] drainOutput(StreamConnection connection) {
        synchronized (written) {
            byte[] bytes = written.toByteArray();
            written.reset();
            return bytes;
        }
    }

    @Test
    public void testBorrowedPipeUntouchedByClose() throws Exception {
        StreamConnection connection = newConnection();
        try {
            PipedOutputStream liveFeed = feed;
            closeConnection(connection);
            // The borrowed feed must still accept bytes: close() never
            // closes caller-owned streams.
            liveFeed.write('q');
            liveFeed.flush();
        } finally {
            closeConnection(connection);
            feed.close();
        }
    }

}
