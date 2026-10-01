/*
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
package org.aesh.terminal.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.aesh.terminal.detect.TerminalReplyFramer.Kind;
import org.aesh.terminal.detect.TerminalReplyFramer.Span;
import org.junit.Test;

/**
 * Framing fixtures for terminal query replies (#352).
 * <p>
 * The splitter serves both standalone probing and live-connection
 * leases: reply shapes frame, ordinary input stays forwardable, and
 * incomplete tails hold. All fixtures are literals; no terminal needed.
 */
public class TerminalReplyFramerTest {

    private static List<Span> split(String response) {
        return TerminalReplyFramer.split(response);
    }

    private static void assertSpan(Span span, Kind kind, int start, int end) {
        assertEquals(new Span(kind, start, end), span);
    }

    @Test
    public void testOscBelAndSt() {
        List<Span> spans = split("\033]11;rgb:0000/0000/0000\007rest");
        assertEquals(2, spans.size());
        assertSpan(spans.get(0), Kind.OSC, 0, 24);
        assertSpan(spans.get(1), Kind.PLAIN_TEXT, 24, 28);

        List<Span> st = split("\033]10;rgb:ffff/ffff/ffff\033\\");
        assertEquals(1, st.size());
        assertSpan(st.get(0), Kind.OSC, 0, 25);
    }

    @Test
    public void testDeviceAttributesShapes() {
        List<Span> spans = split("\033[?63;1;2;4c");
        assertEquals(1, spans.size());
        assertSpan(spans.get(0), Kind.DEVICE_ATTRIBUTES, 0, 12);

        // Bare -c finals counted like the legacy scan (no ? required).
        List<Span> bare = split("\033[0c");
        assertEquals(1, bare.size());
        assertSpan(bare.get(0), Kind.DEVICE_ATTRIBUTES, 0, 4);

        // DA2 (>-body) never frames: accumulates like any opaque shape.
        List<Span> da2 = split("\033[>0;95;0c");
        assertTrue("DA2 must not frame", da2.isEmpty());
    }

    @Test
    public void testModeReportShapes() {
        List<Span> spans = split("\033[?2026;1$y");
        assertEquals(1, spans.size());
        assertSpan(spans.get(0), Kind.MODE_REPORT, 0, 11);
    }

    @Test
    public void testCursorPositionStrict() {
        List<Span> spans = split("\033[24;80R");
        assertEquals(1, spans.size());
        assertSpan(spans.get(0), Kind.CURSOR_POSITION, 0, 8);

        // Arrow keys and DECSTBM are complete but never replies.
        assertTrue("arrow must not frame", split("\033[A").isEmpty());
        assertTrue("DECSTBM must not frame", split("\033[2;24r").isEmpty());
        assertTrue("non-coordinate R must not frame", split("\033[R").isEmpty());
    }

    @Test
    public void testInterleavedKeysStayPlain() {
        // Typed keys around a reply: plain runs forward, the frame holds.
        List<Span> spans = split("ab\033[?1;2cxy");
        assertEquals(3, spans.size());
        assertSpan(spans.get(0), Kind.PLAIN_TEXT, 0, 2);
        assertSpan(spans.get(1), Kind.DEVICE_ATTRIBUTES, 2, 9);
        assertSpan(spans.get(2), Kind.PLAIN_TEXT, 9, 11);
    }

    @Test
    public void testPartialsHold() {
        List<Span> loneEsc = split("ab\033");
        assertEquals(2, loneEsc.size());
        assertSpan(loneEsc.get(0), Kind.PLAIN_TEXT, 0, 2);
        assertSpan(loneEsc.get(1), Kind.PARTIAL, 2, 3);

        List<Span> openCsi = split("\033[?63;");
        assertEquals(1, openCsi.size());
        assertSpan(openCsi.get(0), Kind.PARTIAL, 0, 6);

        List<Span> openOsc = split("\033]11;rgb:00");
        assertEquals(1, openOsc.size());
        assertSpan(openOsc.get(0), Kind.PARTIAL, 0, 11);

        assertTrue("empty input splits to nothing", split("").isEmpty());
        assertTrue("plain input has no tail", split("abc").size() == 1);
    }

    @Test
    public void testResyncOnNestedEscape() {
        // Abandoned frame restarts at the fresh escape (#318 discipline).
        List<Span> spans = split("\033[12\033[?1c");
        assertEquals(1, spans.size());
        assertSpan(spans.get(0), Kind.DEVICE_ATTRIBUTES, 4, 9);
    }

    @Test
    public void testSpanValueSemantics() {
        Span span = new Span(Kind.OSC, 0, 5);
        assertEquals(span, new Span(Kind.OSC, 0, 5));
        assertEquals(span.hashCode(), new Span(Kind.OSC, 0, 5).hashCode());
        assertTrue(span.toString().contains("OSC"));
        assertTrue(!span.equals(null));
        assertTrue(!span.equals("OSC[0,5)"));
    }
}
