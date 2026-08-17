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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;

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
        Stream.Builder<int[]> builder = Stream.<int[]> builder();
        TtyOutputMode out = new TtyOutputMode(builder);
        out.accept(toCodePoints(actual));
        String result = fromCodePoints(builder.build().flatMapToInt(IntStream::of).toArray());
        assertEquals(expected, result);
    }

    /**
     * Verify that accept() produces exactly one downstream call regardless
     * of how many newlines the input contains. Previously, each \n caused
     * 2-3 separate downstream calls (chunk before \n, the \r\n, chunk after).
     */
    @Test
    public void testSingleCallPerAccept() {
        assertCallCount("hello", 1);
        assertCallCount("\n", 1);
        assertCallCount("a\n", 1);
        assertCallCount("\na", 1);
        assertCallCount("a\nb\nc", 1);
        assertCallCount("a\nb\nc\n", 1);
        assertCallCount("\n\n\n", 1);
    }

    @Test
    public void testEmptyInputProducesNoCalls() {
        assertCallCount("", 0);
    }

    @Test
    public void testNoNewlineForwardsOriginalArray() {
        int[] input = toCodePoints("hello");
        List<int[]> received = new ArrayList<>();
        TtyOutputMode out = new TtyOutputMode(received::add);
        out.accept(input);
        assertEquals(1, received.size());
        // Should forward the exact same array (no copy)
        assertEquals(input, received.get(0));
    }

    private void assertCallCount(String input, int expectedCalls) {
        List<int[]> received = new ArrayList<>();
        TtyOutputMode out = new TtyOutputMode(received::add);
        out.accept(toCodePoints(input));
        assertEquals("Expected " + expectedCalls + " call(s) for input \""
                + input.replace("\n", "\\n") + "\" but got " + received.size(),
                expectedCalls, received.size());
    }

    int[] toCodePoints(String s) {
        return s.codePoints().toArray();
    }

    String fromCodePoints(int[] input) {
        return new String(input, 0, input.length);
    }

}
