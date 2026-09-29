package org.aesh.terminal.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.aesh.terminal.tty.Point;
import org.junit.Test;

/**
 * Tests for cursor position (CPR) response parsing in {@link ANSI}.
 * <p>
 * Only a well-formed frame — {@code ESC [ digits ; digits R} — parses.
 * Anything else reports incomplete so fragmented query responses keep
 * waiting instead of latching invented coordinates.
 */
public class ANSICursorPositionTest {

    private static int[] frame(String body) {
        return ("" + (char) 27 + "[" + body).codePoints().toArray();
    }

    @Test
    public void testValidFrame() {
        Point pos = ANSI.getActualCursor(frame("24;80R"));
        assertEquals(80, pos.x());
        assertEquals(24, pos.y());
    }

    @Test
    public void testMissingTerminatorIsIncomplete() {
        assertNull(ANSI.getActualCursor(frame("24;80")));
        assertNull(ANSI.getActualCursor(frame("24;")));
        assertNull(ANSI.getActualCursor(frame("")));
    }

    @Test
    public void testEmptyFieldsAreInvalid() {
        assertNull(ANSI.getActualCursor(frame("R")));
        assertNull(ANSI.getActualCursor(frame(";80R")));
        assertNull(ANSI.getActualCursor(frame("24;R")));
    }

    @Test
    public void testNonDigitsAreInvalid() {
        assertNull(ANSI.getActualCursor(frame("?25R")));
        assertNull(ANSI.getActualCursor(frame("2x4;80R")));
        assertNull(ANSI.getActualCursor(frame("24;8 PR")));
        assertNull(ANSI.getActualCursor(frame("1;2;3R")));
    }

    @Test
    public void testOverflowingCoordinatesAreInvalid() {
        String huge = "9999999999999999999999999";
        assertNull(ANSI.getActualCursor(frame(huge + ";1R")));
        assertNull(ANSI.getActualCursor(frame("1;" + huge + "R")));
    }

    @Test
    public void testGarbageIsIncomplete() {
        assertNull(ANSI.getActualCursor(new int[0]));
        assertNull(ANSI.getActualCursor(new int[] { 'x' }));
        assertNull(ANSI.getActualCursor(new int[] { 27 }));
    }

    @Test
    public void testNoiseAroundFrame() {
        int[] noisy = ("" + (char) 27 + "[3;7R").codePoints().toArray();
        int[] input = concat(new int[] { 'a', 'b' }, noisy, new int[] { 'c' });
        Point pos = ANSI.getActualCursor(input);
        assertEquals(7, pos.x());
        assertEquals(3, pos.y());
    }

    @Test
    public void testGarbageFrameDoesNotPoisonLaterFrame() {
        int[] input = concat(frame("?25R"), frame("R"), frame("24;80R"));
        Point pos = ANSI.getActualCursor(input);
        assertEquals(80, pos.x());
        assertEquals(24, pos.y());
    }

    private static int[] concat(int[]... arrays) {
        int total = 0;
        for (int[] a : arrays) {
            total += a.length;
        }
        int[] out = new int[total];
        int pos = 0;
        for (int[] a : arrays) {
            System.arraycopy(a, 0, out, pos, a.length);
            pos += a.length;
        }
        return out;
    }
}
