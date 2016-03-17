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
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class BufferIntTest {

    @Test
    public void insertNoPrompt() {
        BufferInt buffer = new BufferInt();

        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, 65);
        assertEquals(1, buffer.length());
        assertEquals(65, buffer.get(0));
        buffer.insert(outConsumer::add, 66);
        buffer.insert(outConsumer::add, 67);
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        assertArrayEquals(new int[] {65}, outConsumer.get(0));
        assertArrayEquals(new int[] {66}, outConsumer.get(1));
        assertArrayEquals(new int[] {67}, outConsumer.get(2));
        buffer.reset();
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertTrue(outConsumer.get(0).length == 0);

        buffer.insert(outConsumer::add, 'A');
        buffer.insert(outConsumer::add, 'B');
        buffer.insert(outConsumer::add, 'C');
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        outConsumer.clear();
        buffer.insert(outConsumer::add, " foo");
        //buffer.print(outConsumer::add, 120);
        assertEquals(" foo", Parser.fromCodePoints(outConsumer.get(0)));
    }

    @Test
    public void insert() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, 65);
        assertEquals(1, buffer.length());
        assertEquals(65, buffer.get(0));
        buffer.insert(outConsumer::add, 66);
        buffer.insert(outConsumer::add, 67);
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        outConsumer.clear();
        buffer.print(outConsumer::add, 120);

        assertArrayEquals(ANSI.CURSOR_START,
                Arrays.copyOfRange(outConsumer.get(0), 0, 3));

        assertEquals(": ",
                Parser.fromCodePoints( Arrays.copyOfRange(outConsumer.get(0), 3, 5)));
        assertArrayEquals(new int[] {65,66,67},
                Arrays.copyOfRange(outConsumer.get(0), 5, 8));
        assertEquals("ABC",
                Parser.fromCodePoints( Arrays.copyOfRange(outConsumer.get(0), 5, 8)));
        buffer.reset();
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals(": ", Parser.fromCodePoints( outConsumer.get(0)));
        assertEquals(1, outConsumer.size());

        buffer.insert(outConsumer::add, 'A');
        buffer.insert(outConsumer::add, 'B');
        buffer.insert(outConsumer::add, 'C');
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        outConsumer.clear();
        buffer.insert(outConsumer::add, " foo");
        assertEquals(" foo", Parser.fromCodePoints(outConsumer.get(0)));
    }

    @Test
    public void insertString() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar");
        buffer.insert(outConsumer::add, " ");
        buffer.insert(outConsumer::add, "foo bar");
        assertEquals(": ",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), 0, 2 )));
        assertEquals("foo bar",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), 2, 9 )));
        assertEquals(" ", Parser.fromCodePoints(outConsumer.get(1)));
        assertEquals("foo bar", Parser.fromCodePoints(outConsumer.get(2)));

    }

    @Test
    public void delete() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar");
        outConsumer.clear();
        buffer.delete(outConsumer::add, -2, 120);
        assertArrayEquals(new int[] {27,'[','G'}, Arrays.copyOfRange(outConsumer.get(0), 0, 3 ));
        assertArrayEquals(new int[] {27,'[','K'}, Arrays.copyOfRange(outConsumer.get(0), 3, 6 ));
        assertEquals(": foo b",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), 6, outConsumer.get(0).length )));
        outConsumer.clear();
        buffer.delete(outConsumer::add, -2, 120);
        assertArrayEquals(new int[] {27,'[','G'}, Arrays.copyOfRange(outConsumer.get(0), 0, 3 ));
        assertArrayEquals(new int[] {27,'[','K'}, Arrays.copyOfRange(outConsumer.get(0), 3, 6 ));
        assertEquals(": foo",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), 6, outConsumer.get(0).length )));
        outConsumer.clear();
        buffer.delete(outConsumer::add, -4, 120);
        assertArrayEquals(new int[] {27,'[','G'},
                Arrays.copyOfRange(outConsumer.get(0), 0, 3 ));
        assertArrayEquals(new int[] {27,'[','K'}, Arrays.copyOfRange(outConsumer.get(0), 3, 6 ));
        assertEquals(": ",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), 6, outConsumer.get(0).length )));
    }

    @Test
    public void move() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar");
        assertEquals(7, buffer.getMultiCursor());
        buffer.move(outConsumer::add, 1, 120);
        assertEquals(7, buffer.getMultiCursor());
        buffer.move(outConsumer::add, -1, 120);
        assertEquals(6, buffer.getMultiCursor());
        buffer.move(outConsumer::add, -100, 120);
        assertEquals(0, buffer.getMultiCursor());
        buffer.move(outConsumer::add, 100, 120);
        assertEquals(7, buffer.getMultiCursor());
    }

    @Test
    public void moveAndInsert() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar");
        buffer.move(outConsumer::add, -1, 120);
        buffer.insert(outConsumer::add, 'A');
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertArrayEquals(new int[] {27,'[','G'},
                Arrays.copyOfRange(outConsumer.get(0), 0, 3));
        assertEquals(": ",
                Parser.fromCodePoints( Arrays.copyOfRange(outConsumer.get(0), 3, 5)));
        assertEquals("foo baAr",
        Parser.fromCodePoints( Arrays.copyOfRange(outConsumer.get(0), 5, 13)));
        buffer.move(outConsumer::add, -1, 120);
        outConsumer.clear();
        buffer.insert(outConsumer::add, 'b');
        assertEquals("bAr", Parser.fromCodePoints(outConsumer.get(0)));
        buffer.move(outConsumer::add, -10, 120);
        outConsumer.clear();
        buffer.insert(outConsumer::add, "foo ");
        assertEquals("foo foo babAr", Parser.fromCodePoints(outConsumer.get(0)));
        buffer.move(outConsumer::add, 20, 120);
        outConsumer.clear();
        buffer.insert(outConsumer::add, " bar");
        assertEquals(" bar", Parser.fromCodePoints(outConsumer.get(0)));
    }


    @Test
    public void moveAndDelete() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar");
        buffer.move(outConsumer::add, -3, 120);
        outConsumer.clear();
        buffer.delete(outConsumer::add, -1, 120);
        assertEquals("foobar",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-6, outConsumer.get(0).length )));
        buffer.move(outConsumer::add, 1, 120);
        outConsumer.clear();
        buffer.delete(outConsumer::add, -3, 120);
        assertEquals("far",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-3, outConsumer.get(0).length )));
        outConsumer.clear();
        buffer.delete(outConsumer::add, 2, 120);
        assertEquals("f",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-1, outConsumer.get(0).length )));
        outConsumer.clear();
        buffer.delete(outConsumer::add, 2, 120);
        assertEquals("f",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-1, outConsumer.get(0).length )));
        outConsumer.clear();
        buffer.delete(outConsumer::add, -5, 120);
        assertEquals(": ",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-2, outConsumer.get(0).length )));
    }

    @Test
    public void replaceChar() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar");
        outConsumer.clear();
        buffer.replace(outConsumer::add, 'R');
        assertEquals("R", Parser.fromCodePoints(outConsumer.get(0)));
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals("foo bar",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), outConsumer.get(0).length-7, outConsumer.get(0).length )));
        buffer.move(outConsumer::add, -1, 120);
        buffer.replace(outConsumer::add, 'R');
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals("foo baR",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), outConsumer.get(0).length-7, outConsumer.get(0).length )));
        buffer.move(outConsumer::add, -4, 120);
        buffer.replace(outConsumer::add, 'O');
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals("foO baR",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), outConsumer.get(0).length-7, outConsumer.get(0).length )));
    }

    @Test
    public void changeCase() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar");
        buffer.move(outConsumer::add, -1, 120);
        outConsumer.clear();
        buffer.upCase(outConsumer::add);
        assertEquals("R", Parser.fromCodePoints(outConsumer.get(0)));
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);

        assertEquals("foo baR",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), outConsumer.get(0).length-7, outConsumer.get(0).length )));
        buffer.move(outConsumer::add, -4, 120);
        outConsumer.clear();
        buffer.changeCase(outConsumer::add);
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals("foO baR",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), outConsumer.get(0).length-7, outConsumer.get(0).length )));
        outConsumer.clear();
        buffer.changeCase(outConsumer::add);
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals("foo baR",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), outConsumer.get(0).length-7, outConsumer.get(0).length )));
        buffer.move(outConsumer::add, 10, 120);
        outConsumer.clear();
        buffer.lowCase(outConsumer::add);
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals("foo baR",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), outConsumer.get(0).length-7, outConsumer.get(0).length )));
        buffer.move(outConsumer::add, -1, 120);
        outConsumer.clear();
        buffer.lowCase(outConsumer::add);
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals("foo bar",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), outConsumer.get(0).length-7, outConsumer.get(0).length )));
    }

    @Test
    public void replace() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar");
        outConsumer.clear();
        buffer.replace(outConsumer::add, " gar", 120);
        assertEquals(" gar",
        Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                outConsumer.get(0).length-4, outConsumer.get(0).length )));
        outConsumer.clear();
        buffer.insert(outConsumer::add, 'd');
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals(" gard",
        Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                outConsumer.get(0).length-5, outConsumer.get(0).length )));
    }

    @Test
    public void multiLine() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar \\");
        buffer.setMultiLine(true);
        buffer.updateMultiLineBuffer();
        buffer.insert(outConsumer::add, " bar ");
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        //assertEquals("> ", Parser.fromCodePoints(outConsumer.get(1)));
        assertEquals(">  bar ",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-7, outConsumer.get(0).length )));

        assertEquals("foo bar  bar ", buffer.asString());

        buffer.insert(outConsumer::add, "\\");
        buffer.updateMultiLineBuffer();
        buffer.insert(outConsumer::add, "gar");
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals("> gar",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-5, outConsumer.get(0).length )));

        assertEquals("foo bar  bar gar", buffer.asString());
    }

    @Test
    public void manyLinesInsert() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "1234567890");
        outConsumer.clear();
        buffer.move(outConsumer::add, -10, 5);
        assertArrayEquals(new int[] {27,'[',2,'A',27,'[',3,'G'}, outConsumer.get(0));

        buffer.insert(outConsumer::add, ' ');
        outConsumer.clear();
        buffer.print(outConsumer::add, 5);
        assertEquals(" 1234567890", buffer.asString());
        outConsumer.clear();
        buffer.move(outConsumer::add, 5, 5);
        assertArrayEquals(new int[] {27,'[',1,'B',27,'[',4,'G'}, outConsumer.get(0));
        buffer.insert(outConsumer::add, ' ');
        outConsumer.clear();
        buffer.print(outConsumer::add, 5);
        assertEquals(" 12345 67890", buffer.asString());
        buffer.move(outConsumer::add, 13, 5);
        buffer.insert(outConsumer::add, ' ');
        outConsumer.clear();
        buffer.print(outConsumer::add, 5);
        assertEquals(" 12345 67890 ", buffer.asString());
        buffer.move(outConsumer::add, -3, 5);
        buffer.insert(outConsumer::add, ' ');
        outConsumer.clear();
        buffer.print(outConsumer::add, 5);
        assertEquals(" 12345 678 90 ", buffer.asString());
    }

    @Test
    public void replaceMultiLine() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "1234567890");
        outConsumer.clear();
        buffer.replace(outConsumer::add, "foo", 5);
        assertArrayEquals(ANSI.ERASE_WHOLE_LINE,
                Arrays.copyOfRange(outConsumer.get(0), 0, 4));
        assertArrayEquals(ANSI.MOVE_LINE_UP,
                Arrays.copyOfRange(outConsumer.get(0), 4, 8));
        assertArrayEquals(ANSI.ERASE_WHOLE_LINE,
                Arrays.copyOfRange(outConsumer.get(0), 8, 12));
        assertArrayEquals(ANSI.MOVE_LINE_UP,
                Arrays.copyOfRange(outConsumer.get(0), 12, 16));
        assertArrayEquals(ANSI.CURSOR_START,
                Arrays.copyOfRange(outConsumer.get(0), 16, 19));
    }

    @Test
    public void disablePrompt() {
        BufferInt buffer = new BufferInt(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo");

        assertEquals(": foo",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-5, outConsumer.get(0).length )));

        buffer.clear();
        buffer.insert(outConsumer::add, "foo");

        assertEquals(": foo",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-5, outConsumer.get(0).length )));

        buffer.clear();
        buffer.disablePrompt(true);
        buffer.insert(outConsumer::add, "foo");

        assertEquals("foo",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-3, outConsumer.get(0).length )));

    }

}
