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
package org.aesh.readline.history;

import org.aesh.readline.terminal.Key;
import org.aesh.readline.tty.terminal.TestConnection;
import org.aesh.utils.Config;
import org.aesh.util.FileAccessPermission;
import org.aesh.util.Parser;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class HistoryTest {

    @Test
    public void testHistory() throws Exception {

        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.EMACS).create());
        term.read("1234"+ Config.getLineSeparator());
        term.readline();
        term.read("567"+Config.getLineSeparator());
        term.readline();
        term.read(Key.UP);
        term.read(Key.UP);
        term.read(Key.ENTER);
        term.assertLine("1234");
        term.readline();
        term.read(Key.UP);
        term.read(Key.UP);
        term.read(Key.ENTER);
        term.assertLine("567");
    }

    @Test
    public void testHistoryMultiLine1() throws Exception {

        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.EMACS).create());
        term.read("1234 \\" + Config.getLineSeparator());
        term.read("567" + Config.getLineSeparator());
        term.readline();
        term.read(Key.UP);
        term.read(Key.ENTER);
        term.assertLine("1234 567");
    }

    @Test
    public void testHistoryMultiLine2() throws Exception {

        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.EMACS).create());
        term.read("1234\\" + Config.getLineSeparator());
        term.read("567" + Config.getLineSeparator());
        term.readline();
        term.read(Key.UP);
        term.read(Key.ENTER);
        term.assertLine("1234567");
    }

    @Test
    public void testReverseSearch() throws Exception {

        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.EMACS).create());
        term.read("1234"+ Config.getLineSeparator());
        term.readline();
        term.read("567"+Config.getLineSeparator());
        term.readline();
        term.read("589"+Config.getLineSeparator());
        term.readline();
        term.clearOutputBuffer();
        term.read(Key.CTRL_R);
        term.assertBuffer("(reverse-i-search) `': ");
        term.clearOutputBuffer();
        term.read("5");
        term.assertBuffer("(reverse-i-search) `5': 589");
        term.clearLineBuffer();
        term.read(Key.ENTER);
        term.assertLine("589");
    }

    @Test
    public void testForwardSearch() throws Exception {

        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.EMACS).create());
        term.read("1234"+ Config.getLineSeparator());
        term.readline();
        term.read("567"+Config.getLineSeparator());
        term.readline();
        term.read("589"+Config.getLineSeparator());
        term.readline();
        term.clearOutputBuffer();
        term.read(Key.CTRL_S);
        term.assertBuffer("(forward-i-search) `': ");
        term.clearOutputBuffer();
        term.read("5");
        term.assertBuffer("(forward-i-search) `5': 567");
        term.clearLineBuffer();
        term.read(Key.ENTER);
        term.assertLine("567");
    }

    @Test
    public void testReverseSearchEscape() throws Exception {

        TestConnection term = new TestConnection(EditModeBuilder.builder(EditMode.Mode.EMACS).create());
        term.read("1234"+ Config.getLineSeparator());
        term.readline();
        term.read("567"+Config.getLineSeparator());
        term.readline();
        term.read("589"+Config.getLineSeparator());
        term.readline();
        term.clearOutputBuffer();
        term.read(Key.CTRL_R);
        term.assertBuffer("(reverse-i-search) `': ");
        term.clearOutputBuffer();
        term.read("5");
        term.assertBuffer("(reverse-i-search) `5': 589");
        term.clearLineBuffer();
        term.clearOutputBuffer();
        term.read(Key.ESC);
        term.assertBuffer("589");
        term.assertLine(null);
        term.read(Key.ENTER);
        term.assertLine("589");
    }


    @Test
    public void testSearch() {
        History history = new InMemoryHistory(20);
        history.push(Parser.toCodePoints("foo1"));
        history.push(Parser.toCodePoints("foo2"));
        history.push(Parser.toCodePoints("foo3"));

        history.setSearchDirection(SearchDirection.REVERSE);
        assertArrayEquals(Parser.toCodePoints("foo3"),history.search(Parser.toCodePoints("foo")) );
        assertArrayEquals(Parser.toCodePoints("foo2"),history.search(Parser.toCodePoints("foo")) );
        assertArrayEquals(Parser.toCodePoints("foo1"),history.search(Parser.toCodePoints("foo")) );

        history.setSearchDirection(SearchDirection.FORWARD);
        assertArrayEquals(Parser.toCodePoints("foo1"),history.search(Parser.toCodePoints("foo")) );
        assertArrayEquals(Parser.toCodePoints("foo2"),history.search(Parser.toCodePoints("foo")) );
        assertArrayEquals(Parser.toCodePoints("foo3"),history.search(Parser.toCodePoints("foo")) );

        history.setSearchDirection(SearchDirection.REVERSE);
        assertArrayEquals(Parser.toCodePoints("foo3"),history.search(Parser.toCodePoints("foo")) );
        assertArrayEquals(Parser.toCodePoints("foo2"),history.search(Parser.toCodePoints("foo")) );
        assertArrayEquals(Parser.toCodePoints("foo1"),history.search(Parser.toCodePoints("foo")) );

        history.setSearchDirection(SearchDirection.REVERSE);

        assertArrayEquals(Parser.toCodePoints("foo3"),history.search(Parser.toCodePoints("foo")) );
        assertArrayEquals(Parser.toCodePoints("foo2"),history.search(Parser.toCodePoints("foo")) );
        assertArrayEquals(Parser.toCodePoints("foo1"),history.search(Parser.toCodePoints("foo")) );
        assertArrayEquals(Parser.toCodePoints("foo3"),history.search(Parser.toCodePoints("foo")) );
    }

    @Test
    public void testSearchAndFetch() {
        History history = new InMemoryHistory(20);
        history.push(Parser.toCodePoints("foo1"));
        history.push(Parser.toCodePoints("foo2"));
        history.push(Parser.toCodePoints("foo3"));

        history.setSearchDirection(SearchDirection.REVERSE);
        assertArrayEquals(Parser.toCodePoints("foo3"),history.search(Parser.toCodePoints("foo")) );
        assertArrayEquals(Parser.toCodePoints("foo2"), history.getPreviousFetch());
    }

    @Test
    public void testHistorySize() {
        History history = new InMemoryHistory(20);

        for(int i=0; i < 25; i++)
            history.push(Parser.toCodePoints(String.valueOf(i)));


        assertEquals(20, history.size());
        assertArrayEquals(Parser.toCodePoints("24"), history.getPreviousFetch());
    }

    @Test
    public void testClear() {
        History history = new InMemoryHistory(10);
        history.push(Parser.toCodePoints("1"));
        history.push(Parser.toCodePoints("2"));

        assertArrayEquals(Parser.toCodePoints("2"), history.getPreviousFetch());
        history.clear();
        assertEquals(null, history.getPreviousFetch());
    }

    @Test
    public void testDupes() {
        History history = new InMemoryHistory(10);
        history.push(Parser.toCodePoints("1"));
        history.push(Parser.toCodePoints("2"));
        history.push(Parser.toCodePoints("3"));
        history.push(Parser.toCodePoints("1"));
        history.push(Parser.toCodePoints("1"));
        assertArrayEquals(Parser.toCodePoints("1"), history.getPreviousFetch());
        assertArrayEquals(Parser.toCodePoints("3"), history.getPreviousFetch());
        assertArrayEquals(Parser.toCodePoints("1"), history.getNextFetch());
        assertArrayEquals(Parser.toCodePoints("3"), history.getPreviousFetch());
        assertArrayEquals(Parser.toCodePoints("2"), history.getPreviousFetch());
        assertArrayEquals(Parser.toCodePoints("1"), history.getPreviousFetch());
        assertEquals(4, history.getAll().size());
    }

    @Test
    public void testFileHistoryPermission() throws IOException{
        if(Config.isOSPOSIXCompatible()) {
            File historyFile = new File(System.getProperty("java.io.tmpdir"), "aesh-history-file.test.1");
            historyFile.deleteOnExit();
            int maxSize = 10;
            FileAccessPermission perm = new FileAccessPermission();
            perm.setExecutable(false);
            perm.setExecutableOwnerOnly(false);
            perm.setReadable(true);
            perm.setReadableOwnerOnly(true);
            perm.setWritable(true);
            perm.setWritableOwnerOnly(true);
            History history = new FileHistory(historyFile, maxSize, perm, false);
            history.push(Parser.toCodePoints("1"));
            history.stop(); // it will write history to local file
            assertTrue(historyFile.canRead());
            assertFalse(historyFile.canExecute());
            assertTrue(historyFile.canWrite());

            historyFile = new File(System.getProperty("java.io.tmpdir"), "aesh-history-file.test.2");
            historyFile.deleteOnExit();
            perm = new FileAccessPermission();
            perm.setExecutable(true);
            perm.setExecutableOwnerOnly(true);
            perm.setReadable(false);
            perm.setReadableOwnerOnly(true);
            perm.setWritable(true);
            perm.setWritableOwnerOnly(true);
            history = new FileHistory(historyFile, maxSize, perm, false);
            history.push(Parser.toCodePoints("1"));
            history.stop(); // it will write history to local file
            assertFalse(historyFile.canRead());
            assertTrue(historyFile.canExecute());
            assertTrue(historyFile.canWrite());

            historyFile = new File(System.getProperty("java.io.tmpdir"), "aesh-history-file.test.3");
            historyFile.deleteOnExit();
            perm = new FileAccessPermission();
            perm.setExecutable(false);
            perm.setExecutableOwnerOnly(true);
            perm.setReadable(false);
            perm.setReadableOwnerOnly(true);
            perm.setWritable(false);
            perm.setWritableOwnerOnly(true);
            history = new FileHistory(historyFile, maxSize, perm, false);
            history.push(Parser.toCodePoints("1"));
            history.stop(); // it will write history to local file
            assertFalse(historyFile.canRead());
            assertFalse(historyFile.canExecute());
            assertFalse(historyFile.canWrite());
        }
    }

    @Test
    public void testPrevHistory() {
        History history = new InMemoryHistory(20);
        history.push(Parser.toCodePoints("foo1"));
        history.push(Parser.toCodePoints("foo2"));
        history.push(Parser.toCodePoints("foo3"));

        assertArrayEquals(Parser.toCodePoints("foo3"), history.getPreviousFetch());
        history.push(Parser.toCodePoints("foo3"));
        assertArrayEquals(Parser.toCodePoints("foo3"), history.getPreviousFetch());

    }
}
