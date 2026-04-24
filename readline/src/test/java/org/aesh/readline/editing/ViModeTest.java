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
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class ViModeTest {

    @Test
    public void testSimpleMovementAndEdit() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("abcd");
        term.read(Key.ESC);
        term.read(Key.x);
        term.assertBuffer("abc");
        term.read(Key.h);
        term.read(Key.s);
        term.read(Key.T);
        term.assertBuffer("aTc");
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.x);
        term.assertBuffer("Tc");

        term.read(Key.l);
        term.read(Key.a);
        term.read(Key.o);
        term.assertBuffer("Tco");
        term.read(Key.ENTER);
        term.assertLine("Tco");

        term.readline();
        term.read("123");
        term.read(Key.ESC);
        term.assertBuffer("123");
        term.read(Key.ONE);
        term.read(Key.z);
        term.assertBuffer("123");
    }

    @Test
    public void testWordMovementAndEdit() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("  ..");
        term.read(Key.ESC);
        term.read(Key.b);
        term.read(Key.x);
        term.assertBuffer("  .");
        term.read(Key.ZERO);
        term.read(Key.D);
        term.assertBuffer("");
        term.read(Key.i);
        term.read("foo bar");
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.w);
        term.read(Key.x);
        term.read(Key.ENTER);
        term.assertLine("foo ar");
    }

    @Test
    public void testWordMovementAndEdit2() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("foo  bar...  Foo-Bar.");
        term.read(Key.ESC);
        term.read(Key.B);
        term.read(Key.d);
        term.read(Key.b);

        term.assertBuffer("foo  barFoo-Bar.");

        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.W);
        term.read(Key.d);
        term.read(Key.w);

        term.assertBuffer("foo  -Bar.");
        term.read(Key.ENTER);
        term.assertLine("foo  -Bar.");
    }

    @Test
    public void testWordMovementAndEdit3() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("foo bar... Bar");
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.w);
        term.read(Key.h);
        term.read(Key.W);
        term.read(Key.D);
        term.read(Key.ENTER);

        term.assertLine("foo ");
    }

    @Test
    public void testEnter() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("foo bar");
        term.read(Key.ENTER);
        term.assertLine("foo bar");

        term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("bar");
        term.read(Key.ESC);
        term.read(Key.CTRL_M);

        term.assertLine("bar");
    }

    @Test
    public void testRepeatAndEdit() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());

        term.read("/cd /home/foo/ ls/ cd Desktop/ ls ../");
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.w);
        term.read(Key.w);
        term.read(Key.w);
        term.read(Key.w);
        term.read(Key.w);
        term.read(Key.c);
        term.read(Key.w);
        term.read(Key.b);
        term.read(Key.a);
        term.read(Key.r);
        term.read(Key.ESC);
        term.read(Key.W);
        term.read(Key.d);
        term.read(Key.w);
        term.read(Key.PERIOD);

        term.assertBuffer("/cd /home/bar/ cd Desktop/ ls ../");
        term.read(Key.DOLLAR);
        term.read(Key.d);
        term.read(Key.ZERO);
        term.assertBuffer("/");

        term.read(Key.C);
        term.read("/cd /home/foo/ ls/ cd Desktop/ ls ../");
        term.read(Key.ESC);
        term.read(Key.B);
        term.read(Key.D);
        term.assertBuffer("/cd /home/foo/ ls/ cd Desktop/ ls ");
        term.read(Key.B);
        term.read(Key.PERIOD);
        term.assertBuffer("/cd /home/foo/ ls/ cd Desktop/ ");
        term.read(Key.B);
        term.read(Key.PERIOD);

        term.assertBuffer("/cd /home/foo/ ls/ cd ");
        term.read(Key.ENTER);
        term.assertLine("/cd /home/foo/ ls/ cd ");
    }

    @Test
    public void testTildeAndEdit() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());

        term.read("apt-get install vIM");
        term.read(Key.ESC);
        term.read(Key.b);
        term.read(Key.TILDE);
        term.read(Key.TILDE);
        term.read(Key.TILDE);

        term.assertBuffer("apt-get install Vim");

        term.read(Key.ZERO);
        term.read(Key.w);
        term.read(Key.w);
        term.read(Key.c);
        term.read(Key.w);

        term.read("cache");
        term.assertBuffer("apt-cache install Vim");

        term.read(Key.ESC);
        term.read(Key.w);
        term.read(Key.c);
        term.read(Key.w);

        term.read("search");
        term.read(Key.ENTER);

        term.assertLine("apt-cache search Vim");
    }

    @Test
    public void testPasteAndEdit() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("apt-get install vIM");
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.d);
        term.read(Key.W);
        term.read(Key.w);
        term.read(Key.P);
        term.read(Key.W);
        term.read(Key.y);
        term.read(Key.w);
        term.read(Key.DOLLAR);
        term.read(Key.p);

        term.read(Key.ENTER);
        term.assertLine("install apt-get vIMvIM");
    }

    @Test
    public void testSearch() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("asdf jkl\n");
        term.readline();
        term.read("footing\n");
        term.readline();

        term.read(Key.CTRL_R);
        term.read(Key.a);

        term.assertBuffer("(reverse-i-search) `a': asdf jkl");
    }

    @Test
    public void testSearchWithArrownRight() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("asdf jkl\n");
        term.readline();
        term.read("footing\n");
        term.readline();

        term.read(Key.CTRL_R);
        term.read(Key.a);
        term.read(Key.RIGHT_2);

        term.assertBuffer("asdf jkl");

        term.read(Key.a);
        term.assertBuffer("asdf jkla");
    }

    @Test
    public void testSearchWithArrownLeft() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("asdf jkl\n");
        term.readline();
        term.read("footing\n");
        term.readline();

        term.read(Key.CTRL_R);
        term.read(Key.a);
        term.read(Key.LEFT_2);

        term.assertBuffer("asdf jkl");
    }

    @Test
    public void testSearchWithArrownUp() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("footing\n");
        term.readline();
        term.read("asdf jkl\n");
        term.readline();

        term.read(Key.CTRL_R);
        term.read(Key.a);
        term.read(Key.UP_2);

        term.assertBuffer("footing");
    }

    @Test
    public void testSearchWithArrownDown() {
        TestReadlineConnection term = new TestReadlineConnection(EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("asdf jkl\n");
        term.readline();
        term.read("footing\n");
        term.readline();

        term.read(Key.CTRL_R);
        term.read(Key.a);
        term.read(Key.DOWN_2);
        term.assertBuffer("footing");
    }

    @Test
    public void testTransposeChars() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_T.getKeyValues(), "transpose-chars")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        // In insert mode: at end of line, swap last two chars
        term.read("abcd");
        term.read(Key.CTRL_T);
        term.assertBuffer("abdc");
        // Switch to command mode, move back, switch to insert mode and transpose
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.l);
        term.read(Key.i);
        term.read(Key.CTRL_T); // cursor at 1, swap a<->b
        term.assertBuffer("badc");
    }

    @Test
    public void testTransposeWords() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_T.getKeyValues(), "transpose-words")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        // At end of line: swap last two words
        term.read("foo bar");
        term.read(Key.CTRL_T);
        term.assertBuffer("bar foo");
        // With extra spaces preserved
        TestReadlineConnection term2 = new TestReadlineConnection(editMode);
        term2.read("hello   world");
        term2.read(Key.CTRL_T);
        term2.assertBuffer("world   hello");
    }

    @Test
    public void testDeleteHorizontalSpace() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "delete-horizontal-space")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("foo   bar");
        // Use Vi command mode to navigate into the whitespace, then insert mode to trigger action
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.w); // move to 'b' in command mode
        term.read(Key.i); // enter insert mode (cursor before 'b', inside trailing space)
        term.read(Key.CTRL_G);
        term.assertBuffer("foobar");
    }

    @Test
    public void testUnixFilenameRubout() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "unix-filename-rubout")
                .build();
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
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "insert-comment")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("ls -la");
        term.read(Key.CTRL_G);
        term.assertLine("#ls -la");
    }

    @Test
    public void testInsertCommentAfterViEditing() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "insert-comment")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("apt-get install vim");
        // Use Vi commands to change "apt-get" to "apt-cache" using big-word change
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.c);
        term.read(Key.W);
        term.read("apt-cache ");
        // Now comment out the edited line
        term.read(Key.CTRL_G);
        term.assertLine("#apt-cache install vim");
    }

    @Test
    public void testRevertLineAfterViEdits() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "revert-line")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("hello world");
        term.assertBuffer("hello world");
        // Make some changes using Vi commands
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.x); // delete 'h' -> "ello world"
        term.read(Key.DOLLAR);
        term.read(Key.x); // delete 'd' -> "ello worl"
        // Switch back to insert mode and revert
        term.read(Key.i);
        term.read(Key.CTRL_G);
        term.assertBuffer("hello world");
    }

    @Test
    public void testHistorySearchBackward() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "history-search-backward")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
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
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "history-search-forward")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
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
    public void testCharacterSearch() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "character-search")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("abcdefgh");
        // Move to beginning using Vi
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.i);
        // Search forward for 'f'
        term.read(Key.CTRL_G);
        term.read(Key.f);
        // Insert at cursor position to verify we moved to 'f'
        term.read('X');
        term.assertBuffer("abcdeXfgh");
    }

    @Test
    public void testCharacterSearchBackward() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "character-search-backward")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("abcdefgh");
        // Cursor at end, search backward for 'c'
        term.read(Key.CTRL_G);
        term.read(Key.c);
        // Insert at cursor to verify position
        term.read('X');
        term.assertBuffer("abXcdefgh");
    }

    @Test
    public void testYankLastArg() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "yank-last-arg")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("git commit -m message\n");
        term.assertLine("git commit -m message");
        term.readline();
        // In insert mode, yank the last arg from previous command
        term.read("echo ");
        term.read(Key.CTRL_G);
        term.assertBuffer("echo message");
    }

    @Test
    public void testYankNthArg() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "yank-nth-arg")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("git commit -m message\n");
        term.assertLine("git commit -m message");
        term.readline();
        // Yank first arg (second word = "commit")
        term.read("echo ");
        term.read(Key.CTRL_G);
        term.assertBuffer("echo commit");
    }

    @Test
    public void testSetMarkAndKillRegion() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "set-mark")
                .addAction(Key.CTRL_X_CTRL_U.getKeyValues(), "kill-region")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("hello world");
        // Navigate using Vi command mode to position 5
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.w); // move to 'w' in "world"
        // Switch to insert mode and set mark (cursor before 'w' = position 6)
        term.read(Key.i);
        term.read(Key.CTRL_G);
        // Move to beginning and kill region
        term.read(Key.HOME);
        term.read(Key.CTRL_X_CTRL_U);
        term.assertBuffer("world");
    }

    @Test
    public void testExchangePointAndMark() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "set-mark")
                .addAction(Key.CTRL_X_CTRL_U.getKeyValues(), "exchange-point-and-mark")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("abcdefgh");
        // Move to position 3 using Vi
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.l);
        term.read(Key.l);
        term.read(Key.l);
        // Enter insert mode and set mark at position 3
        term.read(Key.i);
        term.read(Key.CTRL_G);
        // Move to end
        term.read(Key.END);
        // Exchange point and mark: cursor goes to 3
        term.read(Key.CTRL_X_CTRL_U);
        // Insert to verify cursor is at position 3
        term.read('X');
        term.assertBuffer("abcXdefgh");
    }

    @Test
    public void testOverwriteMode() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "overwrite-mode")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("abcdef");
        // Navigate to position 2 using Vi command mode
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.l);
        term.read(Key.l);
        // Enter insert mode
        term.read(Key.i);
        // Toggle overwrite mode
        term.read(Key.CTRL_G);
        // Type 'X' — should replace 'c'
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
    public void testQuotedInsert() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "quoted-insert")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("hello");
        // Move to beginning with Vi
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.i);
        // Use quoted-insert
        term.read(Key.CTRL_G);
        term.read(Key.x);
        term.assertBuffer("xhello");
    }

    @Test
    public void testTildeExpand() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "tilde-expand")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        String home = System.getProperty("user.home");
        term.read("cd ~/docs");
        // Move cursor into the ~/docs word
        term.read(Key.ESC);
        term.read(Key.ZERO);
        term.read(Key.w); // move to '~'
        term.read(Key.i); // insert mode
        term.read(Key.CTRL_G);
        term.assertBuffer("cd " + home + "/docs");
    }

    @Test
    public void testNonIncrementalReverseSearch() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "non-incremental-reverse-search-history")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
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
        term.read("echo");
        term.read(Key.ENTER);
        // Should find "echo world" (most recent match searching backward)
        term.assertBuffer("echo world");
    }

    @Test
    public void testNonIncrementalForwardSearch() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "non-incremental-forward-search-history")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
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
        term.read("echo");
        term.read(Key.ENTER);
        // Should find "echo hello" (first match searching forward)
        term.assertBuffer("echo hello");
    }

    @Test
    public void testNonIncrementalSearchCancel() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "non-incremental-reverse-search-history")
                .build();
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

    @Test
    public void testBeginningAndEndOfHistory() {
        EditMode editMode = EditModeBuilder.builder(EditMode.Mode.VI)
                .addAction(Key.CTRL_G.getKeyValues(), "beginning-of-history")
                .addAction(Key.CTRL_X_CTRL_U.getKeyValues(), "end-of-history")
                .build();
        TestReadlineConnection term = new TestReadlineConnection(editMode);
        term.read("first\n");
        term.assertLine("first");
        term.readline();
        term.read("second\n");
        term.assertLine("second");
        term.readline();
        term.read("third\n");
        term.assertLine("third");
        term.readline();
        // Jump to beginning of history
        term.read(Key.CTRL_G);
        term.assertBuffer("first");
        // Jump to end of history (should restore current empty line)
        term.read(Key.CTRL_X_CTRL_U);
        term.assertBuffer("");
    }

}
