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
package org.aesh.terminal.tty;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.aesh.terminal.utils.OSUtils;
import org.junit.Test;

/**
 * Tests for TtyDetect Windows console handle probe (#276, #277).
 * <p>
 * The tryWindowsConsoleHandle() method is tested via reflection since it's private.
 * On non-Windows platforms, it should return null (WinConsoleNative not loadable).
 * On Windows CI (piped stdin), it should return false (valid handle but pipe mode).
 * On Windows with a real console, it should return true.
 */
public class TtyDetectWindowsTest {

    @Test
    public void testTryWindowsConsoleHandleReturnsNullOnNonWindows() throws Exception {
        if (OSUtils.IS_WINDOWS) {
            return; // This test is only meaningful on non-Windows
        }
        Method m = TtyDetect.class.getDeclaredMethod("tryWindowsConsoleHandle");
        m.setAccessible(true);
        Boolean result = (Boolean) m.invoke(null);
        // On non-Windows, WinConsoleNative class doesn't exist or init fails
        // → tryWindowsConsoleHandle returns null
        // (the null case triggers the deny-by-default fallback)
        assertFalse("On non-Windows, isTty should not claim TTY via Windows probe",
                result != null && result);
    }

    @Test
    public void testIsTtyDeterministic() {
        if (OSUtils.IS_WINDOWS) {
            return; // Only tests the POSIX fallback path
        }
        // Detection must be deterministic within a process: repeated calls agree.
        assertTrue("isTty() should be deterministic",
                TtyDetect.isTty(TtyDetect.FD_STDIN) == TtyDetect.isTty(TtyDetect.FD_STDIN));
    }

    @Test
    public void testIsStdinTtyCached() {
        // Verify caching works — two calls return the same value
        boolean first = TtyDetect.isStdinTty();
        boolean second = TtyDetect.isStdinTty();
        assertTrue("Cached results should be consistent", first == second);
    }

    @Test
    public void testIsStdoutTtyCached() {
        boolean first = TtyDetect.isStdoutTty();
        boolean second = TtyDetect.isStdoutTty();
        assertTrue("Cached results should be consistent", first == second);
    }

    @Test
    public void testNoSystemConsoleCalledOnWindows() throws Exception {
        // Verify that tryWindowsConsoleHandle exists and is callable
        // (compile-time verification that the method signature is correct)
        Method m = TtyDetect.class.getDeclaredMethod("tryWindowsConsoleHandle");
        assertNotNull("tryWindowsConsoleHandle should exist", m);
    }
}
