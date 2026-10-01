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
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
    // Standard Win32 value, kept local: the main code deliberately names
    // no constant for a flag it must never set.
    private static final int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;

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

    /**
     * The SetConsoleMode contract every composed word must satisfy: the
     * extended flag present (otherwise Quick Edit cannot switch off),
     * Quick Edit absent, and VT input absent (it duplicates key events).
     */
    private static void assertRawModeContract(int mode) {
        assertTrue("EXTENDED_FLAGS required to disable QUICK_EDIT",
                (mode & ENABLE_EXTENDED_FLAGS) != 0);
        assertEquals("QUICK_EDIT must never be composed", 0,
                mode & ENABLE_QUICK_EDIT_MODE);
        assertEquals("VIRTUAL_TERMINAL_INPUT must stay off", 0,
                mode & ENABLE_VIRTUAL_TERMINAL_INPUT);
    }

    // ==================== setAttributes flag matrix (#277 M5) ====================

    @Test
    public void testRawModeOnlyWindowInput() throws IOException {
        // Raw mode: ECHO=false, ICANON=false, ISIG=false
        StubWindowsTerminal term = new StubWindowsTerminal(
                ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT | ENABLE_QUICK_EDIT_MODE);

        try {
            Attributes raw = new Attributes();
            // All flags false by default in new Attributes
            term.setAttributes(raw);

            // Raw mode: WINDOW_INPUT plus EXTENDED_FLAGS (required to
            // switch QUICK_EDIT off), nothing else.
            assertEquals("Raw mode should have WINDOW_INPUT + EXTENDED_FLAGS",
                    ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS, term.currentMode);
            assertRawModeContract(term.currentMode);
        } finally {
            term.close();
        }
    }

    @Test
    public void testRawModeWithSignals() throws IOException {
        StubWindowsTerminal term = new StubWindowsTerminal(
                ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT | ENABLE_QUICK_EDIT_MODE);

        try {
            Attributes raw = new Attributes();
            raw.setLocalFlag(Attributes.LocalFlag.ISIG, true);
            term.setAttributes(raw);

            assertEquals("Raw mode with ISIG should have WINDOW_INPUT + PROCESSED_INPUT",
                    ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS | ENABLE_PROCESSED_INPUT,
                    term.currentMode);
            assertRawModeContract(term.currentMode);
        } finally {
            term.close();
        }
    }

    @Test
    public void testCookedMode() throws IOException {
        StubWindowsTerminal term = new StubWindowsTerminal(0);

        try {
            Attributes cooked = new Attributes();
            cooked.setLocalFlag(Attributes.LocalFlag.ECHO, true);
            cooked.setLocalFlag(Attributes.LocalFlag.ICANON, true);
            cooked.setLocalFlag(Attributes.LocalFlag.ISIG, true);
            term.setAttributes(cooked);

            int expected = ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS | ENABLE_ECHO_INPUT
                    | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT;
            assertEquals("Cooked mode should have WINDOW + EXTENDED + ECHO + LINE + PROCESSED",
                    expected, term.currentMode);
            assertRawModeContract(term.currentMode);
        } finally {
            term.close();
        }
    }

    @Test
    public void testQuickEditModeClearedInRawMode() throws IOException {
        // Start with QUICK_EDIT_MODE enabled (Windows default)
        StubWindowsTerminal term = new StubWindowsTerminal(
                ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT | ENABLE_QUICK_EDIT_MODE);

        try {
            Attributes raw = new Attributes();
            raw.setLocalFlag(Attributes.LocalFlag.ISIG, true);
            term.setAttributes(raw);

            // QUICK_EDIT_MODE must NOT be preserved — it blocks ReadConsoleInputW
            assertEquals("QUICK_EDIT_MODE should be cleared in raw mode", 0,
                    term.currentMode & ENABLE_QUICK_EDIT_MODE);
            assertRawModeContract(term.currentMode);
        } finally {
            term.close();
        }
    }

    @Test
    public void testMouseInputSetsExtendedFlags() throws IOException {
        StubWindowsTerminal term = new StubWindowsTerminal(0);
        term.mouseInputEnabled = true;

        try {
            Attributes raw = new Attributes();
            term.setAttributes(raw);

            assertTrue("Mouse input should set ENABLE_MOUSE_INPUT",
                    (term.currentMode & ENABLE_MOUSE_INPUT) != 0);
            assertTrue("Mouse input should set ENABLE_EXTENDED_FLAGS",
                    (term.currentMode & ENABLE_EXTENDED_FLAGS) != 0);
            assertEquals("Mouse + raw should be WINDOW + MOUSE + EXTENDED",
                    ENABLE_WINDOW_INPUT | ENABLE_MOUSE_INPUT | ENABLE_EXTENDED_FLAGS,
                    term.currentMode);
            assertRawModeContract(term.currentMode);
        } finally {
            term.close();
        }
    }

    @Test
    public void testBuildFromScratchNeverPreservesStaleFlags() throws IOException {
        // Start with a mode that has every flag imaginable
        int staleMode = 0xFFFF;
        StubWindowsTerminal term = new StubWindowsTerminal(staleMode);

        try {
            Attributes raw = new Attributes();
            term.setAttributes(raw);

            // Build-from-scratch should produce WINDOW_INPUT + EXTENDED_FLAGS
            // regardless of what was in the console mode before
            assertEquals("Stale flags should not be preserved",
                    ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS, term.currentMode);
            assertRawModeContract(term.currentMode);
        } finally {
            term.close();
        }
    }

    @Test
    public void testGetAttributesReportsEchoCtl() throws IOException {
        // Readline's INT handler prints ^C iff ECHOCTL is set. POSIX cooked
        // mode has it on, so report it for identical Ctrl+C feedback.
        StubWindowsTerminal term = new StubWindowsTerminal(
                ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT);
        try {
            assertTrue(term.getAttributes().getLocalFlag(Attributes.LocalFlag.ECHOCTL));
            Attributes raw = new Attributes();
            term.setAttributes(raw);
            assertTrue(term.getAttributes().getLocalFlag(Attributes.LocalFlag.ECHOCTL));
        } finally {
            term.close();
        }
    }

    // ==================== close() mode restore (#277 M5) ====================

    @Test
    public void testCloseRestoresOriginalMode() throws IOException {
        int originalMode = ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT | ENABLE_QUICK_EDIT_MODE;
        StubWindowsTerminal term = new StubWindowsTerminal(originalMode);

        try {
            // Enter raw mode
            Attributes raw = new Attributes();
            term.setAttributes(raw);
            assertNotEquals("Mode should change in raw mode", originalMode, term.currentMode);

            // Close — should restore original mode
            term.close();
            assertEquals("close() should restore the original console mode",
                    originalMode, term.currentMode);
        } finally {
            term.close();
        }
    }

    @Test
    public void testCloseRestoresAfterMouseMode() throws IOException {
        int originalMode = ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT;
        StubWindowsTerminal term = new StubWindowsTerminal(originalMode);

        try {
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
        } finally {
            term.close();
        }
    }

    // ==================== close() output restore + idempotency (#278) ====================

    @Test
    public void testCloseRestoresOriginalOutputMode() throws IOException {
        int originalInput = ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT;
        int originalOutput = 0x0004; // e.g. ENABLE_VIRTUAL_TERMINAL_PROCESSING pre-set
        StubWindowsTerminal term = new StubWindowsTerminal(originalInput, originalOutput);

        try {
            // Simulate VT-output enable changing the output mode, plus raw input mode
            term.setOutputConsoleMode(0x0104);
            Attributes raw = new Attributes();
            term.setAttributes(raw);

            term.close();
            assertEquals("close() should restore the original output console mode",
                    originalOutput, term.outputMode);
            assertEquals("close() should still restore the original input console mode",
                    originalInput, term.currentMode);
        } finally {
            term.close();
        }
    }

    @Test
    public void testDoubleCloseRestoresOnce() throws IOException {
        int originalInput = ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT;
        int originalOutput = 0x0004;
        StubWindowsTerminal term = new StubWindowsTerminal(originalInput, originalOutput);

        try {
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
        } finally {
            term.close();
        }
    }

    // ==================== single live instance guard (#289) ====================

    @Test
    public void testSecondLiveTerminalRejectedAndSlotReusable() throws IOException {
        // Two input pumps on one console compete for ReadConsoleInputW
        // events (#276) — the second concurrent construction must fail.
        StubWindowsTerminal first = new StubWindowsTerminal(0);
        try {
            try {
                new StubWindowsTerminal(0);
                fail("Second live Windows terminal must be rejected");
            } catch (IOException expected) {
                // expected: single live instance guard
            }
        } finally {
            first.close();
        }
        // The slot is reusable after close — sequential use still works.
        StubWindowsTerminal second = new StubWindowsTerminal(0);
        second.close();
    }

    // ==================== pump handoff on close/reopen (#344) ====================

    /**
     * Scriptable pump: the native wait/read touchpoints are programmable,
     * so wait/close/reopen interleavings run deterministically headless.
     * <p>
     * The scripted wait models native-wait semantics — close()'s interrupt
     * does not release it, only the event (or the 30s cap) does — so the
     * close-during-wait interleaving is reachable. All waits are bounded;
     * threads are daemons, so a forgotten release fails the test, never
     * the suite.
     */
    private static class ScriptedPumpTerminal extends StubWindowsTerminal {

        final CountDownLatch waitEntered = new CountDownLatch(1);
        final CountDownLatch releaseWait = new CountDownLatch(1);
        final CountDownLatch readEntered = new CountDownLatch(1);
        final CountDownLatch releaseRead = new CountDownLatch(1);
        volatile boolean signalWait;
        volatile boolean closeOnRead;
        volatile int waitCalls;
        volatile int readCalls;
        final byte[] payload;

        ScriptedPumpTerminal(byte[] payload) throws IOException {
            super(ENABLE_WINDOW_INPUT);
            this.payload = payload.clone();
        }

        @Override
        protected long inputHandle() {
            return 1L;
        }

        @Override
        protected int waitForInput(long handle, int timeoutMs) {
            waitCalls++;
            waitEntered.countDown();
            if (releaseWait.getCount() == 0) {
                return signalWait ? WinConsoleNative.WAIT_OBJECT_0 : WinConsoleNative.WAIT_TIMEOUT;
            }
            long deadline = System.currentTimeMillis() + 30000;
            boolean released = false;
            while (!released && System.currentTimeMillis() < deadline) {
                try {
                    released = releaseWait.await(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    // Swallowed deliberately: models the uninterruptible
                    // native wait, which close()'s interrupt cannot wake.
                }
            }
            if (!released) {
                return WinConsoleNative.WAIT_TIMEOUT;
            }
            return signalWait ? WinConsoleNative.WAIT_OBJECT_0 : WinConsoleNative.WAIT_TIMEOUT;
        }

        @Override
        protected int pendingInputEvents(long handle) {
            return 0;
        }

        @Override
        protected byte[] readConsoleInput() {
            readCalls++;
            readEntered.countDown();
            try {
                if (!releaseRead.await(30, TimeUnit.SECONDS)) {
                    return new byte[0];
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return new byte[0];
            }
            if (closeOnRead) {
                close();
            }
            return payload.clone();
        }
    }

    private static Thread closeInBackground(final ScriptedPumpTerminal term) {
        Thread closer = new Thread(new Runnable() {
            @Override
            public void run() {
                term.close();
            }
        }, "pump-handoff-closer");
        closer.setDaemon(true);
        closer.start();
        return closer;
    }

    private static void joinBounded(Thread thread, String what) throws InterruptedException {
        thread.join(10000);
        assertTrue(what + " must finish promptly", !thread.isAlive());
    }

    @Test
    public void testSignaledWaitAfterCloseSkipsRead() throws Exception {
        ScriptedPumpTerminal term = new ScriptedPumpTerminal(new byte[] { 'k' });
        // Pre-release the read: a lost race then fails fast, never hangs.
        term.releaseRead.countDown();
        try {
            assertTrue("pump must reach its wait", term.waitEntered.await(5, TimeUnit.SECONDS));
            Thread closer = closeInBackground(term);
            // close() sets closing synchronously before joining; settle so
            // the release below lands strictly after.
            Thread.sleep(500);
            term.signalWait = true;
            term.releaseWait.countDown();
            joinBounded(closer, "close()");
            assertEquals("a wait signaled after close started must not be read",
                    0, term.readCalls);
        } finally {
            term.releaseWait.countDown();
            term.close();
        }
    }

    @Test
    public void testLiveSlotHeldUntilPumpStops() throws Exception {
        ScriptedPumpTerminal old = new ScriptedPumpTerminal(new byte[] { 'k' });
        try {
            assertTrue("pump must reach its wait", old.waitEntered.await(5, TimeUnit.SECONDS));
            Thread closer = closeInBackground(old);
            // The old pump is still inside its wait: replacement must fail.
            ScriptedPumpTerminal leaked = null;
            try {
                leaked = new ScriptedPumpTerminal(new byte[] { 'x' });
                fail("replacement created while the old pump is alive must be rejected");
            } catch (IOException expected) {
                // expected: slot still held
            } finally {
                if (leaked != null) {
                    leaked.close();
                }
            }
            // Release the old pump; it exits via timeout with closing set.
            old.releaseWait.countDown();
            joinBounded(closer, "close()");
            assertTrue("old pump must be dead after close", !old.pump.isAlive());
            // Slot reusable, and the first key lands in the new pipe.
            ScriptedPumpTerminal next = new ScriptedPumpTerminal(new byte[] { 'y' });
            try {
                next.releaseRead.countDown();
                next.signalWait = true;
                next.releaseWait.countDown();
                assertTrue("replacement pump must read", next.readEntered.await(5, TimeUnit.SECONDS));
                boolean delivered = false;
                long deadline = System.currentTimeMillis() + 5000;
                while (!delivered && System.currentTimeMillis() < deadline) {
                    if (next.input().available() > 0) {
                        delivered = next.input().read() == 'y';
                    } else {
                        Thread.sleep(10);
                    }
                }
                assertTrue("first key after replacement must reach the new terminal", delivered);
            } finally {
                next.close();
            }
        } finally {
            old.releaseWait.countDown();
            old.close();
        }
    }

    @Test
    public void testCloseOnPumpThreadReleasesViaBackstop() throws Exception {
        ScriptedPumpTerminal term = new ScriptedPumpTerminal(new byte[] { 'k' });
        term.closeOnRead = true;
        term.releaseRead.countDown();
        try {
            term.signalWait = true;
            term.releaseWait.countDown();
            assertTrue("pump must reach its read", term.readEntered.await(5, TimeUnit.SECONDS));
            // close() ran on the pump thread (no join, no direct release):
            // poll for the backstop freeing the slot.
            ScriptedPumpTerminal next = null;
            long deadline = System.currentTimeMillis() + 5000;
            while (next == null && System.currentTimeMillis() < deadline) {
                try {
                    next = new ScriptedPumpTerminal(new byte[] { 'y' });
                } catch (IOException e) {
                    Thread.sleep(50);
                }
            }
            assertTrue("pump-exit backstop must free the slot after self-close", next != null);
            next.releaseWait.countDown();
            next.close();
            joinBounded(term.pump, "old pump");
        } finally {
            term.close();
        }
    }

    @Test
    public void testCloseReleasesSlotWhenPumpWedged() throws Exception {
        ScriptedPumpTerminal term = new ScriptedPumpTerminal(new byte[] { 'k' });
        try {
            assertTrue("pump must reach its wait", term.waitEntered.await(5, TimeUnit.SECONDS));
            // Never release the wait: the join backstop (5s) must fire and
            // close() must still return bounded with the slot freed.
            long start = System.currentTimeMillis();
            term.close();
            long elapsed = System.currentTimeMillis() - start;
            assertTrue("close() with a wedged pump must stay bounded, took " + elapsed + "ms",
                    elapsed < 15000);
            ScriptedPumpTerminal next = new ScriptedPumpTerminal(new byte[] { 'y' });
            next.releaseWait.countDown();
            next.close();
        } finally {
            term.releaseWait.countDown();
            term.close();
        }
    }
}
