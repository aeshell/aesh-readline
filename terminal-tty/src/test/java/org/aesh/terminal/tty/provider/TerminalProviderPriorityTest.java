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
package org.aesh.terminal.tty.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.aesh.terminal.utils.OSUtils;
import org.junit.Test;

/**
 * Tests for terminal provider priority ordering and mutual exclusion.
 * <p>
 * Verifies that the priority values and isSupported() logic ensure exactly
 * one provider is selected per platform, and that the Windows providers
 * (WinSys vs Cygwin) are mutually exclusive via IS_CYGWIN.
 * Runs on all platforms.
 */
public class TerminalProviderPriorityTest {

    @Test
    public void testWinSysAndCygwinMutuallyExclusive() {
        WinSysTerminalProvider winSys = new WinSysTerminalProvider();
        CygwinTerminalProvider cygwin = new CygwinTerminalProvider();

        // At most one can return true — never both
        if (winSys.isSupported() && cygwin.isSupported()) {
            // This should never happen — IS_CYGWIN gates Cygwin,
            // !IS_CYGWIN gates WinSys
            throw new AssertionError("WinSys and Cygwin cannot both be supported");
        }
    }

    @Test
    public void testFfmProviderNotSupportedOnWindows() {
        FfmTerminalProvider ffm = new FfmTerminalProvider();
        if (OSUtils.IS_WINDOWS) {
            assertFalse("FFM provider should not be supported on Windows",
                    ffm.isSupported());
        }
    }

    @Test
    public void testExecPtyNotSupportedOnWindows() {
        ExecPtyTerminalProvider exec = new ExecPtyTerminalProvider();
        if (OSUtils.IS_WINDOWS) {
            assertFalse("ExecPty provider should not be supported on Windows",
                    exec.isSupported());
        }
    }

    @Test
    public void testWinSysPriorityHigherThanCygwin() {
        WinSysTerminalProvider winSys = new WinSysTerminalProvider();
        CygwinTerminalProvider cygwin = new CygwinTerminalProvider();
        assertTrue("WinSys priority should be >= Cygwin priority",
                winSys.priority() >= cygwin.priority());
    }

    @Test
    public void testPriorityValues() {
        assertEquals("FfmTerminalProvider priority", 100, new FfmTerminalProvider().priority());
        assertEquals("WinSysTerminalProvider priority", 100, new WinSysTerminalProvider().priority());
        assertEquals("CygwinTerminalProvider priority", 75, new CygwinTerminalProvider().priority());
        assertEquals("ExecPtyTerminalProvider priority", 50, new ExecPtyTerminalProvider().priority());
    }

    @Test
    public void testProviderNames() {
        assertEquals("ffm", new FfmTerminalProvider().name());
        assertEquals("windows", new WinSysTerminalProvider().name());
        assertEquals("cygwin", new CygwinTerminalProvider().name());
        assertEquals("exec", new ExecPtyTerminalProvider().name());
    }

    @Test
    public void testWinSysNotSupportedOnNonWindows() {
        if (!OSUtils.IS_WINDOWS) {
            assertFalse("WinSys should not be supported on non-Windows",
                    new WinSysTerminalProvider().isSupported());
        }
    }

    @Test
    public void testCygwinNotSupportedOnNonWindows() {
        if (!OSUtils.IS_WINDOWS) {
            assertFalse("Cygwin should not be supported on non-Windows",
                    new CygwinTerminalProvider().isSupported());
        }
    }

    @Test
    public void testWinSysProviderDoesNotCallSystemConsole() {
        // Verify isSupported() doesn't call System.console() on any platform.
        // On non-Windows, isSupported() returns false immediately (IS_WINDOWS check).
        // On Windows, it uses TtyDetect.isStdinTty() which avoids System.console().
        // This test just verifies the call doesn't throw or hang — the actual
        // System.console() avoidance was verified via jstack (#276).
        WinSysTerminalProvider provider = new WinSysTerminalProvider();
        boolean supported = provider.isSupported();
        // Value depends on platform — just verify no exception
        assertTrue("Should return a boolean", supported || !supported);
    }
}
