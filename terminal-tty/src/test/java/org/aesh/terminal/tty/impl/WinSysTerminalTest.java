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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.function.Function;

import org.aesh.terminal.tty.Capability;
import org.junit.Test;

/**
 * Tests for WinSysTerminal key event processing logic.
 * <p>
 * These tests exercise the static {@code processKeyEvent()} method directly,
 * so they run on all platforms (no Windows console required).
 * <p>
 * Note: ENABLE_VIRTUAL_TERMINAL_INPUT is deliberately not enabled on the
 * console input handle. That flag causes Windows to generate duplicate
 * KEY_EVENT records per keypress, leading to double character input (#276).
 * Input is handled via traditional ReadConsoleInput KEY_EVENT records with
 * manual virtual key code to ANSI escape sequence translation.
 */
public class WinSysTerminalTest {

    // KEY_EVENT type constant (matches WinConsoleNative.KEY_EVENT)
    private static final int KEY_EVENT = 1;

    // Control key state constants (match WinSysTerminal fields)
    private static final int LEFT_ALT_PRESSED = 0x0002;
    private static final int LEFT_CTRL_PRESSED = 0x0008;
    private static final int SHIFT_PRESSED = 0x0010;

    // Stub lookups — return null for most keys (no terminfo database needed)
    private static final Function<Short, String> NO_ESCAPE = vk -> null;
    private static final Function<Capability, String> NO_CAPABILITY = cap -> null;

    // Simple arrow key lookup for virtual key tests
    private static final Function<Short, String> ARROW_ESCAPE = vk -> {
        switch (vk) {
            case 0x25:
                return "\033[D"; // VK_LEFT
            case 0x26:
                return "\033[A"; // VK_UP
            case 0x27:
                return "\033[C"; // VK_RIGHT
            case 0x28:
                return "\033[B"; // VK_DOWN
            default:
                return null;
        }
    };

    /**
     * Build a KEY_EVENT array matching the Windows INPUT_RECORD format.
     * Format: {KEY_EVENT, keyDown, repeatCount, vKeyCode, unicodeChar, controlKeyState}
     */
    private static int[] keyEvent(boolean keyDown, int repeatCount, int vKeyCode,
            char unicodeChar, int controlKeyState) {
        return new int[] { KEY_EVENT, keyDown ? 1 : 0, repeatCount, vKeyCode,
                unicodeChar, controlKeyState };
    }

    // ==================== Basic key-down / key-up ====================

