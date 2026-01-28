package org.aesh.terminal.formatting;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
