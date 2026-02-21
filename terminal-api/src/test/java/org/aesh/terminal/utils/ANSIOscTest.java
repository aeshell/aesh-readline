/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.terminal.utils;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Tests for OSC (Operating System Command) utilities in the ANSI class.
 * These tests verify the parsing and building of OSC sequences used for
 * terminal color queries.
 *
 * @author Ståle Pedersen
 */
public class ANSIOscTest {

    @Test
    public void testOscConstants() {
        assertEquals("\u001B]", ANSI.OSC_START);
        assertEquals("\u0007", ANSI.BEL);
        assertEquals("\u001B\\", ANSI.ST);
        assertEquals(10, ANSI.OSC_FOREGROUND);
        assertEquals(11, ANSI.OSC_BACKGROUND);
        assertEquals(12, ANSI.OSC_CURSOR_COLOR);
    }

    @Test
    public void testBuildOscQuery() {
        // Test foreground color query (OSC 10)
        String query = ANSI.buildOscQuery(10, "?");
        assertEquals("\u001B]10;?\u0007", query);

        // Test background color query (OSC 11)
        query = ANSI.buildOscQuery(11, "?");
        assertEquals("\u001B]11;?\u0007", query);

        // Test cursor color query (OSC 12)
        query = ANSI.buildOscQuery(12, "?");
        assertEquals("\u001B]12;?\u0007", query);

        // Test with different parameter
        query = ANSI.buildOscQuery(10, "rgb:ffff/ffff/ffff");
        assertEquals("\u001B]10;rgb:ffff/ffff/ffff\u0007", query);
    }

    @Test
    public void testParseOscColorResponse_ForegroundWith4DigitHex() {
        // Standard OSC 10 response with 4-digit hex values (common format)
        // Response: ESC ] 10 ; rgb:FFFF/8080/0000 BEL
        String response = "\u001B]10;rgb:FFFF/8080/0000\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 10);

        assertNotNull("Should parse valid OSC 10 response", rgb);
        assertEquals(3, rgb.length);
        assertEquals(255, rgb[0]); // FFFF >> 8 = 255
        assertEquals(128, rgb[1]); // 8080 >> 8 = 128
        assertEquals(0, rgb[2]); // 0000 >> 8 = 0
    }

