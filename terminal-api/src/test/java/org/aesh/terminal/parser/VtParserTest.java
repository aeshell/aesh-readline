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
package org.aesh.terminal.parser;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

/**
 * Tests for the VT escape sequence parser.
 */
public class VtParserTest {

    // ==================== Helper ====================

    /** Records all parser events for assertion. */
    static class RecordingHandler implements VtHandler {
        final List<String> events = new ArrayList<>();

        @Override
        public void print(int codePoint) {
            events.add("print:" + (char) codePoint);
        }

        @Override
        public void execute(int controlChar) {
            events.add("execute:0x" + Integer.toHexString(controlChar));
        }

        @Override
        public void escDispatch(int finalChar, int[] intermediates, int intermediateCount) {
            StringBuilder sb = new StringBuilder("esc:");
            for (int i = 0; i < intermediateCount; i++) {
                sb.append((char) intermediates[i]);
            }
            sb.append((char) finalChar);
            events.add(sb.toString());
        }

        @Override
        public void csiDispatch(int finalChar, int[] params, int paramCount,
                int[] intermediates, int intermediateCount,
                boolean hasSubParams) {
            StringBuilder sb = new StringBuilder("csi:");
            for (int i = 0; i < intermediateCount; i++) {
                sb.append((char) intermediates[i]);
            }
            sb.append('[');
            for (int i = 0; i < paramCount; i++) {
                if (i > 0)
                    sb.append(',');
                sb.append(params[i]);
            }
            sb.append(']');
            sb.append((char) finalChar);
            if (hasSubParams)
                sb.append(":sub");
            events.add(sb.toString());
        }

        @Override
        public void hook(int finalChar, int[] params, int paramCount,
                int[] intermediates, int intermediateCount) {
            events.add("hook:" + (char) finalChar);
        }

        @Override
        public void put(int b) {
            events.add("put:" + (char) b);
        }

        @Override
        public void unhook() {
            events.add("unhook");
        }

        @Override
        public void oscEnd(String data) {
            events.add("osc:" + data);
        }
    }

    private void feed(VtParser parser, String s) {
        byte[] bytes = s.getBytes();
        parser.advance(bytes, 0, bytes.length);
    }

    // ==================== Ground state ====================

