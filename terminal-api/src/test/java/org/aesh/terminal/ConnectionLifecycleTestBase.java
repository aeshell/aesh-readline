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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assume;
import org.junit.Test;

/**
 * Shared lifecycle/ownership matrix for input transports (#355).
 * <p>
 * Every transport honors the {@code Connection} ownership contract with
 * its own mechanism (flags, interrupts, stream closure, bounded waits):
 * these legs pin the common shape while overridable hooks carry the
 * per-transport definitions. A hook that cannot hold for an
 * implementation opts out explicitly rather than weakening the leg.
 * <p>
 * Adopt by extending with the transport type and implementing the
 * primitives; all I/O stays headless. Markers are ASCII so byte and
 * code-point shapes compare exactly.
 *
 * @param <C> the transport type under test
 */
public abstract class ConnectionLifecycleTestBase<C> {

    /** Bound (ms) for close() itself to return. */
    private static final long CLOSE_PROMPT_MS = 5000;
    /** Bound (ms) for a stopped reader thread to join. */
    private static final long JOIN_TIMEOUT_MS = 10000;
    /** Settle window (ms) for asserting that nothing further arrives. */
    private static final long SETTLE_MS = 300;
    /** Bound (ms) for expected deliveries to arrive. */
    private static final long DELIVERY_TIMEOUT_MS = 5000;

    /**
     * Create a fresh transport whose input never delivers unless fed.
     *
     * @return the new transport
     * @throws Exception on setup failure
     */
    protected abstract C newConnection() throws Exception;

    /**
     * Close the transport.
     *
     * @param connection the transport
     * @throws Exception on failure
     */
    protected abstract void closeConnection(C connection) throws Exception;

    /**
     * Start the transport's reader. Runs on a test driver thread, so
     * blocking readers are safe: pump auto-starters no-op here.
     *
     * @param connection the transport
     * @throws Exception on failure
     */
    protected abstract void startReading(C connection) throws Exception;

    /**
     * Feed bytes into the transport's input.
     *
     * @param connection the transport
     * @param data the bytes to deliver
     * @throws Exception on failure
     */
    protected abstract void feedInput(C connection, byte[] data) throws Exception;

    /**
     * Drain bytes delivered so far, clearing any recording.
     *
     * @param connection the transport
     * @return the delivered bytes since the last drain
     * @throws Exception on failure
     */
    protected abstract byte[] drainDelivered(C connection) throws Exception;

    /**
     * Write application output through the transport.
     *
     * @param connection the transport
     * @param text the text to write
     * @throws Exception on failure
     */
    protected abstract void writeOutput(C connection, String text) throws Exception;

    /**
     * Drain bytes written through the transport since the last drain.
     *
     * @param connection the transport
     * @return the written bytes
     * @throws Exception on failure
     */
    protected abstract byte[] drainOutput(C connection) throws Exception;

    /**
     * Whether the reader thread stops on close() with never-fed input.
     * Transports whose reader can outlive close on uninterruptible
     * borrowed streams return false; their close must still be prompt.
     *
     * @return true when the reader is guaranteed stopped after close
     */
    protected boolean readerStopsOnClose() {
        return true;
    }

    /**
     * Assert raw-sink bypass behavior. Skipped unless overridden:
     * only transports with region-routed output implement this leg.
     *
     * @param connection the transport
     * @throws Exception on failure
     */
    protected void assertRawBypass(C connection) throws Exception {
        Assume.assumeTrue("no raw sink on this transport", false);
    }

    @Test
    public void testCloseIsIdempotentAndPrompt() throws Exception {
        C connection = newConnection();
        try {
            long start = System.currentTimeMillis();
            closeConnection(connection);
            closeConnection(connection);
            assertTrue("close() must stay prompt, took "
                    + (System.currentTimeMillis() - start) + "ms",
                    System.currentTimeMillis() - start < CLOSE_PROMPT_MS);
        } finally {
            closeConnection(connection);
        }
    }

