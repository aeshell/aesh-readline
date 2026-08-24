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

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class DecoderTest {

    private void assertDecode(int initialSize, List<String> chars, int... bytes) {
        final List<String> abc = new ArrayList<>();
        Decoder decoder = new Decoder(initialSize, StandardCharsets.UTF_8, event -> {
            StringBuilder sb = new StringBuilder();
            for (int cp : event) {
                sb.appendCodePoint(cp);
            }
            abc.add(sb.toString());
        });
        byte[] data = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            data[i] = (byte) bytes[i];
        }
        decoder.write(data);
        assertEquals(chars, abc);
    }

    @Test
    public void testDecoder() {
        assertDecode(4, Arrays.asList("ABCD", "E"), 65, 66, 67, 68, 69);
        assertDecode(4, Collections.singletonList("\rfoo"), 13, 102, 111, 111);
        assertDecode(4, Collections.singletonList("\u001B["), 27, 91);
    }

    @Test
    public void testDecoderOverflow() {
        assertDecode(2, Arrays.asList("AB", "CD", "E"), 65, 66, 67, 68, 69);
        assertDecode(3, Arrays.asList("ABC", "DE"), 65, 66, 67, 68, 69);
        assertDecode(4, Arrays.asList("ABCD", "E"), 65, 66, 67, 68, 69);
        assertDecode(5, Collections.singletonList("ABCDE"), 65, 66, 67, 68, 69);
        assertDecode(6, Collections.singletonList("ABCDE"), 65, 66, 67, 68, 69);
    }

    @Test
    public void testDecoderUnderflow() {
        final ArrayList<Integer> codePoints = new ArrayList<>();
        Decoder decoder = new Decoder(10, StandardCharsets.UTF_8, event -> codePoints.addAll(list(event)));
        decoder.write(new byte[] { (byte) 0xE2 });
        assertEquals(0, codePoints.size());
        decoder.write(new byte[] { (byte) 0x82 });
        assertEquals(0, codePoints.size());
        decoder.write(new byte[] { (byte) 0xAC });
        assertEquals(1, codePoints.size());
        assertEquals('\u20AC', (int) codePoints.get(0));
    }

    @Test
    public void testMalformedUtf8ReplacedWithFFFD() {
        // 0xFE and 0xFF are never valid in UTF-8
        final List<Integer> codePoints = new ArrayList<>();
        Decoder decoder = new Decoder(10, StandardCharsets.UTF_8, event -> codePoints.addAll(list(event)));
        decoder.write(new byte[] { 'A', (byte) 0xFE, 'B' });
        assertEquals(3, codePoints.size());
        assertEquals('A', (int) codePoints.get(0));
        assertEquals(0xFFFD, (int) codePoints.get(1)); // replacement char
        assertEquals('B', (int) codePoints.get(2));
    }

    @Test
    public void testMalformedUtf8TwoInvalidBytes() {
        // Two consecutive invalid bytes should each produce a replacement
        final List<Integer> codePoints = new ArrayList<>();
        Decoder decoder = new Decoder(10, StandardCharsets.UTF_8, event -> codePoints.addAll(list(event)));
        decoder.write(new byte[] { (byte) 0xFF, (byte) 0xFE });
        assertEquals(2, codePoints.size());
        assertEquals(0xFFFD, (int) codePoints.get(0));
        assertEquals(0xFFFD, (int) codePoints.get(1));
    }

    @Test
    public void testTruncatedMultiByteFollowedByAscii() {
        // Start of a 2-byte sequence (0xC3) followed by ASCII instead of continuation
        final List<Integer> codePoints = new ArrayList<>();
        Decoder decoder = new Decoder(10, StandardCharsets.UTF_8, event -> codePoints.addAll(list(event)));
        decoder.write(new byte[] { (byte) 0xC3, 'X' });
        // The CharsetDecoder with REPLACE will handle this: 0xC3 alone is
        // malformed (missing continuation), so it becomes U+FFFD, then 'X'
        assertEquals(2, codePoints.size());
        assertEquals(0xFFFD, (int) codePoints.get(0));
        assertEquals('X', (int) codePoints.get(1));
    }

    @Test
    public void testEmojiDecoding() {
        // U+1F600 (grinning face) = F0 9F 98 80 in UTF-8
        // This is a 4-byte sequence that produces a surrogate pair in UTF-16
        final List<Integer> codePoints = new ArrayList<>();
        Decoder decoder = new Decoder(10, StandardCharsets.UTF_8, event -> codePoints.addAll(list(event)));
        decoder.write(new byte[] { (byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x80 });
        assertEquals(1, codePoints.size());
        assertEquals(0x1F600, (int) codePoints.get(0));
    }

    @Test
    public void testEmojiWithAscii() {
        // Mix of ASCII and emoji
        final List<Integer> codePoints = new ArrayList<>();
        Decoder decoder = new Decoder(10, StandardCharsets.UTF_8, event -> codePoints.addAll(list(event)));
        decoder.write(new byte[] { 'H', 'i', (byte) 0xF0, (byte) 0x9F, (byte) 0x98, (byte) 0x80, '!' });
        assertEquals(4, codePoints.size());
        assertEquals('H', (int) codePoints.get(0));
        assertEquals('i', (int) codePoints.get(1));
        assertEquals(0x1F600, (int) codePoints.get(2));
        assertEquals('!', (int) codePoints.get(3));
    }

    @Test
    public void testEmojiSplitAcrossWrites() {
        // U+1F600 bytes split across two write() calls
        final List<Integer> codePoints = new ArrayList<>();
        Decoder decoder = new Decoder(10, StandardCharsets.UTF_8, event -> codePoints.addAll(list(event)));
        decoder.write(new byte[] { (byte) 0xF0, (byte) 0x9F });
        assertEquals(0, codePoints.size()); // underflow — waiting for more bytes
        decoder.write(new byte[] { (byte) 0x98, (byte) 0x80 });
        assertEquals(1, codePoints.size());
        assertEquals(0x1F600, (int) codePoints.get(0));
    }

    @Test
    public void testOverlongEncodingReplaced() {
        // Overlong encoding of NUL: C0 80 (should be just 00)
        // This is invalid UTF-8 and should be replaced
        final List<Integer> codePoints = new ArrayList<>();
        Decoder decoder = new Decoder(10, StandardCharsets.UTF_8, event -> codePoints.addAll(list(event)));
        decoder.write(new byte[] { (byte) 0xC0, (byte) 0x80 });
        // Both bytes are malformed — decoder replaces with U+FFFD
        for (int cp : codePoints) {
            assertEquals(0xFFFD, cp);
        }
    }

    @Test
    public void testEmptyInput() {
        final List<Integer> codePoints = new ArrayList<>();
        Decoder decoder = new Decoder(10, StandardCharsets.UTF_8, event -> codePoints.addAll(list(event)));
        decoder.write(new byte[0]);
        assertEquals(0, codePoints.size());
    }

    public static List<Integer> list(int... list) {
        ArrayList<Integer> result = new ArrayList<>(list.length);
        for (int i : list) {
            result.add(i);
        }
        return result;
    }
}
