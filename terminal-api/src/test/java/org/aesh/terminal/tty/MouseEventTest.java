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
package org.aesh.terminal.tty;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.aesh.terminal.EventDecoder;
import org.junit.Test;

/**
 * Tests for mouse event parsing, protocol control, and EventDecoder integration.
 */
public class MouseEventTest {

    // ==================== SGR Parsing ====================

    @Test
    public void testSgrLeftPress() {
        // CSI < 0 ; 5 ; 3 M → left press at (5,3)
        MouseEvent e = MouseEvent.parseSgr('M', new int[] { 0, 5, 3 }, 3);
        assertNotNull(e);
        assertEquals(MouseEvent.Type.PRESS, e.type());
        assertEquals(MouseEvent.Button.LEFT, e.button());
        assertEquals(5, e.x());
        assertEquals(3, e.y());
        assertFalse(e.shift());
        assertFalse(e.alt());
        assertFalse(e.ctrl());
    }

    @Test
    public void testSgrLeftRelease() {
        // CSI < 0 ; 5 ; 3 m → left release at (5,3)
        MouseEvent e = MouseEvent.parseSgr('m', new int[] { 0, 5, 3 }, 3);
        assertNotNull(e);
        assertEquals(MouseEvent.Type.RELEASE, e.type());
        assertEquals(MouseEvent.Button.LEFT, e.button());
    }

    @Test
    public void testSgrMiddlePress() {
        MouseEvent e = MouseEvent.parseSgr('M', new int[] { 1, 10, 20 }, 3);
        assertEquals(MouseEvent.Button.MIDDLE, e.button());
        assertEquals(MouseEvent.Type.PRESS, e.type());
    }

    @Test
    public void testSgrRightPress() {
        MouseEvent e = MouseEvent.parseSgr('M', new int[] { 2, 10, 20 }, 3);
        assertEquals(MouseEvent.Button.RIGHT, e.button());
    }

    @Test
    public void testSgrRightRelease() {
        MouseEvent e = MouseEvent.parseSgr('m', new int[] { 2, 10, 20 }, 3);
        assertEquals(MouseEvent.Type.RELEASE, e.type());
        assertEquals(MouseEvent.Button.RIGHT, e.button());
    }

    @Test
    public void testSgrScrollUp() {
        // button byte 64 = scroll wheel (bit 6) + index 0 (scroll up)
        MouseEvent e = MouseEvent.parseSgr('M', new int[] { 64, 5, 3 }, 3);
        assertEquals(MouseEvent.Type.SCROLL, e.type());
        assertEquals(MouseEvent.Button.SCROLL_UP, e.button());
    }

    @Test
    public void testSgrScrollDown() {
        // button byte 65 = scroll wheel (bit 6) + index 1 (scroll down)
        MouseEvent e = MouseEvent.parseSgr('M', new int[] { 65, 5, 3 }, 3);
        assertEquals(MouseEvent.Type.SCROLL, e.type());
        assertEquals(MouseEvent.Button.SCROLL_DOWN, e.button());
    }

    @Test
    public void testSgrDrag() {
        // button byte 32 = motion (bit 5) + index 0 (left)
        MouseEvent e = MouseEvent.parseSgr('M', new int[] { 32, 5, 3 }, 3);
        assertEquals(MouseEvent.Type.DRAG, e.type());
        assertEquals(MouseEvent.Button.LEFT, e.button());
    }

    @Test
    public void testSgrMove() {
        // button byte 35 = motion (bit 5) + index 3 (none) → hover
        MouseEvent e = MouseEvent.parseSgr('M', new int[] { 35, 5, 3 }, 3);
        assertEquals(MouseEvent.Type.MOVE, e.type());
        assertEquals(MouseEvent.Button.NONE, e.button());
    }

    @Test
    public void testSgrModifiers() {
        // button byte 0x1C = shift(4) + alt(8) + ctrl(16) + index 0 (left)
        MouseEvent e = MouseEvent.parseSgr('M', new int[] { 0x1C, 5, 3 }, 3);
        assertEquals(MouseEvent.Type.PRESS, e.type());
        assertTrue(e.shift());
        assertTrue(e.alt());
        assertTrue(e.ctrl());
    }

    @Test
    public void testSgrShiftOnly() {
        MouseEvent e = MouseEvent.parseSgr('M', new int[] { 4, 5, 3 }, 3);
        assertTrue(e.shift());
        assertFalse(e.alt());
        assertFalse(e.ctrl());
    }

