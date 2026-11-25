package org.aesh.terminal.formatting;

import static org.junit.Assert.assertEquals;
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

        TerminalTextStyle mutable = new TerminalTextStyle();
        assertEquals(0, mutable.getLength());
        mutable.setBold(true);
        assertEquals(mutable.toString().length(), mutable.getLength());
        assertTrue(mutable.getLength() > 0);

        TerminalTextStyle concealed = new TerminalTextStyle();
        concealed.setConceal(true);
        assertTrue(concealed.isFormatted());
        assertEquals(concealed.toString().length(), concealed.getLength());
    }
}
