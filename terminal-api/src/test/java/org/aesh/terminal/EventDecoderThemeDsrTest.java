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
package org.aesh.terminal;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.aesh.terminal.utils.TerminalTheme;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests for EventDecoder's theme DSR (CSI ? 997 ; Ps n) filtering.
 * <p>
 * Verifies that unsolicited theme change notifications are intercepted
 * and routed to the theme change handler, while normal input passes through.
 */
public class EventDecoderThemeDsrTest {

    private EventDecoder decoder;
    private List<int[]> receivedInput;
    private List<TerminalTheme> receivedThemes;

    // CSI ? 997 ; 1 n  (dark mode)
    private static final int[] DSR_DARK = { 27, 91, 63, 57, 57, 55, 59, 49, 110 };
    // CSI ? 997 ; 2 n  (light mode)
    private static final int[] DSR_LIGHT = { 27, 91, 63, 57, 57, 55, 59, 50, 110 };

    @Before
    public void setUp() {
        decoder = new EventDecoder();
        receivedInput = new ArrayList<>();
        receivedThemes = new ArrayList<>();
        decoder.setInputHandler(data -> receivedInput.add(data));
        decoder.setThemeChangeHandler(theme -> receivedThemes.add(theme));
    }

    // ==================== Basic DSR detection ====================

    @Test
    public void testDarkModeDsr() {
        decoder.accept(DSR_DARK);

        assertEquals("Handler should receive one theme", 1, receivedThemes.size());
        assertEquals(TerminalTheme.DARK, receivedThemes.get(0));
        assertNoInput("DSR should be consumed, not passed as input");
    }

    @Test
    public void testLightModeDsr() {
        decoder.accept(DSR_LIGHT);

        assertEquals(1, receivedThemes.size());
        assertEquals(TerminalTheme.LIGHT, receivedThemes.get(0));
        assertNoInput("DSR should be consumed");
    }

    // ==================== DSR embedded in normal input ====================

    @Test
    public void testDsrEmbeddedInInput() {
        // 'A' + DSR_DARK + 'B'
        int[] input = concat(new int[] { 65 }, DSR_DARK, new int[] { 66 });
        decoder.accept(input);

        assertEquals(1, receivedThemes.size());
        assertEquals(TerminalTheme.DARK, receivedThemes.get(0));
        assertInputEquals("Normal input around DSR should pass through",
                new int[] { 65, 66 });
    }

    @Test
    public void testDsrAtStartOfInput() {
        // DSR_LIGHT + 'Hello'
        int[] input = concat(DSR_LIGHT, new int[] { 72, 101, 108, 108, 111 });
        decoder.accept(input);

        assertEquals(1, receivedThemes.size());
        assertEquals(TerminalTheme.LIGHT, receivedThemes.get(0));
        assertInputEquals("Input after DSR should pass through",
                new int[] { 72, 101, 108, 108, 111 });
    }

    @Test
    public void testDsrAtEndOfInput() {
        // 'Hi' + DSR_DARK
        int[] input = concat(new int[] { 72, 105 }, DSR_DARK);
        decoder.accept(input);

        assertEquals(1, receivedThemes.size());
        assertEquals(TerminalTheme.DARK, receivedThemes.get(0));
        assertInputEquals("Input before DSR should pass through",
                new int[] { 72, 105 });
    }

    @Test
    public void testMultipleDsrInOneChunk() {
        // DSR_DARK + 'x' + DSR_LIGHT
        int[] input = concat(DSR_DARK, new int[] { 120 }, DSR_LIGHT);
        decoder.accept(input);

        assertEquals(2, receivedThemes.size());
        assertEquals(TerminalTheme.DARK, receivedThemes.get(0));
        assertEquals(TerminalTheme.LIGHT, receivedThemes.get(1));
        assertInputEquals("Only 'x' should pass through", new int[] { 120 });
    }

    // ==================== Split DSR across chunks ====================

    @Test
    public void testDsrSplitAcrossChunks_PrefixThenRest() {
        // First chunk: ESC [ ? 9
        decoder.accept(new int[] { 27, 91, 63, 57 });
        assertNoInput("Partial DSR prefix should be buffered");
        assertEquals(0, receivedThemes.size());

        // Second chunk: 9 7 ; 1 n
        decoder.accept(new int[] { 57, 55, 59, 49, 110 });
        assertEquals(1, receivedThemes.size());
        assertEquals(TerminalTheme.DARK, receivedThemes.get(0));
        assertNoInput("Complete DSR should be consumed");
    }

    @Test
    public void testDsrSplitAtSemicolon() {
        // First chunk: ESC [ ? 9 9 7 ;
        decoder.accept(new int[] { 27, 91, 63, 57, 57, 55, 59 });
        assertEquals(0, receivedThemes.size());

        // Second chunk: 2 n
        decoder.accept(new int[] { 50, 110 });
        assertEquals(1, receivedThemes.size());
        assertEquals(TerminalTheme.LIGHT, receivedThemes.get(0));
    }

