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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.Size;
import org.junit.Test;

/**
 * Tests for AbstractWindowsTerminal console mode management.
 * <p>
 * Uses a stubbed subclass to test setAttributes() flag matrix and
 * close() mode restoration without a real Windows console.
 * Runs on all platforms.
 */
public class AbstractWindowsTerminalTest {

    // Console mode flag constants (match AbstractWindowsTerminal)
    private static final int ENABLE_PROCESSED_INPUT = 0x0001;
    private static final int ENABLE_LINE_INPUT = 0x0002;
    private static final int ENABLE_ECHO_INPUT = 0x0004;
    private static final int ENABLE_WINDOW_INPUT = 0x0008;
    private static final int ENABLE_MOUSE_INPUT = 0x0010;
    private static final int ENABLE_QUICK_EDIT_MODE = 0x0040;
    private static final int ENABLE_EXTENDED_FLAGS = 0x0080;

    /**
     * Stubbed AbstractWindowsTerminal for testing without a real console.
     * Tracks the console mode in a field instead of calling kernel32.
     * <p>
     * The initial mode must be set via a static field before construction
     * because super() calls getConsoleMode() before subclass fields are initialized.
     */
    private static class StubWindowsTerminal extends AbstractWindowsTerminal {

        private static volatile int INIT_MODE = 0;
        private static volatile int INIT_OUTPUT_MODE = 0;
        private static volatile boolean CONSTRUCTED = false;

        int currentMode;
        int outputMode;
        int setInputModeCalls;
        int setOutputModeCalls;
        boolean closed;

        StubWindowsTerminal(int initialMode) throws IOException {
            this(initialMode, 0);
        }

        StubWindowsTerminal(int initialMode, int initialOutputMode) throws IOException {
            // Set the modes BEFORE calling super() — super() calls getConsoleMode()
            // and getOutputConsoleMode() to save the originals, and we need them
            // to return the initial values.
            this(setInitModes(initialMode, initialOutputMode));
        }

        private StubWindowsTerminal(boolean ignored) throws IOException {
            super(false, System.out, "test", false, SignalHandlers.SIG_DFL);
            CONSTRUCTED = true;
            this.currentMode = INIT_MODE;
            this.outputMode = INIT_OUTPUT_MODE;
        }

        private static boolean setInitModes(int mode, int outputMode) {
            INIT_MODE = mode;
            INIT_OUTPUT_MODE = outputMode;
            CONSTRUCTED = false;
            return true;
        }

        @Override
        protected int getConsoleOutputCP() {
            return 65001; // UTF-8
        }

        @Override
        protected int getConsoleMode() {
            // During super() construction, instance fields are Java-default (0),
            // not their initializer values. Use INIT_MODE until fully constructed.
            return CONSTRUCTED ? currentMode : INIT_MODE;
        }

        @Override
        protected void setConsoleMode(int mode) {
            currentMode = mode;
            setInputModeCalls++;
        }

        @Override
        protected int getOutputConsoleMode() {
            // Same construction-time rule as getConsoleMode above.
            return CONSTRUCTED ? outputMode : INIT_OUTPUT_MODE;
        }

        @Override
        protected void setOutputConsoleMode(int mode) {
            outputMode = mode;
            setOutputModeCalls++;
        }

        @Override
        protected byte[] readConsoleInput() {
            // Return empty — pump thread calls this but we don't need real input
            return new byte[0];
        }

        @Override
        public Size getSize() {
            return new Size(80, 24);
        }

        @Override
        public void close() {
            closed = true;
            super.close();
        }
    }

    // ==================== setAttributes flag matrix (#277 M5) ====================

    @Test
    public void testRawModeOnlyWindowInput() throws IOException {
        // Raw mode: ECHO=false, ICANON=false, ISIG=false
        StubWindowsTerminal term = new StubWindowsTerminal(
                ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT | ENABLE_QUICK_EDIT_MODE);

        Attributes raw = new Attributes();
        // All flags false by default in new Attributes
        term.setAttributes(raw);

        // Should have ONLY ENABLE_WINDOW_INPUT — everything else cleared
        assertEquals("Raw mode should have only WINDOW_INPUT",
                ENABLE_WINDOW_INPUT, term.currentMode);
        term.close();
    }

    @Test
    public void testRawModeWithSignals() throws IOException {
        StubWindowsTerminal term = new StubWindowsTerminal(
                ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT | ENABLE_QUICK_EDIT_MODE);

        Attributes raw = new Attributes();
        raw.setLocalFlag(Attributes.LocalFlag.ISIG, true);
        term.setAttributes(raw);

        assertEquals("Raw mode with ISIG should have WINDOW_INPUT + PROCESSED_INPUT",
                ENABLE_WINDOW_INPUT | ENABLE_PROCESSED_INPUT, term.currentMode);
        term.close();
    }

    @Test
    public void testCookedMode() throws IOException {
        StubWindowsTerminal term = new StubWindowsTerminal(0);

        Attributes cooked = new Attributes();
        cooked.setLocalFlag(Attributes.LocalFlag.ECHO, true);
        cooked.setLocalFlag(Attributes.LocalFlag.ICANON, true);
        cooked.setLocalFlag(Attributes.LocalFlag.ISIG, true);
        term.setAttributes(cooked);

        int expected = ENABLE_WINDOW_INPUT | ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT;
        assertEquals("Cooked mode should have WINDOW + ECHO + LINE + PROCESSED",
                expected, term.currentMode);
        term.close();
    }

