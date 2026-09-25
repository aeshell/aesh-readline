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
package org.aesh.terminal.detect;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Test;

/**
 * Headless tests for the Win32 probe transport (Java 22+ MRJAR layer).
 * <p>
 * All access is reflective so this test compiles and passes on pre-22
 * runtimes (where the transport class does not exist). Off-Windows the
 * transport must report unavailable without throwing and without loading
 * any FFM API classes. Live verification on a real Windows console
 * (conhost, Windows Terminal) requires field testing — CI runners have
 * no window station (see #290).
 */
public class Win32ProbeTransportAvailabilityTest {

    private static final String WIN32_TRANSPORT = "org.aesh.terminal.detect.Win32ProbeTransport";

    private static Class<?> loadTransportClass() {
        try {
            return Class.forName(WIN32_TRANSPORT);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Test
    public void testNativeAccessCheckDoesNotThrow() throws Exception {
        Class<?> clazz = loadTransportClass();
        if (clazz == null) {
            return;
        }
        Method check = clazz.getDeclaredMethod("isNativeAccessEnabled");
        Object result = check.invoke(null);
        assertTrue(result instanceof Boolean);
    }

    @Test
    public void testUnavailableOffWindows() throws Exception {
        if (isWindows()) {
            return;
        }
        Class<?> clazz = loadTransportClass();
        if (clazz == null) {
            return;
        }
        TerminalProbeTransport transport = (TerminalProbeTransport) clazz.getDeclaredConstructor().newInstance();
        assertFalse(transport.isAvailable());
    }

    @Test
    public void testWin32NeverShadowsPosixTransports() throws Exception {
        // Selection order is FFM → Win32 → stty. Off-Windows the Win32
        // transport must stay out of the way so POSIX probing is unaffected.
        if (isWindows()) {
            return;
        }
        Class<?> clazz = loadTransportClass();
        if (clazz == null) {
            return;
        }
        TerminalProbeTransport transport = (TerminalProbeTransport) clazz.getDeclaredConstructor().newInstance();
        assertFalse(transport.isAvailable());
        // A null/empty color query through the default path must still
        // complete gracefully (stty fallback or clean skip).
        TerminalColorQuery.query();
    }
}
