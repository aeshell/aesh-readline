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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import org.aesh.terminal.utils.OSUtils;
import org.junit.Test;

/**
 * Environment-sensitive provider selection tests.
 * <p>
 * Each test self-activates only in its matching environment, so the suite
 * is green (by skipping) on dev machines and asserts for real in the
 * Windows CI env-matrix legs (msys, dumb-term, native), where the env is
 * controlled per job. Provider isSupported() is a pure env heuristic
 * (no console needed), so these run headless.
 */
public class TerminalProviderEnvTest {

    @Test
    public void testCygwinEligibleInMsysEnv() {
        assumeTrue("requires Cygwin/MSYS env (MSYSTEM, CYGWIN, mintty, or POSIX PWD)",
                OSUtils.IS_CYGWIN);
        assertTrue("Cygwin provider must be eligible in MSYS env",
                new CygwinTerminalProvider().isSupported());
    }

    @Test
    public void testWinSysDeclinesDumbTerm() {
        assumeTrue("requires TERM=dumb", "dumb".equals(System.getenv("TERM")));
        assertFalse("WinSys provider must decline a dumb terminal",
                new WinSysTerminalProvider().isSupported());
    }

    @Test
    public void testWinSysEligibleInNativeWindowsEnv() {
        assumeTrue("requires native Windows env (no Cygwin indicators)",
                OSUtils.IS_WINDOWS && !OSUtils.IS_CYGWIN);
        assumeTrue("requires non-dumb TERM", !"dumb".equals(System.getenv("TERM")));
        assertTrue("WinSys provider must be eligible on a native Windows console env",
                new WinSysTerminalProvider().isSupported());
    }
}