    @Test
    public void testPrintableText() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        feed(p, "Hello");
        assertEquals(Arrays.asList(
                "print:H", "print:e", "print:l", "print:l", "print:o"),
                h.events);
    }

    @Test
    public void testC0Controls() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        feed(p, "\r\n\t");
        assertEquals(Arrays.asList(
                "execute:0xd", "execute:0xa", "execute:0x9"),
                h.events);
    }

    @Test
    public void testMixedTextAndControls() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        feed(p, "A\nB");
        assertEquals(Arrays.asList(
                "print:A", "execute:0xa", "print:B"),
                h.events);
    }

    // ==================== ESC sequences ====================

    @Test
    public void testSimpleEscSequence() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // ESC M = Reverse Index
        feed(p, "\033M");
        assertEquals(Arrays.asList("esc:M"), h.events);
        assertEquals(VtParser.GROUND, p.getState());
    }

    @Test
    public void testEscWithIntermediate() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // ESC ( B = Select ASCII charset
        feed(p, "\033(B");
        assertEquals(Arrays.asList("esc:(B"), h.events);
    }

    @Test
    public void testEscSaveCursor() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // ESC 7 = Save Cursor (DECSC)
        feed(p, "\0337");
        assertEquals(Arrays.asList("esc:7"), h.events);
    }

    // ==================== CSI sequences ====================

    @Test
    public void testCsiNoParams() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI H = Cursor Home (no params = defaults)
        feed(p, "\033[H");
        assertEquals(1, h.events.size());
        assertEquals("csi:[-1]H", h.events.get(0));
    }

    @Test
    public void testCsiSingleParam() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI 2 J = Erase Display
        feed(p, "\033[2J");
        assertEquals("csi:[2]J", h.events.get(0));
    }

    @Test
    public void testCsiMultipleParams() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI 10;20 H = Cursor Position (row 10, col 20)
        feed(p, "\033[10;20H");
        assertEquals("csi:[10,20]H", h.events.get(0));
    }

    @Test
    public void testCsiDefaultParams() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI ;5 H = Cursor Position (default row, col 5)
        feed(p, "\033[;5H");
        assertEquals("csi:[-1,5]H", h.events.get(0));
    }

    @Test
    public void testCsiPrivateMarker() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI ? 1049 h = Enable alt screen
        feed(p, "\033[?1049h");
        assertEquals("csi:?[1049]h", h.events.get(0));
    }

    @Test
    public void testCsiSgrColors() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI 38;2;255;0;128 m = Set foreground to RGB
        feed(p, "\033[38;2;255;0;128m");
        assertEquals("csi:[38,2,255,0,128]m", h.events.get(0));
    }

    @Test
    public void testCsiColonSubParams() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI 38:2:255:0:128 m = Set foreground to RGB (colon syntax)
        feed(p, "\033[38:2:255:0:128m");
        assertTrue(h.events.get(0).endsWith(":sub"));
    }

    @Test
    public void testCsiArrowKeys() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // Up, Down, Right, Left
        feed(p, "\033[A\033[B\033[C\033[D");
        assertEquals(4, h.events.size());
        assertEquals("csi:[-1]A", h.events.get(0));
        assertEquals("csi:[-1]B", h.events.get(1));
        assertEquals("csi:[-1]C", h.events.get(2));
        assertEquals("csi:[-1]D", h.events.get(3));
    }

    @Test
    public void testCsiC0Executed() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI with embedded LF — C0 should be executed inline per Williams
        feed(p, "\033[2\nJ");
        assertEquals(2, h.events.size());
        assertEquals("execute:0xa", h.events.get(0));
        assertEquals("csi:[2]J", h.events.get(1));
    }

    // ==================== CSI error recovery ====================

    @Test
    public void testCsiIgnoreOnInvalidPrivateMarker() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI ? after params → csi_ignore
        feed(p, "\033[1?H");
        // Should NOT dispatch — the sequence is ignored
        assertTrue(h.events.isEmpty());
        assertEquals(VtParser.GROUND, p.getState());
    }

    @Test
    public void testCsiIgnoreParamAfterIntermediate() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI with param after intermediate → csi_ignore
        feed(p, "\033[ 1H");
        // Space is intermediate (0x20), then '1' is param → error → ignore
        assertTrue(h.events.isEmpty());
    }

    // ==================== OSC sequences ====================

    @Test
    public void testOscBelTerminated() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // OSC 0 ; title BEL = Set window title
        feed(p, "\033]0;My Title\007");
        assertEquals(1, h.events.size());
        assertEquals("osc:0;My Title", h.events.get(0));
        assertEquals(VtParser.GROUND, p.getState());
    }

    @Test
    public void testOscStTerminated() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // OSC 0 ; title ST (ESC \) = Set window title
        feed(p, "\033]0;My Title\033\\");
        // OSC ends on ESC (which cancels to escape state), then \ dispatches
        assertEquals(2, h.events.size());
        assertEquals("osc:0;My Title", h.events.get(0));
        // The \ after ESC is an esc_dispatch
        assertEquals("esc:\\", h.events.get(1));
    }

    @Test
    public void testOscColorQuery() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // OSC 10 ; rgb:ffff/8080/0000 BEL = foreground color response
        feed(p, "\033]10;rgb:ffff/8080/0000\007");
        assertEquals("osc:10;rgb:ffff/8080/0000", h.events.get(0));
    }

    // ==================== DCS sequences ====================

    @Test
    public void testDcsPassthrough() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // DCS q ... ST = Sixel data
        feed(p, "\033Pq#0;2;0;0;0\033\\");
        assertTrue(h.events.get(0).equals("hook:q"));
        // Data bytes: #0;2;0;0;0
        assertTrue(h.events.contains("put:#"));
        assertTrue(h.events.contains("unhook"));
    }

    // ==================== Anywhere transitions ====================

    @Test
    public void testEscCancelsSequence() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // Start a CSI, then cancel with ESC M
        feed(p, "\033[1\033M");
        // The CSI is cancelled, ESC M is dispatched
        assertEquals(1, h.events.size());
        assertEquals("esc:M", h.events.get(0));
    }

    @Test
    public void testCanCancelsToGround() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // Start a CSI, cancel with CAN (0x18)
        feed(p, "\033[1\030X");
        // CAN executes and goes to ground, then X is printed
        assertEquals(Arrays.asList("execute:0x18", "print:X"), h.events);
    }

    @Test
    public void testSubCancelsToGround() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // Start a CSI, cancel with SUB (0x1A)
        feed(p, "\033[1\032X");
        assertEquals(Arrays.asList("execute:0x1a", "print:X"), h.events);
    }

    // ==================== DA1/DA2 responses ====================

    @Test
    public void testDa1Response() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // DA1 response: CSI ? 65 ; 1 ; 9 c
        feed(p, "\033[?65;1;9c");
        assertEquals("csi:?[65,1,9]c", h.events.get(0));
    }

    @Test
    public void testDa2Response() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // DA2 response: CSI > 1 ; 4600 ; 0 c
        feed(p, "\033[>1;4600;0c");
        assertEquals("csi:>[1,4600,0]c", h.events.get(0));
    }

    // ==================== Mouse events (SGR) ====================

    @Test
    public void testSgrMousePress() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI < 0 ; 5 ; 3 M = Left button press at (5,3)
        feed(p, "\033[<0;5;3M");
        assertEquals("csi:<[0,5,3]M", h.events.get(0));
    }

    @Test
    public void testSgrMouseRelease() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // CSI < 0 ; 5 ; 3 m = Left button release at (5,3)
        feed(p, "\033[<0;5;3m");
        assertEquals("csi:<[0,5,3]m", h.events.get(0));
    }

    // ==================== SOS/PM/APC strings ====================

    @Test
    public void testSosIgnored() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // ESC X (SOS) ... ESC \ (ST)
        feed(p, "\033Xsome data\033\\");
        // SOS content is ignored, ESC cancels, \ is esc_dispatch
        assertEquals(1, h.events.size());
        assertEquals("esc:\\", h.events.get(0));
    }

    @Test
    public void testApcIgnored() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // ESC _ (APC) ... ESC \ (ST)
        feed(p, "\033_app data\033\\");
        assertEquals(1, h.events.size());
        assertEquals("esc:\\", h.events.get(0));
    }

    // ==================== Table completeness ====================

    @Test
    public void testTableCompleteness() {
        // Verify every state/byte combination has a defined entry
        for (int s = 0; s < VtParser.STATE_COUNT; s++) {
            for (int b = 0; b < 256; b++) {
                short entry = VtParser.TABLE[(s << 8) | b];
                int action = (entry >> 4) & 0x0F;
                int nextState = entry & 0x0F;
                assertTrue("State " + s + " byte " + b + ": nextState out of range",
                        nextState >= 0 && nextState < VtParser.STATE_COUNT);
                assertTrue("State " + s + " byte " + b + ": action out of range",
                        action >= 0 && action <= 14);
            }
        }
    }

    @Test
    public void testGrEqualsGl() {
        // Williams: GR area (A0-FF) treated identically to GL (20-7F) in all states
        for (int s = 0; s < VtParser.STATE_COUNT; s++) {
            for (int b = 0xA0; b <= 0xFF; b++) {
                assertEquals(
                        "State " + s + ": GR byte " + b + " should match GL byte " + (b - 0x80),
                        VtParser.TABLE[(s << 8) | (b - 0x80)],
                        VtParser.TABLE[(s << 8) | b]);
            }
        }
    }

    @Test
    public void testNegativeInputIgnored() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        p.advance(-1);
        p.advance(Integer.MIN_VALUE);
        assertTrue(p.isGroundState());
        assertTrue(h.events.isEmpty());
    }

    @Test
    public void testParamOverflowSaturates() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        feed(p, "\033[99999999999m");
        assertEquals(1, h.events.size());
        assertEquals("csi:[" + Integer.MAX_VALUE + "]m", h.events.get(0));
    }

    @Test
    public void testOversizedOscDeliversAndRecovers() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        StringBuilder payload = new StringBuilder(6000);
        for (int i = 0; i < 6000; i++) {
            payload.append('a');
        }
        String big = "52;c;" + payload;
        feed(p, "\033]" + big + "\007");
        feed(p, "\033]0;title\007");
        assertEquals(2, h.events.size());
        assertEquals("osc:" + big, h.events.get(0));
        assertEquals("osc:0;title", h.events.get(1));
    }

    // ==================== Byte-at-a-time vs batch ====================

    @Test
    public void testByteAtATimeMatchesBatch() {
        String input = "\033[?1049h\033]0;title\007\033[38;2;255;0;0mHello\033[0m";

        RecordingHandler h1 = new RecordingHandler();
        VtParser p1 = new VtParser(h1);
        byte[] bytes = input.getBytes();
        p1.advance(bytes, 0, bytes.length);

        RecordingHandler h2 = new RecordingHandler();
        VtParser p2 = new VtParser(h2);
        for (byte b : bytes) {
            p2.advance(b & 0xFF);
        }

        assertEquals(h1.events, h2.events);
    }

    // ==================== Code point input ====================

    @Test
    public void testCodePointArray() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        int[] cps = { 'H', 'i' };
        p.advance(cps, 0, cps.length);
        assertEquals(Arrays.asList("print:H", "print:i"), h.events);
    }

    @Test
    public void testHighCodePointsArePrintable() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // Unicode code point > 255 should be treated as printable in ground state
        p.advance(0x1F600); // emoji
        assertEquals(1, h.events.size());
        // print event with the code point value
        assertTrue(h.events.get(0).startsWith("print:"));
    }

    @Test
    public void testHighCodePointsIgnoredInEscapeState() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // Start ESC, then feed a high code point — should be ignored, not printed
        p.advance(0x1B); // ESC
        p.advance(0x1F600); // high code point in escape state — ignored
        p.advance('M'); // complete ESC M
        // Only the ESC M should produce an event
        assertEquals(Arrays.asList("esc:M"), h.events);
    }

    @Test
    public void testCsiViaCodePoints() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        int[] cps = { 0x1B, '[', '2', 'J' };
        p.advance(cps, 0, cps.length);
        assertEquals("csi:[2]J", h.events.get(0));
    }

    // ==================== Partial input ====================

    @Test
    public void testPartialCsiAcrossAdvanceCalls() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        // Split CSI 2 J across two advance() calls
        feed(p, "\033[");
        assertTrue(h.events.isEmpty()); // not dispatched yet
        feed(p, "2J");
        assertEquals("csi:[2]J", h.events.get(0));
    }

    @Test
    public void testPartialOscAcrossAdvanceCalls() {
        RecordingHandler h = new RecordingHandler();
        VtParser p = new VtParser(h);
        feed(p, "\033]0;My");
        assertTrue(h.events.isEmpty());
        feed(p, " Title\007");
        assertEquals("osc:0;My Title", h.events.get(0));
    }
}
