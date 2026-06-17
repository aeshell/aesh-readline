package org.aesh.readline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.prompt.Prompt;
import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.aesh.terminal.Key;
import org.junit.Test;

/**
 * Tests for multi-line prompt support (#208).
 */
public class MultiLinePromptTest {

    @Test
    public void testSingleLinePromptDefaults() {
        Prompt p = new Prompt("$ ");
        assertEquals(1, p.lineCount());
        assertEquals(2, p.length());
        assertEquals(2, p.getLength()); // deprecated but should work
    }

    @Test
    public void testMultiLinePromptFromString() {
        Prompt p = new Prompt("info line\n$ ");
        assertEquals(2, p.lineCount());
        assertEquals(2, p.length()); // last line "$ " is 2 chars
    }

    @Test
    public void testMultiLinePromptThreeLines() {
        Prompt p = new Prompt("line1\nline2\n> ");
        assertEquals(3, p.lineCount());
        assertEquals(2, p.length()); // last line "> " is 2 chars
    }

    @Test
    public void testMultiLinePromptWithAnsi() {
        Prompt p = new Prompt("\033[36mmyapp on main\033[0m\n\033[32m>\033[0m ");
        assertEquals(2, p.lineCount());
        assertEquals(2, p.length()); // last line visible: "> "
        assertTrue(p.hasANSI());
    }

    @Test
    public void testMultiLinePromptBuilder() {
        Prompt p = Prompt.builder()
                .line("myapp on main via ☕ v21")
                .line("❯ ")
                .build();
        assertEquals(2, p.lineCount());
        assertEquals(2, p.length()); // last line "❯ "
    }

    @Test
    public void testMultiLinePromptBuilderThreeLines() {
        Prompt p = Prompt.builder()
                .line("┌─[myapp]─[git:main]")
                .line("│ context info")
                .line("└─$ ")
                .build();
        assertEquals(3, p.lineCount());
        assertEquals(4, p.length()); // last line "└─$ "
    }

    @Test
    public void testMultiLinePromptCopy() {
        Prompt original = new Prompt("info\n$ ");
        Prompt copy = original.copy();
        assertEquals(original.lineCount(), copy.lineCount());
        assertEquals(original.length(), copy.length());
    }

    @Test
    public void testMultiLinePromptGetAnsi() {
        Prompt p = new Prompt("line1\n$ ");
        int[] ansi = p.getANSI();
        // Should contain the full multi-line content including \n
        String ansiStr = org.aesh.terminal.utils.Parser.fromCodePoints(ansi);
        assertTrue(ansiStr.contains("\n"));
        assertTrue(ansiStr.startsWith("line1"));
        assertTrue(ansiStr.endsWith("$ "));
    }

    @Test
    public void testMultiLinePromptWithRightPrompt() {
        Prompt p = Prompt.builder()
                .line("myapp on main")
                .line("❯ ")
                .rightPrompt("12:30")
                .build();
        assertEquals(2, p.lineCount());
        assertEquals(2, p.length());
        assertEquals("12:30", p.getRightPrompt());
    }

    @Test
    public void testSingleLineMessageStillWorks() {
        // Verify message() still works for single-line prompts
        Prompt p = Prompt.builder()
                .message("$ ")
                .build();
        assertEquals(1, p.lineCount());
        assertEquals(2, p.length());
    }

    @Test
    public void testMultiLinePromptWithReadline() {
        // Two-line prompt, type and submit
        Prompt prompt = new Prompt("info\n> ");
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.setPrompt(prompt);
        term.read(Key.h);
        term.read(Key.i);
        term.read(Key.ENTER);
        term.assertLine("hi");
    }

    @Test
    public void testMultiLinePromptBufferLength() {
        // Verify Buffer uses last-line length for cursor positioning
        Prompt prompt = new Prompt("info line with lots of text\n> ");
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.setPrompt(prompt);
        term.read(Key.a);
        term.read(Key.b);
        term.read(Key.c);
        term.assertBuffer("abc");
        term.read(Key.ENTER);
        term.assertLine("abc");
    }

    @Test
    public void testEmptyPromptLineCount() {
        Prompt p = new Prompt("");
        assertEquals(1, p.lineCount());
        assertEquals(0, p.length());
    }

    @Test
    public void testNullPromptLineCount() {
        Prompt p = new Prompt((String) null);
        assertEquals(1, p.lineCount());
        assertEquals(0, p.length());
    }

    @Test
    public void testBufferCursorDisplayRow() {
        // Single-line prompt on 80-col terminal
        Buffer buf = new Buffer(new Prompt("$ "));
        assertEquals(0, buf.cursorDisplayRow(80));

        // Multi-line prompt — cursor starts on row 1 (after the info line)
        Buffer buf2 = new Buffer(new Prompt("info\n$ "));
        assertEquals(1, buf2.cursorDisplayRow(80));
    }

    @Test
    public void testBufferTotalDisplayRows() {
        // Single-line prompt "$ " with no input on 80-col terminal
        Buffer buf = new Buffer(new Prompt("$ "));
        assertEquals(1, buf.totalDisplayRows(80));

        // Multi-line prompt "info\n$ " with no input
        Buffer buf2 = new Buffer(new Prompt("info\n$ "));
        assertEquals(2, buf2.totalDisplayRows(80));
    }
}
