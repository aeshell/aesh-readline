package org.aesh.readline.fuzzy;

import static org.junit.Assert.*;

import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.aesh.terminal.Key;
import org.aesh.terminal.utils.Config;
import org.junit.Test;

/**
 * Integration tests for FuzzySearchHistory using TestReadlineConnection.
 * <p>
 * Tests the end-to-end behavior of the fuzzy search action:
 * opening the search, typing queries, selecting entries, and cancelling.
 * <p>
 * Important: after each {@code term.read(cmd + separator)} that submits a line,
 * the result must be drained with {@code term.assertLine(cmd)} before calling
 * {@code term.readline()} for the next session.
 */
public class FuzzySearchHistoryTest {

    private TestReadlineConnection setupWithHistory(String... commands) {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        for (String cmd : commands) {
            term.read(cmd + Config.getLineSeparator());
            term.assertLine(cmd); // drain the submitted line from the output queue
            term.readline();
        }
        term.clearOutputBuffer();
        return term;
    }

    // ---- Select entry ----

    @Test
    public void testSelectFirstEntryWithEnter() {
        TestReadlineConnection term = setupWithHistory("ls -la", "git status", "mvn clean test");
        term.read(Key.CTRL_R);
        // Empty query — most recent first: "mvn clean test"
        term.read(Key.ENTER);
        // Entry is placed in buffer for editing, not executed
        term.assertBuffer("mvn clean test");
        term.assertLine(null); // not submitted yet
        // User presses Enter to execute
        term.read(Key.ENTER);
        term.assertLine("mvn clean test");
    }

    @Test
    public void testSelectWithTypedQuery() {
        TestReadlineConnection term = setupWithHistory("ls -la", "git status", "mvn clean test");
        term.read(Key.CTRL_R);
        term.read(Key.m);
        term.read(Key.v);
        term.read(Key.n);
        term.read(Key.ENTER);
        term.assertBuffer("mvn clean test");
        term.assertLine(null);
        term.read(Key.ENTER);
        term.assertLine("mvn clean test");
    }

    @Test
    public void testSelectWithPartialMatch() {
        TestReadlineConnection term = setupWithHistory(
                "git commit -m fix", "git push origin main", "gradle build");
        term.read(Key.CTRL_R);
        term.read(Key.g);
        term.read(Key.p);
        term.read(Key.ENTER);
        // "gp" should fuzzy-match "git push origin main" best
        term.assertBuffer("git push origin main");
        term.assertLine(null);
        term.read(Key.ENTER);
        term.assertLine("git push origin main");
    }

    // ---- Cancel ----

    @Test
    public void testCancelWithEsc() {
        TestReadlineConnection term = setupWithHistory("ls -la", "git status");
        term.read(Key.e);
        term.read(Key.c);
        term.read(Key.h);
        term.read(Key.o);
        term.clearOutputBuffer();
        term.read(Key.CTRL_R);
        term.read(Key.ESC);
        term.assertBuffer("echo");
        term.assertLine(null);
        term.read(Key.ENTER);
        term.assertLine("echo");
    }

    // ---- Empty history ----

