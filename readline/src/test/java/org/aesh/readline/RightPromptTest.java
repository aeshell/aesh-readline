package org.aesh.readline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.prompt.Prompt;
import org.aesh.readline.tty.terminal.TestReadlineConnection;
import org.aesh.terminal.Key;
import org.aesh.terminal.utils.Config;
import org.junit.Test;

/**
 * Tests for right prompt support.
 */
public class RightPromptTest {

    @Test
    public void testPromptWithRightPrompt() {
        Prompt prompt = Prompt.builder()
                .message("> ")
                .rightPrompt("git:main")
                .build();

        assertEquals("git:main", prompt.getRightPrompt());
        assertEquals(2, prompt.getLength()); // left prompt length unchanged
    }

    @Test
    public void testPromptWithoutRightPrompt() {
        Prompt prompt = new Prompt("> ");
        assertNull(prompt.getRightPrompt());
    }

    @Test
    public void testPromptCopyWithRightPrompt() {
        Prompt original = Prompt.builder()
                .message("$ ")
                .rightPrompt("info")
                .build();

        Prompt copy = original.copy();
        assertEquals("info", copy.getRightPrompt());
        assertEquals(original.getLength(), copy.getLength());
    }

    @Test
    public void testPromptBuilderRightPromptNull() {
        Prompt prompt = Prompt.builder()
                .message("> ")
                .build();

        assertNull(prompt.getRightPrompt());
    }

    @Test
    public void testPromptSetRightPrompt() {
        Prompt prompt = new Prompt("> ");
        assertNull(prompt.getRightPrompt());

        prompt.setRightPrompt("time: 12:00");
        assertEquals("time: 12:00", prompt.getRightPrompt());

        prompt.setRightPrompt(null);
        assertNull(prompt.getRightPrompt());
    }

    @Test
    public void testRightPromptWithReadline() {
        Prompt prompt = Prompt.builder()
                .message("> ")
                .rightPrompt("right")
                .build();

        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.setPrompt(prompt);
        // Type and submit — right prompt shouldn't affect the input
        term.read(Key.h);
        term.read(Key.i);
        term.read(Key.ENTER);
        term.assertLine("hi");
    }

    @Test
    public void testRightPromptDoesNotAffectBuffer() {
        Prompt prompt = Prompt.builder()
                .message("$ ")
                .rightPrompt("status")
                .build();

        TestReadlineConnection term = new TestReadlineConnection(
                EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        term.setPrompt(prompt);
        term.read("hello" + Config.getLineSeparator());
        term.assertLine("hello");
    }

    @Test
    public void testBuilderAllFieldsWithRightPrompt() {
        Prompt prompt = Prompt.builder()
                .message("prompt> ")
                .rightPrompt("info")
                .mask('*')
                .build();

        assertEquals("info", prompt.getRightPrompt());
        assertEquals('*', (char) prompt.getMask());
        assertNotNull(prompt.getPromptCharacters());
    }
}
