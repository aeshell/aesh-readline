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
package org.aesh.readline;

import java.io.IOException;
import org.aesh.readline.cursor.CursorLocation;
import org.aesh.readline.cursor.CursorLocator;
import org.aesh.readline.cursor.Line;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.tty.terminal.TerminalConnection;
import org.junit.Assert;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class CursorLocatorTest {

    private static final String PROMPT = "test> ";
    private static final String MULTI_LINE_PROMPT = "> ";
    private static final int WIDTH = 80;
    @Test
    public void test() {

        { // No cmd
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            check(buffer, 0, 0, PROMPT.length(), WIDTH);
        }

        { // Index is after buffer of size 0.
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            CursorLocator locator = buffer.getCursorLocator();
            CursorLocation loc = locator.locate(10, WIDTH);
            assertTrue(loc == null);
        }

        { // Nominal, retrieve index after the prompt inside a cmd.
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1";
            int offset = 3;
            buffer.insert((c) -> {
            }, cmd, WIDTH);
            check(buffer, offset, 0, PROMPT.length() + offset, WIDTH);
        }
    }

    @Test
    public void testWrapping() {

        { // Nominal, retrieve index after the prompt inside a cmd longer than
          // terminal width.
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1";
            int width = PROMPT.length() + (cmd.length() / 2);
            int offset = (cmd.length() / 2);
            buffer.insert((c) -> {
            }, cmd, width);
            check(buffer, offset, 1, 0, width);
            checkCursor(buffer, 1, offset, width);
        }

        { // Nominal, retrieve index after the prompt inside a cmd longer than
            // terminal width.
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1";
            int width = PROMPT.length() + (cmd.length() / 2);
            int offset = (cmd.length() / 2) - 1;
            buffer.insert((c) -> {
            }, cmd, width);
            check(buffer, offset, 0, width - 1, width);
        }

        { // Set a cmd as large as the width.
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1";
            int width = PROMPT.length() + cmd.length();
            int offset = cmd.length() - 1;
            buffer.insert((c) -> {
            }, cmd, width);
            check(buffer, offset, 0, offset + PROMPT.length(), width);
            checkCursor(buffer, 1, 0, width);
        }
    }

    @Test
    public void testMultiline() {

        {
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1\\";
            buffer.insert((c) -> {
            }, cmd, WIDTH);
            buffer.setMultiLine(true);
            buffer.updateMultiLineBuffer();
            String cmd2 = "--opt2";
            buffer.insert((c) -> {
            }, cmd2, WIDTH);
            int offset = cmd.length() - 1 + cmd2.length();
            check(buffer, offset, 1, cmd2.length() + MULTI_LINE_PROMPT.length(), WIDTH);
            checkCursor(buffer, 1, cmd2.length() + MULTI_LINE_PROMPT.length(), WIDTH);
        }

        {// Check that the cursor location is on col=MULTI_LINE_PROMPT, row=1
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1";
            buffer.insert((c) -> {
            }, cmd + "\\", WIDTH);
            buffer.setMultiLine(true);
            buffer.updateMultiLineBuffer();
            int offset = cmd.length() - 1;
            check(buffer, offset, 0, cmd.length() + PROMPT.length() - 1, WIDTH);
            checkCursor(buffer, 1, MULTI_LINE_PROMPT.length(), WIDTH);
        }

        { //Wrapped and multiline.
            Buffer buffer = new Buffer(new Prompt(PROMPT));
            String cmd = "cmd --opt1 --opt2 --opt3\\";
            int width = PROMPT.length() + (cmd.length() / 2);
            buffer.insert((c) -> {
            }, cmd, width);
            buffer.setMultiLine(true);
            buffer.updateMultiLineBuffer();
            String cmd2 = "--opt4";
            buffer.insert((c) -> {
            }, cmd2, width);
            int offset = cmd.length() - 1 + cmd2.length();
            check(buffer, offset, 2, cmd2.length() + MULTI_LINE_PROMPT.length(), width);
        }
    }

    @Test
    public void lineTest() throws IOException {
        TerminalConnection connection = new TerminalConnection();
        Buffer buffer = new Buffer(new Prompt(PROMPT));
        String cmd1 = "cmd --opt1 --opt2 ";
        String cmd2 = "--opt3 --opt4";
        String cmd = cmd1 + cmd2;
        buffer.insert((c) -> {
        }, cmd1 + "\\", WIDTH);
        buffer.setMultiLine(true);
        buffer.updateMultiLineBuffer();
        buffer.insert((c) -> {
        }, cmd2, WIDTH);
        Line line = new Line(buffer, connection, WIDTH);
        String s = line.getLineToCursor();
        Assert.assertEquals(cmd, s);
        Assert.assertFalse(line.getCursorLocator() == null);
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().move(10).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().moveBackward(10).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().moveForward(10).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().moveDown(10).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().moveUp(10).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
        line.newCursorTransactionBuilder().colorize(10, Color.DEFAULT, Color.DEFAULT,
                true).build().run();
        Assert.assertEquals(buffer.multiCursor(), s.length());
    }

    private static void checkCursor(Buffer buffer, int row, int col, int width) {
        int c = buffer.multiCursor();
        check(buffer, c, row, col, width);
    }

    private static void check(Buffer buffer, int offset, int row, int col, int width) {
        CursorLocation cursorLoc = buffer.getCursorLocator().locate(offset, width);
        assertTrue("Invalid column " + cursorLoc.getColumn()
                + ". Expected " + col,
                cursorLoc.getColumn() == col);
        assertTrue("Invalid row " + cursorLoc.getRow() + ". Expected " + row,
                cursorLoc.getRow() == row);
    }
}