    @Test
    public void testCloseDuringBlockedReadBounded() throws Exception {
        final C connection = newConnection();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    startReading(connection);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        }, "lifecycle-reader");
        reader.setDaemon(true);
        try {
            reader.start();
            Thread.sleep(200);
            long start = System.currentTimeMillis();
            closeConnection(connection);
            assertTrue("close() during a blocked read must stay prompt, took "
                    + (System.currentTimeMillis() - start) + "ms",
                    System.currentTimeMillis() - start < CLOSE_PROMPT_MS);
            if (readerStopsOnClose()) {
                reader.join(JOIN_TIMEOUT_MS);
                assertFalse("reader must stop after close", reader.isAlive());
            }
            assertNull("reader must not fail: " + failure.get(), failure.get());
        } finally {
            closeConnection(connection);
        }
    }

    @Test
    public void testPostCloseDeliversNothing() throws Exception {
        C connection = newConnection();
        Thread reader = startReaderThread(connection);
        try {
            byte[] before = "before;".getBytes(StandardCharsets.US_ASCII);
            feedInput(connection, before);
            assertDelivered(connection, before);
            closeConnection(connection);
            feedInput(connection, "after;".getBytes(StandardCharsets.US_ASCII));
            Thread.sleep(SETTLE_MS);
            assertEquals("nothing delivered after close",
                    "", new String(drainDelivered(connection), StandardCharsets.US_ASCII));
        } finally {
            closeConnection(connection);
        }
    }

    @Test
    public void testConcurrentWritesCompleteExactly() throws Exception {
        final C connection = newConnection();
        try {
            final int writers = 4;
            final int messages = 25;
            Thread[] threads = new Thread[writers];
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            for (int w = 0; w < writers; w++) {
                final String marker = "w" + w + ";";
                threads[w] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            for (int i = 0; i < messages; i++) {
                                writeOutput(connection, marker);
                            }
                        } catch (Throwable t) {
                            failure.compareAndSet(null, t);
                        }
                    }
                }, "lifecycle-writer");
                threads[w].setDaemon(true);
            }
            for (Thread thread : threads) {
                thread.start();
            }
            for (Thread thread : threads) {
                thread.join(JOIN_TIMEOUT_MS);
                assertFalse("writer must finish", thread.isAlive());
            }
            assertNull("no writer may fail: " + failure.get(), failure.get());
            String delivered = new String(drainAllOutput(connection, writers * messages * 3),
                    StandardCharsets.US_ASCII);
            for (int w = 0; w < writers; w++) {
                assertEquals("every payload must arrive exactly once",
                        messages, countOccurrences(delivered, "w" + w + ";"));
            }
        } finally {
            closeConnection(connection);
        }
    }

    @Test
    public void testRawSinkBypass() throws Exception {
        C connection = newConnection();
        try {
            assertRawBypass(connection);
        } finally {
            closeConnection(connection);
        }
    }

    private Thread startReaderThread(final C connection) {
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    startReading(connection);
                } catch (Throwable ignored) {
                }
            }
        }, "lifecycle-driver");
        reader.setDaemon(true);
        reader.start();
        return reader;
    }

    private void assertDelivered(C connection, byte[] expected) throws Exception {
        long deadline = System.currentTimeMillis() + DELIVERY_TIMEOUT_MS;
        StringBuilder delivered = new StringBuilder();
        String want = new String(expected, StandardCharsets.US_ASCII);
        while (System.currentTimeMillis() < deadline) {
            delivered.append(new String(drainDelivered(connection), StandardCharsets.US_ASCII));
            if (delivered.toString().contains(want)) {
                return;
            }
            Thread.sleep(10);
        }
        assertEquals("expected delivery", want, delivered.toString());
    }

    private byte[] drainAllOutput(C connection, int expected) throws Exception {
        ByteArrayOutputStream collected = new ByteArrayOutputStream();
        long deadline = System.currentTimeMillis() + DELIVERY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline && collected.size() < expected) {
            byte[] chunk = drainOutput(connection);
            if (chunk.length > 0) {
                collected.write(chunk, 0, chunk.length);
            } else {
                Thread.sleep(10);
            }
        }
        return collected.toByteArray();
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }
}
