package org.aesh.terminal.detect;

import static org.junit.Assert.*;

import org.junit.Test;

public class TerminalColorQueryTest {

    @Test
    public void testParseOscColorResponse_4DigitHex() {
        String response = "\033]11;rgb:1c1c/1c1c/1c1c\007";
        int[] rgb = TerminalColorQuery.parseOscColorResponse(response, 11, -1);
        assertNotNull(rgb);
        assertEquals(28, rgb[0]);
        assertEquals(28, rgb[1]);
        assertEquals(28, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_2DigitHex() {
        String response = "\033]10;rgb:ff/80/00\007";
        int[] rgb = TerminalColorQuery.parseOscColorResponse(response, 10, -1);
        assertNotNull(rgb);
        assertEquals(255, rgb[0]);
        assertEquals(128, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_WithPaletteIndex() {
        String response = "\033]4;1;rgb:cc00/0000/0000\007";
        int[] rgb = TerminalColorQuery.parseOscColorResponse(response, 4, 1);
        assertNotNull(rgb);
        assertEquals(204, rgb[0]);
        assertEquals(0, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_WrongCode() {
        String response = "\033]11;rgb:ff/ff/ff\007";
        assertNull(TerminalColorQuery.parseOscColorResponse(response, 10, -1));
    }

    @Test
    public void testParseOscColorResponse_WrongPaletteIndex() {
        String response = "\033]4;2;rgb:ff/ff/ff\007";
        assertNull(TerminalColorQuery.parseOscColorResponse(response, 4, 1));
    }

    @Test
    public void testParseOscColorResponse_StTerminator() {
        String response = "\033]11;rgb:0000/8080/ffff\033\\";
        int[] rgb = TerminalColorQuery.parseOscColorResponse(response, 11, -1);
        assertNotNull(rgb);
        assertEquals(0, rgb[0]);
        assertEquals(128, rgb[1]);
        assertEquals(255, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_MixedTerminatorsStThenBel() {
        // ST-terminated foreground followed by BEL-terminated
        // background: each reply stops at its own terminator (#306).
        String response = "\033]10;rgb:ffff/ffff/ffff\033\\"
                + "\033]11;rgb:0000/0000/0000\007";
        int[] fg = TerminalColorQuery.parseOscColorResponse(response, 10, -1);
        assertNotNull(fg);
        assertEquals(255, fg[0]);
        assertEquals(255, fg[1]);
        assertEquals(255, fg[2]);
        int[] bg = TerminalColorQuery.parseOscColorResponse(response, 11, -1);
        assertNotNull(bg);
        assertEquals(0, bg[0]);
        assertEquals(0, bg[1]);
        assertEquals(0, bg[2]);
    }

    @Test
    public void testParseOscColorResponse_MixedTerminatorsBelThenSt() {
        String response = "\033]10;rgb:ffff/ffff/ffff\007"
                + "\033]11;rgb:0000/0000/0000\033\\";
        int[] fg = TerminalColorQuery.parseOscColorResponse(response, 10, -1);
        assertNotNull(fg);
        assertEquals(255, fg[0]);
        int[] bg = TerminalColorQuery.parseOscColorResponse(response, 11, -1);
        assertNotNull(bg);
        assertEquals(0, bg[0]);
    }

    @Test
    public void testParseOscColorResponse_IndexedPaletteStTerminated() {
        String response = "\033]4;7;rgb:1111/2222/3333\033\\"
                + "\033]11;rgb:0000/0000/0000\007";
        int[] rgb = TerminalColorQuery.parseOscColorResponse(response, 4, 7);
        assertNotNull(rgb);
        assertEquals(0x11, rgb[0]);
        assertEquals(0x22, rgb[1]);
        assertEquals(0x33, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_MultipleResponses() {
        String response = "\033]10;rgb:cdcd/d6d6/f4f4\007\033]11;rgb:1e1e/2e2e/4e4e\007";
        int[] fg = TerminalColorQuery.parseOscColorResponse(response, 10, -1);
        int[] bg = TerminalColorQuery.parseOscColorResponse(response, 11, -1);
        assertNotNull(fg);
        assertNotNull(bg);
        assertEquals(205, fg[0]);
        assertEquals(30, bg[0]);
    }

    @Test
    public void testParseOscColorResponse_Null() {
        assertNull(TerminalColorQuery.parseOscColorResponse(null, 11, -1));
    }

    @Test
    public void testParseOscColorResponse_TooShort() {
        assertNull(TerminalColorQuery.parseOscColorResponse("short", 11, -1));
    }

    @Test
    public void testParseOscColorResponse_1DigitHex() {
        String response = "\033]10;rgb:F/8/0\007";
        int[] rgb = TerminalColorQuery.parseOscColorResponse(response, 10, -1);
        assertNotNull(rgb);
        assertEquals(255, rgb[0]);
        assertEquals(136, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    @Test
    public void testParseDA1Response_WithSixel() {
        // VT340-like response: class 63, features 1;2;4;6;8;9;15
        String response = "\033[?63;1;2;4;6;8;9;15c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        assertEquals(63, result.da1DeviceClass);
        assertTrue(result.supportsSixel);
        assertTrue(result.da1Features.contains(4));
    }

    @Test
    public void testParseDA1Response_WithoutSixel() {
        // Basic VT100: class 1, feature 2
        String response = "\033[?1;2c";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        assertEquals(1, result.da1DeviceClass);
        assertFalse(result.supportsSixel);
        assertFalse(result.da1Features.contains(4));
    }

    @Test
    public void testParseDA1Response_NoResponse() {
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response("some garbage", result);
        assertEquals(-1, result.da1DeviceClass);
        assertFalse(result.supportsSixel);
    }

    @Test
    public void testParseDA1Response_MixedWithOsc() {
        String response = "\033[?62;4c\033]11;rgb:1c1c/1c1c/1c1c\007";
        TerminalColorQuery result = new TerminalColorQuery();
        TerminalColorQuery.parseDA1Response(response, result);
        assertEquals(62, result.da1DeviceClass);
        assertTrue(result.supportsSixel);
        // OSC still parseable
        int[] bg = TerminalColorQuery.parseOscColorResponse(response, 11, -1);
        assertNotNull(bg);
        assertEquals(28, bg[0]);
    }
}
