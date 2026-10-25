package org.aesh.terminal.detect;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class TerminalDetectionTest {

    @Test
    public void testManagedCapturesSuppliedInstance() {
        TerminalCapabilities supplied = TerminalCapabilities.detect();
        TerminalDetection detection = TerminalDetection.managed(supplied);
        assertSame(supplied, detection.capabilities());
        assertSame(supplied, detection.capabilities());
    }

    @Test
    public void testMissingManagedResultStaysUnknown() {
        TerminalCapabilities caps = TerminalDetection.managed(null).capabilities();
        assertNotNull(caps);
        assertEquals("unknown", caps.terminalName());
        assertEquals(TerminalTheme.UNKNOWN, caps.theme());
        assertEquals(ImageProtocol.NONE, caps.imageProtocol());
        assertFalse(caps.supportsColor());
        assertFalse(caps.supports256Colors());
        assertFalse(caps.supportsTrueColor());
        assertNull(caps.foregroundRGB());
        assertNull(caps.backgroundRGB());
        assertTrue(caps.paletteColors().isEmpty());
        assertNull(caps.synchronizedOutputSupport());
        assertNull(caps.graphemeClusterSupport());
        assertNull(caps.nativeGraphemeClustering());
    }

    @Test
    public void testEnvironmentHintsAreCapturedOnce() {
        Map<String, String> env = new HashMap<>();
        env.put("TERM", "xterm-256color");
        env.put("COLORTERM", "truecolor");
        env.put("COLORFGBG", "0;15");
        env.put("MSYSTEM", "MINGW64");
        env.put("PWD", "/c/work");
        env.put("CI", "true");
        TerminalDetection detection = TerminalDetection.environmentOnly(env);
        env.clear();

        TerminalCapabilities caps = detection.capabilities();
        assertEquals("xterm", caps.terminalName());
        assertEquals(TerminalTheme.LIGHT, caps.theme());
        assertTrue(caps.supportsTrueColor());
        assertEquals(ImageProtocol.NONE, caps.imageProtocol());
        assertNull(caps.backgroundRGB());
        assertTrue(caps.paletteColors().isEmpty());
        assertSame(caps, detection.capabilities());
    }

    @Test
    public void testProcessEnvironmentDoesNotReuseOrReplaceSharedDetection() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities shared = TerminalDetection.managed(null).capabilities();
        try {
            TerminalCapabilities.setInstance(shared);
            TerminalDetection detection = TerminalDetection.environmentOnly();
            assertNotSame(shared, detection.capabilities());
            assertSame(shared, TerminalCapabilities.getInstance());
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testManagedOwnersRemainIsolatedFromDefaultReplacement() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        Map<String, String> dark = new HashMap<>();
        dark.put("GHOSTTY_RESOURCES_DIR", "/fixture/ghostty");
        dark.put("COLORFGBG", "15;0");
        Map<String, String> light = new HashMap<>();
        light.put("TERM", "xterm-256color");
        light.put("COLORFGBG", "0;15");
        TerminalCapabilities first = TerminalDetection.environmentOnly(dark).capabilities();
        TerminalCapabilities second = TerminalDetection.environmentOnly(light).capabilities();
        try {
            TerminalCapabilities.setInstance(second);
            TerminalDetection owner = TerminalDetection.managed(first);
            TerminalDetection other = TerminalDetection.managed(second);
            TerminalDetection missing = TerminalDetection.managed(null);
            assertSame(second, TerminalCapabilities.getInstance());
            assertEquals("unknown", missing.capabilities().terminalName());

            TerminalCapabilities.setInstance(missing.capabilities());
            assertSame(first, owner.capabilities());
            assertSame(second, other.capabilities());
            assertEquals(TerminalTheme.DARK, owner.capabilities().theme());
            assertEquals(TerminalTheme.LIGHT, other.capabilities().theme());
            assertEquals(ImageProtocol.KITTY, owner.capabilities().imageProtocol());
            assertEquals(ImageProtocol.NONE, other.capabilities().imageProtocol());
            TerminalCapabilities.invalidate();
            assertSame(first, owner.capabilities());
            assertSame(second, other.capabilities());
        } finally {
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test(expected = NullPointerException.class)
    public void testExplicitEnvironmentMustNotBeNull() {
        TerminalDetection.environmentOnly(null);
    }

    @Test
    public void testScopedUseDoesNotEnterProcessOrProbeCollaborators() {
        final AtomicInteger calls = new AtomicInteger();
        TerminalCapabilities supplied = TerminalDetection.environmentOnly(
                java.util.Collections.singletonMap("TERM", "xterm-256color")).capabilities();
        ProcessRunner.setInstalled(new ProcessRunner.RunnerTransport() {
            @Override
            public ProcessRunner.Result run(ProcessBuilder starter, long timeoutMs) {
                calls.incrementAndGet();
                throw new AssertionError("passive scope requested a subprocess");
            }
        });
        TerminalCapabilities.setProbeTransport(new TerminalProbeTransport() {
            @Override
            public boolean isAvailable() {
                calls.incrementAndGet();
                throw new AssertionError("passive scope selected a probe transport");
            }

            @Override
            public TerminalProbeSession open() {
                calls.incrementAndGet();
                throw new AssertionError("passive scope opened a probe session");
            }
        });
        try {
            TerminalDetection[] owners = {
                    TerminalDetection.environmentOnly(), TerminalDetection.environmentOnly(new HashMap<String, String>()),
                    TerminalDetection.managed(supplied), TerminalDetection.managed(null)
            };
            for (TerminalDetection owner : owners) {
                TerminalCapabilities caps = owner.capabilities();
                caps.theme();
                caps.imageProtocol();
                caps.backgroundRGB();
                caps.foregroundRGB();
                caps.paletteColors();
                caps.synchronizedOutputSupport();
                caps.graphemeClusterSupport();
            }
            assertEquals("native/JVM portable collaborator budget", 0, calls.get());
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            ProcessRunner.setInstalled(null);
        }
    }
}
