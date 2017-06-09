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
package org.aesh.readline;

import org.aesh.utils.ANSI;
import org.aesh.util.Parser;
import org.junit.Assert;
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
public class BufferTest {

    @Test
    public void insertNoPrompt() {
        Buffer buffer = new Buffer();

        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, 65, 10);
        assertEquals(1, buffer.length());
        assertEquals(65, buffer.get(0));
        buffer.insert(outConsumer::add, 66, 10);
        buffer.insert(outConsumer::add, 67, 10);
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        assertArrayEquals(new int[] {65}, outConsumer.get(0));
        assertArrayEquals(new int[] {66}, outConsumer.get(1));
        assertArrayEquals(new int[] {67}, outConsumer.get(2));
        buffer.reset();
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertTrue(outConsumer.get(0).length == 0);

        buffer.insert(outConsumer::add, 'A', 10);
        buffer.insert(outConsumer::add, 'B', 10);
        buffer.insert(outConsumer::add, 'C', 10);
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        outConsumer.clear();
        buffer.insert(outConsumer::add, " foo", 10);
        //buffer.print(outConsumer::add, 120);
        Assert.assertEquals(" foo", Parser.fromCodePoints(outConsumer.get(0)));
    }

    @Test
    public void insert() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.print(outConsumer::add, 100);
        buffer.insert(outConsumer::add, 65, 100);
        assertEquals(1, buffer.length());
        assertEquals(65, buffer.get(0));
        buffer.insert(outConsumer::add, 66, 100);
        buffer.insert(outConsumer::add, 67, 100);
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        //outConsumer.clear();
        //buffer.print(outConsumer::add, 120);

        //assertArrayEquals(ANSI.CURSOR_START,
        //        Arrays.copyOfRange(outConsumer.get(0), 0, 3));

        assertEquals(": ", Parser.fromCodePoints( outConsumer.get(0)));
        assertArrayEquals(new int[] {65}, outConsumer.get(1));
        assertArrayEquals(new int[] {66}, outConsumer.get(2));
        assertArrayEquals(new int[] {67}, outConsumer.get(3));
          assertEquals("ABC", buffer.asString());
        buffer.reset();
        outConsumer.clear();
        buffer.print(outConsumer::add, 120);
        assertEquals(": ", Parser.fromCodePoints( outConsumer.get(0)));
        assertEquals(1, outConsumer.size());

        buffer.insert(outConsumer::add, 'A', 100);
        buffer.insert(outConsumer::add, 'B', 100);
        buffer.insert(outConsumer::add, 'C', 100);
        assertEquals(66, buffer.get(1));
        assertEquals(67, buffer.get(2));

        outConsumer.clear();
        buffer.insert(outConsumer::add, " foo", 100);
        assertEquals(" foo", Parser.fromCodePoints(outConsumer.get(0)));
    }

    @Test
    public void insertString() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar", 100);
        buffer.insert(outConsumer::add, " ", 100);
        buffer.insert(outConsumer::add, "foo bar", 100);
        assertEquals(": ",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), 0, 2 )));
        assertEquals("foo bar",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0), 2, 9 )));
        assertEquals(" ", Parser.fromCodePoints(outConsumer.get(1)));
        assertEquals("foo bar", Parser.fromCodePoints(outConsumer.get(2)));


        buffer = new Buffer(new Prompt(": "));
        outConsumer.clear();
        buffer.insert(outConsumer::add, "foo", 100);
        buffer.move(outConsumer::add, -10, 100);
        outConsumer.clear();
        buffer.insert(outConsumer::add, "1", 100);
        assertArrayEquals( new int[]{'1','f','o','o',27,'[','3','D'}, outConsumer.get(0));

    }

    @Test
    public void delete() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar", 100);
        outConsumer.clear();
        buffer.delete(outConsumer::add, -2, 120);
        assertArrayEquals(new int[] {27,'[', '2', 'D'}, Arrays.copyOfRange(outConsumer.get(0), 0, 4 ));
        assertArrayEquals(new int[] {27,'[','K'}, Arrays.copyOfRange(outConsumer.get(0), 4, 7 ));
        assertEquals("foo b", buffer.asString());
        outConsumer.clear();
        buffer.delete(outConsumer::add, -2, 120);
        assertArrayEquals(new int[] {27,'[', '2', 'D'}, Arrays.copyOfRange(outConsumer.get(0), 0, 4 ));
        assertArrayEquals(new int[] {27,'[','K'}, Arrays.copyOfRange(outConsumer.get(0), 4, 7 ));
        assertEquals("foo", buffer.asString());
        outConsumer.clear();
        buffer.delete(outConsumer::add, -4, 120);
        assertArrayEquals(new int[] {27,'[', '3', 'D'},
                Arrays.copyOfRange(outConsumer.get(0), 0, 4 ));
        assertArrayEquals(new int[] {27,'[','K'}, Arrays.copyOfRange(outConsumer.get(0), 4, 7 ));
        assertEquals("", buffer.asString());
    }

    @Test
    public void deleteForward() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar", 100);
        buffer.move(outConsumer::add, -10, 100);
        outConsumer.clear();
        buffer.delete(outConsumer::add, 3, 120);
        assertArrayEquals(new int[] {27,'[', '2', 'D'}, Arrays.copyOfRange(outConsumer.get(0), 0, 4 ));
        assertArrayEquals(new int[] {27,'[','K'}, Arrays.copyOfRange(outConsumer.get(0), 4, 7 ));
        assertArrayEquals(new int[] {':',' ',' ','b','a','r'}, Arrays.copyOfRange(outConsumer.get(0), 7, 13));
        assertArrayEquals(new int[] {27,'[','4','D'}, Arrays.copyOfRange(outConsumer.get(0), 13, 17 ));
        outConsumer.clear();
        buffer.delete(outConsumer::add, 4, 120);
        assertArrayEquals(new int[] {27,'[','K'}, Arrays.copyOfRange(outConsumer.get(0), 0,3 ));
        buffer.insert(outConsumer::add, "ab", 100);
        outConsumer.clear();
        buffer.delete(outConsumer::add,1, 120);
        assertEquals(0, outConsumer.size());
        buffer.move(outConsumer::add, -2, 100);
        outConsumer.clear();
        buffer.delete(outConsumer::add,2, 120);
        assertArrayEquals(new int[] {27,'[','K'}, Arrays.copyOfRange(outConsumer.get(0), 0,3 ));
        outConsumer.clear();
        buffer.delete(outConsumer::add,2, 120);
        assertEquals(0, outConsumer.size());
    }

    @Test
    public void move() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar", 100);
        assertEquals(7, buffer.cursor());
        buffer.move(outConsumer::add, 1, 120);
        assertEquals(7, buffer.cursor());
        buffer.move(outConsumer::add, -1, 120);
        assertEquals(6, buffer.cursor());
        buffer.move(outConsumer::add, -100, 120);
        assertEquals(0, buffer.cursor());
        buffer.move(outConsumer::add, 100, 120);
        assertEquals(7, buffer.cursor());
    }

    @Test
    public void moveAndInsert() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar", 100);
        buffer.move(outConsumer::add, -1, 120);
        buffer.insert(outConsumer::add, 'A', 100);
        outConsumer.clear();
        assertEquals("foo baAr", buffer.asString());
        buffer.move(outConsumer::add, -1, 120);
        outConsumer.clear();
        buffer.insert(outConsumer::add, 'b', 100);
        assertEquals("foo babAr", buffer.asString());
        buffer.move(outConsumer::add, -10, 120);
        outConsumer.clear();
        buffer.insert(outConsumer::add, "foo ", 100);
        assertEquals("foo foo babAr", buffer.asString());
        buffer.move(outConsumer::add, 20, 120);
        outConsumer.clear();
        buffer.insert(outConsumer::add, " bar", 100);
        assertEquals(" bar", Parser.fromCodePoints(outConsumer.get(0)));
    }


    @Test
    public void moveAndDelete() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar", 100);
        buffer.move(outConsumer::add, -3, 120);
        outConsumer.clear();
        buffer.delete(outConsumer::add, -1, 120);
        assertEquals("foobar", buffer.asString());
        buffer.move(outConsumer::add, 1, 120);
        outConsumer.clear();
        buffer.delete(outConsumer::add, -3, 120);
        assertEquals("far", buffer.asString());
        outConsumer.clear();
        buffer.delete(outConsumer::add, 2, 120);
        assertEquals("f", buffer.asString());
        outConsumer.clear();
        buffer.delete(outConsumer::add, 2, 120);
        assertEquals("f", buffer.asString());
        outConsumer.clear();
        buffer.delete(outConsumer::add, -5, 120);
        assertEquals("", buffer.asString());
    }

    @Test
    public void replaceChar() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar", 100);
        outConsumer.clear();
        buffer.replace(outConsumer::add, 'R');
        assertEquals("R", Parser.fromCodePoints(outConsumer.get(0)));
        outConsumer.clear();
        assertEquals("foo bar", buffer.asString());
        buffer.move(outConsumer::add, -1, 120);
        buffer.replace(outConsumer::add, 'R');
        assertEquals("foo baR", buffer.asString());
        buffer.move(outConsumer::add, -4, 120);
        buffer.replace(outConsumer::add, 'O');
        assertEquals("foO baR", buffer.asString());
    }

    @Test
    public void changeCase() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar", 100);
        buffer.move(outConsumer::add, -1, 120);
        outConsumer.clear();
        buffer.upCase(outConsumer::add);
        assertEquals("R", Parser.fromCodePoints(outConsumer.get(0)));
        assertEquals("foo baR", buffer.asString());
        buffer.move(outConsumer::add, -4, 120);
        outConsumer.clear();
        buffer.changeCase(outConsumer::add);
        assertEquals("foO baR", buffer.asString());
        outConsumer.clear();
        buffer.changeCase(outConsumer::add);
        assertEquals("foo baR", buffer.asString());
        buffer.move(outConsumer::add, 10, 120);
        outConsumer.clear();
        buffer.downCase(outConsumer::add);
        assertEquals("foo baR", buffer.asString());
        buffer.move(outConsumer::add, -1, 120);
        outConsumer.clear();
        buffer.downCase(outConsumer::add);
        assertEquals("foo bar", buffer.asString());
    }

    @Test
    public void replace() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar", 100);
        outConsumer.clear();
        buffer.replace(outConsumer::add, " gar", 120);
        assertEquals(" gar",
        Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                outConsumer.get(0).length-4, outConsumer.get(0).length )));
        outConsumer.clear();
        buffer.insert(outConsumer::add, 'd', 100);
        assertEquals(" gard", buffer.asString());
        assertEquals("d",
        Parser.fromCodePoints((outConsumer.get(0))));
    }

    @Test
    public void multiLineBackslash() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo bar\\", 100);
        buffer.setMultiLine(true);
        buffer.updateMultiLineBuffer();
        outConsumer.clear();
        buffer.insert(outConsumer::add, " bar ", 100);
        assertEquals(">  bar ",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-7, outConsumer.get(0).length )));

        assertEquals("foo bar bar ", buffer.asString());

        buffer.insert(outConsumer::add, "\\", 100);
        buffer.updateMultiLineBuffer();
        outConsumer.clear();
        buffer.insert(outConsumer::add, "gar", 100);
        assertEquals("> gar",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-5, outConsumer.get(0).length )));

        assertEquals("foo bar bar gar", buffer.asString());
    }

    @Test
    public void multiLineQuote() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo \"bar", 100);
        buffer.setMultiLine(true);
        buffer.updateMultiLineBuffer();
        outConsumer.clear();
        buffer.insert(outConsumer::add, " bar ", 100);
        assertEquals(">  bar ",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-7, outConsumer.get(0).length )));

        assertEquals("foo \"bar bar ", buffer.asString());

        //buffer.insert(outConsumer::add, "\\", 100);
        buffer.updateMultiLineBuffer();
        outConsumer.clear();
        buffer.insert(outConsumer::add, "gar\"", 100);
        assertEquals("> gar\"",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-6, outConsumer.get(0).length )));

        assertEquals("foo \"bar bar gar\"", buffer.asString());
    }

    @Test
    public void manyLinesInsert() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "1234567890", 100);
        outConsumer.clear();
        buffer.move(outConsumer::add, -10, 5);
        assertArrayEquals(new int[] {27,'[','2','A',27,'[','0','D'}, outConsumer.get(0));

        buffer.insert(outConsumer::add, ' ', 100);
        outConsumer.clear();
        buffer.print(outConsumer::add, 5);
        assertEquals(" 1234567890", buffer.asString());
        outConsumer.clear();
        buffer.move(outConsumer::add, 5, 5);
        assertArrayEquals(new int[] {27,'[','1','B',27,'[','0','D'}, outConsumer.get(0));
        buffer.insert(outConsumer::add, ' ', 100);
        outConsumer.clear();
        buffer.print(outConsumer::add, 5);
        assertEquals(" 12345 67890", buffer.asString());
        buffer.move(outConsumer::add, 13, 5);
        buffer.insert(outConsumer::add, ' ', 100);
        outConsumer.clear();
        buffer.print(outConsumer::add, 5);
        assertEquals(" 12345 67890 ", buffer.asString());
        buffer.move(outConsumer::add, -3, 5);
        buffer.insert(outConsumer::add, ' ', 100);
        outConsumer.clear();
        buffer.print(outConsumer::add, 5);
        assertEquals(" 12345 678 90 ", buffer.asString());
    }

    @Test
    public void replaceMultiLine() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "1234567890", 100);
        outConsumer.clear();
        buffer.replace(outConsumer::add, "foo", 5);
        Assert.assertArrayEquals(ANSI.ERASE_WHOLE_LINE,
                Arrays.copyOfRange(outConsumer.get(0), 0, 4));
        assertArrayEquals(ANSI.MOVE_LINE_UP,
                Arrays.copyOfRange(outConsumer.get(0), 4, 8));
        assertArrayEquals(ANSI.ERASE_WHOLE_LINE,
                Arrays.copyOfRange(outConsumer.get(0), 8, 12));
        assertArrayEquals(ANSI.MOVE_LINE_UP,
                Arrays.copyOfRange(outConsumer.get(0), 12, 16));
        assertArrayEquals(new int[]{27,'[', '5','D'},
                Arrays.copyOfRange(outConsumer.get(0), 16, 20));

        assertEquals("foo", buffer.asString());
    }

    @Test
    public void disablePrompt() {
        Buffer buffer = new Buffer(new Prompt(": "));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo", 100);

        assertEquals(": foo",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-5, outConsumer.get(0).length )));

        buffer.clear();
        buffer.insert(outConsumer::add, "foo", 100);

        assertEquals(": foo",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-5, outConsumer.get(0).length )));

        buffer.clear();
        buffer.disablePrompt(true);
        buffer.insert(outConsumer::add, "foo", 100);

        assertEquals("foo",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-3, outConsumer.get(0).length )));
    }

    @Test
    public void masking() {
        Buffer buffer = new Buffer(new Prompt(": ", '#'));
        List<int[]> outConsumer = new ArrayList<>();
        buffer.insert(outConsumer::add, "foo", 100);

        assertEquals(": ###",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-5, outConsumer.get(0).length )));

        outConsumer.clear();
        buffer.delete(outConsumer::add, -1, 120);

        assertArrayEquals(new int[] {27,'[', '1', 'D'}, Arrays.copyOfRange(outConsumer.get(0), 0, 4 ));
        assertArrayEquals(new int[] {27,'[','K'}, Arrays.copyOfRange(outConsumer.get(0), 4, 7 ));
        assertEquals("fo", buffer.asString());

        buffer = new Buffer(new Prompt(": ", (char) 0));
        outConsumer.clear();
        buffer.insert(outConsumer::add, "foo", 100);

        assertEquals(": ",
                Parser.fromCodePoints(Arrays.copyOfRange(outConsumer.get(0),
                        outConsumer.get(0).length-2, outConsumer.get(0).length )));
        assertEquals("foo", buffer.asString());
        outConsumer.clear();
        buffer.delete(outConsumer::add, -3, 120);
        assertEquals("", buffer.asString());

        buffer.insert(outConsumer::add, "bar", 100);
        assertEquals("bar", buffer.asString());
     }
}
