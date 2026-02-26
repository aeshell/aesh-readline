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
package org.aesh.readline.editing;

import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.aesh.terminal.Key;
import org.junit.Test;

/**
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class EmacsModeTest {

    @Test
    public void testSimpleMovementAndEdit() {
        TestReadlineConnection term = new TestReadlineConnection();
        term.setSignalHandler(null);
        term.read("1234");
        term.read(Key.CTRL_D);
        term.assertBuffer("1234");
        term.read(Key.LEFT_2);
        term.read(Key.CTRL_D);
        term.read('5');
        term.assertBuffer("1235");
        term.read(Key.CTRL_A);
        term.read(Key.CTRL_D);
        term.assertBuffer("235");
        term.read(Key.CTRL_F);
        term.read(Key.CTRL_F);
        term.read('6');
        term.assertBuffer("2365");
    }

    @Test
    public void testWordMovementAndEdit() throws Exception {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("foo  bar...  Foo-Bar.");
        term.read(Key.META_b);
        term.read(Key.META_d);
        term.assertBuffer("foo  bar...  Foo-.");
        term.read(Key.CTRL_E);
        term.read(Key.LEFT_2);
        term.read("Bar");
        term.assertBuffer("foo  bar...  Foo-Bar.");
        term.read(Key.CTRL_A);
        term.read(Key.META_f);
        term.read(Key.META_f);
        term.read(Key.META_d);
        term.assertBuffer("foo  bar-Bar.");
        term.read(Key.META_b);
        term.read(Key.CTRL_U);
        term.assertBuffer("bar-Bar.");
    }

    @Test
    public void testWordMovementWithEndAndHome() throws Exception {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("o  ba");
        term.read(Key.HOME);
        term.read("o");
        term.read(Key.END);
        term.read("r");
        term.assertBuffer("oo  bar");
        term.read(Key.CTRL_A);
        term.read("f");
        term.read(Key.CTRL_E);
        term.read(".");
        term.assertBuffer("foo  bar.");
    }

    @Test
    public void testSwitchingEditModes() throws Exception {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("foo  bar...  Foo-Bar.");
        term.read(Key.CTRL_A);
        term.read("A ");
        term.assertBuffer("A foo  bar...  Foo-Bar.");
        term.read(Key.CTRL_E);
        term.read(".");
        term.assertBuffer("A foo  bar...  Foo-Bar..");
        term.read(Key.META_CTRL_J);
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.i);
        term.read("A ");
        term.assertBuffer("A A foo  bar...  Foo-Bar..");
        term.read(Key.ESC);
        term.read(Key.CTRL_E);
        term.read(Key.CTRL_E);
        term.read(".");
        term.assertBuffer("A A foo  bar...  Foo-Bar...");
    }

    @Test
    public void testWordMovementWithArrows() {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("/subsyste=fo/ba:add(pro=abc)");
        term.read(Key.CTRL_LEFT);
        term.read(Key.LEFT_2);
        term.read("p");
        term.assertBuffer("/subsyste=fo/ba:add(prop=abc)");
        term.read(Key.META_b);
        term.read(Key.META_b);
        term.read(Key.LEFT_2);
        term.read("r");
        term.assertBuffer("/subsyste=fo/bar:add(prop=abc)");
        term.read(Key.HOME);
        term.read(Key.CTRL_RIGHT);
        term.read("m");
        term.assertBuffer("/subsystem=fo/bar:add(prop=abc)");
        term.read(Key.META_f);
        term.read("o");
        term.assertBuffer("/subsystem=foo/bar:add(prop=abc)");
    }

    @Test
    public void testCharMovementWithArrows() {
        TestReadlineConnection term = new TestReadlineConnection();
        term.read("foobarfoobarfoo");
        term.read(Key.LEFT_2);
        term.read(Key.LEFT_2);
        term.read(Key.LEFT_2);
        term.read("-");
        term.assertBuffer("foobarfoobar-foo");
        term.read(Key.CTRL_B);
        term.read(Key.CTRL_B);
        term.read(Key.CTRL_B);
        term.read(Key.CTRL_B);
        term.read("-");
        term.assertBuffer("foobarfoo-bar-foo");
        term.read(Key.HOME);
        term.read(Key.RIGHT);
        term.read(Key.RIGHT);
        term.read(Key.RIGHT);
        term.read("-");
        term.assertBuffer("foo-barfoo-bar-foo");
        term.read(Key.CTRL_F);
        term.read(Key.CTRL_F);
        term.read(Key.CTRL_F);
        term.read("-");
        term.assertBuffer("foo-bar-foo-bar-foo");
        term.read(Key.HOME_3);
        term.read("-");
        term.assertBuffer("-foo-bar-foo-bar-foo");
        term.read(Key.END_3);
        term.read("-");
        term.assertBuffer("-foo-bar-foo-bar-foo-");
    }

    @Test
    public void testTransposeChars() {
        TestReadlineConnection term = new TestReadlineConnection();
        // At end of line: swap last two chars
        term.read("abcd");
        term.read(Key.CTRL_T);
        term.assertBuffer("abdc");
        // In middle: swap char before cursor with char at cursor, advance
        term.read(Key.CTRL_A);
        term.read(Key.RIGHT);
        term.read(Key.CTRL_T); // cursor on 'b' (pos 1), swap a<->b
        term.assertBuffer("badc");
        // At beginning (cursor 0): no-op
        term.read(Key.CTRL_A);
        term.read(Key.CTRL_T);
        term.assertBuffer("badc");
        // Single char: no-op
        TestReadlineConnection term2 = new TestReadlineConnection();
        term2.read("x");
        term2.read(Key.CTRL_T);
        term2.assertBuffer("x");
    }

    @Test
    public void testTransposeWords() {
        TestReadlineConnection term = new TestReadlineConnection();
        // Cursor at end: swap last two words
        term.read("foo bar");
        term.read(Key.META_t);
        term.assertBuffer("bar foo");
        // Cursor between words: swap surrounding words
        TestReadlineConnection term2 = new TestReadlineConnection();
        term2.read("aaa bbb ccc");
        term2.read(Key.CTRL_A);
        term2.read(Key.META_f); // move past "aaa"
        term2.read(Key.META_t); // swap "aaa" and "bbb"
        term2.assertBuffer("bbb aaa ccc");
        // Single word: no-op
        TestReadlineConnection term3 = new TestReadlineConnection();
        term3.read("hello");
        term3.read(Key.META_t);
        term3.assertBuffer("hello");
        // Words with extra spaces preserved
        TestReadlineConnection term4 = new TestReadlineConnection();
        term4.read("foo   bar");
        term4.read(Key.META_t);
        term4.assertBuffer("bar   foo");
    }

    @Test
    public void testDeleteHorizontalSpace() {
        // Bind delete-horizontal-space to Ctrl-G for testing
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "delete-horizontal-space")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("foo   bar");
        // Move cursor into the whitespace (position 4)
        term.read(Key.CTRL_A);
        for (int i = 0; i < 4; i++)
            term.read(Key.RIGHT);
        term.read(Key.CTRL_G);
        term.assertBuffer("foobar");
        // No whitespace around cursor: no-op
        TestReadlineConnection term2 = new TestReadlineConnection(editMode);
        term2.read("foobar");
        term2.read(Key.CTRL_A);
        term2.read(Key.RIGHT);
        term2.read(Key.CTRL_G);
        term2.assertBuffer("foobar");
    }

    @Test
    public void testUnixFilenameRubout() {
        // Bind unix-filename-rubout to Ctrl-G for testing
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "unix-filename-rubout")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("/home/user/file.txt");
        term.read(Key.CTRL_G);
        term.assertBuffer("/home/user/");
        term.read(Key.CTRL_G);
        term.assertBuffer("/home/");
        term.read(Key.CTRL_G);
        term.assertBuffer("/");
    }

    @Test
    public void testInsertComment() {
        // Bind insert-comment to Ctrl-G for testing
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "insert-comment")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("echo hello");
        term.read(Key.CTRL_G);
        term.assertLine("#echo hello");
    }

    @Test
    public void testHistoryNavigation() {
        TestReadlineConnection term = new TestReadlineConnection();
        // Enter some commands into history
        term.read("first\n");
        term.assertLine("first");
        term.readline();
        term.read("second\n");
        term.assertLine("second");
        term.readline();
        term.read("third\n");
        term.assertLine("third");
        term.readline();
        // Navigate up through history
        term.read(Key.CTRL_P); // "third"
        term.assertBuffer("third");
        term.read(Key.CTRL_P); // "second"
        term.assertBuffer("second");
        term.read(Key.CTRL_P); // "first"
        term.assertBuffer("first");
        // Navigate back down
        term.read(Key.CTRL_N); // "second"
        term.assertBuffer("second");
        term.read(Key.CTRL_N); // "third"
        term.assertBuffer("third");
    }

    @Test
    public void testRevertLine() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "revert-line")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("hello");
        term.assertBuffer("hello");
        // Make some changes
        term.read(Key.CTRL_A);
        term.read(Key.CTRL_D); // delete 'h' -> "ello"
        term.read("xyz"); // "xyzello"
        term.assertBuffer("xyzello");
        // Revert all changes
        term.read(Key.CTRL_G);
        term.assertBuffer("hello");
    }

    @Test
    public void testTildeExpand() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "tilde-expand")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        String home = System.getProperty("user.home");
        term.read("cd ~/docs");
        // Move cursor into the ~/docs word
        term.read(Key.CTRL_A);
        for (int i = 0; i < 3; i++)
            term.read(Key.RIGHT);
        term.read(Key.CTRL_G);
        term.assertBuffer("cd " + home + "/docs");
    }

    @Test
    public void testYankLastArg() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "yank-last-arg")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        // Enter a command into history
        term.read("git commit -m message\n");
        term.assertLine("git commit -m message");
        term.readline();
        // Now yank the last arg
        term.read("echo ");
        term.read(Key.CTRL_G);
        term.assertBuffer("echo message");
    }

    @Test
    public void testCharacterSearch() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "character-search")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("abcdefgh");
        term.read(Key.CTRL_A);
        // Search for 'e'
        term.read(Key.CTRL_G);
        term.read(Key.e);
        // Cursor should be at position 4 (on 'e')
        term.read('X');
        term.assertBuffer("abcdXefgh");
    }

    @Test
    public void testCharacterSearchBackward() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "character-search-backward")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("abcdefgh");
        // Cursor at end (pos 8), search backward for 'c'
        term.read(Key.CTRL_G);
        term.read(Key.c);
        // Cursor should be at position 2 (on 'c')
        term.read('X');
        term.assertBuffer("abXcdefgh");
    }

    @Test
    public void testQuotedInsert() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "quoted-insert")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("hello");
        term.read(Key.CTRL_A);
        // Use quoted-insert to insert a character at cursor position
        term.read(Key.CTRL_G);
        term.read(Key.x);
        term.assertBuffer("xhello");
        // Verify we can do it again
        term.read(Key.CTRL_G);
        term.read(Key.y);
        term.assertBuffer("xyhello");
    }

    @Test
    public void testHistorySearchBackward() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "history-search-backward")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        // Enter some commands into history
        term.read("git status\n");
        term.assertLine("git status");
        term.readline();
        term.read("git commit\n");
        term.assertLine("git commit");
        term.readline();
        term.read("ls -la\n");
        term.assertLine("ls -la");
        term.readline();
        // Type prefix and search backward
        term.read("git");
        term.read(Key.CTRL_G);
        term.assertBuffer("git commit");
        term.read(Key.CTRL_G);
        term.assertBuffer("git status");
    }

    @Test
    public void testHistorySearchForward() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "history-search-forward")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        // Enter some commands into history
        term.read("git status\n");
        term.assertLine("git status");
        term.readline();
        term.read("git commit\n");
        term.assertLine("git commit");
        term.readline();
        term.read("ls -la\n");
        term.assertLine("ls -la");
        term.readline();
        // Type prefix and search forward from beginning
        term.read("git");
        term.read(Key.CTRL_G);
        term.assertBuffer("git status");
        term.read(Key.CTRL_G);
        term.assertBuffer("git commit");
    }

    @Test
    public void testSetMarkAndKillRegion() {
        // Bind set-mark to CTRL_G and kill-region to META_CTRL_J (already used for mode switch,
        // but we override here since we build a custom EditMode)
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "set-mark")
                .addAction(Key.CTRL_X_CTRL_U.getKeyValues(), "kill-region")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("hello world");
        // Move cursor to position 5 (after "hello")
        term.read(Key.CTRL_A);
        for (int i = 0; i < 5; i++)
            term.read(Key.RIGHT);
        // Set mark at position 5
        term.read(Key.CTRL_G);
        // Move to end
        term.read(Key.CTRL_E);
        // Kill region from mark (5) to cursor (11) → kills " world"
        term.read(Key.CTRL_X_CTRL_U);
        term.assertBuffer("hello");
    }

    @Test
    public void testExchangePointAndMark() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "set-mark")
                .addAction(Key.CTRL_X_CTRL_U.getKeyValues(), "exchange-point-and-mark")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("abcdefgh");
        // Move cursor to position 3
        term.read(Key.CTRL_A);
        for (int i = 0; i < 3; i++)
            term.read(Key.RIGHT);
        // Set mark at position 3
        term.read(Key.CTRL_G);
        // Move to position 6
        for (int i = 0; i < 3; i++)
            term.read(Key.RIGHT);
        // Exchange: cursor goes to 3, mark goes to 6
        term.read(Key.CTRL_X_CTRL_U);
        // Insert a char to verify cursor is at position 3
        term.read('X');
        term.assertBuffer("abcXdefgh");
    }

    @Test
    public void testOverwriteMode() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "overwrite-mode")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("abcdef");
        // Move cursor to position 2
        term.read(Key.CTRL_A);
        term.read(Key.RIGHT);
        term.read(Key.RIGHT);
        // Toggle overwrite mode
        term.read(Key.CTRL_G);
        // Type 'X' — should replace 'c', not insert
        term.read('X');
        term.assertBuffer("abXdef");
        // Type 'Y' — should replace 'd'
        term.read('Y');
        term.assertBuffer("abXYef");
        // Toggle back to insert mode
        term.read(Key.CTRL_G);
        // Type 'Z' — should insert before 'e'
        term.read('Z');
        term.assertBuffer("abXYZef");
    }

    @Test
    public void testYankNthArg() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "yank-nth-arg")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        // Enter a command into history
        term.read("git commit -m message\n");
        term.assertLine("git commit -m message");
        term.readline();
        // Yank first arg (second word = "commit")
        term.read("echo ");
        term.read(Key.CTRL_G);
        term.assertBuffer("echo commit");
    }

    @Test
    public void testPossibleCompletions() {
        // possible-completions delegates to the completer, similar to complete
        // We verify it doesn't crash when there's no completer
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "possible-completions")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("test");
        // Should be a no-op when no completer is set
        term.read(Key.CTRL_G);
        term.assertBuffer("test");
    }

    @Test
    public void testNonIncrementalReverseSearch() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "non-incremental-reverse-search-history")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        // Build some history
        term.read("echo hello\n");
        term.assertLine("echo hello");
        term.readline();
        term.read("git status\n");
        term.assertLine("git status");
        term.readline();
        term.read("echo world\n");
        term.assertLine("echo world");
        term.readline();
        // Start non-incremental reverse search
        term.read(Key.CTRL_G);
        // Type search term and press Enter
        term.read("echo");
        term.read(Key.ENTER);
        // Should find "echo world" (most recent match searching backward)
        term.assertBuffer("echo world");
    }

    @Test
    public void testNonIncrementalForwardSearch() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "non-incremental-forward-search-history")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        // Build some history
        term.read("echo hello\n");
        term.assertLine("echo hello");
        term.readline();
        term.read("git status\n");
        term.assertLine("git status");
        term.readline();
        term.read("echo world\n");
        term.assertLine("echo world");
        term.readline();
        // Start non-incremental forward search
        term.read(Key.CTRL_G);
        // Type search term and press Enter
        term.read("echo");
        term.read(Key.ENTER);
        // Should find "echo hello" (first match searching forward)
        term.assertBuffer("echo hello");
    }

    @Test
    public void testNonIncrementalSearchCancel() {
        EditMode editMode = EditModeBuilder.builder()
                .addAction(Key.CTRL_G.getKeyValues(), "non-incremental-reverse-search-history")
                .create();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("echo hello\n");
        term.assertLine("echo hello");
        term.readline();
        // Start search then cancel with ESC
        term.read(Key.CTRL_G);
        term.read("ech");
        term.read(Key.ESC);
        // Buffer should be empty after cancel
        term.assertBuffer("");
    }
}
