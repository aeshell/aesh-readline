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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.detect.ImageProtocol;
import org.aesh.terminal.detect.TerminalDetection;
import org.junit.Test;

/**
 * Setup validation for stream-backed connections (#381).
 * <p>
 * A held-open silent pipe is the adversarial layout: no data, no EOF,
 * writer alive. The live DA1 fallback must degrade to NONE by waiting
 * out its query timeout instead of hanging; an attached-but-unknown
 * source must decline without any query; close must return promptly
 * while the reader is blocked; and repeated open/close cycles must not
 * accumulate reader workers. The lower time bound pins the waited-out
 * timeout, so a skipped wait cannot pass silently; the upper bounds are
 * generous hang guards, not latency claims.
 */
public class StreamConnectionSetupTest {

    private static final String READER_NAME = "aesh-stream-reader";

    private static StreamConnection remote(final Device device, InputStream input) {
        return new StreamConnection(StandardCharsets.UTF_8, input, new ByteArrayOutputStream()) {
            @Override
            public Device device() {
                return device;
            }

            @Override
            public boolean supportsAnsi() {
                return true;
            }
        };
    }

    private static final class CountingFeatures extends TerminalFeatures {
        int imageQueries;

        CountingFeatures(Connection connection) {
            super(connection);
        }

        @Override
        public ImageProtocol queryImageProtocol(long timeoutMs) {
            imageQueries++;
            return super.queryImageProtocol(timeoutMs);
        }
    }

    /**
     * A readable stream that blocks until interrupted, then fails like
     * a pipe whose blocked read observes interruption. Nothing else can
     * release it: no data ever arrives, EOF never occurs, and the owner
     * never closes it. Only the close-time interrupt ends the block.
     */
    private static final class HeldReader extends InputStream {
        final CountDownLatch readEntered = new CountDownLatch(1);

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int count = read(one, 0, 1);
            return count < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            readEntered.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException();
            }
            return -1;
        }

        void awaitBlocked() throws Exception {
            assertTrue("reader must block in its read",
                    readEntered.await(10, TimeUnit.SECONDS));
        }
    }

    @Test(timeout = 60000)
    public void testHeldPipeQueryDegradesWithoutHanging() throws Exception {
        int baseline = TestBase.countThreadsByPrefix(READER_NAME);
        PipedOutputStream writer = new PipedOutputStream();
        PipedInputStream input = new PipedInputStream(writer, 4096);
        StreamConnection connection = remote(new BaseDevice("linux"), input);
        CountingFeatures features = new CountingFeatures(connection);
        try {
            connection.openNonBlocking();
            assertTrue("pump must be blocked in its read", connection.reading());
            long start = System.nanoTime();
            assertEquals(ImageProtocol.NONE, features.getImageProtocol());
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue("degraded fallback must wait out the query timeout, took "
                    + elapsedMs + "ms", elapsedMs >= 400);
            assertTrue("degraded fallback must stay near the query timeout, took "
                    + elapsedMs + "ms", elapsedMs < 10000);
            assertEquals("the fallback ran exactly once", 1, features.imageQueries);
        } finally {
            connection.close();
            writer.close();
        }
        TestBase.awaitThreadsByPrefix(READER_NAME, baseline, 10000);
    }

    @Test(timeout = 60000)
    public void testAttachedUnknownSuppressesImmediately() throws Exception {
        int baseline = TestBase.countThreadsByPrefix(READER_NAME);
        PipedOutputStream writer = new PipedOutputStream();
        PipedInputStream input = new PipedInputStream(writer, 4096);
        StreamConnection connection = remote(new BaseDevice("linux"), input);
        CountingFeatures features = new CountingFeatures(connection);
        try {
            connection.openNonBlocking();
            connection.attachDetection(TerminalDetection.managed(null));
            long start = System.nanoTime();
            assertEquals(ImageProtocol.NONE, features.getImageProtocol());
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue("suppressed fallback must not wait out a timeout, took "
                    + elapsedMs + "ms", elapsedMs < 10000);
            assertEquals("suppressed fallback performs zero queries",
                    0, features.imageQueries);
        } finally {
            connection.close();
            writer.close();
        }
        TestBase.awaitThreadsByPrefix(READER_NAME, baseline, 10000);
    }

    @Test(timeout = 120000)
    public void testOpenCloseCyclesAccumulateNoReaders() throws Exception {
        int baseline = TestBase.countThreadsByPrefix(READER_NAME);
        for (int i = 0; i < 10; i++) {
            HeldReader held = new HeldReader();
            StreamConnection connection = remote(new BaseDevice("linux"), held);
            connection.openNonBlocking();
            held.awaitBlocked();
            connection.close();
        }
        TestBase.awaitThreadsByPrefix(READER_NAME, baseline, 15000);
    }

    @Test(timeout = 60000)
    public void testCloseReturnsPromptlyOnHeldReader() throws Exception {
        int baseline = TestBase.countThreadsByPrefix(READER_NAME);
        HeldReader held = new HeldReader();
        StreamConnection connection = remote(new BaseDevice("linux"), held);
        try {
            connection.openNonBlocking();
            held.awaitBlocked();
            assertTrue(connection.reading());
            long start = System.nanoTime();
            connection.close();
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue("close must not join a blocked reader, took " + elapsedMs + "ms",
                    elapsedMs < 5000);
            assertFalse(connection.reading());
        } finally {
            connection.close();
        }
        TestBase.awaitThreadsByPrefix(READER_NAME, baseline, 10000);
    }
}