    @Test
    public void testSgrInvalidParams() {
        assertNull(MouseEvent.parseSgr('M', new int[] { 0, 5 }, 2)); // too few
        assertNull(MouseEvent.parseSgr('M', new int[] { 0, 0, 3 }, 3)); // x=0 invalid
        assertNull(MouseEvent.parseSgr('M', new int[] { 0, 5, 0 }, 3)); // y=0 invalid
    }

    @Test
    public void testIsSgrMouseEvent() {
        assertTrue(MouseEvent.isSgrMouseEvent('M', new int[] { '<' }, 1));
        assertTrue(MouseEvent.isSgrMouseEvent('m', new int[] { '<' }, 1));
        assertFalse(MouseEvent.isSgrMouseEvent('H', new int[] { '<' }, 1)); // wrong final
        assertFalse(MouseEvent.isSgrMouseEvent('M', new int[] { '?' }, 1)); // wrong marker
        assertFalse(MouseEvent.isSgrMouseEvent('M', new int[] {}, 0)); // no marker
    }

    @Test
    public void testLargeCoordinates() {
        // SGR supports coordinates > 223 (no limit unlike legacy)
        MouseEvent e = MouseEvent.parseSgr('M', new int[] { 0, 500, 300 }, 3);
        assertEquals(500, e.x());
        assertEquals(300, e.y());
    }

    // ==================== URXVT Parsing ====================

    @Test
    public void testUrxvtLeftPress() {
        // CSI 0 ; 5 ; 3 M (no '<' marker) → left press at (5,3)
        MouseEvent e = MouseEvent.parseUrxvt(new int[] { 0, 5, 3 }, 3);
        assertNotNull(e);
        assertEquals(MouseEvent.Type.PRESS, e.type());
        assertEquals(MouseEvent.Button.LEFT, e.button());
        assertEquals(5, e.x());
        assertEquals(3, e.y());
        assertFalse(e.shift());
        assertFalse(e.alt());
        assertFalse(e.ctrl());
    }

    @Test
    public void testUrxvtReleaseWithoutButtonId() {
        // CSI 3 ; 5 ; 3 M → release, button unknown
        MouseEvent e = MouseEvent.parseUrxvt(new int[] { 3, 5, 3 }, 3);
        assertNotNull(e);
        assertEquals(MouseEvent.Type.RELEASE, e.type());
        assertEquals(MouseEvent.Button.NONE, e.button());
    }

    @Test
    public void testUrxvtModifiersAndScroll() {
        // Pb=4+16+64: shift + ctrl + scroll, button index 0 → scroll up
        MouseEvent e = MouseEvent.parseUrxvt(new int[] { 84, 10, 20 }, 3);
        assertNotNull(e);
        assertEquals(MouseEvent.Type.SCROLL, e.type());
        assertEquals(MouseEvent.Button.SCROLL_UP, e.button());
        assertTrue(e.shift());
        assertFalse(e.alt());
        assertTrue(e.ctrl());
    }

    @Test
    public void testUrxvtInvalidParams() {
        assertNull(MouseEvent.parseUrxvt(new int[] { 0, 5 }, 2)); // too few
        assertNull(MouseEvent.parseUrxvt(new int[] { 0, 0, 3 }, 3)); // x=0 invalid
        assertNull(MouseEvent.parseUrxvt(new int[] { 0, 5, 0 }, 3)); // y=0 invalid
    }

    @Test
    public void testEventDecoderInterceptsUrxvtMouse() {
        List<MouseEvent> mouseEvents = new ArrayList<>();
        List<int[]> inputEvents = new ArrayList<>();

        EventDecoder decoder = new EventDecoder();
        decoder.setInputHandler(inputEvents::add);
        decoder.setMouseHandler(mouseEvents::add);

        // Feed URXVT mouse sequence: ESC [ 0 ; 5 ; 3 M (no '<')
        int[] mouseSeq = { 27, 91, 48, 59, 53, 59, 51, 77 };
        decoder.accept(mouseSeq);

        assertEquals(1, mouseEvents.size());
        assertEquals(MouseEvent.Type.PRESS, mouseEvents.get(0).type());
        assertEquals(MouseEvent.Button.LEFT, mouseEvents.get(0).button());
        assertEquals(5, mouseEvents.get(0).x());
        assertEquals(3, mouseEvents.get(0).y());
        assertTrue(inputEvents.isEmpty());
    }

