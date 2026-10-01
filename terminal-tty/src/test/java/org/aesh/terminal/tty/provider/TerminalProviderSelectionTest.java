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

import org.aesh.terminal.utils.PlatformContext;
import org.junit.Test;

/**
 * Host-independent provider eligibility matrix (#356, #296).
 * <p>
 * Every combination is a fixture context: no JVM-global property or
 * environment variable is read or mutated, so these legs run
 * identically on Linux, macOS, Windows, and every CI job. The
 * ambient-agreement leg pins that a system snapshot decides exactly
 * like the legacy no-arg checks on the live host.
 */
public class TerminalProviderSelectionTest {

    private static Map<String, String> env(String... pairs) {
        Map<String, String> env = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            env.put(pairs[i], pairs[i + 1]);
        }
        return env;
    }

    private static PlatformContext context(String os, String arch, Map<String, String> env) {
        return new PlatformContext(os, arch, env, "/home/test");
    }

    private static final FfmTerminalProvider FFM = new FfmTerminalProvider();
    private static final CygwinTerminalProvider CYGWIN = new CygwinTerminalProvider();
    private static final ExecPtyTerminalProvider EXEC = new ExecPtyTerminalProvider();
    private static final WinSysTerminalProvider WINSYS = new WinSysTerminalProvider();

    @Test
    public void testLinuxAdmitsPosixProviders() {
        PlatformContext linux = context("Linux", "x86_64", env("TERM", "xterm-256color"));
        assertFalse("Cygwin needs Cygwin indicators", CYGWIN.isSupported(linux));
        assertTrue("Exec covers plain Linux", EXEC.isSupported(linux));
        assertFalse("WinSys needs Windows", WINSYS.isSupported(linux));
    }

    @Test
    public void testMsysAdmitsOnlyCygwin() {
        PlatformContext msys = context("Windows 10", "amd64",
                env("MSYSTEM", "MINGW64", "TERM", "xterm"));
        assertTrue("Cygwin owns MSYS", CYGWIN.isSupported(msys));
        assertFalse("Exec declines Windows", EXEC.isSupported(msys));
        assertFalse("WinSys declines Cygwin", WINSYS.isSupported(msys));
        assertFalse("FFM declines Windows", FFM.isSupported(msys));
    }

    @Test
    public void testNativeWindowsAdmitsWinSys() {
        PlatformContext windows = context("Windows 11", "amd64", env("TERM", "xterm-256color"));
        assertTrue("WinSys owns native Windows", WINSYS.isSupported(windows));
        assertFalse("Cygwin needs indicators", CYGWIN.isSupported(windows));
        assertFalse("Exec declines Windows", EXEC.isSupported(windows));
        assertFalse("FFM declines Windows", FFM.isSupported(windows));
    }

    @Test
    public void testDumbTermDeclinesWinSys() {
        PlatformContext dumb = context("Windows 11", "amd64", env("TERM", "dumb"));
        assertFalse("dumb terminals cannot drive the console", WINSYS.isSupported(dumb));
    }

    @Test
    public void testUnsupportedAbisDeclineFfm() {
        assertFalse("no Linux layouts on FreeBSD",
                FFM.isSupported(context("FreeBSD", "amd64", env("TERM", "xterm"))));
        assertFalse("no 32-bit layouts",
                FFM.isSupported(context("Linux", "i386", env("TERM", "xterm"))));
        assertTrue("Exec still covers FreeBSD",
                EXEC.isSupported(context("FreeBSD", "amd64", env("TERM", "xterm"))));
    }

    @Test
    public void testAmbientAgreementOnLiveHost() {
        PlatformContext system = PlatformContext.system();
        assertEquals("context must decide like ambient checks (Cygwin)",
                CYGWIN.isSupported(), CYGWIN.isSupported(system));
        assertEquals("context must decide like ambient checks (Exec)",
                EXEC.isSupported(), EXEC.isSupported(system));
        assertEquals("context must decide like ambient checks (WinSys)",
                WINSYS.isSupported(), WINSYS.isSupported(system));
    }
}
