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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;

import org.aesh.terminal.Attributes;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Live Windows console tests (#290).
 * <p>
 * Allocates a real console via {@link WinConsoleNative#allocConsole()} and
 * drives {@link WinSysTerminal} against true handles — covering what the
 * headless stub tests cannot: real console-mode save/restore, pump
 * lifecycle, VT processing, and the single-live guard. Runs only where
 * allocation succeeds (a console the process owns); everywhere else —
 * non-Windows, headless runners without window-station access, interactive
 * consoles owned by someone else — the whole class skips. Never touches a
 * console it did not allocate.
 */
public class LiveWindowsConsoleTest {

    private static boolean liveConsole;

    @BeforeClass
    public static void allocateConsole() {
        Assume.assumeTrue("Windows only",
                System.getProperty("os.name", "").toLowerCase().contains("win"));
        try {
            liveConsole = WinConsoleNative.allocConsole();
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError | NoClassDefFoundError e) {
            Assume.assumeNoException("Native console bindings not available", e);
        } catch (Throwable e) {
            // Native-image without FFM registration and similar: not usable.
            Assume.assumeNoException("Native console not usable", new RuntimeException(e));
        }
        Assume.assumeTrue("No allocatable console (headless without window-station "
                + "access, or an interactive console owned by someone else)", liveConsole);
    }

    @AfterClass
    public static void freeConsole() {
        if (liveConsole) {
            liveConsole = false;
            try {
                WinConsoleNative.freeConsole();
            } catch (Throwable ignored) {
            }
        }
    }

    @Test
    public void testLiveConsoleModeSaveRestore() throws IOException {
        long input = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
        int before = WinConsoleNative.getConsoleMode(input);
        assertNotEquals("Allocated console must have a readable input mode", -1, before);
        WinSysTerminal term = new WinSysTerminal("live-test", false);
        try {
            // Build-from-scratch must program exactly WINDOW_INPUT for raw.
            term.setAttributes(new Attributes());
            assertEquals("Raw mode on a true handle must be exactly WINDOW_INPUT",
                    AbstractWindowsTerminal.ENABLE_WINDOW_INPUT,
                    WinConsoleNative.getConsoleMode(input));
        } finally {
            term.close();
        }
        assertEquals("close() must restore the original input console mode",
                before, WinConsoleNative.getConsoleMode(input));
    }

    @Test
    public void testLiveOutputModeRestored() throws IOException {
        long output = WinConsoleNative.getStdHandle(WinConsoleNative.STD_OUTPUT_HANDLE);
        int before = WinConsoleNative.getConsoleMode(output);
        WinSysTerminal term = new WinSysTerminal("live-test", false);
        try {
            // Construction enables virtual-terminal processing on output.
            assertTrue("VT processing must be enabled on a true console",
                    (WinConsoleNative.getConsoleMode(output)
                            & WinConsoleNative.ENABLE_VIRTUAL_TERMINAL_PROCESSING) != 0);
        } finally {
            term.close();
        }
        assertEquals("close() must restore the original output console mode",
                before, WinConsoleNative.getConsoleMode(output));
    }

    @Test
    public void testLivePumpLifecycle() throws Exception {
        WinSysTerminal term = new WinSysTerminal("live-pump", false);
        try {
            assertTrue("Pump thread must be alive on a live console", term.pump.isAlive());
        } finally {
            term.close();
        }
        term.pump.join(5000);
        assertFalse("Pump thread must stop after close", term.pump.isAlive());
    }

    @Test
    public void testLiveSingleGuardRejectsSecond() throws IOException {
        WinSysTerminal first = new WinSysTerminal("live-first", false);
        try {
            try {
                new WinSysTerminal("live-second", false);
                fail("Second live Windows terminal must be rejected");
            } catch (IOException expected) {
                // expected: single live instance guard (#289)
            }
        } finally {
            first.close();
        }
        WinSysTerminal second = new WinSysTerminal("live-third", false);
        second.close();
    }

    @Test
    public void testLiveConsoleOutputCp() {
        assertTrue("Allocated console must report a real output code page",
                WinConsoleNative.getConsoleOutputCP() > 0);
    }
}
