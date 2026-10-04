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
package org.aesh.terminal.tty.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.aesh.terminal.Attributes;
import org.junit.Test;

/**
 * Tests for the direct Windows console-mode branch of CygwinPty.
 * <p>
 * When stdin is a real console (ConPTY, hidden mintty console), stty.exe
 * would configure a different object than the one Java reads, silently
 * leaving the console cooked. The direct branch programs the console via
 * the Win32 API instead. A stubbed console mode makes these tests runnable
 * headless on all platforms.
 */
public class CygwinPtyConsoleModeTest {

    private static final int ENABLE_PROCESSED_INPUT = 0x0001;
    private static final int ENABLE_LINE_INPUT = 0x0002;
    private static final int ENABLE_ECHO_INPUT = 0x0004;
    private static final int ENABLE_WINDOW_INPUT = 0x0008;
    private static final int ENABLE_QUICK_EDIT_MODE = 0x0040;
    private static final int ENABLE_EXTENDED_FLAGS = 0x0080;

    /**
     * CygwinPty with a stubbed console: readConsoleMode returns a canned
     * value, writeConsoleMode records every write. Never touches stty.exe
     * or native code, so tests run anywhere.
     */
    private static class StubCygwinPty extends CygwinPty {
        int cannedMode;
        final List<Integer> writes = new ArrayList<>();
        boolean failWrites;
        boolean staleReadback;

        StubCygwinPty(int cannedMode) {
            super("test");
            this.cannedMode = cannedMode;
        }

        @Override
        protected int readConsoleMode() {
            return cannedMode;
        }

        @Override
        protected boolean writeConsoleMode(int mode) {
            writes.add(mode);
            if (failWrites) {
                return false;
            }
            if (!staleReadback) {
                cannedMode = mode;
            }
            return true;
        }
    }

    private static Attributes attrs(boolean echo, boolean icanon, boolean isig) {
        Attributes attr = new Attributes();
        attr.setLocalFlag(Attributes.LocalFlag.ECHO, echo);
        attr.setLocalFlag(Attributes.LocalFlag.ICANON, icanon);
        attr.setLocalFlag(Attributes.LocalFlag.ISIG, isig);
        return attr;
    }

    // ==================== mapping table ====================

    @Test
    public void testToConsoleModeRaw() {
        assertEquals(ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS,
                CygwinPty.toConsoleMode(attrs(false, false, false)));
    }

    @Test
    public void testToConsoleModeCooked() {
        assertEquals(ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS | ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT
                | ENABLE_PROCESSED_INPUT,
                CygwinPty.toConsoleMode(attrs(true, true, true)));
    }

    @Test
    public void testToConsoleModeIndividualFlags() {
        assertEquals(ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS | ENABLE_ECHO_INPUT,
                CygwinPty.toConsoleMode(attrs(true, false, false)));
        assertEquals(ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS | ENABLE_LINE_INPUT,
                CygwinPty.toConsoleMode(attrs(false, true, false)));
        assertEquals(ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS | ENABLE_PROCESSED_INPUT,
                CygwinPty.toConsoleMode(attrs(false, false, true)));
    }

    @Test
    public void testToConsoleModeNeverSetsQuickEdit() {
        // Stale flags like QUICK_EDIT_MODE must never leak into raw mode —
        // it blocks console reads while text selection is active. The
        // extended flag must always be present: without it Quick Edit
        // cannot switch off even though it is never set here.
        for (boolean echo : new boolean[] { false, true }) {
            for (boolean icanon : new boolean[] { false, true }) {
                for (boolean isig : new boolean[] { false, true }) {
                    int mode = CygwinPty.toConsoleMode(attrs(echo, icanon, isig));
                    assertEquals("No stale flags for echo=" + echo + " icanon=" + icanon + " isig=" + isig,
                            0, mode & ~(ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS
                                    | ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT));
                    assertTrue("EXTENDED_FLAGS required to keep QUICK_EDIT off",
                            (mode & ENABLE_EXTENDED_FLAGS) != 0);
                    assertEquals("QUICK_EDIT must never be composed", 0,
                            mode & ENABLE_QUICK_EDIT_MODE);
                }
            }
        }
    }

    @Test
    public void testFromConsoleMode() {
        Attributes cooked = CygwinPty.fromConsoleMode(
                ENABLE_WINDOW_INPUT | ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT);
        assertTrue(cooked.getLocalFlag(Attributes.LocalFlag.ECHO));
        assertTrue(cooked.getLocalFlag(Attributes.LocalFlag.ICANON));
        assertTrue(cooked.getLocalFlag(Attributes.LocalFlag.ISIG));

        Attributes raw = CygwinPty.fromConsoleMode(ENABLE_WINDOW_INPUT);
        assertFalse(raw.getLocalFlag(Attributes.LocalFlag.ECHO));
        assertFalse(raw.getLocalFlag(Attributes.LocalFlag.ICANON));
        assertFalse(raw.getLocalFlag(Attributes.LocalFlag.ISIG));
    }

