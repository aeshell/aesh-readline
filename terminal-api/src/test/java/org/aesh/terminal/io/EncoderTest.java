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

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
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

    @Test
    public void testSupplementaryThenBmpAtInitialBoundary() throws Exception {
        // 128 emoji fill the initial 256-char buffer exactly; the
        // trailing ASCII then wrote past its end.
        int[] input = new int[256];
        for (int i = 0; i < 128; i++) {
            input[i] = 0x1F600;
        }
        for (int i = 128; i < 256; i++) {
            input[i] = 'A';
        }
        assertEncodesLikeJdk(Charset.forName("UTF-16LE"), input);
    }

    @Test
    public void testSupplementaryThenBmpAtGrownBoundary() throws Exception {
        // 225 emoji fill the grown 450-char buffer exactly (300
        // code points grow 256 to 300 + 150); the trailing ASCII
        // then wrote past its end without tripping the pair-only
        // growth check.
        int[] input = new int[300];
        for (int i = 0; i < 225; i++) {
            input[i] = 0x1F600;
        }
        for (int i = 225; i < 300; i++) {
            input[i] = 'A';
        }
        assertEncodesLikeJdk(Charset.forName("UTF-16LE"), input);
    }

    @Test
    public void testPureShapesAtBoundaries() throws Exception {
        int[] pairs = new int[128];
        for (int i = 0; i < pairs.length; i++) {
            pairs[i] = 0x1F600;
        }
        assertEncodesLikeJdk(Charset.forName("UTF-16LE"), pairs);

        int[] bmp = new int[256];
        for (int i = 0; i < bmp.length; i++) {
            bmp[i] = 'A';
        }
        assertEncodesLikeJdk(Charset.forName("UTF-16LE"), bmp);
    }

    @Test
    public void testGeneralPathMatchesJdkAcrossCharsets() throws Exception {
        int[] mixed = "A\u00E9\u4E2D\u00BB".codePoints().toArray();
        Charset[] charsets = {
                Charset.forName("windows-1252"),
                Charset.forName("UTF-16LE"),
                Charset.forName("Shift_JIS") };
        for (Charset charset : charsets) {
            assertEncodesLikeJdk(charset, mixed);
        }
    }

    /**
     * Encode code points through the general (non-UTF-8) path and
     * require byte equality with the JDK's own conversion of the
     * same input. Both sides replace unmappable characters.
     *
     * @param charset a non-UTF-8 charset selecting the general path
     * @param input valid code points to encode
     */
    private static void assertEncodesLikeJdk(Charset charset, int[] input) {
        final List<byte[]> captured = new ArrayList<>();
        Encoder encoder = new Encoder(charset, new ByteWriter() {
            @Override
            public void write(byte[] buf, int off, int len) {
                byte[] copy = new byte[len];
                System.arraycopy(buf, off, copy, 0, len);
                captured.add(copy);
            }
        });
        encoder.accept(input);

        int total = 0;
        for (byte[] part : captured) {
            total += part.length;
        }
        byte[] actual = new byte[total];
        int pos = 0;
        for (byte[] part : captured) {
            System.arraycopy(part, 0, actual, pos, part.length);
            pos += part.length;
        }
        assertArrayEquals(new String(input, 0, input.length).getBytes(charset), actual);
    }

    private static final char LONE_HIGH = (char) 0xD800;
    private static final char LONE_LOW = (char) 0xDC00;
    private static final String EMOJI = new String(new int[] { 0x1F600 }, 0, 1);

    @Test
    public void testUnpairedSurrogatesEmitReplacement() throws Exception {
        assertUtf8StringEncodes("A" + LONE_HIGH + "X", "A?X");
        assertUtf8StringEncodes("A" + LONE_HIGH, "A?");
        assertUtf8StringEncodes(LONE_LOW + "A", "?A");
        assertUtf8StringEncodes("A" + LONE_LOW, "A?");
        assertUtf8StringEncodes("" + LONE_HIGH + LONE_HIGH, "??");
        assertUtf8StringEncodes("" + LONE_LOW + LONE_LOW, "??");
    }

    @Test
    public void testValidPairBesideMalformedUnits() throws Exception {
        assertUtf8StringEncodes("A" + LONE_HIGH + EMOJI + LONE_LOW + "B",
                "A?" + EMOJI + "?B");
    }

    @Test
    public void testInvalidCodePointsEmitReplacement() throws Exception {
        assertUtf8CodePointsEncode(new int[] { 0xD800 }, new int[] { '?' });
        assertUtf8CodePointsEncode(new int[] { 0xDFFF }, new int[] { '?' });
        assertUtf8CodePointsEncode(new int[] { -1 }, new int[] { '?' });
        assertUtf8CodePointsEncode(new int[] { 0x110000 }, new int[] { '?' });
        assertUtf8CodePointsEncode(new int[] { 'A', 0xD800, 0x1F600, -5, 'B' },
                new int[] { 'A', '?', 0x1F600, '?', 'B' });
    }

    @Test
    public void testGeneralPathReplacesSurrogatesLikeJdk() throws Exception {
        assertEncodesLikeJdk(Charset.forName("windows-1252"), new int[] { 'A', 0xD800, 'B' });
        assertEncodesLikeJdk(Charset.forName("windows-1252"), new int[] { 'A', 0xDC00, 'B' });
        assertEncodesLikeJdk(Charset.forName("UTF-16LE"), new int[] { 'A', 0xD800, 'B' });
    }

    /**
     * Encode a string through the UTF-8 path and require byte equality
     * with the JDK's conversion plus strict decodability: the output
     * must be valid UTF-8, not just the expected bytes.
     *
     * @param input the string to encode, possibly with lone surrogates
     * @param expected the string the output must strictly decode to
     */
    private static void assertUtf8StringEncodes(String input, String expected) throws Exception {
        byte[] actual = encodeUtf8(input, false);
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), actual);
        assertStrictDecodesTo(expected, actual);
        // The int[] path must agree with the String path on the same shapes.
        byte[] viaCodePoints = encodeUtf8(input, true);
        assertArrayEquals("String and int[] paths disagree for " + readable(input),
                actual, viaCodePoints);
    }

    /**
     * Encode raw code points through the UTF-8 path, covering values
     * no String can hold (negatives, beyond U+10FFFF).
     *
     * @param input the code points to encode
     * @param expectedCodePoints the code points the output must strictly decode to
     */
    private static void assertUtf8CodePointsEncode(int[] input, int[] expectedCodePoints)
            throws Exception {
        byte[] actual = encodeUtf8CodePoints(input);
        String expected = new String(expectedCodePoints, 0, expectedCodePoints.length);
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), actual);
        assertStrictDecodesTo(expected, actual);
    }

    private static void assertStrictDecodesTo(String expected, byte[] actual) throws Exception {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        assertEquals(expected, decoder.decode(ByteBuffer.wrap(actual)).toString());
    }

    private static byte[] encodeUtf8(String text, boolean asCodePoints) {
        if (asCodePoints) {
            return encodeUtf8CodePoints(text.codePoints().toArray());
        }
        final List<byte[]> captured = new ArrayList<>();
        Encoder encoder = new Encoder(StandardCharsets.UTF_8, new ByteWriter() {
            @Override
            public void write(byte[] buf, int off, int len) {
                byte[] copy = new byte[len];
                System.arraycopy(buf, off, copy, 0, len);
                captured.add(copy);
            }
        });
        encoder.accept(text);
        return flatten(captured);
    }

    private static byte[] encodeUtf8CodePoints(int[] input) {
        final List<byte[]> captured = new ArrayList<>();
        Encoder encoder = new Encoder(StandardCharsets.UTF_8, new ByteWriter() {
            @Override
            public void write(byte[] buf, int off, int len) {
                byte[] copy = new byte[len];
                System.arraycopy(buf, off, copy, 0, len);
                captured.add(copy);
            }
        });
        encoder.accept(input);
        return flatten(captured);
    }

    private static byte[] flatten(List<byte[]> parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, pos, part.length);
            pos += part.length;
        }
        return out;
    }

    private static String readable(String text) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append("U+");
            sb.append(Integer.toHexString(text.charAt(i)).toUpperCase());
        }
        return sb.toString();
    }
}