    @Test
    public void testKeyDownProducesCharacter() {
        int[] event = keyEvent(true, 1, 0x41, 'a', 0); // VK_A
        byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, NO_CAPABILITY);
        assertArrayEquals("Key-down should produce 'a'", new byte[] { 'a' }, result);
    }

    @Test
    public void testKeyUpFiltered() {
        int[] event = keyEvent(false, 1, 0x41, 'a', 0);
        byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, NO_CAPABILITY);
        assertEquals("Key-up should be filtered", 0, result.length);
    }

    @Test
    public void testFullKeypressCycle() {
        // Simulate key-down + key-up for 'a' — only key-down produces output
        int[] down = keyEvent(true, 1, 0x41, 'a', 0);
        int[] up = keyEvent(false, 1, 0x41, 'a', 0);

        byte[] r1 = WinSysTerminal.processKeyEvent(down, NO_ESCAPE, NO_CAPABILITY);
        byte[] r2 = WinSysTerminal.processKeyEvent(up, NO_ESCAPE, NO_CAPABILITY);

        assertArrayEquals("Key-down should produce 'a'", new byte[] { 'a' }, r1);
        assertEquals("Key-up should be filtered", 0, r2.length);
    }

    // ==================== Virtual keys (arrows, F-keys) ====================

    @Test
    public void testArrowKeyProducesEscapeSequence() {
        int[] event = keyEvent(true, 1, 0x25, '\0', 0); // VK_LEFT
        byte[] result = WinSysTerminal.processKeyEvent(event, ARROW_ESCAPE, NO_CAPABILITY);
        assertArrayEquals("Left arrow should produce ESC[D",
                "\033[D".getBytes(), result);
    }

    @Test
    public void testVirtualKeyRepeatCount() {
        int[] event = keyEvent(true, 3, 0x27, '\0', 0); // VK_RIGHT x3
        byte[] result = WinSysTerminal.processKeyEvent(event, ARROW_ESCAPE, NO_CAPABILITY);
        assertArrayEquals("Right arrow x3 should produce ESC[C three times",
                "\033[C\033[C\033[C".getBytes(), result);
    }

    @Test
    public void testUnmappedVirtualKeyProducesNothing() {
        int[] event = keyEvent(true, 1, 0xFF, '\0', 0); // unmapped vk
        byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, NO_CAPABILITY);
        assertEquals("Unmapped virtual key should produce nothing", 0, result.length);
    }

    // ==================== Alt key combinations ====================

    @Test
    public void testAltKeyProducesEscPrefix() {
        int[] event = keyEvent(true, 1, 0x46, 'f', LEFT_ALT_PRESSED); // Alt+F
        byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, NO_CAPABILITY);
        assertArrayEquals("Alt+F should produce ESC f", new byte[] { 0x1b, 'f' }, result);
    }

    @Test
    public void testAltGrNotTreatedAsAlt() {
        // AltGr = Alt+Ctrl — should NOT produce ESC prefix (needed for non-US keyboards)
        int[] event = keyEvent(true, 1, 0x32, '@', LEFT_ALT_PRESSED | LEFT_CTRL_PRESSED);
        byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, NO_CAPABILITY);
        assertArrayEquals("AltGr+@ should produce @ without ESC prefix",
                new byte[] { '@' }, result);
    }

    @Test
    public void testAltWithVirtualKey() {
        int[] event = keyEvent(true, 1, 0x25, '\0', LEFT_ALT_PRESSED); // Alt+Left
        byte[] result = WinSysTerminal.processKeyEvent(event, ARROW_ESCAPE, NO_CAPABILITY);
        assertArrayEquals("Alt+Left should produce ESC + ESC[D",
                "\033\033[D".getBytes(), result);
    }

    // ==================== Control characters ====================

    @Test
    public void testCtrlDProducesEof() {
        // Ctrl+D: vk=0x44, unicodeChar=0x04 (EOT)
        int[] event = keyEvent(true, 1, 0x44, '\u0004', LEFT_CTRL_PRESSED);
        byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, NO_CAPABILITY);
        assertArrayEquals("Ctrl+D should produce 0x04", new byte[] { 0x04 }, result);
    }

    @Test
    public void testCtrlCProducesInterrupt() {
        // Ctrl+C: vk=0x43, unicodeChar=0x03 (ETX)
        int[] event = keyEvent(true, 1, 0x43, '\u0003', LEFT_CTRL_PRESSED);
        byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, NO_CAPABILITY);
        assertArrayEquals("Ctrl+C should produce 0x03", new byte[] { 0x03 }, result);
    }

    // ==================== ALT+NumPad input method ====================

    @Test
    public void testAltNumPadOnKeyUp() {
        // ALT+NumPad: character is produced on ALT key-UP (vk=0x12, VK_MENU)
        int[] event = keyEvent(false, 1, 0x12, '\u00e9', 0); // key-up of ALT, char='é'
        byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, NO_CAPABILITY);
        assertEquals("ALT+NumPad should produce the character on key-up",
                "\u00e9", new String(result));
    }

    @Test
    public void testNonAltKeyUpProducesNothing() {
        int[] event = keyEvent(false, 1, 0x41, 'a', 0); // key-up of 'A'
        byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, NO_CAPABILITY);
        assertEquals("Non-ALT key-up should produce nothing", 0, result.length);
    }

    // ==================== Shift+Tab ====================

    @Test
    public void testShiftTabWithCapability() {
        Function<Capability, String> btabLookup = cap -> cap == Capability.key_btab ? "\033[Z" : null;

        int[] event = keyEvent(true, 1, 0x09, '\t', SHIFT_PRESSED); // Shift+Tab
        byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, btabLookup);
        assertArrayEquals("Shift+Tab should produce key_btab sequence",
                "\033[Z".getBytes(), result);
    }

    @Test
    public void testTabWithoutShift() {
        int[] event = keyEvent(true, 1, 0x09, '\t', 0); // Tab (no shift)
        byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, NO_CAPABILITY);
        assertArrayEquals("Tab should produce tab character",
                new byte[] { '\t' }, result);
    }

    // ==================== Special characters requiring Shift ====================

    @Test
    public void testSpecialCharsWithShift() {
        // Characters like @#$% require Shift on most keyboards
        char[] specials = { '@', '#', '$', '%', '!', '&' };
        for (char c : specials) {
            int[] event = keyEvent(true, 1, 0x32, c, SHIFT_PRESSED); // vk varies, but unicodeChar is set
            byte[] result = WinSysTerminal.processKeyEvent(event, NO_ESCAPE, NO_CAPABILITY);
            assertEquals("Shift+key for '" + c + "' should produce the character",
                    String.valueOf(c), new String(result));
        }
    }

    // ==================== Output transport selection ====================

    /**
     * WinSysTerminal with stubbed console access for headless testing.
     * Overrides every native touchpoint so no console, DLL, or FFM is needed.
     */
    private static class TestableWinSysTerminal extends WinSysTerminal {
        static boolean consoleValid = true;
        static boolean vtEnableResult = true;
        boolean vtOutputEnabled;

        TestableWinSysTerminal() throws java.io.IOException {
            super("test", false, SignalHandlers.SIG_DFL);
        }

        @Override
        protected boolean enableVTOutput() {
            vtOutputEnabled = true;
            return vtEnableResult;
        }

        @Override
        protected boolean isOutputConsoleValid() {
            return consoleValid;
        }

        @Override
        protected int getConsoleMode() {
            return 0;
        }

        @Override
        protected void setConsoleMode(int mode) {
        }

        @Override
        protected int getOutputConsoleMode() {
            return 0;
        }

        @Override
        protected void setOutputConsoleMode(int mode) {
        }

        @Override
        protected int getConsoleOutputCP() {
            return 65001;
        }

        @Override
        protected byte[] readConsoleInput() {
            return new byte[0];
        }

        @Override
        protected void pump() {
        }

        @Override
        public org.aesh.terminal.tty.Size getSize() {
            return new org.aesh.terminal.tty.Size(80, 24);
        }
    }

    @Test
    public void testValidConsoleSelectsWriteConsolePath() throws java.io.IOException {
        TestableWinSysTerminal.consoleValid = true;
        TestableWinSysTerminal.vtEnableResult = true;
        TestableWinSysTerminal term = new TestableWinSysTerminal();
        try {
            assertNotNull("Valid console should use the WriteConsoleW codepoint consumer",
                    term.getCodePointConsumer());
            assertTrue("VT output should still be enabled", term.vtOutputEnabled);
        } finally {
            term.close();
        }
    }

    @Test
    public void testInvalidConsoleFallsBackToEncoderPath() throws java.io.IOException {
        TestableWinSysTerminal.consoleValid = false;
        TestableWinSysTerminal.vtEnableResult = true;
        TestableWinSysTerminal term = new TestableWinSysTerminal();
        try {
            assertNull("Pipes should fall back to the Encoder byte stream (null consumer)",
                    term.getCodePointConsumer());
        } finally {
            TestableWinSysTerminal.consoleValid = true;
            term.close();
        }
    }

    @Test
    public void testVtFailureStillSelectsWriteConsolePath() throws java.io.IOException {
        TestableWinSysTerminal.consoleValid = true;
        TestableWinSysTerminal.vtEnableResult = false;
        TestableWinSysTerminal term = new TestableWinSysTerminal();
        try {
            // Transport depends only on having a real console: WriteConsoleW
            // renders text correctly with or without VT interpretation,
            // while the Encoder byte path depends on codepage agreement.
            assertNotNull("WriteConsoleW path must not depend on VTP success",
                    term.getCodePointConsumer());
        } finally {
            TestableWinSysTerminal.vtEnableResult = true;
            term.close();
        }
    }
}
