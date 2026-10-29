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

import java.util.HashMap;
import java.util.Map;

import org.junit.Assume;
import org.junit.Test;

/**
 * Lazy JNI loading for the Windows console bridge (#389).
 * <p>
 * Touching the holder class must never load the DLL: availability is
 * decided from OS/arch facts alone, and loading happens only on the
 * first native call, with guard failures preceding any loader use.
 * System properties are spoofed and restored so the matrix runs
 * identically on every host OS.
 */
public class WinConsoleNativeJniTest {

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

    @Test
    public void testUnsupportedOsDeclinesWithoutLoading() {
        Map<String, String> spoof = new HashMap<String, String>();
        spoof.put("os.name", "Linux");
        withProperties(spoof, new Runnable() {
            @Override
            public void run() {
                assertFalse("non-Windows must decline before any loader use",
                        WinConsoleNativeJni.isSupported());
            }
        });
    }

    @Test
    public void testArchMappingDecidesSupportOnWindows() {
        String[][] cases = {
                { "amd64", "true" },
                { "x86_64", "true" },
                { "aarch64", "false" },
                { "x86", "false" },
        };
        for (int i = 0; i < cases.length; i++) {
            final String arch = cases[i][0];
            final boolean expected = Boolean.parseBoolean(cases[i][1]);
            Map<String, String> spoof = new HashMap<String, String>();
            spoof.put("os.name", "Windows 11");
            spoof.put("os.arch", arch);
            withProperties(spoof, new Runnable() {
                @Override
                public void run() {
                    assertEquals("arch mapping decides support on Windows: " + arch,
                            expected, WinConsoleNativeJni.isSupported());
                }
            });
        }
    }

    @Test
    public void testNativeCallFailsWithGuardErrorOffWindows() {
        Map<String, String> spoof = new HashMap<String, String>();
        spoof.put("os.name", "Linux");
        withProperties(spoof, new Runnable() {
            @Override
            public void run() {
                try {
                    WinConsoleNativeJni.ensureLoaded();
                    fail("expected UnsatisfiedLinkError from the OS guard");
                } catch (UnsatisfiedLinkError e) {
                    assertTrue("guard must fire before any loader use, got: " + e.getMessage(),
                            e.getMessage() != null && e.getMessage().contains("not Windows"));
                }
            }
        });
    }

    @Test
    public void testMissingBinaryForArchFailsBeforeLoading() {
        Map<String, String> spoof = new HashMap<String, String>();
        spoof.put("os.name", "Windows 11");
        spoof.put("os.arch", "aarch64");
        withProperties(spoof, new Runnable() {
            @Override
            public void run() {
                try {
                    WinConsoleNativeJni.ensureLoaded();
                    fail("expected UnsatisfiedLinkError for the missing binary");
                } catch (UnsatisfiedLinkError e) {
                    assertTrue("arch gate must fire before any loader use, got: " + e.getMessage(),
                            e.getMessage() != null && e.getMessage().contains("aarch64"));
                }
            }
        });
    }

    @Test
    public void testFacadeEntryPointsEnforceLoadingGate() {
        Assume.assumeFalse("base facade is shadowed by the FFM overlay on 22+",
                isJava22OrLater());
        try {
            WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
            fail("expected UnsatisfiedLinkError from the OS guard");
        } catch (UnsatisfiedLinkError e) {
            assertTrue("facade entry points must run the guard first, got: " + e.getMessage(),
                    e.getMessage() != null && e.getMessage().contains("not Windows"));
        }
    }

    /**
     * Whether the runtime is Java 22 or later (release-8-safe: no
     * {@code Runtime.version()} which needs Java 9+). Only below 22 does
     * the base JNI facade serve calls; on 22+ the FFM overlay shadows it.
     */
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
    public void testClassTouchNeverLoads() throws Exception {
        Class.forName("org.aesh.terminal.tty.impl.WinConsoleNativeJni", true,
                WinConsoleNativeJniTest.class.getClassLoader());
    }
}
