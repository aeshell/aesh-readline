/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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
package org.aesh.terminal.tty;

import org.junit.Test;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class TtyOutputModeTest {

  @Test
  public void testTranslateLFToCRLF() {
    assertOutput("a", "a");
    assertOutput("\r\n", "\n");
    assertOutput("a\r\n", "a\n");
    assertOutput("\r\na", "\na");
    assertOutput("a\r\nb\r\nc", "a\nb\nc");
  }

  private void assertOutput(String expected, String actual) {
    Stream.Builder<int[]> builder = Stream.<int[]>builder();
    TtyOutputMode out = new TtyOutputMode(builder);
    out.accept(toCodePoints(actual));
    String result = fromCodePoints(builder.build().flatMapToInt(IntStream::of).toArray());
    assertEquals(expected, result);
  }

  int[] toCodePoints(String s) {
    return s.codePoints().toArray();
  }

  String fromCodePoints(int[] input) {
    return new String(input, 0, input.length);
  }

}