    @Test
    public void testQuickEditModeClearedInRawMode() throws IOException {
        // Start with QUICK_EDIT_MODE enabled (Windows default)
        StubWindowsTerminal term = new StubWindowsTerminal(
                ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT | ENABLE_QUICK_EDIT_MODE);

        Attributes raw = new Attributes();
        raw.setLocalFlag(Attributes.LocalFlag.ISIG, true);
        term.setAttributes(raw);

        // QUICK_EDIT_MODE must NOT be preserved — it blocks ReadConsoleInputW
        assertEquals("QUICK_EDIT_MODE should be cleared in raw mode", 0,
                term.currentMode & ENABLE_QUICK_EDIT_MODE);
        term.close();
    }

    @Test
    public void testMouseInputSetsExtendedFlags() throws IOException {
        StubWindowsTerminal term = new StubWindowsTerminal(0);
        term.mouseInputEnabled = true;

        Attributes raw = new Attributes();
        term.setAttributes(raw);

        assertTrue("Mouse input should set ENABLE_MOUSE_INPUT",
                (term.currentMode & ENABLE_MOUSE_INPUT) != 0);
        assertTrue("Mouse input should set ENABLE_EXTENDED_FLAGS",
                (term.currentMode & ENABLE_EXTENDED_FLAGS) != 0);
        assertEquals("Mouse + raw should not have QUICK_EDIT_MODE", 0,
                term.currentMode & ENABLE_QUICK_EDIT_MODE);
        term.close();
    }

    @Test
    public void testBuildFromScratchNeverPreservesStaleFlags() throws IOException {
        // Start with a mode that has every flag imaginable
        int staleMode = 0xFFFF;
        StubWindowsTerminal term = new StubWindowsTerminal(staleMode);

        Attributes raw = new Attributes();
        term.setAttributes(raw);

        // Build-from-scratch should produce ONLY ENABLE_WINDOW_INPUT
        // regardless of what was in the console mode before
        assertEquals("Stale flags should not be preserved",
                ENABLE_WINDOW_INPUT, term.currentMode);
        term.close();
    }

    @Test
    public void testGetAttributesReportsEchoCtl() throws IOException {
        // Readline's INT handler prints ^C iff ECHOCTL is set. POSIX cooked
        // mode has it on, so report it for identical Ctrl+C feedback.
        StubWindowsTerminal term = new StubWindowsTerminal(
                ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT);
        assertTrue(term.getAttributes().getLocalFlag(Attributes.LocalFlag.ECHOCTL));
        Attributes raw = new Attributes();
        term.setAttributes(raw);
        assertTrue(term.getAttributes().getLocalFlag(Attributes.LocalFlag.ECHOCTL));
        term.close();
    }

    // ==================== close() mode restore (#277 M5) ====================

    @Test
    public void testCloseRestoresOriginalMode() throws IOException {
        int originalMode = ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT | ENABLE_QUICK_EDIT_MODE;
        StubWindowsTerminal term = new StubWindowsTerminal(originalMode);

        // Enter raw mode
        Attributes raw = new Attributes();
        term.setAttributes(raw);
        assertNotEquals("Mode should change in raw mode", originalMode, term.currentMode);

        // Close — should restore original mode
        term.close();
        assertEquals("close() should restore the original console mode",
                originalMode, term.currentMode);
    }

    @Test
    public void testCloseRestoresAfterMouseMode() throws IOException {
        int originalMode = ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT;
        StubWindowsTerminal term = new StubWindowsTerminal(originalMode);

        // Enable mouse + raw mode
        term.mouseInputEnabled = true;
        Attributes raw = new Attributes();
        term.setAttributes(raw);
        assertTrue("Mouse mode should be active",
                (term.currentMode & ENABLE_MOUSE_INPUT) != 0);

        // Close — should restore original (without mouse flags)
        term.close();
        assertEquals("close() should restore original mode without mouse flags",
                originalMode, term.currentMode);
    }

    // ==================== close() output restore + idempotency (#278) ====================

    @Test
    public void testCloseRestoresOriginalOutputMode() throws IOException {
        int originalInput = ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT;
        int originalOutput = 0x0004; // e.g. ENABLE_VIRTUAL_TERMINAL_PROCESSING pre-set
        StubWindowsTerminal term = new StubWindowsTerminal(originalInput, originalOutput);

        // Simulate VT-output enable changing the output mode, plus raw input mode
        term.setOutputConsoleMode(0x0104);
        Attributes raw = new Attributes();
        term.setAttributes(raw);

        term.close();
        assertEquals("close() should restore the original output console mode",
                originalOutput, term.outputMode);
        assertEquals("close() should still restore the original input console mode",
                originalInput, term.currentMode);
    }

    @Test
    public void testDoubleCloseRestoresOnce() throws IOException {
        int originalInput = ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT;
        int originalOutput = 0x0004;
        StubWindowsTerminal term = new StubWindowsTerminal(originalInput, originalOutput);

        Attributes raw = new Attributes();
        term.setAttributes(raw);
        term.setOutputConsoleMode(0x0104);

        term.close();
        int inputSetsAfterFirstClose = term.setInputModeCalls;
        int outputSetsAfterFirstClose = term.setOutputModeCalls;

        term.close();
        assertEquals("Second close() must not touch the input console mode again",
                inputSetsAfterFirstClose, term.setInputModeCalls);
        assertEquals("Second close() must not touch the output console mode again",
                outputSetsAfterFirstClose, term.setOutputModeCalls);
        assertEquals("Restored input mode must survive double close",
                originalInput, term.currentMode);
        assertEquals("Restored output mode must survive double close",
                originalOutput, term.outputMode);
    }
}
