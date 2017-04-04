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
package org.aesh.io;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.IntBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Had to revert back to reading char by char.
 * Code is taken from Julien Viet's BinaryDecoder in termd.
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Decoder {

    private static final ByteBuffer EMPTY = ByteBuffer.allocate(0);
    private static final Logger LOGGER = Logger.getLogger(Decoder.class.getName());

    private CharsetDecoder decoder;
    private ByteBuffer bBuf;
    private final CharBuffer cBuf;
    private Consumer<int[]> onChar;

    private int[] leftOverCodePoints;

    public Decoder(Charset charset, Consumer<int[]> onChar) {
        this(4, charset, onChar);
    }

    public Decoder(int initialSize, Charset charset, Consumer<int[]> onChar) {
        if (initialSize < 2) {
            throw new IllegalArgumentException("Initial size must be at least 2");
        }
        if(charset != null)
            decoder = charset.newDecoder();
        else
            decoder = Charset.defaultCharset().newDecoder();
        bBuf = EMPTY;
        cBuf = CharBuffer.allocate(initialSize); // We need at least 2
        this.onChar = onChar;
    }

    public void setCharset(Charset charset) {
        decoder = charset.newDecoder();
    }

    public void write(byte[] data) {
        write(data, 0, data.length);
    }

    public void write(byte[] data, int start, int len) {

        //if we have some leftovers, we use them first
        if(leftOverCodePoints != null && leftOverCodePoints.length > 0 &&
                onChar != null) {
            onChar.accept(leftOverCodePoints);
            leftOverCodePoints = null;
        }

        // Fill the byte buffer
        int remaining = bBuf.remaining();
        if (len > remaining) {
            // Allocate a new buffer
            ByteBuffer tmp = bBuf;
            int length = tmp.position() + len;
            bBuf = ByteBuffer.allocate(length);
            tmp.flip();
            bBuf.put(tmp);
        }
        bBuf.put(data, start, len);
        bBuf.flip();

        // Drain the byte buffer
        while (true) {
            IntBuffer iBuf = IntBuffer.allocate(bBuf.remaining());
            CoderResult result = decoder.decode(bBuf, cBuf, false);
            cBuf.flip();
            while (cBuf.hasRemaining()) {
                char c = cBuf.get();
                if (Character.isSurrogate(c)) {
                    if (Character.isHighSurrogate(c)) {
                        if (cBuf.hasRemaining()) {
                            char low = cBuf.get();
                            if (Character.isLowSurrogate(low)) {
                                int codePoint = Character.toCodePoint(c, low);
                                if (Character.isValidCodePoint(codePoint)) {
                                    iBuf.put(codePoint);
                                } else {
                                    throw new UnsupportedOperationException("Handle me gracefully");
                                }
                            } else {
                                throw new UnsupportedOperationException("Handle me gracefully");
                            }
                        } else {
                            throw new UnsupportedOperationException("Handle me gracefully");
                        }
                    } else {
                        throw new UnsupportedOperationException("Handle me gracefully");
                    }
                } else {
                    iBuf.put((int) c);
                }
            }
            iBuf.flip();
            int[] codePoints = new int[iBuf.limit()];
            iBuf.get(codePoints);
            if(onChar != null)
                onChar.accept(codePoints);
            else {
                LOGGER.log(Level.WARNING, "InputHandler is set to null, will ignore input: " + fromCodePoints(codePoints));
                leftOverCodePoints = Arrays.copyOf(codePoints, codePoints.length);
            }
            cBuf.compact();
            if (result.isOverflow()) {
                // We still have work to do
            } else if (result.isUnderflow()) {
                //TODO: need to add logic here
                if (bBuf.hasRemaining()) {
                    // We need more input
                } else {
                    // We are done
                }
                break;
            } else {
                throw new UnsupportedOperationException("Handle me gracefully");
            }
        }
        bBuf.compact();
    }

    public void setConsumer(Consumer<int[]> inputHandler) {
        onChar = inputHandler;
    }

    private String fromCodePoints(int[] input) {
        return new String(input, 0, input.length);
    }

}
