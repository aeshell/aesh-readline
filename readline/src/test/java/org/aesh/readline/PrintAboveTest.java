package org.aesh.readline;

import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.aesh.terminal.Key;
import org.junit.Test;

/**
 * Tests for Connection.printAbove() — printing text above the current prompt
 * without disrupting the user's input.
 */
public class PrintAboveTest {

    /**
     * printAbove while readline is active should preserve the buffer content.
     */
    @Test
    public void testPrintAbovePreservesBuffer() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());

        // Type some text
        term.read(Key.h);
        term.read(Key.e);
        term.read(Key.l);
        term.read(Key.l);
        term.read(Key.o);
        term.assertBuffer("hello");

        // Print above
        term.printAbove("notification message");

        // Buffer content should be preserved
        term.assertBuffer("hello");

        // Should still be able to type and submit
        term.read(Key.ENTER);
        term.assertLine("hello");
    }

    /**
     * Multiple readline sessions — printAbove works across sessions.
     */
    @Test
    public void testPrintAboveAcrossSessions() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());

        // First session
        term.read(Key.a);
        term.printAbove("first notification");
        term.assertBuffer("a");
        term.read(Key.ENTER);
        term.assertLine("a");

        // Second session (TestReadlineConnection auto-restarts readline)
        term.readline();
        term.read(Key.b);
        term.printAbove("second notification");
        term.assertBuffer("b");
        term.read(Key.ENTER);
        term.assertLine("b");
    }

    /**
     * printAbove with empty string should be a no-op.
     */
    @Test
    public void testPrintAboveEmptyString() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.read(Key.a);
        term.read(Key.b);
        term.assertBuffer("ab");

        term.printAbove("");
        term.assertBuffer("ab");

        term.printAbove(null);
        term.assertBuffer("ab");
    }

    /**
     * printAbove with multiple lines of text.
     */
    @Test
    public void testPrintAboveMultipleLines() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.read(Key.x);
        term.assertBuffer("x");

        term.printAbove("line 1\nline 2\nline 3");
        term.assertBuffer("x");

        term.read(Key.ENTER);
        term.assertLine("x");
    }

    /**
     * printAbove should work when cursor is in the middle of the buffer.
     */
    @Test
    public void testPrintAboveWithCursorInMiddle() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.read(Key.a);
        term.read(Key.b);
        term.read(Key.c);
        term.read(Key.d);
        // Move cursor left twice — cursor is now between 'b' and 'c'
        term.read(Key.LEFT);
        term.read(Key.LEFT);

        term.printAbove("above text");

        // Buffer content should be preserved
        term.assertBuffer("abcd");

        // Typing should insert at cursor position (between b and c)
        term.read(Key.X);
        term.assertBuffer("abXcd");
    }

    /**
     * printAbove from a separate thread should work (thread safety).
     */
    @Test
    public void testPrintAboveFromAnotherThread() throws InterruptedException {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.read(Key.h);
        term.read(Key.i);
        term.assertBuffer("hi");

        // Print from another thread
        Thread t = new Thread(() -> term.printAbove("async message"));
        t.start();
        t.join(1000);

        term.assertBuffer("hi");
        term.read(Key.ENTER);
        term.assertLine("hi");
    }

    /**
     * Multiple printAbove calls in sequence.
     */
    @Test
    public void testMultiplePrintAbove() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.read(Key.a);

        term.printAbove("msg 1");
        term.printAbove("msg 2");
        term.printAbove("msg 3");

        term.assertBuffer("a");
        term.read(Key.ENTER);
        term.assertLine("a");
    }

    /**
     * printAbove on empty buffer.
     */
    @Test
    public void testPrintAboveEmptyBuffer() {
        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.assertBuffer("");

        term.printAbove("notification");
        term.assertBuffer("");

        term.read(Key.y);
        term.assertBuffer("y");
        term.read(Key.ENTER);
        term.assertLine("y");
    }
}
