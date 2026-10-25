package org.aesh.terminal.detect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.Assume;
import org.junit.Test;

public class TerminalDetectionNoIoTest {

    @Test
    public void testEverySideEffectSpyActuallyRuns() throws Exception {
        assumeJvm();
        String[][] operations = {
                { "filesystem", "filesystem" }, { "process", "process" }, { "native", "native" },
                { "terminal", "terminal" }, { "stream-read", "terminal" }, { "stream-write", "terminal" },
                { "worker", "worker" }, { "wait", "wait" }, { "shared", "shared" },
                { "environment", "environment" }, { "properties", "properties" }
        };
        for (String[] operation : operations) {
            String kind = operation[1];
            try (DetectionSideEffectGuard loader = new DetectionSideEffectGuard(Probe.class)) {
                Runnable probe = loader.probe(operation[0]);
                DetectionSideEffectGuard.Effects effects = DetectionSideEffectGuard.Effects.begin(false, false);
                try {
                    probe.run();
                    fail("spy did not reject " + kind);
                } catch (AssertionError expected) {
                    assertEquals("forbidden " + kind, expected.getMessage());
                    assertEquals(Collections.singletonList(kind), effects.violations);
                } finally {
                    DetectionSideEffectGuard.Effects.end();
                }
            }
        }
    }

    @Test
    public void testScopedFactoriesAndReadsStayWithinTheirBudget() throws Exception {
        assumeJvm();
        for (String scenario : new String[] { "read-process", "read-map", "read-map-windows", "read-managed",
                "read-unknown" }) {
            try (DetectionSideEffectGuard loader = new DetectionSideEffectGuard(Probe.class)) {
                Runnable probe = loader.probe(scenario);
                assertSame(loader, probe.getClass().getClassLoader());
                DetectionSideEffectGuard.Effects effects = DetectionSideEffectGuard.Effects.begin(
                        scenario.equals("read-process"), scenario.equals("read-process"));
                try {
                    probe.run();
                } finally {
                    DetectionSideEffectGuard.Effects.end();
                }
                assertEquals("forbidden work during " + scenario, Collections.emptyList(), effects.violations);
                assertTrue("the real owner must be instrumented",
                        loader.instrumented.contains(TerminalDetection.class.getName()));
                assertTrue("the real facade must be instrumented",
                        loader.instrumented.contains(TerminalCapabilities.class.getName()));
            }
        }
    }

    @Test
    public void testCaughtViolationsCannotLookLikeZeroWork() throws Exception {
        assumeJvm();
        try (DetectionSideEffectGuard loader = new DetectionSideEffectGuard(Probe.class)) {
            Runnable probe = loader.probe("caught-filesystem");
            DetectionSideEffectGuard.Effects effects = DetectionSideEffectGuard.Effects.begin(false, false);
            try {
                probe.run();
            } finally {
                DetectionSideEffectGuard.Effects.end();
            }
            assertEquals(Collections.singletonList("filesystem"), effects.violations);
        }
    }

    private static void assumeJvm() {
        Assume.assumeTrue("call-site instrumentation runs in JVM tests, not the closed-world native image",
                System.getProperty("org.graalvm.nativeimage.imagecode") == null);
    }

    /** Canary operations must be trapped before reaching their real effect. */
    public static final class Probe implements Runnable {
        private final String scenario;
        private final Map<String, String> environment = new HashMap<>();
        private final TerminalCapabilities supplied;

        public Probe(String scenario) {
            this.scenario = scenario;
            environment.put("TERM", "xterm-256color");
            environment.put("MSYSTEM", "MINGW64");
            environment.put("PWD", "/c/work");
            environment.put("CI", "true");
            if (scenario.equals("read-map-windows")) {
                environment.put("WT_SESSION", "inherited-session");
                environment.put("WT_PROFILE_ID", "inherited-profile");
            }
            // Preparing an already-owned result is outside consumption.
            // Other scenarios retain cold facade/owner initialization.
            supplied = scenario.equals("read-managed") ? TerminalCapabilities.detect() : null;
        }