    @Test
    public void testDsrSplitByteByByte() {
        // Send each byte separately
        for (int cp : DSR_DARK) {
            decoder.accept(new int[] { cp });
        }

        assertEquals(1, receivedThemes.size());
        assertEquals(TerminalTheme.DARK, receivedThemes.get(0));
        assertNoInput("DSR should be consumed even when split byte-by-byte");
    }

    @Test
    public void testDsrSplitWithNormalInputBefore() {
        // 'A' then partial DSR in two chunks
        decoder.accept(new int[] { 65, 27, 91, 63 }); // 'A' + ESC [ ?
        decoder.accept(new int[] { 57, 57, 55, 59, 49, 110 }); // 9 9 7 ; 1 n

        assertEquals(1, receivedThemes.size());
        assertEquals(TerminalTheme.DARK, receivedThemes.get(0));
        assertInputEquals("'A' should pass through", new int[] { 65 });
    }

    @Test
    public void testDsrSplitWithNormalInputAfter() {
        // Partial DSR then rest + 'B'
        decoder.accept(new int[] { 27, 91, 63, 57, 57 }); // ESC [ ? 9 9
        decoder.accept(new int[] { 55, 59, 50, 110, 66 }); // 7 ; 2 n B

        assertEquals(1, receivedThemes.size());
        assertEquals(TerminalTheme.LIGHT, receivedThemes.get(0));
        assertInputEquals("'B' should pass through", new int[] { 66 });
    }

    // ==================== Mismatch / false start ====================

    @Test
    public void testEscFollowedByNonBracket() {
        // ESC followed by 'A' (this is the Up arrow key sequence part)
        decoder.accept(new int[] { 27, 65 });

        assertEquals(0, receivedThemes.size());
        assertInputEquals("ESC + A should pass through as normal input",
                new int[] { 27, 65 });
    }

    @Test
    public void testEscBracketFollowedByNonQuestion() {
        // ESC [ A (Up arrow)
        decoder.accept(new int[] { 27, 91, 65 });

        assertEquals(0, receivedThemes.size());
        assertInputEquals("ESC [ A should pass through",
                new int[] { 27, 91, 65 });
    }

    @Test
    public void testPartialPrefixMismatch() {
        // ESC [ ? 9 9 8 (wrong — 997 expected, got 998)
        decoder.accept(new int[] { 27, 91, 63, 57, 57, 56 });

        assertEquals(0, receivedThemes.size());
        assertInputEquals("Mismatched prefix should flush through",
                new int[] { 27, 91, 63, 57, 57, 56 });
    }

    @Test
    public void testDsrPrefixThenNonDigit() {
        // ESC [ ? 9 9 7 ; X (X is not a digit or 'n')
        int[] input = { 27, 91, 63, 57, 57, 55, 59, 88 };
        decoder.accept(input);

        assertEquals(0, receivedThemes.size());
        assertInputEquals("Invalid DSR should flush through",
                new int[] { 27, 91, 63, 57, 57, 55, 59, 88 });
    }

    @Test
    public void testDsrPrefixThenNWithoutDigits() {
        // ESC [ ? 9 9 7 ; n (no parameter digits)
        int[] input = { 27, 91, 63, 57, 57, 55, 59, 110 };
        decoder.accept(input);

        assertEquals(0, receivedThemes.size());
        assertInputEquals("DSR with no param should flush through",
                new int[] { 27, 91, 63, 57, 57, 55, 59, 110 });
    }

    @Test
    public void testSplitPrefixMismatch() {
        // First chunk: ESC [ ?
        decoder.accept(new int[] { 27, 91, 63 });
        assertNoInput("Partial prefix should be buffered");

        // Second chunk: starts with something wrong
        decoder.accept(new int[] { 48 }); // '0' instead of '9'

        assertEquals(0, receivedThemes.size());
        // ESC [ ? should be flushed, then '0' processed normally
        assertInputEquals("Mismatched partial prefix should flush",
                new int[] { 27, 91, 63, 48 });
    }

    // ==================== No handler — passthrough ====================

    @Test
    public void testDsrPassesThroughWithoutHandler() {
        decoder.setThemeChangeHandler(null);
        decoder.accept(DSR_DARK);

        // Without a handler, the DSR sequence should pass through as normal input
        assertInputEquals("DSR should pass through without handler", DSR_DARK);
    }

    @Test
    public void testNormalInputPassesThroughWithHandler() {
        // Normal text should pass through unmodified
        int[] input = { 72, 101, 108, 108, 111 }; // "Hello"
        decoder.accept(input);

        assertEquals(0, receivedThemes.size());
        assertInputEquals("Normal input should pass through", input);
    }

    // ==================== Unknown mode values ====================

    @Test
    public void testUnknownModeValue() {
        // CSI ? 997 ; 3 n  (mode 3 is unknown)
        int[] input = { 27, 91, 63, 57, 57, 55, 59, 51, 110 };
        decoder.accept(input);

        // Sequence should be consumed (not passed as input) but no theme dispatched
        assertEquals("Unknown mode should not dispatch", 0, receivedThemes.size());
        assertNoInput("Unknown mode DSR should still be consumed");
    }

