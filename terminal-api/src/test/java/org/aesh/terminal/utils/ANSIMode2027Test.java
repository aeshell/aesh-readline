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
 * Tests for Mode 2027 (grapheme cluster mode) ANSI constants and DECRPM parsing.
 *
 * @author Ståle Pedersen
 */
public class ANSIMode2027Test {

    // ==================== Constants ====================

    @Test
    public void testMode2027QueryConstant() {
        assertEquals("\u001B[?2027$p", ANSI.MODE_2027_QUERY);
    }

    @Test
    public void testMode2027EnableConstant() {
        assertEquals("\u001B[?2027h", ANSI.MODE_2027_ENABLE);
    }

    @Test
    public void testMode2027DisableConstant() {
        assertEquals("\u001B[?2027l", ANSI.MODE_2027_DISABLE);
    }

    // ==================== DECRPM Parser: Valid Responses ====================

    @Test
    public void testParseMode2027Response_Set() {
        // Ps=1: mode is set (enabled)
        String response = "\u001B[?2027;1$y";
        int[] input = response.codePoints().toArray();

        Boolean result = ANSI.parseMode2027Response(input);

        assertNotNull("Should parse Ps=1 (set)", result);
        assertTrue("Ps=1 means terminal supports Mode 2027", result);
    }

    @Test
    public void testParseMode2027Response_Reset() {
        // Ps=2: mode is reset (disabled, but recognized)
        String response = "\u001B[?2027;2$y";
        int[] input = response.codePoints().toArray();

        Boolean result = ANSI.parseMode2027Response(input);

        assertNotNull("Should parse Ps=2 (reset)", result);
        assertTrue("Ps=2 means terminal recognizes Mode 2027", result);
    }

    @Test
    public void testParseMode2027Response_PermanentlySet() {
        // Ps=3: mode is permanently set
        String response = "\u001B[?2027;3$y";
        int[] input = response.codePoints().toArray();

        Boolean result = ANSI.parseMode2027Response(input);

        assertNotNull("Should parse Ps=3 (permanently set)", result);
        assertTrue("Ps=3 means terminal supports Mode 2027", result);
    }

    @Test
    public void testParseMode2027Response_NotRecognized() {
        // Ps=0: mode is not recognized
        String response = "\u001B[?2027;0$y";
        int[] input = response.codePoints().toArray();

        Boolean result = ANSI.parseMode2027Response(input);

        assertNotNull("Should parse Ps=0 (not recognized)", result);
        assertFalse("Ps=0 means terminal does not support Mode 2027", result);
    }

    @Test
    public void testParseMode2027Response_PermanentlyReset() {
        // Ps=4: mode is permanently reset
        String response = "\u001B[?2027;4$y";
        int[] input = response.codePoints().toArray();

        Boolean result = ANSI.parseMode2027Response(input);

        assertNotNull("Should parse Ps=4 (permanently reset)", result);
        assertFalse("Ps=4 means terminal does not support Mode 2027", result);
    }

    // ==================== DECRPM Parser: Invalid/Edge Cases ====================

    @Test
    public void testParseMode2027Response_NullInput() {
        assertNull("Should return null for null input", ANSI.parseMode2027Response(null));
    }

    @Test
    public void testParseMode2027Response_EmptyInput() {
        assertNull("Should return null for empty input", ANSI.parseMode2027Response(new int[0]));
    }

    @Test
    public void testParseMode2027Response_TooShortInput() {
        // Less than 9 characters
        int[] input = new int[] { 27, '[', '?', '2' };
        assertNull("Should return null for too short input", ANSI.parseMode2027Response(input));
    }

    @Test
    public void testParseMode2027Response_MissingTerminator() {
        // Valid prefix but no "$y" terminator
        String response = "\u001B[?2027;1";
        int[] input = response.codePoints().toArray();

        assertNull("Should return null when terminator is missing", ANSI.parseMode2027Response(input));
    }

    @Test
    public void testParseMode2027Response_WrongModeNumber() {
        // DECRPM for a different mode (not 2027)
        String response = "\u001B[?1;1$y";
        int[] input = response.codePoints().toArray();

        assertNull("Should return null for wrong mode number", ANSI.parseMode2027Response(input));
    }

    @Test
    public void testParseMode2027Response_MalformedNoSemicolon() {
        String response = "\u001B[?20271$y";
        int[] input = response.codePoints().toArray();

        assertNull("Should return null for malformed response without semicolon",
                ANSI.parseMode2027Response(input));
    }

    @Test
    public void testParseMode2027Response_UnknownPsValue() {
        // Ps=5 is not defined in the DECRPM spec
        String response = "\u001B[?2027;5$y";
        int[] input = response.codePoints().toArray();

        assertNull("Should return null for unknown Ps value", ANSI.parseMode2027Response(input));
    }

    @Test
    public void testParseMode2027Response_NonNumericPs() {
        String response = "\u001B[?2027;x$y";
        int[] input = response.codePoints().toArray();

        assertNull("Should return null for non-numeric Ps", ANSI.parseMode2027Response(input));
    }

    @Test
    public void testParseMode2027Response_EmptyPs() {
        String response = "\u001B[?2027;$y";
        int[] input = response.codePoints().toArray();

        assertNull("Should return null for empty Ps", ANSI.parseMode2027Response(input));
    }

    @Test
    public void testParseMode2027Response_WithPrefixNoise() {
        // Response with leading noise (common in real terminal I/O)
        String response = "some noise\u001B[?2027;1$y";
        int[] input = response.codePoints().toArray();

        Boolean result = ANSI.parseMode2027Response(input);

        assertNotNull("Should parse response with prefix noise", result);
        assertTrue(result);
    }

    @Test
    public void testParseMode2027Response_WithTrailingData() {
        // Response with trailing data
        String response = "\u001B[?2027;2$yextra data";
        int[] input = response.codePoints().toArray();

        Boolean result = ANSI.parseMode2027Response(input);

        assertNotNull("Should parse response with trailing data", result);
        assertTrue(result);
    }
}
