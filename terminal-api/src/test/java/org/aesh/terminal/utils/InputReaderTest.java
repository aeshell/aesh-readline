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
package org.aesh.terminal.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * Tests for {@link InputReader}.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class InputReaderTest {

    @Test
    public void testBasicRead() throws Exception {
        InputReader reader = new InputReader();
        reader.push("Hello");

        char[] buf = new char[16];
        int n = reader.read(buf, 0, buf.length);
        assertEquals(5, n);
        assertEquals("Hello", new String(buf, 0, n));
        reader.close();
    }

    @Test
    public void testReadWithTimeout() throws Exception {
        InputReader reader = new InputReader();
        reader.push('A');
        reader.push('B');

        assertEquals('A', reader.read(100));
        assertEquals('B', reader.read(100));

        reader.close();
    }

    @Test
    public void testReadWithTimeoutExpires() throws Exception {
        InputReader reader = new InputReader();

        assertEquals(-2, reader.read(50));

        reader.close();
    }

    @Test
    public void testReadCodePoint() throws Exception {
        InputReader reader = new InputReader();
        reader.push('A');
        reader.push('B');

        OptionalInt cp1 = reader.readCodePoint(1, TimeUnit.SECONDS);
        assertTrue(cp1.isPresent());
        assertEquals('A', cp1.getAsInt());

        OptionalInt cp2 = reader.readCodePoint(1, TimeUnit.SECONDS);
        assertTrue(cp2.isPresent());
        assertEquals('B', cp2.getAsInt());

        reader.close();
    }

    @Test
    public void testReadCodePointTimeout() throws Exception {
        InputReader reader = new InputReader();

        OptionalInt result = reader.readCodePoint(50, TimeUnit.MILLISECONDS);
        assertFalse(result.isPresent());

        reader.close();
    }

    @Test
    public void testReadCodePointSupplementary() throws Exception {
        InputReader reader = new InputReader();
        int emoji = 0x1F600;
        reader.push(emoji);

        OptionalInt cp = reader.readCodePoint(1, TimeUnit.SECONDS);
        assertTrue(cp.isPresent());
        assertEquals(emoji, cp.getAsInt());

        reader.close();
    }

    @Test
    public void testBlockingRead() throws Exception {
        InputReader reader = new InputReader();
        char[] buf = new char[16];

        // Start a thread that will push data after a short delay
        CountDownLatch started = new CountDownLatch(1);
        AtomicInteger readCount = new AtomicInteger(-1);

        Thread readThread = new Thread(() -> {
            started.countDown();
            try {
                readCount.set(reader.read(buf, 0, buf.length));
            } catch (IOException e) {
                // test will fail via readCount check
            }
        });
        readThread.start();
        started.await(1, TimeUnit.SECONDS);

        // Give the read thread time to block
        Thread.sleep(50);

        // Push data — this should unblock the read
        reader.push('X');
        reader.push('Y');
        readThread.join(2000);

        assertEquals(2, readCount.get());
        assertEquals('X', buf[0]);
        assertEquals('Y', buf[1]);
        reader.close();
    }

    @Test
    public void testSupplementaryCodePoints() throws Exception {
        InputReader reader = new InputReader();
        // U+1F600 (Grinning Face emoji) and U+20000 (CJK Unified Ideographs Extension B)
        int emoji = 0x1F600;
        int cjk = 0x20000;
        reader.push(emoji);
        reader.push(cjk);

        char[] buf = new char[16];
        int n = reader.read(buf, 0, buf.length);
        // Each supplementary code point produces 2 chars (surrogate pair)
        assertEquals(4, n);

        // Verify the surrogate pairs
        char[] expectedEmoji = Character.toChars(emoji);
        char[] expectedCjk = Character.toChars(cjk);
        assertEquals(expectedEmoji[0], buf[0]);
        assertEquals(expectedEmoji[1], buf[1]);
        assertEquals(expectedCjk[0], buf[2]);
        assertEquals(expectedCjk[1], buf[3]);

        // Verify we can reconstruct the original code points
        String result = new String(buf, 0, n);
        int[] codePoints = result.codePoints().toArray();
        assertArrayEquals(new int[] { emoji, cjk }, codePoints);

        reader.close();
    }

    @Test(expected = IOException.class)
    public void testReadAfterClose() throws Exception {
        InputReader reader = new InputReader();
        reader.close();
        reader.read(new char[8], 0, 8);
    }

    @Test(expected = IOException.class)
    public void testReadCodePointAfterClose() throws Exception {
        InputReader reader = new InputReader();
        reader.close();
        reader.readCodePoint(1, TimeUnit.SECONDS);
    }

    @Test(expected = IOException.class)
    public void testReadWithTimeoutAfterClose() throws Exception {
        InputReader reader = new InputReader();
        reader.close();
        reader.read(100);
    }

    @Test(expected = IOException.class)
    public void testReadyAfterClose() throws Exception {
        InputReader reader = new InputReader();
        reader.close();
        reader.ready();
    }

    @Test
    public void testCloseUnblocksRead() throws Exception {
        InputReader reader = new InputReader();
        AtomicInteger readResult = new AtomicInteger(0);
        CountDownLatch started = new CountDownLatch(1);

        Thread readThread = new Thread(() -> {
            started.countDown();
            try {
                readResult.set(reader.read(new char[8], 0, 8));
            } catch (IOException e) {
                readResult.set(-99);
            }
        });
        readThread.start();
        started.await(1, TimeUnit.SECONDS);

        // Give the read thread time to block on take()
        Thread.sleep(50);

        // Close should unblock via wakeup char, returning -1 (EOF)
        reader.close();
        readThread.join(2000);

        assertEquals(-1, readResult.get());
    }

    @Test
    public void testBoundedQueueDropsExcess() throws Exception {
        InputReader reader = new InputReader(4);

        // Push 8 code points into a queue of capacity 4
        reader.push("ABCDEFGH");

        char[] buf = new char[16];
        int n = reader.read(buf, 0, buf.length);
        // Only first 4 should have been accepted
        assertEquals(4, n);
        assertEquals("ABCD", new String(buf, 0, n));

        reader.close();
    }

    @Test
    public void testReady() throws Exception {
        InputReader reader = new InputReader();
        assertFalse(reader.ready());

        reader.push('Z');
        assertTrue(reader.ready());

        char[] buf = new char[4];
        reader.read(buf, 0, buf.length);
        assertFalse(reader.ready());

        reader.close();
    }

    @Test
    public void testMultipleEvents() throws Exception {
        InputReader reader = new InputReader();
        reader.push('A');
        reader.push('B');
        reader.push("CD");

        char[] buf = new char[16];
        int n = reader.read(buf, 0, buf.length);
        assertEquals(4, n);
        assertEquals("ABCD", new String(buf, 0, n));

        reader.close();
    }

    @Test
    public void testHandlerAfterClose() throws Exception {
        InputReader reader = new InputReader();
        reader.close();
        // Should not throw — push is simply dropped
        reader.push('X');
    }

    @Test
    public void testDoubleClose() throws Exception {
        InputReader reader = new InputReader();
        reader.close();
        // Second close should be a no-op
        reader.close();
    }
}
