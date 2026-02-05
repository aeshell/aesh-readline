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
}
