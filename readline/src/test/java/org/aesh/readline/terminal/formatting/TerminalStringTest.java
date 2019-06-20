package org.aesh.readline.terminal.formatting;

import org.aesh.terminal.utils.ANSI;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TerminalStringTest {

    @Test
    public void testTerminalString() {
        TerminalString string = new TerminalString("foo");

        assertFalse(string.containSpaces());
        assertFalse(string.isFormatted());
        assertEquals("foo", string.getCharacters());

        string = new TerminalString("foo bar", new TerminalColor(Color.BLACK, Color.WHITE));
        assertTrue(string.containSpaces());
        assertTrue(string.isFormatted());
        assertEquals("foo bar", string.getCharacters());
        string.switchSpacesToEscapedSpaces();
        assertEquals("foo\\ bar", string.getCharacters());
        assertEquals(ANSI.START+";30;47mfoo\\ bar"+ANSI.RESET, string.toString());

        string = new TerminalString("foo bar", true);
        assertTrue(string.containSpaces());
        assertFalse(string.isFormatted());
        assertEquals("foo bar", string.getCharacters());

        assertEquals(0, string.getANSILength());
     }
}
