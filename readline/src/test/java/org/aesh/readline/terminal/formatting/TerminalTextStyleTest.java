package org.aesh.readline.terminal.formatting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TerminalTextStyleTest {

    @Test
    public void testTerminalTextStyle() {
        TerminalTextStyle textStyle = new TerminalTextStyle();

        assertFalse(textStyle.isBlink());
        assertFalse(textStyle.isBold());
        assertFalse(textStyle.isFormatted());
        assertFalse(textStyle.isUnderline());

        textStyle = new TerminalTextStyle(CharacterType.BOLD);
        assertFalse(textStyle.isBlink());
        assertTrue(textStyle.isBold());
        assertTrue(textStyle.isFormatted());
        assertFalse(textStyle.isUnderline());
    }
}
