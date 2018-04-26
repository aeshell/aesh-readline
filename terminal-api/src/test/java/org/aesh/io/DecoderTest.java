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
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class DecoderTest {

    private void assertDecode(int initialSize, List<String> chars, int... bytes) {
        final List<String> abc = new ArrayList<>();
        Decoder decoder = new Decoder(initialSize, Charset.forName("UTF-8"), event -> {
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
    public void testDecoder() {
        assertDecode(4, Arrays.asList("ABCD","E"), 65, 66, 67, 68, 69);
        assertDecode(4, Arrays.asList("\rfoo"), 13, 102, 111, 111);
        assertDecode(4, Arrays.asList("\u001B["), 27, 91);
    }

    @Test
  public void testDecoderOverflow() {
    assertDecode(2, Arrays.asList("AB", "CD", "E"), 65, 66, 67, 68, 69);
    assertDecode(3, Arrays.asList("ABC", "DE"), 65, 66, 67, 68, 69);
    assertDecode(4, Arrays.asList("ABCD", "E"), 65, 66, 67, 68, 69);
    assertDecode(5, Arrays.asList("ABCDE"), 65, 66, 67, 68, 69);
    assertDecode(6, Arrays.asList("ABCDE"), 65, 66, 67, 68, 69);
  }

  @Test
  public void testDecoderUnderflow() {
    final ArrayList<Integer> codePoints = new ArrayList<>();
    Decoder decoder = new Decoder(10, Charset.forName("UTF-8"), new Consumer<int[]>() {
      @Override
      public void accept(int[] event) {
        codePoints.addAll(list(event));
      }
    });
    decoder.write(new byte[]{(byte) 0xE2});
    assertEquals(0, codePoints.size());
    decoder.write(new byte[]{(byte) 0x82});
    assertEquals(0, codePoints.size());
    decoder.write(new byte[]{(byte) 0xAC});
    assertEquals(1, codePoints.size());
    assertEquals('\u20AC', (int)codePoints.get(0));
  }

  public static List<Integer> list(int... list) {
    ArrayList<Integer> result = new ArrayList<>(list.length);
    for (int i : list) {
      result.add(i);
    }
    return result;
  }
}