    @Test
    public void testMultiDigitModeValue() {
        // CSI ? 997 ; 12 n  (mode 12 is unknown but multi-digit)
        int[] input = { 27, 91, 63, 57, 57, 55, 59, 49, 50, 110 };
        decoder.accept(input);

        assertEquals("Multi-digit unknown mode should not dispatch", 0, receivedThemes.size());
        assertNoInput("Multi-digit mode DSR should still be consumed");
    }

    // ==================== Handler management ====================

    @Test
    public void testSetHandlerResetsState() {
        // Start a partial DSR
        decoder.accept(new int[] { 27, 91, 63, 57 });

        // Clear handler — should reset state
        decoder.setThemeChangeHandler(null);

        // Set new handler and send another partial DSR
        List<TerminalTheme> newThemes = new ArrayList<>();
        decoder.setThemeChangeHandler(newThemes::add);

        // The old partial should be gone — this should work as a fresh sequence
        decoder.accept(DSR_DARK);
        assertEquals(1, newThemes.size());
        assertEquals(TerminalTheme.DARK, newThemes.get(0));
    }

    @Test
    public void testGetThemeChangeHandler() {
        assertNotNull(decoder.getThemeChangeHandler());

        decoder.setThemeChangeHandler(null);
        assertNull(decoder.getThemeChangeHandler());
    }

    // ==================== Edge cases ====================

    @Test
    public void testEmptyInput() {
        decoder.accept(new int[0]);

        assertEquals(0, receivedThemes.size());
        assertEquals("Empty input should produce no output", 0, receivedInput.size());
    }

    @Test
    public void testConsecutiveDsrWithoutGap() {
        // Two DSR sequences back to back
        int[] input = concat(DSR_DARK, DSR_LIGHT);
        decoder.accept(input);

        assertEquals(2, receivedThemes.size());
        assertEquals(TerminalTheme.DARK, receivedThemes.get(0));
        assertEquals(TerminalTheme.LIGHT, receivedThemes.get(1));
        assertNoInput("Both DSRs should be consumed");
    }

    @Test
    public void testEscAtEndOfChunk() {
        // Chunk ends with ESC — could be start of DSR or normal escape
        decoder.accept(new int[] { 65, 27 }); // 'A' + ESC

        // 'A' should pass through, ESC should be buffered
        assertInputEquals("'A' should pass through", new int[] { 65 });

        // Next chunk completes the DSR
        decoder.accept(new int[] { 91, 63, 57, 57, 55, 59, 49, 110 });
        assertEquals(1, receivedThemes.size());
        assertEquals(TerminalTheme.DARK, receivedThemes.get(0));
    }

    @Test
    public void testEscAtEndOfChunkFollowedByNormalInput() {
        // Chunk ends with ESC
        decoder.accept(new int[] { 65, 27 }); // 'A' + ESC

        // Next chunk is NOT a DSR continuation
        decoder.accept(new int[] { 91, 65 }); // [ A (up arrow)

        // ESC [ A should be flushed as normal input
        int[] allInput = flattenInput();
        assertArrayEquals("All input should pass through",
                new int[] { 65, 27, 91, 65 }, allInput);
    }

    // ==================== Interaction with signal handling ====================

    @Test
    public void testDsrFilteringWorksWithSignalHandler() {
        List<String> signals = new ArrayList<>();
        decoder.setSignalHandler(signal -> signals.add(signal.name()));

        // Ctrl-C (3) + DSR_DARK + 'A'
        int[] input = concat(new int[] { 3 }, DSR_DARK, new int[] { 65 });
        decoder.accept(input);

        assertEquals("Signal should be detected", 1, signals.size());
        assertEquals("INT", signals.get(0));
        assertEquals("Theme should be detected", 1, receivedThemes.size());
        assertEquals(TerminalTheme.DARK, receivedThemes.get(0));
        assertInputEquals("'A' should pass through", new int[] { 65 });
    }

    // ==================== Helpers ====================

    private void assertNoInput(String message) {
        int totalLen = 0;
        for (int[] chunk : receivedInput) {
            totalLen += chunk.length;
        }
        assertEquals(message, 0, totalLen);
    }

    private void assertInputEquals(String message, int[] expected) {
        int[] actual = flattenInput();
        assertArrayEquals(message, expected, actual);
    }

    private int[] flattenInput() {
        int totalLen = 0;
        for (int[] chunk : receivedInput) {
            totalLen += chunk.length;
        }
        int[] result = new int[totalLen];
        int pos = 0;
        for (int[] chunk : receivedInput) {
            System.arraycopy(chunk, 0, result, pos, chunk.length);
            pos += chunk.length;
        }
        return result;
    }

    private static int[] concat(int[]... arrays) {
        int totalLen = 0;
        for (int[] a : arrays) {
            totalLen += a.length;
        }
        int[] result = new int[totalLen];
        int pos = 0;
        for (int[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }
}
