package org.aesh.readline;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.aesh.readline.prompt.Prompt;
import org.junit.Test;

/**
 * Tests for Prompt ANSI code handling — verifies that getLength()
 * returns the visible length when ANSI escape codes are present.
 */
public class PromptAnsiTest {

    @Test
    public void testPlainPromptLength() {
        Prompt p = new Prompt("$ ");
        assertEquals(2, p.getLength());
    }

    @Test
    public void testAnsiPromptLength() {
        Prompt p = new Prompt("\033[32m$\033[0m ");
        assertEquals(2, p.getLength());
    }

    @Test
    public void testAnsiPromptHasAnsi() {
        Prompt p = new Prompt("\033[32m$\033[0m ");
        assertTrue("Should detect ANSI", p.hasANSI());
    }

    @Test
    public void testAnsiPromptDisplayString() {
        Prompt p = new Prompt("\033[32m$\033[0m ");
        assertNotNull("ANSI string should be set", p.getANSI());
        assertTrue("ANSI array should be longer than visible length",
                p.getANSI().length > p.getLength());
    }

    @Test
    public void testPlainPromptNoAnsi() {
        Prompt p = new Prompt("$ ");
        assertEquals(p.getLength(), p.getANSI().length);
    }

    @Test
    public void testEmptyPrompt() {
        Prompt p = new Prompt("");
        assertEquals(0, p.getLength());
    }

    @Test
    public void testNullPrompt() {
        Prompt p = new Prompt((String) null);
        assertEquals(0, p.getLength());
    }

    @Test
    public void testMultipleAnsiCodes() {
        Prompt p = new Prompt("\033[1m\033[31mERROR>\033[0m ");
        assertEquals(7, p.getLength());
    }

    @Test
    public void testAnsiPromptCopy() {
        Prompt original = new Prompt("\033[36m>\033[0m ");
        Prompt copy = original.copy();
        assertEquals(original.getLength(), copy.getLength());
        assertTrue(copy.hasANSI());
    }
}