        @Override
        public void run() {
            try {
                switch (scenario) {
                    case "filesystem":
                        new File("no-io-canary").exists();
                        break;
                    case "caught-filesystem":
                        try {
                            new File("no-io-canary").exists();
                        } catch (AssertionError expected) {
                            if (!"forbidden filesystem".equals(expected.getMessage())) {
                                throw expected;
                            }
                        }
                        break;
                    case "process":
                        new ProcessBuilder("no-io-canary-must-not-start").start();
                        break;
                    case "native":
                        System.loadLibrary("no_io_canary_must_not_load");
                        break;
                    case "terminal":
                        TerminalProbeTransport transport = new TerminalProbeTransport() {
                            @Override
                            public boolean isAvailable() {
                                throw new AssertionError("transport spy was bypassed");
                            }

                            @Override
                            public TerminalProbeSession open() {
                                throw new AssertionError("transport must not open");
                            }
                        };
                        transport.isAvailable();
                        break;
                    case "stream-read":
                        InputStream input = new ByteArrayInputStream(new byte[] { 1 });
                        input.read();
                        break;
                    case "stream-write":
                        OutputStream output = new ByteArrayOutputStream();
                        output.write(1);
                        break;
                    case "worker":
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                            }
                        }).start();
                        break;
                    case "wait":
                        new CountDownLatch(1).await(0, TimeUnit.NANOSECONDS);
                        break;
                    case "shared":
                        TerminalCapabilities.getInstance();
                        break;
                    case "environment":
                        System.getenv("TERM");
                        break;
                    case "properties":
                        System.getProperty("os.name");
                        break;
                    default:
                        readScopedResult();
                }
            } catch (IOException | InterruptedException e) {
                throw new AssertionError("side-effect spy was bypassed", e);
            }
        }

        private void readScopedResult() {
            TerminalDetection detection;
            switch (scenario) {
                case "read-process":
                    detection = TerminalDetection.environmentOnly();
                    break;
                case "read-map":
                case "read-map-windows":
                    detection = TerminalDetection.environmentOnly(environment);
                    break;
                case "read-managed":
                    detection = TerminalDetection.managed(supplied);
                    break;
                case "read-unknown":
                    detection = TerminalDetection.managed(null);
                    break;
                default:
                    throw new AssertionError("unknown scenario " + scenario);
            }
            if (TerminalDetection.class.getClassLoader() != getClass().getClassLoader()) {
                throw new AssertionError("owner escaped to parent loader");
            }
            TerminalCapabilities caps = detection.capabilities();
            if (caps == null || caps != detection.capabilities()) {
                throw new AssertionError("owner must retain one result");
            }
            if (scenario.equals("read-managed") && caps != supplied) {
                throw new AssertionError("managed owner replaced its result");
            }
            if (scenario.equals("read-map") && !caps.terminalName().equals("xterm")) {
                throw new AssertionError("explicit environment was skipped");
            }
            if (scenario.equals("read-map-windows") && !caps.terminalName().equals("windows-terminal")) {
                throw new AssertionError("inherited terminal hints were skipped");
            }
            if (scenario.equals("read-unknown") && (!caps.terminalName().equals("unknown") || caps.supportsColor())) {
                throw new AssertionError("missing result borrowed environment facts");
            }
            caps.terminalName();
            caps.supportsColor();
            caps.supports256Colors();
            caps.supportsTrueColor();
            caps.theme();
            caps.imageProtocol();
            caps.foregroundRGB();
            caps.backgroundRGB();
            caps.paletteColors();
            caps.paletteColor(0);
            caps.synchronizedOutputSupport();
            caps.graphemeClusterSupport();
            caps.nativeGraphemeClustering();
            caps.toString();
        }
    }
}