    @Test
    public void testEventDecoderDeleteLinesNotMouse() {
        // CSI 3 M (Delete Lines, single param) must pass through as input,
        // not be mistaken for a URXVT mouse report.
        List<MouseEvent> mouseEvents = new ArrayList<>();
        List<int[]> inputEvents = new ArrayList<>();

        EventDecoder decoder = new EventDecoder();
        decoder.setInputHandler(inputEvents::add);
        decoder.setMouseHandler(mouseEvents::add);

        int[] deleteLines = { 27, 91, 51, 77 };
        decoder.accept(deleteLines);

        assertTrue(mouseEvents.isEmpty());
        assertFalse(inputEvents.isEmpty());
    }

    // ==================== MouseEvent Object ====================

    @Test
    public void testEquals() {
        MouseEvent a = new MouseEvent(MouseEvent.Type.PRESS, MouseEvent.Button.LEFT,
                5, 3, false, false, false);
        MouseEvent b = new MouseEvent(MouseEvent.Type.PRESS, MouseEvent.Button.LEFT,
                5, 3, false, false, false);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testNotEquals() {
        MouseEvent a = new MouseEvent(MouseEvent.Type.PRESS, MouseEvent.Button.LEFT,
                5, 3, false, false, false);
        MouseEvent b = new MouseEvent(MouseEvent.Type.RELEASE, MouseEvent.Button.LEFT,
                5, 3, false, false, false);
        assertNotEquals(a, b);
    }

    @Test
    public void testToString() {
        MouseEvent e = new MouseEvent(MouseEvent.Type.PRESS, MouseEvent.Button.LEFT,
                5, 3, true, false, true);
        String s = e.toString();
        assertTrue(s.contains("PRESS"));
        assertTrue(s.contains("LEFT"));
        assertTrue(s.contains("5"));
        assertTrue(s.contains("3"));
        assertTrue(s.contains("Shift"));
        assertTrue(s.contains("Ctrl"));
        assertFalse(s.contains("Alt"));
    }

    // ==================== MouseTracking Sequences ====================

    @Test
    public void testEnableNormal() throws IOException {
        StringBuilder sb = new StringBuilder();
        MouseTracking.enable(sb, MouseTracking.Protocol.NORMAL);
        assertEquals("\033[?1000h", sb.toString());
    }

    @Test
    public void testDisableNormal() throws IOException {
        StringBuilder sb = new StringBuilder();
        MouseTracking.disable(sb, MouseTracking.Protocol.NORMAL);
        assertEquals("\033[?1000l", sb.toString());
    }

    @Test
    public void testEnableSgrEncoding() throws IOException {
        StringBuilder sb = new StringBuilder();
        MouseTracking.enableEncoding(sb, MouseTracking.Encoding.SGR);
        assertEquals("\033[?1006h", sb.toString());
    }

    @Test
    public void testDisableSgrEncoding() throws IOException {
        StringBuilder sb = new StringBuilder();
        MouseTracking.disableEncoding(sb, MouseTracking.Encoding.SGR);
        assertEquals("\033[?1006l", sb.toString());
    }

    @Test
    public void testAllProtocols() throws IOException {
        StringBuilder sb = new StringBuilder();
        MouseTracking.enable(sb, MouseTracking.Protocol.X10);
        assertEquals("\033[?9h", sb.toString());

        sb.setLength(0);
        MouseTracking.enable(sb, MouseTracking.Protocol.BUTTON_MOTION);
        assertEquals("\033[?1002h", sb.toString());

        sb.setLength(0);
        MouseTracking.enable(sb, MouseTracking.Protocol.ANY_MOTION);
        assertEquals("\033[?1003h", sb.toString());
    }

    // ==================== EventDecoder Integration ====================

    @Test
    public void testEventDecoderInterceptsMouse() {
        List<MouseEvent> mouseEvents = new ArrayList<>();
        List<int[]> inputEvents = new ArrayList<>();

        EventDecoder decoder = new EventDecoder();
        decoder.setInputHandler(inputEvents::add);
        decoder.setMouseHandler(mouseEvents::add);

        // Feed SGR mouse sequence: ESC [ < 0 ; 5 ; 3 M
        int[] mouseSeq = { 27, 91, 60, 48, 59, 53, 59, 51, 77 };
        decoder.accept(mouseSeq);

        // Mouse event should be dispatched
        assertEquals(1, mouseEvents.size());
        assertEquals(MouseEvent.Type.PRESS, mouseEvents.get(0).type());
        assertEquals(MouseEvent.Button.LEFT, mouseEvents.get(0).button());
        assertEquals(5, mouseEvents.get(0).x());
        assertEquals(3, mouseEvents.get(0).y());

        // No input should leak through
        assertTrue(inputEvents.isEmpty());
    }

    @Test
    public void testEventDecoderTextWithMouse() {
        List<MouseEvent> mouseEvents = new ArrayList<>();
        List<int[]> inputEvents = new ArrayList<>();

        EventDecoder decoder = new EventDecoder();
        decoder.setInputHandler(inputEvents::add);
        decoder.setMouseHandler(mouseEvents::add);

        // Text + mouse + text: "hi" + ESC[<0;5;3M + "lo"
        int[] mixed = { 'h', 'i', 27, 91, 60, 48, 59, 53, 59, 51, 77, 'l', 'o' };
        decoder.accept(mixed);

        // Should get mouse event
        assertEquals(1, mouseEvents.size());
        assertEquals(MouseEvent.Type.PRESS, mouseEvents.get(0).type());

        // Should get input (text before and after mouse)
        assertFalse(inputEvents.isEmpty());
        // Verify no mouse bytes leaked into input
        int totalInputChars = 0;
        for (int[] chunk : inputEvents) {
            for (int cp : chunk) {
                assertTrue("Unexpected control char " + cp + " in input",
                        cp >= 32 || cp == 27); // only printable or ESC
            }
            totalInputChars += chunk.length;
        }
    }

    @Test
    public void testEventDecoderNoHandlerPassesThrough() {
        List<int[]> inputEvents = new ArrayList<>();

        EventDecoder decoder = new EventDecoder();
        decoder.setInputHandler(inputEvents::add);
        // No mouse handler — sequences should pass through as input

        int[] mouseSeq = { 27, 91, 60, 48, 59, 53, 59, 51, 77 };
        decoder.accept(mouseSeq);

        // Input should contain the raw mouse bytes
        assertFalse(inputEvents.isEmpty());
    }

    @Test
    public void testEventDecoderMouseRelease() {
        List<MouseEvent> mouseEvents = new ArrayList<>();

        EventDecoder decoder = new EventDecoder();
        decoder.setInputHandler(input -> {
        });
        decoder.setMouseHandler(mouseEvents::add);

        // SGR release: ESC [ < 0 ; 5 ; 3 m (lowercase m)
        int[] releaseSeq = { 27, 91, 60, 48, 59, 53, 59, 51, 109 };
        decoder.accept(releaseSeq);

        assertEquals(1, mouseEvents.size());
        assertEquals(MouseEvent.Type.RELEASE, mouseEvents.get(0).type());
    }

    @Test
    public void testEventDecoderMultiDigitCoords() {
        List<MouseEvent> mouseEvents = new ArrayList<>();

        EventDecoder decoder = new EventDecoder();
        decoder.setInputHandler(input -> {
        });
        decoder.setMouseHandler(mouseEvents::add);

        // ESC [ < 0 ; 42 ; 17 M
        int[] seq = { 27, 91, 60, 48, 59, 52, 50, 59, 49, 55, 77 };
        decoder.accept(seq);

        assertEquals(1, mouseEvents.size());
        assertEquals(42, mouseEvents.get(0).x());
        assertEquals(17, mouseEvents.get(0).y());
    }

    @Test
    public void testEventDecoderNonMouseCsiPassesThrough() {
        List<int[]> inputEvents = new ArrayList<>();

        EventDecoder decoder = new EventDecoder();
        decoder.setInputHandler(inputEvents::add);
        decoder.setMouseHandler(event -> {
        });

        // Arrow up: ESC [ A — not a mouse sequence
        int[] arrowUp = { 27, 91, 65 };
        decoder.accept(arrowUp);

        // Should pass through as input, not consumed by mouse handler
        assertFalse(inputEvents.isEmpty());
    }

    @Test
    public void testEventDecoderPartialMouseAcrossChunks() {
        List<MouseEvent> mouseEvents = new ArrayList<>();

        EventDecoder decoder = new EventDecoder();
        decoder.setInputHandler(input -> {
        });
        decoder.setMouseHandler(mouseEvents::add);

        // Split ESC [ < 0 ; 5 ; 3 M across two chunks
        decoder.accept(new int[] { 27, 91, 60 });
        assertEquals(0, mouseEvents.size()); // not complete yet

        decoder.accept(new int[] { 48, 59, 53, 59, 51, 77 });
        assertEquals(1, mouseEvents.size());
        assertEquals(5, mouseEvents.get(0).x());
        assertEquals(3, mouseEvents.get(0).y());
    }
}