    @Test
    public void testFromConsoleModeAlwaysReportsEchoCtl() {
        // Readline's INT handler prints ^C iff ECHOCTL is set. POSIX cooked
        // mode has it on, so Windows must report it too for identical
        // Ctrl+C feedback — in both cooked and raw mappings.
        Attributes cooked = CygwinPty.fromConsoleMode(
                ENABLE_WINDOW_INPUT | ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT);
        assertTrue(cooked.getLocalFlag(Attributes.LocalFlag.ECHOCTL));
        Attributes raw = CygwinPty.fromConsoleMode(ENABLE_WINDOW_INPUT);
        assertTrue(raw.getLocalFlag(Attributes.LocalFlag.ECHOCTL));
    }

    @Test
    public void testMappingRoundTrip() {
        for (boolean echo : new boolean[] { false, true }) {
            for (boolean icanon : new boolean[] { false, true }) {
                for (boolean isig : new boolean[] { false, true }) {
                    Attributes roundTripped = CygwinPty.fromConsoleMode(CygwinPty.toConsoleMode(attrs(echo, icanon, isig)));
                    assertEquals(echo, roundTripped.getLocalFlag(Attributes.LocalFlag.ECHO));
                    assertEquals(icanon, roundTripped.getLocalFlag(Attributes.LocalFlag.ICANON));
                    assertEquals(isig, roundTripped.getLocalFlag(Attributes.LocalFlag.ISIG));
                }
            }
        }
    }

    // ==================== direct branch behavior ====================

    @Test
    public void testGetAttrUsesConsoleDirectly() throws IOException {
        int cooked = ENABLE_WINDOW_INPUT | ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT;
        StubCygwinPty pty = new StubCygwinPty(cooked);
        Attributes attr = pty.getAttr();
        assertTrue(attr.getLocalFlag(Attributes.LocalFlag.ECHO));
        assertTrue(attr.getLocalFlag(Attributes.LocalFlag.ICANON));
        assertTrue(attr.getLocalFlag(Attributes.LocalFlag.ISIG));
        assertTrue("Direct getAttr must not spawn stty.exe", pty.writes.isEmpty());
    }

    @Test
    public void testSetAttrProgramsConsoleDirectly() throws IOException {
        int cooked = ENABLE_WINDOW_INPUT | ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT;
        StubCygwinPty pty = new StubCygwinPty(cooked);
        pty.setAttr(attrs(false, false, false));
        assertEquals(1, pty.writes.size());
        assertEquals(Integer.valueOf(ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS),
                pty.writes.get(0));
    }

    @Test
    public void testSetAttrWriteDeclinedThrowsLoudly() {
        // A console that declines the write must not look like success:
        // falling back to stty.exe would program a different object and
        // silently leave the console cooked (arrows eaten, #360).
        int cooked = ENABLE_WINDOW_INPUT | ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT;
        StubCygwinPty pty = new StubCygwinPty(cooked);
        pty.failWrites = true;
        try {
            pty.setAttr(attrs(false, false, false));
            org.junit.Assert.fail("Declined console write must throw, not silently stay cooked");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("declined"));
        }
    }

    @Test
    public void testSetAttrWriteNotStickingThrowsLoudly() {
        // Write accepted but mode unchanged on read-back: same silent-cooked
        // trap, must also throw instead of claiming direct success.
        int cooked = ENABLE_WINDOW_INPUT | ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT;
        StubCygwinPty pty = new StubCygwinPty(cooked);
        pty.staleReadback = true;
        try {
            pty.setAttr(attrs(false, false, false));
            org.junit.Assert.fail("Non-sticking console write must throw, not silently stay cooked");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("did not take effect"));
        }
    }

    @Test
    public void testModeMatchesIgnoresUnrelatedFlags() {
        int desired = ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS;
        assertTrue(CygwinPty.modeMatches(desired | ENABLE_QUICK_EDIT_MODE, desired));
        assertFalse(CygwinPty.modeMatches(
                desired | ENABLE_LINE_INPUT, desired));
        assertFalse(CygwinPty.modeMatches(
                desired, desired | ENABLE_ECHO_INPUT));
    }

    @Test
    public void testCloseRestoresOriginalConsoleMode() throws IOException {
        int cooked = ENABLE_WINDOW_INPUT | ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT;
        StubCygwinPty pty = new StubCygwinPty(cooked);
        pty.setAttr(attrs(false, false, false));
        assertEquals(1, pty.writes.size());
        pty.close();
        assertEquals(2, pty.writes.size());
        assertEquals("close() must restore the exact stashed console mode",
                Integer.valueOf(cooked), pty.writes.get(1));
        pty.close();
        assertEquals("Second close() must not write again", 2, pty.writes.size());
    }
}
