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

import org.junit.Test;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class DecoderTest {

    private void assertDecode(List<String> chars, int... bytes) {
        final List<String> abc = new ArrayList<>();
        Decoder decoder = new Decoder(Charset.forName("UTF-8"), event -> {
            StringBuilder sb = new StringBuilder();
            for (int cp : event) {
                sb.appendCodePoint(cp);
            }
            abc.add(sb.toString());
        });
        byte[] data = new byte[bytes.length];
        for (int i = 0;i < bytes.length;i++) {
            data[i] = (byte) bytes[i];
        }
        decoder.write(data);
        assertEquals(chars, abc);
    }

    @Test
    public void testDecoder() throws Exception {
        assertDecode(Arrays.asList("ABCD","E"), 65, 66, 67, 68, 69);
        assertDecode(Arrays.asList("\rfoo"), 13, 102, 111, 111);
        assertDecode(Arrays.asList("\u001B["), 27, 91);
    }

}
