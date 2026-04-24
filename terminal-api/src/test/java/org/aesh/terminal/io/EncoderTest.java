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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

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
        Encoder encoder = new Encoder(charset, event -> System.arraycopy(event, 0, output, 0, event.length));

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
}
