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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.aesh.terminal.detect.BoundedProbeTransport;
import org.junit.Assume;
import org.junit.Test;

/**
 * JNI Win32 probe transport for pre-FFM runtimes (#389).
 * <p>
 * Mirrors the FFM {@code Win32ProbeTransport} session shape over the JNI
 * console bindings so standalone detection can probe on JDKs without the
 * Java 22 foreign API. All legs run headless: availability and record
 * translation never touch native code, and opening without a console
 * declines with {@link IOException} instead of hanging or leaking a
 * linkage error.
 */
public class Win32JniProbeTransportTest {

    private static void withProperties(Map<String, String> overrides, Runnable check) {
        Map<String, String> saved = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            if (System.getProperty(entry.getKey()) != null) {
                saved.put(entry.getKey(), System.getProperty(entry.getKey()));
            }
            System.setProperty(entry.getKey(), entry.getValue());
        }
        try {
            check.run();
        } finally {
            for (Map.Entry<String, String> entry : overrides.entrySet()) {
                if (saved.containsKey(entry.getKey())) {
                    System.setProperty(entry.getKey(), saved.get(entry.getKey()));
                } else {
                    System.clearProperty(entry.getKey());
                }
            }
        }
    }

    private static Map<String, String> spoofed(String os, String arch) {
        Map<String, String> spoof = new HashMap<String, String>();
        spoof.put("os.name", os);
        spoof.put("os.arch", arch);
        return spoof;
    }

    @Test
    public void testRawInputModeMatchesFfmComposition() {
        assertEquals("probe raw mode stays window-input without VT input or mouse",
                0x0008 | 0x0080, Win32JniProbeTransport.rawInputMode());
    }

    @Test
    public void testUnavailableOffWindows() {
        withProperties(spoofed("Linux", "amd64"), new Runnable() {
            @Override
            public void run() {
                assertFalse(new Win32JniProbeTransport().isAvailable());
            }
        });
    }

    @Test
    public void testUnavailableWithoutShippedBinary() {
        withProperties(spoofed("Windows 11", "aarch64"), new Runnable() {
            @Override
            public void run() {
                assertFalse(new Win32JniProbeTransport().isAvailable());
            }
        });
    }

    @Test
    public void testOpenDeclinesWithoutConsole() {
        withProperties(spoofed("Linux", "amd64"), new Runnable() {
            @Override
            public void run() {
                try {
                    new Win32JniProbeTransport().open();
                    fail("expected IOException, not a hang or a linkage leak");
                } catch (IOException expected) {
                    assertTrue("decline must explain itself, got: " + expected.getMessage(),
                            expected.getMessage() != null && !expected.getMessage().isEmpty());
                }
            }
        });
    }

    @Test
    public void testOpenDeclinesWithGuardMessage() {
        Assume.assumeFalse("base facade is shadowed by the FFM overlay on 22+",
                isJava22OrLater());
        withProperties(spoofed("Linux", "amd64"), new Runnable() {
            @Override
            public void run() {
                try {
                    new Win32JniProbeTransport().open();
                    fail("expected IOException, not a hang or a linkage leak");
                } catch (IOException expected) {
                    assertTrue("decline must name the guard, got: " + expected.getMessage(),
                            expected.getMessage() != null
                                    && expected.getMessage().contains("not Windows"));
                }
            }
        });
    }

    private static boolean isJava22OrLater() {
        String spec = System.getProperty("java.specification.version", "8");
        try {
            if (spec.startsWith("1.")) {
                return false;
            }
            return Integer.parseInt(spec) >= 22;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Test
    public void testKeyDownRecordContributesItsCharacter() {
        StringBuilder sink = new StringBuilder();
        assertTrue(Win32JniProbeTransport.appendRecordChars(
                new int[] { 1, 1, 1, 65, 66, 0 }, sink));
        assertEquals("B", sink.toString());
    }

    @Test
    public void testKeyUpRecordIsConsumedWithoutBytes() {
        StringBuilder sink = new StringBuilder();
        assertTrue(Win32JniProbeTransport.appendRecordChars(
                new int[] { 1, 0, 1, 65, 66, 0 }, sink));
        assertEquals("", sink.toString());
    }

    @Test
    public void testCharlessKeyDownIsConsumedWithoutBytes() {
        StringBuilder sink = new StringBuilder();
        assertTrue(Win32JniProbeTransport.appendRecordChars(
                new int[] { 1, 1, 1, 16, 0, 0 }, sink));
        assertEquals("", sink.toString());
    }

    @Test
    public void testNonKeyRecordIsConsumedWithoutBytes() {
        StringBuilder sink = new StringBuilder();
        assertTrue("mouse event", Win32JniProbeTransport.appendRecordChars(
                new int[] { 2, 0, 0, 0, 0, 0 }, sink));
        assertTrue("window-buffer-size event", Win32JniProbeTransport.appendRecordChars(
                new int[] { 4, 80, 24, 0, 0, 0 }, sink));
        assertEquals("", sink.toString());
    }

    @Test
    public void testNullRecordReadsAsFailure() {
        assertFalse(Win32JniProbeTransport.appendRecordChars(null, new StringBuilder()));
    }

    @Test
    public void testTransportExtendsBoundedContract() {
        assertTrue(new Win32JniProbeTransport() instanceof BoundedProbeTransport);
    }
}
