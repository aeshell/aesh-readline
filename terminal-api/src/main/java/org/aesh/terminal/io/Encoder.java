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

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Consumer;

import org.aesh.terminal.utils.CodePointUtils;

/**
 * Encodes int arrays (unicode code points) to byte arrays using a specified charset.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class Encoder implements Consumer<int[]> {

    private Charset charset;
    private boolean isUtf8;
    private CharsetEncoder cachedEncoder;
    private final Consumer<byte[]> out;

    // Reusable buffers
    private char[] charBuf = new char[256]; // general path only
    private byte[] byteBuf = new byte[512]; // shared by both UTF-8 and general paths

    /**
     * Create a new Encoder with the specified charset and output consumer.
     *
     * @param charset the charset to use for encoding, or null for the default charset
     * @param out the consumer to receive encoded byte arrays
     */
    public Encoder(Charset charset, Consumer<byte[]> out) {
        if (charset != null)
            this.charset = charset;
        else
            this.charset = Charset.defaultCharset();
        this.isUtf8 = isUtf8Charset(this.charset);
        if (!this.isUtf8)
            this.cachedEncoder = createEncoder(this.charset);
        this.out = out;
    }

    /**
     * Set the charset to use for encoding.
     *
     * @param charset the charset to use, ignored if null
     */
    public void setCharset(Charset charset) {
        if (charset != null) {
            this.charset = charset;
            this.isUtf8 = isUtf8Charset(charset);
            if (!this.isUtf8)
                this.cachedEncoder = createEncoder(charset);
            else
                this.cachedEncoder = null;
        }
    }

    private static boolean isUtf8Charset(Charset cs) {
        return StandardCharsets.UTF_8.equals(cs);
    }

    private static CharsetEncoder createEncoder(Charset cs) {
        return cs.newEncoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
    }

    /**
     * Encode an array of Unicode code points and send the resulting bytes to the output consumer.
     *
     * @param input the code points to encode
     */
    @Override
    public void accept(int[] input) {
        if (isUtf8) {
            acceptUtf8(input);
        } else {
            acceptGeneral(input);
        }
    }

    /**
     * Encode a String directly to bytes, bypassing the int[] intermediary.
     * For UTF-8 with ASCII-only strings (the common case for ANSI sequences),
     * this converts directly from String chars to byte[] in a single pass.
     *
     * @param s the string to encode
     */
    public void accept(String s) {
        if (s.isEmpty())
            return;

        if (isUtf8) {
            out.accept(s.getBytes(StandardCharsets.UTF_8));
        } else {
            accept(CodePointUtils.toCodePoints(s));
        }
    }

    /**
     * Direct UTF-8 encoding from code points to bytes.
     * ASCII fast path: allocates exact-size byte[len] and encodes in a single pass.
     * Falls back to two-pass encoding for inputs containing non-ASCII code points.
     */
    private void acceptUtf8(int[] input) {
        int len = input.length;
        // ASCII fast path: exact-size allocation, no trim needed
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            int cp = input[i];
            if (cp >= 0x80) {
                acceptUtf8MultiByte(input);
                return;
            }
            bytes[i] = (byte) cp;
        }
        out.accept(bytes);
    }

    /**
     * UTF-8 encoding for inputs containing non-ASCII code points.
     * Two-pass: count exact byte size, then encode into exact-size array.
     */
    private void acceptUtf8MultiByte(int[] input) {
        int byteCount = 0;
        for (int cp : input) {
            if (cp < 0x80)
                byteCount++;
            else if (cp < 0x800)
                byteCount += 2;
            else if (cp < 0x10000)
                byteCount += 3;
            else
                byteCount += 4;
        }

        byte[] bytes = new byte[byteCount];
        int pos = 0;
        for (int cp : input) {
            if (cp < 0x80) {
                bytes[pos++] = (byte) cp;
            } else if (cp < 0x800) {
                bytes[pos++] = (byte) (0xC0 | (cp >> 6));
                bytes[pos++] = (byte) (0x80 | (cp & 0x3F));
            } else if (cp < 0x10000) {
                bytes[pos++] = (byte) (0xE0 | (cp >> 12));
                bytes[pos++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
                bytes[pos++] = (byte) (0x80 | (cp & 0x3F));
            } else {
                bytes[pos++] = (byte) (0xF0 | (cp >> 18));
                bytes[pos++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
                bytes[pos++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
                bytes[pos++] = (byte) (0x80 | (cp & 0x3F));
            }
        }
        out.accept(bytes);
    }

    /**
     * General encoding path for non-UTF-8 charsets.
     * Uses cached CharsetEncoder with reusable buffers.
     */
    private void acceptGeneral(int[] input) {
        int charLen = codePointsToChars(input);

        int maxBytes = (int) (charLen * cachedEncoder.maxBytesPerChar()) + 1;
        if (byteBuf.length < maxBytes)
            byteBuf = new byte[maxBytes];

        CharBuffer cbuf = CharBuffer.wrap(charBuf, 0, charLen);
        ByteBuffer bbuf = ByteBuffer.wrap(byteBuf);

        cachedEncoder.reset();
        CoderResult result = cachedEncoder.encode(cbuf, bbuf, true);
        if (!result.isError())
            cachedEncoder.flush(bbuf);

        int len = bbuf.position();
        if (len == byteBuf.length) {
            out.accept(byteBuf);
        } else {
            out.accept(Arrays.copyOf(byteBuf, len));
        }
    }

    /**
     * Convert code points to chars in the reusable charBuf.
     * Used by the general (non-UTF-8) encoding path.
     */
    private int codePointsToChars(int[] input) {
        int len = input.length;
        if (charBuf.length < len)
            charBuf = new char[len + (len >> 1)];

        int pos = 0;
        for (int cp : input) {
            if (Character.isBmpCodePoint(cp)) {
                charBuf[pos++] = (char) cp;
            } else {
                if (pos + 1 >= charBuf.length)
                    charBuf = Arrays.copyOf(charBuf, charBuf.length + (charBuf.length >> 1));
                charBuf[pos++] = Character.highSurrogate(cp);
                charBuf[pos++] = Character.lowSurrogate(cp);
            }
        }
        return pos;
    }

    /**
     * Convert an array of Unicode code points to a CharBuffer.
     * Handles surrogate pairs for code points outside the Basic Multilingual Plane.
     *
     * @param input the code points to convert
     * @return a CharBuffer containing the character representation
     */
    public static CharBuffer toCharBuffer(int[] input) {
        final char[] tmp = new char[2];
        int capacity = 0;
        for (int codePoint : input) {
            capacity += Character.charCount(codePoint);
        }
        CharBuffer charBuf = CharBuffer.allocate(capacity);
        for (int in : input) {
            int size = Character.toChars(in, tmp, 0);
            charBuf.put(tmp, 0, size);
        }
        charBuf.flip();
        return charBuf;
    }
}
