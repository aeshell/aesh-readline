package org.aesh.terminal.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Test;

/**
 * The environment-only path must not enter the optional discovery modules.
 * An isolated loader keeps their previous initialization from hiding a call.
 * This guards the current module split, not arbitrary future JDK I/O calls.
 */
public class EnvironmentOnlyDetectionTest {

    private static final String PACKAGE = "org.aesh.terminal.detect.";

    @Test
    public void testLinuxEnvironmentAndPassiveReads() throws Exception {
        assertEnvironmentOnly("Linux");
    }

    @Test
    public void testMacEnvironmentAndPassiveReads() throws Exception {
        assertEnvironmentOnly("Mac OS X");
    }

    @Test
    public void testWindowsEnvironmentAndPassiveReads() throws Exception {
        assertEnvironmentOnly("Windows 11");
    }

    @Test
    public void testActiveDiscoveryTripsTheSameGuard() throws Exception {
        assumeJvm();
        try (DiscoveryGuard loader = new DiscoveryGuard()) {
            Runnable probe = loader.probe("Linux", true);
            try {
                probe.run();
                fail("active discovery must reach the forbidden-module guard");
            } catch (AssertionError expected) {
                assertEquals("optional discovery module requested: " + PACKAGE + "TerminalColorQuery",
                        expected.getMessage());
            }
            assertEquals(1, loader.optionalLoads);
        }
    }

    private static void assertEnvironmentOnly(String osName) throws Exception {
        assumeJvm();
        try (DiscoveryGuard loader = new DiscoveryGuard()) {
            Runnable probe = loader.probe(osName, false);
            assertSame("the check itself must run in the isolated loader", loader, probe.getClass().getClassLoader());
            probe.run();
            assertEquals("no optional module may be requested, even if its error was caught", 0, loader.optionalLoads);
            assertTrue("the real capability facade must have run", loader.loaded.contains(PACKAGE + "TerminalCapabilities"));
            assertTrue("the real environment detector must have run", loader.loaded.contains(PACKAGE + "TerminalDetector"));
        }
    }

    private static void assumeJvm() {
        Assume.assumeTrue("isolated class-loading coverage runs in the JVM suites, not the closed-world native image",
                System.getProperty("org.graalvm.nativeimage.imagecode") == null);
    }

    private static final class DiscoveryGuard extends URLClassLoader {
        private final Set<String> allowed = new HashSet<>(Arrays.asList(
                PACKAGE + "TerminalCapabilities", PACKAGE + "TerminalDetector", PACKAGE + "TerminalTheme",
                PACKAGE + "ImageProtocol", PACKAGE + "ModeSupport",
                PACKAGE + "TerminalSubscription", PACKAGE + "TerminalSnapshot",
                ReadOnlyProbe.class.getName()));
        private final Set<String> loaded = new HashSet<>();
        private int optionalLoads;

        DiscoveryGuard() {
            super(new URL[] {
                    EnvironmentOnlyDetectionTest.class.getProtectionDomain().getCodeSource().getLocation(),
                    TerminalCapabilities.class.getProtectionDomain().getCodeSource().getLocation()
            }, EnvironmentOnlyDetectionTest.class.getClassLoader());
        }

        private boolean isInnerOfAllowed(String name) {
            int dollar = name.indexOf('$');
            return dollar > 0 && allowed.contains(name.substring(0, dollar));
        }

        Runnable probe(String osName, boolean active) throws Exception {
            return (Runnable) loadClass(ReadOnlyProbe.class.getName()).getConstructor(String.class, boolean.class)
                    .newInstance(osName, active);
        }

        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!name.startsWith(PACKAGE)) {
                return super.loadClass(name, resolve);
            }
            if (!allowed.contains(name) && !isInnerOfAllowed(name)) {
                optionalLoads++;
                throw new AssertionError("optional discovery module requested: " + name);
            }
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                type = findClass(name);
                loaded.add(name);
            }
            if (resolve) {
                resolveClass(type);
            }
            return type;
        }
    }

    /** Direct calls avoid reflection resolving unused facade method types. */
    public static final class ReadOnlyProbe implements Runnable {
        private final String osName;
        private final boolean active;

        public ReadOnlyProbe(String osName, boolean active) {
            this.osName = osName;
            this.active = active;
        }

        @Override
        public void run() {
            Map<String, String> env = new HashMap<>();
            env.put("TERM", "xterm-256color");
            env.put("MSYSTEM", "MINGW64");
            env.put("PWD", "/c/work");
            env.put("CI", "true");
            TerminalDetector detector = new TerminalDetector(env, osName);
            if (!"xterm".equals(detector.terminalName) || !detector.colors256
                    || detector.theme != TerminalTheme.UNKNOWN) {
                throw new AssertionError("injected OS/launcher hints must remain environment-only");
            }
            if (active) {
                // No multiplexer marker: this must exercise discovery,
                // not pass through an environment-dependent skip.
                TerminalCapabilities.computeFull(detector);
                return;
            }

            TerminalCapabilities caps = TerminalCapabilities.detect();
            if (TerminalCapabilities.class.getClassLoader() != getClass().getClassLoader()) {
                throw new AssertionError("facade escaped to a previously initialized parent loader");
            }
            TerminalCapabilities shared = TerminalCapabilities.getInstance();
            if (shared != TerminalCapabilities.getInstance() || shared == caps) {
                throw new AssertionError("shared lookup must cache; explicit detection must stay independent");
            }
            TerminalCapabilities.setInstance(caps);
            if (TerminalCapabilities.getInstance() != caps) {
                throw new AssertionError("supplied instance must be consumed without an upgrade");
            }
            // Read missing facts too: absence is not permission to probe.
            boolean trueColor = caps.supportsTrueColor();
            boolean colors256 = caps.supports256Colors();
            boolean color = caps.supportsColor();
            if ((trueColor && !colors256) || (colors256 && !color)
                    || caps.terminalName() == null || caps.theme() == null || caps.imageProtocol() == null
                    || !caps.toString().contains("TerminalCapabilities")) {
                throw new AssertionError("passive environment facts must remain consistent");
            }
            if (caps.foregroundRGB() != null || caps.backgroundRGB() != null || !caps.paletteColors().isEmpty()
                    || caps.paletteColor(0) != null || caps.synchronizedOutputSupport() != null
                    || caps.graphemeClusterSupport() != null || caps.nativeGraphemeClustering() != null) {
                throw new AssertionError("environment-only detection must not invent queried facts");
            }
            try {
                if (!caps.awaitColors(0, TimeUnit.NANOSECONDS)) {
                    throw new AssertionError("environment-only result must not have pending discovery");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
