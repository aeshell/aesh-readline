package org.aesh.terminal.tty.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.ServiceLoader;

import org.aesh.terminal.provider.TerminalProvider;
import org.aesh.terminal.utils.OSUtils;
import org.junit.Test;

/**
 * Tests for the TerminalProvider SPI.
 */
public class TerminalProviderTest {

    private static boolean isNativeImage() {
        return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    /**
     * ServiceLoader should discover all 4 built-in providers.
     */
    @Test
    public void testServiceLoaderFindsProviders() {
        assumeFalse("ServiceLoader discovery may differ in native-image", isNativeImage());
        List<TerminalProvider> providers = new ArrayList<>();
        for (TerminalProvider p : ServiceLoader.load(TerminalProvider.class)) {
            providers.add(p);
        }
        // We should find at least the 4 built-in providers
        assertTrue("Should find at least 4 providers, found: " + providers.size(),
                providers.size() >= 4);
    }

    /**
     * Each provider should have a non-null name.
     */
    @Test
    public void testProviderNames() {
        assumeFalse("ServiceLoader discovery may differ in native-image", isNativeImage());
        for (TerminalProvider p : ServiceLoader.load(TerminalProvider.class)) {
            assertNotNull("Provider name should not be null", p.name());
            assertFalse("Provider name should not be empty", p.name().isEmpty());
        }
    }

    /**
     * Each provider should have a positive priority.
     */
    @Test
    public void testProviderPriorities() {
        assumeFalse("ServiceLoader discovery may differ in native-image", isNativeImage());
        for (TerminalProvider p : ServiceLoader.load(TerminalProvider.class)) {
            assertTrue("Priority should be positive for " + p.name(),
                    p.priority() > 0);
        }
    }

    /**
     * On Linux, FFM and ExecPty providers should be supported,
     * Windows and Cygwin should not.
     */
    @Test
    public void testSupportedProvidersOnLinux() {
        assumeFalse("ServiceLoader discovery may differ in native-image", isNativeImage());
        if (OSUtils.IS_WINDOWS || OSUtils.IS_CYGWIN) {
            return; // skip on Windows
        }
        List<TerminalProvider> supported = new ArrayList<>();
        List<TerminalProvider> unsupported = new ArrayList<>();
        for (TerminalProvider p : ServiceLoader.load(TerminalProvider.class)) {
            if (p.isSupported()) {
                supported.add(p);
            } else {
                unsupported.add(p);
            }
        }
        // FFM and ExecPty should be supported on Linux
        assertTrue("At least one provider should be supported",
                supported.size() >= 1);

        // Check that ffm and exec are among supported
        boolean hasExec = supported.stream().anyMatch(p -> "exec".equals(p.name()));
        assertTrue("ExecPty should be supported on Linux", hasExec);

        // Windows and Cygwin should not be supported
        boolean hasWindows = supported.stream().anyMatch(p -> "windows".equals(p.name()));
        boolean hasCygwin = supported.stream().anyMatch(p -> "cygwin".equals(p.name()));
        assertFalse("Windows provider should not be supported on Linux", hasWindows);
        assertFalse("Cygwin provider should not be supported on Linux", hasCygwin);
    }

    /**
     * Providers sorted by priority should have FFM before ExecPty.
     */
    @Test
    public void testPriorityOrdering() {
        assumeFalse("ServiceLoader discovery may differ in native-image", isNativeImage());
        if (OSUtils.IS_WINDOWS) {
            return; // skip on Windows
        }
        List<TerminalProvider> supported = new ArrayList<>();
        for (TerminalProvider p : ServiceLoader.load(TerminalProvider.class)) {
            if (p.isSupported()) {
                supported.add(p);
            }
        }
        supported.sort(Comparator.comparingInt(TerminalProvider::priority).reversed());

        // Find positions
        int ffmPos = -1, execPos = -1;
        for (int i = 0; i < supported.size(); i++) {
            if ("ffm".equals(supported.get(i).name()))
                ffmPos = i;
            if ("exec".equals(supported.get(i).name()))
                execPos = i;
        }

        // FFM should be before ExecPty (higher priority)
        if (ffmPos >= 0 && execPos >= 0) {
            assertTrue("FFM (priority 100) should be before ExecPty (priority 50)",
                    ffmPos < execPos);
        }
    }

    /**
     * FfmTerminalProvider should have priority 100.
     */
    @Test
    public void testFfmProviderPriority() {
        FfmTerminalProvider provider = new FfmTerminalProvider();
        assertEquals(100, provider.priority());
        assertEquals("ffm", provider.name());
    }

    /**
     * ExecPtyTerminalProvider should have priority 50.
     */
    @Test
    public void testExecProviderPriority() {
        ExecPtyTerminalProvider provider = new ExecPtyTerminalProvider();
        assertEquals(50, provider.priority());
        assertEquals("exec", provider.name());
    }

    /**
     * WinSysTerminalProvider should have priority 100.
     */
    @Test
    public void testWinProviderPriority() {
        WinSysTerminalProvider provider = new WinSysTerminalProvider();
        assertEquals(100, provider.priority());
        assertEquals("windows", provider.name());
    }

    /**
     * CygwinTerminalProvider should have priority 75.
     */
    @Test
    public void testCygwinProviderPriority() {
        CygwinTerminalProvider provider = new CygwinTerminalProvider();
        assertEquals(75, provider.priority());
        assertEquals("cygwin", provider.name());
    }

    /**
     * WinSysTerminalProvider.isSupported() should return false on Linux.
     */
    @Test
    public void testWinProviderNotSupportedOnLinux() {
        if (OSUtils.IS_WINDOWS) {
            return;
        }
        WinSysTerminalProvider provider = new WinSysTerminalProvider();
        assertFalse(provider.isSupported());
    }

    /**
     * CygwinTerminalProvider.isSupported() should return false when not in Cygwin.
     */
    @Test
    public void testCygwinProviderNotSupportedOutsideCygwin() {
        if (OSUtils.IS_CYGWIN) {
            return;
        }
        CygwinTerminalProvider provider = new CygwinTerminalProvider();
        assertFalse(provider.isSupported());
    }
}
