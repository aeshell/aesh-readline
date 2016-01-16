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
package org.jboss.aesh.readline;

import org.jboss.aesh.parser.Parser;
import org.jboss.aesh.util.ANSI;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class BufferIntTest {

    @Test
    public void testInsertNoPrompt() {
        BufferInt buffer = new BufferInt();

        buffer.insert(65);
        assertEquals(1, buffer.length());
        assertEquals(65, buffer.get(0));
        buffer.insert(66);
        buffer.insert(67);
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        List<int[]> outConsumer = new ArrayList<>();
        buffer.print(outConsumer::add, 120);

        assertArrayEquals(new int[] {65,66,67}, outConsumer.get(0));
        assertEquals("ABC", Parser.fromCodePoints(outConsumer.get(0)));
        buffer.reset();
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertTrue(outConsumer.isEmpty());

        buffer.write('A');
        buffer.write('B');
        buffer.write('C');
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        buffer.write(" foo");
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals("ABC foo", Parser.fromCodePoints(outConsumer.get(0)));
    }

    @Test
    public void testInsert() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        buffer.insert(65);
        assertEquals(1, buffer.length());
        assertEquals(65, buffer.get(0));
        buffer.insert(66);
        buffer.insert(67);
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        List<int[]> outConsumer = new ArrayList<>();
        buffer.print(outConsumer::add, 120);

        assertArrayEquals(ANSI.CURSOR_START, outConsumer.get(0));
        assertEquals(": ", Parser.fromCodePoints( outConsumer.get(1)));
        assertArrayEquals(new int[] {65,66,67}, outConsumer.get(2));
        assertEquals("ABC", Parser.fromCodePoints(outConsumer.get(2)));
        buffer.reset();
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals(": ", Parser.fromCodePoints( outConsumer.get(0)));
        assertEquals(1, outConsumer.size());

        buffer.write('A');
        buffer.write('B');
        buffer.write('C');
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        buffer.write(" foo");
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertArrayEquals(ANSI.CURSOR_START, outConsumer.get(0));
        assertEquals(": ", Parser.fromCodePoints( outConsumer.get(1)));
        assertEquals("ABC foo", Parser.fromCodePoints(outConsumer.get(2)));
    }

    @Test
    public void testInsertString() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        buffer.write("foo bar");
        buffer.write(" ");
        buffer.write("foo bar");
        List<int[]> outConsumer = new ArrayList<>();
        buffer.print(outConsumer::add, 120);
        assertArrayEquals(new int[] {27,'[','G'}, outConsumer.get(0));
        assertEquals(": ", Parser.fromCodePoints(outConsumer.get(1)));
        assertEquals("foo bar foo bar", Parser.fromCodePoints(outConsumer.get(2)));

    }

    @Test
    public void testDelete() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        buffer.write("foo bar");
        buffer.delete(-2);
        List<int[]> outConsumer = new ArrayList<>();
        buffer.print(outConsumer::add, 120);
        assertArrayEquals(new int[] {27,'[','G'}, outConsumer.get(0));
        assertArrayEquals(new int[] {27,'[','K'}, outConsumer.get(1));
        assertEquals(": ", Parser.fromCodePoints(outConsumer.get(2)));
        assertEquals("foo b", Parser.fromCodePoints(outConsumer.get(3)));
        outConsumer.clear();
        buffer.delete(-2);
        buffer.print(outConsumer::add, 120);
        assertArrayEquals(new int[] {27,'[','G'}, outConsumer.get(0));
        assertArrayEquals(new int[] {27,'[','K'}, outConsumer.get(1));
        assertEquals(": ", Parser.fromCodePoints(outConsumer.get(2)));
        assertEquals("foo", Parser.fromCodePoints(outConsumer.get(3)));
        outConsumer.clear();
        buffer.delete(-4);
        buffer.print(outConsumer::add, 120);
        System.out.println(Parser.fromCodePoints(outConsumer.get(0)));
        assertArrayEquals(new int[] {27,'[','G'}, outConsumer.get(0));
        assertArrayEquals(new int[] {27,'[','K'}, outConsumer.get(1));
        assertEquals(": ", Parser.fromCodePoints(outConsumer.get(2)));
        assertEquals(3, outConsumer.size());

    }

}
