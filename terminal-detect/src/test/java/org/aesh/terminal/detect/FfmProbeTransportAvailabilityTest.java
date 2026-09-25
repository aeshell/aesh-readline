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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Method;

import org.junit.Test;

/**
 * Tests for the FFM probe transport (Java 22+ MRJAR layer).
 * <p>
 * All access is reflective so this test compiles and passes on pre-22
 * runtimes (where the transport class does not exist) and skips live
 * I/O unless a usable terminal is present. Live checks never write
 * query bytes and never read: opening a session briefly sets raw mode
 * and closing it must restore the exact previous state (verified via
 * {@code stty -g} before/after when stty is available).
 */
public class FfmProbeTransportAvailabilityTest {

    private static final String FFM_TRANSPORT = "org.aesh.terminal.detect.FfmProbeTransport";

    private static Class<?> loadTransportClass() {
        try {
            return Class.forName(FFM_TRANSPORT);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
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
    public void testAvailabilityCheckDoesNotThrow() throws Exception {
        Class<?> clazz = loadTransportClass();
        if (clazz == null) {
            return;
        }
        TerminalProbeTransport transport = newTransport(clazz);
        // Must not throw with or without native access or /dev/tty
        boolean available = transport.isAvailable();
        if (!hasDevTty()) {
            // Without a terminal the transport can never be available
            assertTrue(!available);
        }
    }

    @Test
    public void testOpenCloseRoundTripRestoresState() throws Exception {
        Class<?> clazz = loadTransportClass();
        if (clazz == null) {
            return;
        }
        TerminalProbeTransport transport = newTransport(clazz);
        if (!transport.isAvailable()) {
            return;
        }
        String before = sttyState();
        TerminalProbeSession session;
        try {
            session = transport.open();
        } catch (java.io.IOException e) {
            // No controlling terminal for this process (e.g. surefire with
            // redirected stdio): /dev/tty exists but cannot be opened.
            // Production treats this as "skip probing" — so does the test.
            return;
        }
        assertNotNull(session);
        try {
            assertNotNull(session.input());
            // Empty write must be a harmless no-op (no bytes hit the terminal)
            session.write(new byte[0]);
            InputStream in = session.input();
            assertTrue(in == session.input());
        } finally {
            session.close();
            // Idempotent close must not throw
            session.close();
        }
        String after = sttyState();
        if (before != null && after != null) {
            assertEquals("raw query mode must be fully restored on close", before, after);
        }
    }

    private static TerminalProbeTransport newTransport(Class<?> clazz) throws Exception {
        return (TerminalProbeTransport) clazz.getDeclaredConstructor().newInstance();
    }

    private static boolean hasDevTty() {
        File devTty = new File("/dev/tty");
        return devTty.exists() && devTty.canRead() && devTty.canWrite();
    }

    /**
     * Capture the current termios state via stty, or null when stty or
     * /dev/tty is unavailable.
     *
     * @return the stty state string, or null if it cannot be captured
     */
    private static String sttyState() {
        Process p = null;
        try {
            p = new ProcessBuilder("stty", "-g")
                    .redirectInput(new File("/dev/tty"))
                    .redirectErrorStream(true)
                    .start();
            byte[] buf = new byte[256];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = p.getInputStream().read(buf)) != -1) {
                sb.append(new String(buf, 0, n));
            }
            p.waitFor();
            return p.exitValue() == 0 ? sb.toString().trim() : null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (p != null) {
                p.destroy();
            }
        }
    }
}
