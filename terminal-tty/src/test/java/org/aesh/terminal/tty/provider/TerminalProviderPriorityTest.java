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

import java.util.HashMap;
import java.util.Map;

import org.aesh.terminal.utils.OSUtils;
import org.aesh.terminal.utils.PlatformContext;
import org.junit.Test;

/**
 * Tests for terminal provider priority ordering and overlap resolution.
 * <p>
 * WinSys and Cygwin may both be eligible under a Cygwin/MSYS shell; the
 * overlap is resolved by priority (WinSys first) plus the ground-truth
 * probes in createTerminal (GetConsoleMode vs tty), which fall through
 * to the next provider on failure. Runs on all platforms.
 */
public class TerminalProviderPriorityTest {

    private static Map<String, String> env(String... pairs) {
        Map<String, String> env = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            env.put(pairs[i], pairs[i + 1]);
        }
        return env;
    }

    @Test
    public void testWinSysAndCygwinOverlapResolvedByPriority() {
        WinSysTerminalProvider winSys = new WinSysTerminalProvider();
        CygwinTerminalProvider cygwin = new CygwinTerminalProvider();

        // ConPTY-hosted bash: both eligible (TERM must not be dumb for WinSys).
        PlatformContext msysConsole = new PlatformContext("Windows 11", "amd64",
                env("MSYSTEM", "MINGW64", "TERM", "xterm-256color"), "/home/test");
        assertTrue("WinSys eligible with a console under Cygwin (#360)",
                winSys.isSupported(msysConsole));
        assertTrue("Cygwin eligible under Cygwin", cygwin.isSupported(msysConsole));
        assertTrue("WinSys must be tried before Cygwin",
                winSys.priority() > cygwin.priority());

        // Native Windows without Cygwin indicators: WinSys only.
        PlatformContext nativeWindows = new PlatformContext("Windows 11", "amd64",
                env("TERM", "xterm-256color"), "/home/test");
        assertTrue("WinSys owns native Windows", winSys.isSupported(nativeWindows));
        assertFalse("Cygwin needs Cygwin indicators", cygwin.isSupported(nativeWindows));
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
    public void testPriorityOrderingChain() {
        // Relational ordering: system providers beat Cygwin, Cygwin beats the fallback.
        // Asserted relationally (not pinned literals) so priority values can evolve.
        int ffm = new FfmTerminalProvider().priority();
        int winSys = new WinSysTerminalProvider().priority();
        int cygwin = new CygwinTerminalProvider().priority();
        int exec = new ExecPtyTerminalProvider().priority();
        assertTrue("WinSys should outrank Cygwin", winSys >= cygwin);
        assertTrue("FFM should outrank Cygwin", ffm >= cygwin);
        assertTrue("Cygwin should outrank ExecPty fallback", cygwin > exec);
    }

    @Test
    public void testProviderNamesDistinct() {
        // Provider names distinguish providers in logs/diagnostics — they must
        // be non-null, non-empty, and distinct (values themselves are free to change).
        String[] names = {
                new FfmTerminalProvider().name(),
                new WinSysTerminalProvider().name(),
                new CygwinTerminalProvider().name(),
                new ExecPtyTerminalProvider().name()
        };
        java.util.Set<String> distinct = new java.util.HashSet<>();
        for (String name : names) {
            assertTrue("Provider name should be non-empty", name != null && !name.isEmpty());
            assertTrue("Provider names should be distinct: " + name, distinct.add(name));
        }
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
    public void testWinSysIsSupportedConsistent() {
        // isSupported() must be side-effect-free and idempotent: repeated calls
        // agree (no System.console() terminal init, no state drift — #276).
        WinSysTerminalProvider provider = new WinSysTerminalProvider();
        assertEquals("isSupported() should be consistent across calls",
                provider.isSupported(), provider.isSupported());
    }
}