    @Test
    public void testEmptyHistoryCancel() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.read(Key.CTRL_R);
        term.read(Key.ESC);
        term.read(Key.ENTER);
        term.assertLine("");
    }

    @Test
    public void testEmptyHistoryEnter() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.read(Key.CTRL_R);
        term.read(Key.ENTER);
        // No history — buffer should be empty, not submitted
        term.assertBuffer("");
        term.read(Key.ENTER);
        term.assertLine("");
    }

    // ---- Navigation ----

    @Test
    public void testNavigateDown() {
        TestReadlineConnection term = setupWithHistory("aaa", "bbb", "ccc");
        term.read(Key.CTRL_R);
        term.read(Key.DOWN);
        term.read(Key.ENTER);
        term.assertBuffer("bbb");
        term.read(Key.ENTER);
        term.assertLine("bbb");
    }

    @Test
    public void testNavigateUpDown() {
        TestReadlineConnection term = setupWithHistory("aaa", "bbb", "ccc");
        term.read(Key.CTRL_R);
        term.read(Key.DOWN);
        term.read(Key.DOWN);
        term.read(Key.UP);
        term.read(Key.ENTER);
        term.assertBuffer("bbb");
        term.read(Key.ENTER);
        term.assertLine("bbb");
    }

    // ---- Pre-populated query ----

    @Test
    public void testPrePopulateFromBuffer() {
        TestReadlineConnection term = setupWithHistory(
                "mvn clean test", "mvn clean install", "gradle build");
        term.read(Key.m);
        term.read(Key.v);
        term.read(Key.n);
        term.clearOutputBuffer();
        term.read(Key.CTRL_R);
        term.read(Key.ENTER);
        // Selected entry is placed in buffer, not executed
        String buffer = term.getOutputBuffer();
        // Submit it
        term.read(Key.ENTER);
        String line = term.getLine();
        assertNotNull(line);
        assertTrue("Selected entry should contain 'mvn', got: " + line, line.contains("mvn"));
    }

    // ---- Backspace ----

    @Test
    public void testBackspaceInQuery() {
        TestReadlineConnection term = setupWithHistory("ls -la", "git status", "mvn clean test");
        term.read(Key.CTRL_R);
        term.read(Key.m);
        term.read(Key.v);
        term.read(Key.n);
        term.read(Key.x);
        term.read(Key.BACKSPACE);
        term.read(Key.ENTER);
        term.assertBuffer("mvn clean test");
        term.read(Key.ENTER);
        term.assertLine("mvn clean test");
    }

    // ---- Vi mode ----

    @Test
    public void testFuzzySearchInViMode() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.VI).build());
        term.read("git status" + Config.getLineSeparator());
        term.assertLine("git status");
        term.readline();
        term.read("mvn clean test" + Config.getLineSeparator());
        term.assertLine("mvn clean test");
        term.readline();
        term.clearOutputBuffer();
        term.read(Key.CTRL_R);
        term.read(Key.g);
        term.read(Key.s);
        term.read(Key.ENTER);
        term.assertBuffer("git status");
        term.read(Key.ENTER);
        term.assertLine("git status");
    }

    // ---- Deduplication ----

    @Test
    public void testDuplicateEntriesDeduped() {
        TestReadlineConnection term = setupWithHistory(
                "ls -la", "git status", "ls -la", "git status");
        term.read(Key.CTRL_R);
        term.read(Key.ENTER);
        term.assertBuffer("git status");
        term.read(Key.ENTER);
        term.assertLine("git status");
    }

    @Test
    public void testSecondCtrlRWorks() {
        TestReadlineConnection term = setupWithHistory("aaa", "bbb");
        // First Ctrl+R cycle
        term.read(Key.CTRL_R);
        term.read(Key.ENTER);
        term.assertBuffer("bbb");
        // Submit it
        term.read(Key.ENTER);
        term.assertLine("bbb");
        term.readline();
        // Second Ctrl+R cycle — should work again
        term.read(Key.CTRL_R);
        term.read(Key.ENTER);
        term.assertBuffer("bbb");
        term.read(Key.ENTER);
        term.assertLine("bbb");
    }

    @Test
    public void testCtrlRAfterCancel() {
        TestReadlineConnection term = setupWithHistory("aaa", "bbb");
        // First: open and cancel
        term.read(Key.CTRL_R);
        term.read(Key.ESC);
        term.assertLine(null);
        // Second: open and select — should work
        term.read(Key.CTRL_R);
        term.read(Key.ENTER);
        term.assertBuffer("bbb");
        term.read(Key.ENTER);
        term.assertLine("bbb");
    }

    @Test
    public void testDuplicateNavigateToSecond() {
        TestReadlineConnection term = setupWithHistory(
                "ls -la", "git status", "ls -la", "git status");
        term.read(Key.CTRL_R);
        term.read(Key.DOWN);
        term.read(Key.ENTER);
        term.assertBuffer("ls -la");
        term.read(Key.ENTER);
        term.assertLine("ls -la");
    }
}
