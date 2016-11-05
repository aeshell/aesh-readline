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
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.function.Consumer;

/**
 * TODO: might not work to read the entire array in on go,
 * might go back to read one and one
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Decoder {

    private CharsetDecoder decoder;
    private Consumer<int[]> out;

    public Decoder(Charset charset, Consumer<int[]> out) {
        decoder = charset.newDecoder();
        this.out = out;
    }

    public void setConsumer(Consumer<int[]> out) {
        this.out = out;
    }

    public void write(byte[] data) {
        write(data, 0, data.length);
    }

    public void write(byte[] data, int start, int len) {
        decode(data, start, len);
    }

    private void decode(byte[] ba, int off, int len) {
        int en = scale(len, decoder.maxCharsPerByte());
        char[] ca = new char[en];
        if (len == 0)
            return;

        decoder.reset();
        ByteBuffer bb = ByteBuffer.wrap(ba, off, len);
        CharBuffer cb = CharBuffer.wrap(ca);
        try {
            CoderResult cr = decoder.decode(bb, cb, true);
            if (!cr.isUnderflow())
                cr.throwException();
            cr = decoder.flush(cb);
            if (!cr.isUnderflow())
                cr.throwException();
        }
        catch (CharacterCodingException x) {
            // Substitution is always enabled,
            // so this shouldn't happen
            throw new Error(x);
        }

        parseChars(ca);
    }

    private static int scale(int len, float expansionFactor) {
        // We need to perform double, not float, arithmetic; otherwise
        // we lose low order bits when len is larger than 2**24.
        return (int) (len * (double) expansionFactor);
    }

    private void parseChars(char[] chars) {
        int[] ints = new int[chars.length];
        for(int i=0; i < chars.length; i++)
            ints[i] = chars[i];

        if(out != null)
            out.accept(ints);
    }


}