    @Test
    public void testParseOscColorResponse_BackgroundWith4DigitHex() {
        // Standard OSC 11 response with 4-digit hex values
        String response = "\u001B]11;rgb:2828/2828/2828\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 11);

        assertNotNull("Should parse valid OSC 11 response", rgb);
        assertEquals(3, rgb.length);
        assertEquals(40, rgb[0]); // 2828 >> 8 = 40
        assertEquals(40, rgb[1]);
        assertEquals(40, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_With2DigitHex() {
        // Some terminals respond with 2-digit hex values
        String response = "\u001B]10;rgb:FF/80/00\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 10);

        assertNotNull("Should parse 2-digit hex response", rgb);
        assertEquals(255, rgb[0]);
        assertEquals(128, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_With1DigitHex() {
        // X11 color spec allows 1-digit hex per channel
        String response = "\u001B]10;rgb:F/8/0\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 10);

        assertNotNull("Should parse 1-digit hex response", rgb);
        assertEquals(255, rgb[0]); // F * 17 = 255
        assertEquals(136, rgb[1]); // 8 * 17 = 136
        assertEquals(0, rgb[2]); // 0 * 17 = 0
    }

    @Test
    public void testParseOscColorResponse_With3DigitHex() {
        // X11 color spec allows 3-digit hex per channel
        String response = "\u001B]11;rgb:FFF/800/000\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 11);

        assertNotNull("Should parse 3-digit hex response", rgb);
        assertEquals(255, rgb[0]); // FFF >> 4 = 255
        assertEquals(128, rgb[1]); // 800 >> 4 = 128
        assertEquals(0, rgb[2]); // 000 >> 4 = 0
    }

    @Test
    public void testParseOscColorResponse_WithSTTerminator() {
        // Some terminals use ESC \ as terminator instead of BEL
        String response = "\u001B]10;rgb:FFFF/0000/FFFF\u001B\\";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 10);

        assertNotNull("Should parse response with ST terminator", rgb);
        assertEquals(255, rgb[0]);
        assertEquals(0, rgb[1]);
        assertEquals(255, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_CursorColor() {
        // OSC 12 for cursor color
        String response = "\u001B]12;rgb:0000/FFFF/0000\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 12);

        assertNotNull("Should parse cursor color response", rgb);
        assertEquals(0, rgb[0]);
        assertEquals(255, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_WrongOscCode() {
        // Response is for OSC 11 but we're looking for OSC 10
        String response = "\u001B]11;rgb:FFFF/FFFF/FFFF\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 10);

        assertNull("Should return null for wrong OSC code", rgb);
    }

    @Test
    public void testParseOscColorResponse_InvalidFormat() {
        // Missing rgb: prefix
        String response = "\u001B]10;FFFF/FFFF/FFFF\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 10);

        assertNull("Should return null for invalid format", rgb);
    }

    @Test
    public void testParseOscColorResponse_NullInput() {
        int[] rgb = ANSI.parseOscColorResponse(null, 10);
        assertNull("Should return null for null input", rgb);
    }

    @Test
    public void testParseOscColorResponse_EmptyInput() {
        int[] rgb = ANSI.parseOscColorResponse(new int[0], 10);
        assertNull("Should return null for empty input", rgb);
    }

    @Test
    public void testParseOscColorResponse_TooShortInput() {
        int[] rgb = ANSI.parseOscColorResponse(new int[] { 27, ']', '1', '0' }, 10);
        assertNull("Should return null for too short input", rgb);
    }

    @Test
    public void testParseOscColorResponse_WithPrefixNoise() {
        // Sometimes there's noise before the actual response
        String response = "some noise\u001B]10;rgb:8080/8080/8080\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 10);

        assertNotNull("Should parse response with prefix noise", rgb);
        assertEquals(128, rgb[0]);
        assertEquals(128, rgb[1]);
        assertEquals(128, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_WhiteColor() {
        // Test parsing white color (max values)
        String response = "\u001B]11;rgb:FFFF/FFFF/FFFF\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 11);

        assertNotNull(rgb);
        assertEquals(255, rgb[0]);
        assertEquals(255, rgb[1]);
        assertEquals(255, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_BlackColor() {
        // Test parsing black color (min values)
        String response = "\u001B]11;rgb:0000/0000/0000\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 11);

        assertNotNull(rgb);
        assertEquals(0, rgb[0]);
        assertEquals(0, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_LowercaseHex() {
        // Some terminals might respond with lowercase hex
        String response = "\u001B]10;rgb:abcd/ef01/2345\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 10);

        assertNotNull("Should parse lowercase hex", rgb);
        assertEquals(0xab, rgb[0]); // abcd >> 8 = 0xab = 171
        assertEquals(0xef, rgb[1]); // ef01 >> 8 = 0xef = 239
        assertEquals(0x23, rgb[2]); // 2345 >> 8 = 0x23 = 35
    }

    // ==================== OSC 4 Palette Color Tests ====================

    @Test
    public void testOscPaletteConstant() {
        assertEquals(4, ANSI.OSC_PALETTE);
    }

    @Test
    public void testBuildOscQueryWithIndex() {
        // Test palette color query (OSC 4 with index)
        String query = ANSI.buildOscQuery(4, 1, "?");
        assertEquals("\u001B]4;1;?\u0007", query);

        // Test with different index
        query = ANSI.buildOscQuery(4, 255, "?");
        assertEquals("\u001B]4;255;?\u0007", query);
    }

    @Test
    public void testParseOscColorResponse_PaletteColorWithIndex() {
        // OSC 4 response with palette index 1
        // Response: ESC ] 4 ; 1 ; rgb:dc19/686d/6b19 BEL
        String response = "\u001B]4;1;rgb:dc19/686d/6b19\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 4, 1);

        assertNotNull("Should parse OSC 4 response with index", rgb);
        assertEquals(3, rgb.length);
        assertEquals(0xdc, rgb[0]); // dc19 >> 8 = 0xdc = 220
        assertEquals(0x68, rgb[1]); // 686d >> 8 = 0x68 = 104
        assertEquals(0x6b, rgb[2]); // 6b19 >> 8 = 0x6b = 107
    }

    @Test
    public void testParseOscColorResponse_PaletteColorIndex0() {
        // OSC 4 response with palette index 0 (black in standard palette)
        String response = "\u001B]4;0;rgb:0000/0000/0000\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 4, 0);

        assertNotNull("Should parse OSC 4 response for index 0", rgb);
        assertEquals(0, rgb[0]);
        assertEquals(0, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_PaletteColorHighIndex() {
        // OSC 4 response with palette index 255
        String response = "\u001B]4;255;rgb:EEEE/EEEE/EEEE\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 4, 255);

        assertNotNull("Should parse OSC 4 response for index 255", rgb);
        assertEquals(0xee, rgb[0]); // EEEE >> 8 = 238
        assertEquals(0xee, rgb[1]);
        assertEquals(0xee, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_PaletteColorWrongIndex() {
        // OSC 4 response with index 2, but we're looking for index 1
        String response = "\u001B]4;2;rgb:FFFF/0000/0000\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 4, 1);

        assertNull("Should return null for wrong palette index", rgb);
    }

    @Test
    public void testParseOscColorResponse_PaletteColorNoIndexParam() {
        // Using parseOscColorResponse without index parameter
        // This simulates the issue #96 - old code couldn't parse OSC 4 responses
        String response = "\u001B]4;1;rgb:dc19/686d/6b19\u0007";
        int[] input = response.codePoints().toArray();

        // The 2-param version should still find rgb: even with index present
        int[] rgb = ANSI.parseOscColorResponse(input, 4);

        assertNotNull("Should find rgb: even when index is present", rgb);
        assertEquals(0xdc, rgb[0]);
        assertEquals(0x68, rgb[1]);
        assertEquals(0x6b, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_PaletteColorWithSTTerminator() {
        // OSC 4 response with ESC \ terminator
        String response = "\u001B]4;7;rgb:c0c0/c0c0/c0c0\u001B\\";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, 4, 7);

        assertNotNull("Should parse OSC 4 response with ST terminator", rgb);
        assertEquals(0xc0, rgb[0]); // c0c0 >> 8 = 192
        assertEquals(0xc0, rgb[1]);
        assertEquals(0xc0, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_Issue96Scenario() {
        // Exact scenario from GitHub issue #96
        // Query: OSC 4;1;? - query palette color 1
        // Response: ESC]4;1;rgb:dc19/686d/6b19
        String response = "\u001B]4;1;rgb:dc19/686d/6b19\u0007";
        int[] input = response.codePoints().toArray();

        // This should now work with the 3-param method
        int[] rgb = ANSI.parseOscColorResponse(input, ANSI.OSC_PALETTE, 1);

        assertNotNull("Issue #96: Should parse OSC 4;1 palette color response", rgb);
        assertEquals(220, rgb[0]); // 0xdc
        assertEquals(104, rgb[1]); // 0x68
        assertEquals(107, rgb[2]); // 0x6b
    }

    @Test
    public void testParseOscColorResponse_RegularOscStillWorks() {
        // Verify that regular OSC 11 still works after the changes
        String response = "\u001B]11;rgb:19ee/2448/2b8a\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = ANSI.parseOscColorResponse(input, ANSI.OSC_BACKGROUND);

        assertNotNull("Regular OSC 11 should still work", rgb);
        assertEquals(0x19, rgb[0]); // 19ee >> 8 = 25
        assertEquals(0x24, rgb[1]); // 2448 >> 8 = 36
        assertEquals(0x2b, rgb[2]); // 2b8a >> 8 = 43
    }

    // ==================== RGB to ANSI Color Conversion Tests ====================

    @Test
    public void testRgbTo256Color_Black() {
        // Pure black should map to index 16 (black in color cube)
        assertEquals(16, ANSI.rgbTo256Color(0, 0, 0));
    }

    @Test
    public void testRgbTo256Color_White() {
        // Pure white should map to index 231 (white in color cube)
        assertEquals(231, ANSI.rgbTo256Color(255, 255, 255));
    }

    @Test
    public void testRgbTo256Color_Grayscale() {
        // Mid-gray should be in grayscale range (232-255)
        int gray = ANSI.rgbTo256Color(128, 128, 128);
        assertTrue("Mid-gray should be in grayscale range", gray >= 232 && gray <= 255);
    }

    @Test
    public void testRgbTo256Color_Red() {
        // Pure red should map to a red in the color cube
        int red = ANSI.rgbTo256Color(255, 0, 0);
        // Should be 16 + 36*5 + 6*0 + 0 = 196
        assertEquals(196, red);
    }

    @Test
    public void testRgbTo256Color_Green() {
        // Pure green should map to a green in the color cube
        int green = ANSI.rgbTo256Color(0, 255, 0);
        // Should be 16 + 36*0 + 6*5 + 0 = 46
        assertEquals(46, green);
    }

    @Test
    public void testRgbTo256Color_Blue() {
        // Pure blue should map to a blue in the color cube
        int blue = ANSI.rgbTo256Color(0, 0, 255);
        // Should be 16 + 36*0 + 6*0 + 5 = 21
        assertEquals(21, blue);
    }

    @Test
    public void testRgbToAnsiColor_Black() {
        // Black should return 30 (normal) or stay as black
        int code = ANSI.rgbToAnsiColor(0, 0, 0);
        assertEquals(30, code); // Normal black
    }

    @Test
    public void testRgbToAnsiColor_BrightWhite() {
        // Bright white should return 97 (bright white)
        int code = ANSI.rgbToAnsiColor(255, 255, 255);
        assertEquals(97, code); // Bright white (37 + 60)
    }

    @Test
    public void testRgbToAnsiColor_Red() {
        // Red should return red code
        int code = ANSI.rgbToAnsiColor(200, 50, 50);
        assertTrue("Should be red (31 or 91)", code == 31 || code == 91);
    }

    @Test
    public void testRgbToAnsiColor_ExplicitBrightness() {
        // Test explicit brightness parameter
        int normalRed = ANSI.rgbToAnsiColor(255, 0, 0, false);
        int brightRed = ANSI.rgbToAnsiColor(255, 0, 0, true);

        assertEquals(31, normalRed);
        assertEquals(91, brightRed);
    }

    @Test
    public void testRgbToAnsiBackgroundColor() {
        // Background colors should be 40-47 or 100-107
        int bgBlack = ANSI.rgbToAnsiBackgroundColor(0, 0, 0);
        assertEquals(40, bgBlack); // Normal black background

        int bgWhite = ANSI.rgbToAnsiBackgroundColor(255, 255, 255);
        assertEquals(107, bgWhite); // Bright white background
    }

    @Test
    public void testRgbToBasicColorCode() {
        // Test direct basic color code mapping
        assertEquals(30, ANSI.rgbToBasicColorCode(0, 0, 0)); // Black
        assertEquals(31, ANSI.rgbToBasicColorCode(200, 0, 0)); // Red
        assertEquals(32, ANSI.rgbToBasicColorCode(0, 200, 0)); // Green
        assertEquals(33, ANSI.rgbToBasicColorCode(200, 200, 0)); // Yellow
        assertEquals(34, ANSI.rgbToBasicColorCode(0, 0, 200)); // Blue
        assertEquals(35, ANSI.rgbToBasicColorCode(200, 0, 200)); // Magenta
        assertEquals(36, ANSI.rgbToBasicColorCode(0, 200, 200)); // Cyan
        assertEquals(37, ANSI.rgbToBasicColorCode(200, 200, 200)); // White
    }

    @Test
    public void testRgbIsBright() {
        assertFalse(ANSI.rgbIsBright(0, 0, 0)); // Black is not bright
        assertTrue(ANSI.rgbIsBright(255, 255, 255)); // White is bright
        assertFalse(ANSI.rgbIsBright(100, 100, 100)); // Dark gray is not bright
        assertTrue(ANSI.rgbIsBright(200, 200, 200)); // Light gray is bright
    }

    @Test
    public void testColor256ToRgb_Black() {
        int[] rgb = ANSI.color256ToRgb(0);
        assertArrayEquals(new int[] { 0, 0, 0 }, rgb);
    }

    @Test
    public void testColor256ToRgb_BrightWhite() {
        int[] rgb = ANSI.color256ToRgb(15);
        assertArrayEquals(new int[] { 255, 255, 255 }, rgb);
    }

    @Test
    public void testColor256ToRgb_ColorCube() {
        // Index 196 should be bright red (5, 0, 0 in cube)
        int[] rgb = ANSI.color256ToRgb(196);
        assertEquals(255, rgb[0]); // 55 + 5*40 = 255
        assertEquals(0, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    @Test
    public void testColor256ToRgb_Grayscale() {
        // Index 244 is mid-grayscale
        int[] rgb = ANSI.color256ToRgb(244);
        int expected = 8 + (244 - 232) * 10; // 8 + 12*10 = 128
        assertEquals(expected, rgb[0]);
        assertEquals(expected, rgb[1]);
        assertEquals(expected, rgb[2]);
    }

    @Test
    public void testColor256ToRgb_RoundTrip() {
        // Converting RGB to 256 and back should give similar values
        int original256 = ANSI.rgbTo256Color(128, 64, 192);
        int[] rgb = ANSI.color256ToRgb(original256);
        int roundTrip = ANSI.rgbTo256Color(rgb[0], rgb[1], rgb[2]);

        assertEquals("Round-trip should return same index", original256, roundTrip);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testColor256ToRgb_InvalidIndex() {
        ANSI.color256ToRgb(256);
    }

    @Test
    public void testRgbTo256Color_ClampValues() {
        // Values outside 0-255 should be clamped
        assertEquals(ANSI.rgbTo256Color(255, 255, 255), ANSI.rgbTo256Color(300, 300, 300));
        assertEquals(ANSI.rgbTo256Color(0, 0, 0), ANSI.rgbTo256Color(-10, -10, -10));
    }

    // ==================== Batch OSC Query Tests ====================

    @Test
    public void testBuildBatchOscQuery() {
        // Test building batch query for foreground, background, cursor
        String query = ANSI.buildBatchOscQuery(10, 11, 12);
        assertEquals("\u001B]10;?\u0007\u001B]11;?\u0007\u001B]12;?\u0007", query);
    }

    @Test
    public void testBuildBatchOscQuery_Single() {
        String query = ANSI.buildBatchOscQuery(10);
        assertEquals("\u001B]10;?\u0007", query);
    }

    @Test
    public void testBuildBatchOscQueryWithIndices() {
        // Test building batch query for palette colors 0, 1, 2
        String query = ANSI.buildBatchOscQueryWithIndices(4, 0, 1, 2);
        assertEquals("\u001B]4;0;?\u0007\u001B]4;1;?\u0007\u001B]4;2;?\u0007", query);
    }

    @Test
    public void testParseMultipleOscColorResponses_FgAndBg() {
        // Simulate concatenated responses for OSC 10 and 11
        String response = "\u001B]10;rgb:FFFF/0000/0000\u0007\u001B]11;rgb:0000/0000/2828\u0007";
        int[] input = response.codePoints().toArray();

        java.util.Map<Integer, int[]> results = ANSI.parseMultipleOscColorResponses(input, 10, 11);

        assertEquals(2, results.size());

        int[] fg = results.get(10);
        assertNotNull("Should have foreground color", fg);
        assertEquals(255, fg[0]);
        assertEquals(0, fg[1]);
        assertEquals(0, fg[2]);

        int[] bg = results.get(11);
        assertNotNull("Should have background color", bg);
        assertEquals(0, bg[0]);
        assertEquals(0, bg[1]);
        assertEquals(40, bg[2]); // 2828 >> 8 = 40
    }

    @Test
    public void testParseMultipleOscColorResponses_FgBgCursor() {
        // All three standard color queries
        String response = "\u001B]10;rgb:EEEE/EEEE/EEEE\u0007"
                + "\u001B]11;rgb:1919/1919/1919\u0007"
                + "\u001B]12;rgb:00FF/FF00/00FF\u0007";
        int[] input = response.codePoints().toArray();

        java.util.Map<Integer, int[]> results = ANSI.parseMultipleOscColorResponses(input, 10, 11, 12);

        assertEquals(3, results.size());

        int[] fg = results.get(10);
        assertEquals(0xee, fg[0]);
        assertEquals(0xee, fg[1]);
        assertEquals(0xee, fg[2]);

        int[] bg = results.get(11);
        assertEquals(0x19, bg[0]);
        assertEquals(0x19, bg[1]);
        assertEquals(0x19, bg[2]);

        int[] cursor = results.get(12);
        assertEquals(0, cursor[0]);
        assertEquals(255, cursor[1]);
        assertEquals(0, cursor[2]);
    }

    @Test
    public void testParseMultipleOscColorResponses_PartialResponse() {
        // Only OSC 10 responds, 11 is missing
        String response = "\u001B]10;rgb:FFFF/FFFF/FFFF\u0007";
        int[] input = response.codePoints().toArray();

        java.util.Map<Integer, int[]> results = ANSI.parseMultipleOscColorResponses(input, 10, 11);

        assertEquals(1, results.size());
        assertNotNull(results.get(10));
        assertNull(results.get(11));
    }

    @Test
    public void testParseMultipleOscColorResponses_Empty() {
        java.util.Map<Integer, int[]> results = ANSI.parseMultipleOscColorResponses(null, 10, 11);
        assertTrue(results.isEmpty());

        results = ANSI.parseMultipleOscColorResponses(new int[0], 10, 11);
        assertTrue(results.isEmpty());
    }

    @Test
    public void testParseMultiplePaletteResponses() {
        // Simulate batch palette color responses for indices 0, 1, 2
        String response = "\u001B]4;0;rgb:0000/0000/0000\u0007"
                + "\u001B]4;1;rgb:AAAA/0000/0000\u0007"
                + "\u001B]4;2;rgb:0000/AAAA/0000\u0007";
        int[] input = response.codePoints().toArray();

        java.util.Map<Integer, int[]> results = ANSI.parseMultiplePaletteResponses(input, 0, 1, 2);

        assertEquals(3, results.size());

        int[] color0 = results.get(0);
        assertNotNull(color0);
        assertEquals(0, color0[0]);
        assertEquals(0, color0[1]);
        assertEquals(0, color0[2]);

        int[] color1 = results.get(1);
        assertNotNull(color1);
        assertEquals(0xaa, color1[0]);
        assertEquals(0, color1[1]);
        assertEquals(0, color1[2]);

        int[] color2 = results.get(2);
        assertNotNull(color2);
        assertEquals(0, color2[0]);
        assertEquals(0xaa, color2[1]);
        assertEquals(0, color2[2]);
    }

    @Test
    public void testParseMultiplePaletteResponses_HighIndices() {
        // Test higher palette indices (e.g., grayscale region)
        String response = "\u001B]4;232;rgb:0808/0808/0808\u0007"
                + "\u001B]4;255;rgb:EEEE/EEEE/EEEE\u0007";
        int[] input = response.codePoints().toArray();

        java.util.Map<Integer, int[]> results = ANSI.parseMultiplePaletteResponses(input, 232, 255);

        assertEquals(2, results.size());

        int[] gray232 = results.get(232);
        assertEquals(8, gray232[0]);
        assertEquals(8, gray232[1]);
        assertEquals(8, gray232[2]);

        int[] gray255 = results.get(255);
        assertEquals(0xee, gray255[0]);
        assertEquals(0xee, gray255[1]);
        assertEquals(0xee, gray255[2]);
    }

    @Test
    public void testParseMultipleOscColorResponses_WithSTTerminator() {
        // Test with ESC \ terminator instead of BEL
        String response = "\u001B]10;rgb:FFFF/0000/0000\u001B\\"
                + "\u001B]11;rgb:0000/0000/FFFF\u001B\\";
        int[] input = response.codePoints().toArray();

        java.util.Map<Integer, int[]> results = ANSI.parseMultipleOscColorResponses(input, 10, 11);

        assertEquals(2, results.size());
        assertEquals(255, results.get(10)[0]);
        assertEquals(0, results.get(10)[1]);
        assertEquals(0, results.get(10)[2]);
        assertEquals(0, results.get(11)[0]);
        assertEquals(0, results.get(11)[1]);
        assertEquals(255, results.get(11)[2]);
    }

    // ==================== Theme Mode DSR (CSI ? 996 n) Tests ====================

    @Test
    public void testParseThemeDsrResponse_DarkMode() {
        // ESC [ ? 997 ; 1 n
        String response = "\u001B[?997;1n";
        int[] input = response.codePoints().toArray();
        assertEquals(TerminalTheme.DARK, ANSI.parseThemeDsrResponse(input));
    }

    @Test
    public void testParseThemeDsrResponse_LightMode() {
        // ESC [ ? 997 ; 2 n
        String response = "\u001B[?997;2n";
        int[] input = response.codePoints().toArray();
        assertEquals(TerminalTheme.LIGHT, ANSI.parseThemeDsrResponse(input));
    }

    @Test
    public void testParseThemeDsrResponse_NullInput() {
        assertNull(ANSI.parseThemeDsrResponse(null));
    }

    @Test
    public void testParseThemeDsrResponse_EmptyInput() {
        assertNull(ANSI.parseThemeDsrResponse(new int[0]));
    }

    @Test
    public void testParseThemeDsrResponse_TooShort() {
        // Less than 8 characters
        String response = "\u001B[?99";
        int[] input = response.codePoints().toArray();
        assertNull(ANSI.parseThemeDsrResponse(input));
    }

    @Test
    public void testParseThemeDsrResponse_InvalidMode() {
        // ESC [ ? 997 ; 3 n — mode 3 is not defined
        String response = "\u001B[?997;3n";
        int[] input = response.codePoints().toArray();
        assertNull(ANSI.parseThemeDsrResponse(input));
    }

    @Test
    public void testParseThemeDsrResponse_NoTerminator() {
        // Missing terminating 'n'
        String response = "\u001B[?997;1";
        int[] input = response.codePoints().toArray();
        assertNull(ANSI.parseThemeDsrResponse(input));
    }

    @Test
    public void testParseThemeDsrResponse_WrongSequence() {
        // Not a theme DSR response
        String response = "\u001B[?998;1n";
        int[] input = response.codePoints().toArray();
        assertNull(ANSI.parseThemeDsrResponse(input));
    }

    @Test
    public void testParseThemeDsrResponse_EmbeddedInOtherData() {
        // Theme DSR response embedded with other terminal data
        String response = "some data\u001B[?997;2nmore data";
        int[] input = response.codePoints().toArray();
        assertEquals(TerminalTheme.LIGHT, ANSI.parseThemeDsrResponse(input));
    }

    @Test
    public void testParseThemeDsrResponse_NonNumericParam() {
        // Non-numeric parameter
        String response = "\u001B[?997;xn";
        int[] input = response.codePoints().toArray();
        assertNull(ANSI.parseThemeDsrResponse(input));
    }

    @Test
    public void testThemeModeQueryConstant() {
        assertEquals("\u001B[?996n", ANSI.THEME_MODE_QUERY);
    }

    @Test
    public void testThemeNotifyEnableConstant() {
        assertEquals("\u001B[?2031h", ANSI.THEME_NOTIFY_ENABLE);
    }

    @Test
    public void testThemeNotifyDisableConstant() {
        assertEquals("\u001B[?2031l", ANSI.THEME_NOTIFY_DISABLE);
    }

    @Test
    public void testThemeDsrConstants() {
        assertEquals(1, ANSI.THEME_DSR_DARK);
        assertEquals(2, ANSI.THEME_DSR_LIGHT);
    }
}
