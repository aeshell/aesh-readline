/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
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
package org.aesh.terminal.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.aesh.terminal.tty.TtyOutputMode;
import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class EncoderTest {

    public void decodeEndcode(String incoming, String[] expected) {
        Charset charset = StandardCharsets.UTF_8;
        //ArrayList<String> decodeResult = new ArrayList<>();
        final ArrayList<int[]> result = new ArrayList<>();
        Decoder decoder = new Decoder(charset, result::add);

        final byte[] output = new byte[4];
        Encoder encoder = new Encoder(charset, new ByteWriter() {
            @Override
            public void write(byte[] buf, int off, int len) {
                System.arraycopy(buf, off, output, 0, len);
            }
        });

        decoder.write(incoming.getBytes());

        for (int i = 0; i < expected.length; i++) {
            encoder.accept(result.get(i));
            for (int j = 0; j < expected[i].length(); j++)
                assertEquals(expected[i].getBytes()[j], output[j]);
        }
    }

    @Test
    public void testInputs() {
        decodeEndcode("foo", new String[] { "foo" });
        decodeEndcode("foo bar!!??", new String[] { "foo ", "bar!", "!??" });
        decodeEndcode("\r", new String[] { "\r" });
    }

    @Test
    public void testConcurrentStringWritesDoNotInterleave() throws Exception {
        runTwoWriterRace(StandardCharsets.UTF_8, "AAAA", "BBBB", false, false);
    }

    @Test
    public void testConcurrentCodePointWritesDoNotInterleave() throws Exception {
        // Non-breaking space (U+00A0) forces the multi-byte UTF-8 path.
        runTwoWriterRace(StandardCharsets.UTF_8, "A AA", "B BB", true, false);
    }

    @Test
    public void testConcurrentNonUtf8WritesDoNotInterleave() throws Exception {
        runTwoWriterRace(Charset.forName("windows-1252"), "AéAA", "BéBB", true, false);
    }

    @Test
    public void testConcurrentWritesThroughTtyOutputMode() throws Exception {
        // TtyOutputMode(Encoder) is the ssh/http connection output
        // topology; newlines also cross the substitution path.
        runTwoWriterRace(StandardCharsets.UTF_8, "AA\nAA", "BB\nBB", true, true);
    }

    /**
     * Hold writer A inside the ByteWriter while writer B encodes, then
     * release A. Without encode-and-consume serialization B overwrites
     * the shared buffers mid-slice; with it B blocks on the monitor
     * and both payloads land byte-exact, in order. All waits are
     * bounded so a regression fails instead of hanging the suite.
     *
     * @param charset the encoder charset (selects the UTF-8 or general path)
     * @param first payload for the writer that holds the slice
     * @param second payload for the concurrent writer
     * @param asCodePoints encode via accept(int[]) when true, accept(String) otherwise
     * @param throughMode route encodes through TtyOutputMode when true
     */
    private static void runTwoWriterRace(Charset charset, final String first,
            final String second, final boolean asCodePoints, boolean throughMode)
            throws Exception {
        final byte[] expectedFirst = expectedBytes(charset, first, throughMode);
        final byte[] expectedSecond = expectedBytes(charset, second, throughMode);

        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicInteger calls = new AtomicInteger();
        final List<byte[]> captured = Collections.synchronizedList(new ArrayList<byte[]>());
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final Encoder encoder = new Encoder(charset, new ByteWriter() {
            @Override
            public void write(byte[] buf, int off, int len) {
                if (calls.getAndIncrement() == 0) {
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            failure.compareAndSet(null, new AssertionError(
                                    "release latch timed out, suite would hang"));
                            return;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                byte[] copy = new byte[len];
                System.arraycopy(buf, off, copy, 0, len);
                captured.add(copy);
            }
        });
        final Consumer<int[]> target = throughMode ? new TtyOutputMode(encoder) : encoder;

        Thread writerA = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    encode(target, encoder, first, asCodePoints, throughMode);
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            }
        });
        writerA.setDaemon(true);
        writerA.start();

        assertTrue("first writer never entered the ByteWriter",
                entered.await(5, TimeUnit.SECONDS));

        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setDaemon(true);
                return thread;
            }
        });
        try {
            Future<?> secondWrite = executor.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        encode(target, encoder, second, asCodePoints, throughMode);
                    } catch (Throwable t) {
                        failure.compareAndSet(null, t);
                        throw new RuntimeException(t);
                    }
                }
            });
            try {
                secondWrite.get(500, TimeUnit.MILLISECONDS);
                fail("second encode completed while the first slice was held");
            } catch (TimeoutException expected) {
                // The monitor is held, so the second writer blocks. This is the fix.
            } catch (ExecutionException e) {
                throw new AssertionError(e.getCause());
            }
            release.countDown();
            try {
                secondWrite.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                throw new AssertionError(e.getCause());
            }
            writerA.join(5000);
            assertTrue("first writer did not finish", !writerA.isAlive());
            if (failure.get() != null) {
                throw new AssertionError(failure.get());
            }
            assertEquals(2, captured.size());
            assertArrayEquals(expectedFirst, captured.get(0));
            assertArrayEquals(expectedSecond, captured.get(1));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static void encode(Consumer<int[]> target, Encoder encoder,
            String text, boolean asCodePoints, boolean throughMode) {
        if (asCodePoints || throughMode) {
            target.accept(text.codePoints().toArray());
        } else {
            encoder.accept(text);
        }
    }

    private static byte[] expectedBytes(Charset charset, String text, boolean throughMode) {
        if (throughMode) {
            return text.replace("\n", "\r\n").getBytes(charset);
        }
        return text.getBytes(charset);
    }
}
