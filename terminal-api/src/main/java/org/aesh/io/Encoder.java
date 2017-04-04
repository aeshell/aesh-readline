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
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Encoder implements Consumer<int[]> {

    private Charset charset;
    private final Consumer<byte[]> out;

    public Encoder(Charset charset, Consumer<byte[]> out) {
        if(charset != null)
            this.charset = charset;
        else
            this.charset = Charset.defaultCharset();
        this.out = out;
    }

    public void setCharset(Charset charset) {
        if(charset != null)
            this.charset = charset;
    }

    @Override
    public void accept(int[] input) {
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
        ByteBuffer bytesBuf = charset.encode(charBuf);

        out.accept(safeTrim(bytesBuf.array(), bytesBuf.limit()));
    }

    private static byte[] safeTrim(byte[] bytes, int length) {
        if(bytes.length == length)
            return bytes;
        else
            return Arrays.copyOf(bytes, length);
    }
}
