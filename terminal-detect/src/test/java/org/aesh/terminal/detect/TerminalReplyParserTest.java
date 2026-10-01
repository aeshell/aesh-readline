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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;

import org.aesh.terminal.detect.TerminalReplyParser.TerminalDeviceAttributes;
import org.junit.Test;

/**
 * Canonical reply extraction shared by standalone probing and
 * live-connection queries (#358). These fixtures also run against
 * {@code ANSI.parse*} on the api side: both transports must agree.
 */
public class TerminalReplyParserTest {

    @Test
    public void testOscColor() {
        assertArrayEquals(new int[] { 0, 0, 0 },
                TerminalReplyParser.oscColor("\033]11;rgb:0000/0000/0000", 11, -1));
        assertArrayEquals(new int[] { 255, 255, 255 },
                TerminalReplyParser.oscColor("\033]10;rgb:ffff/ffff/ffff\033\\", 10, -1));
        // ST-then-BEL: earliest terminator wins, later reply untouched.
        assertArrayEquals(new int[] { 0, 0, 0 }, TerminalReplyParser.oscColor(
                "\033]10;rgb:ffff/ffff/ffff\033\\" + "\033]11;rgb:0000/0000/0000", 11, -1));
        assertArrayEquals(new int[] { 0x11, 0x22, 0x33 }, TerminalReplyParser
                .oscColor("\033]4;7;rgb:1111/2222/3333", 4, 7));
        assertNull(TerminalReplyParser.oscColor("no reply here", 11, -1));
        assertNull(TerminalReplyParser.oscColor("\033]11;rgb:00", 11, -1));
    }

    @Test
    public void testModeSupport() {
        assertEquals(ModeSupport.SUPPORTED,
                TerminalReplyParser.modeSupport("\033[?2026;1$y", 2026));
        assertEquals(ModeSupport.NOT_SUPPORTED,
                TerminalReplyParser.modeSupport("\033[?2026;0$y", 2026));
        // DA1 first: the mode report still parses.
        assertEquals(ModeSupport.SUPPORTED,
                TerminalReplyParser.modeSupport("\033[?63;1c\033[?2026;1$y", 2026));
        // Malformed first, valid later: later wins, earlier never poisons.
        assertEquals(ModeSupport.SUPPORTED,
                TerminalReplyParser.modeSupport("\033[?2026;$y\033[?2026;1$y", 2026));
        // Other modes are invisible to this query.
        assertEquals(ModeSupport.NO_RESPONSE,
                TerminalReplyParser.modeSupport("\033[?2027;1$y", 2026));
        assertEquals(ModeSupport.NO_RESPONSE,
                TerminalReplyParser.modeSupport("no reply here", 2026));
        assertEquals(ModeSupport.NO_RESPONSE, TerminalReplyParser.modeSupport(null, 2026));
    }

    @Test
    public void testDeviceAttributes() {
        TerminalDeviceAttributes attributes = TerminalReplyParser.deviceAttributes("\033[?63;1;2;4c");
        assertEquals(new TerminalDeviceAttributes(63, Arrays.asList(1, 2, 4)), attributes);
        // DECRPM first: skipped without consuming the device attributes.
        TerminalDeviceAttributes afterMode = TerminalReplyParser.deviceAttributes("\033[?2026;1$y\033[?63;1c");
        assertEquals(new TerminalDeviceAttributes(63, Arrays.asList(1)), afterMode);
        assertEquals(new TerminalDeviceAttributes(63, Arrays.asList()),
                TerminalReplyParser.deviceAttributes("\033[?63c"));
        assertNull(TerminalReplyParser.deviceAttributes("no reply here"));
        assertNull(TerminalReplyParser.deviceAttributes("\033[?;c"));
        assertNull(TerminalReplyParser.deviceAttributes(null));
    }

    @Test
    public void testCursorPosition() {
        assertArrayEquals(new int[] { 24, 80 },
                TerminalReplyParser.cursorPosition("\033[24;80R"));
        assertArrayEquals(new int[] { 1, 2 },
                TerminalReplyParser.cursorPosition("ab\033[1;2R"));
        // Stray content never poisons a later well-formed frame.
        assertArrayEquals(new int[] { 1, 2 },
                TerminalReplyParser.cursorPosition("R\033[1;2R"));
        assertNull(TerminalReplyParser.cursorPosition("\033[abR"));
        assertNull(TerminalReplyParser.cursorPosition("\033[24;80"));
        assertNull(TerminalReplyParser.cursorPosition(null));
        // Overflow guards: no throw, no invented coordinates.
        assertNull(TerminalReplyParser.cursorPosition("\033[1;99999999999R"));
    }

    @Test
    public void testCursorPositionIgnoresStrayPrefix() {
        // A stray R before a valid frame never poisons it (#318 rule,
        // now shared by both transports).
        assertArrayEquals(new int[] { 1, 2 },
                TerminalReplyParser.cursorPosition("R\033[1;2R"));
    }

    @Test
    public void testDeviceAttributesValueSemantics() {
        TerminalDeviceAttributes attributes = new TerminalDeviceAttributes(63, Arrays.asList(1, 4));
        assertEquals(attributes, new TerminalDeviceAttributes(63, Arrays.asList(1, 4)));
        assertEquals(attributes.hashCode(),
                new TerminalDeviceAttributes(63, Arrays.asList(1, 4)).hashCode());
        assertTrue(attributes.toString().contains("63"));
        assertTrue(!attributes.equals(null));
        assertTrue(!attributes.equals("DA1"));
    }
}
