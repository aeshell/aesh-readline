package org.aesh.terminal.detect;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
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
    public void testDetectionOwnerCloseScopesSubscriptions() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        TerminalDetection firstOwner = TerminalDetection.managed(caps);
        TerminalDetection secondOwner = TerminalDetection.managed(caps);
        List<TerminalSnapshot> first = new ArrayList<>();
        List<TerminalSnapshot> second = new ArrayList<>();
        List<TerminalSnapshot> other = new ArrayList<>();
        TerminalSubscription one = firstOwner.subscribe(first::add);
        TerminalSubscription two = firstOwner.subscribe(second::add);
        TerminalSubscription three = secondOwner.subscribe(other::add);
        try {
            firstOwner.close();
            firstOwner.close();
            TerminalColorQuery measured = new TerminalColorQuery();
            measured.background = new int[] { 10, 10, 10 };
            caps.applyColorResult(measured);

            assertEquals("closing one owner closes its first subscriber", 1, first.size());
            assertEquals("closing one owner closes its second subscriber", 1, second.size());
            assertEquals("another owner of the same result is independent", 2, other.size());

            List<TerminalSnapshot> late = new ArrayList<>();
            TerminalSubscription closed = firstOwner.subscribe(late::add);
            caps.applyThemeChange(TerminalTheme.DARK);
            assertEquals("a closed owner accepts no new live subscription", 0, late.size());
            assertEquals(1, first.size());
            assertEquals(1, second.size());
            assertEquals(3, other.size());
            closed.close();
        } finally {
            one.close();
            two.close();
            three.close();
            firstOwner.close();
            secondOwner.close();
        }
    }

    @Test
    public void testOwnerCloseDuringRegistrationWaitsAndSuppressesReplay() throws Exception {
        final TerminalCapabilities caps = TerminalCapabilities.detect();
        final TerminalDetection owner = TerminalDetection.managed(caps);
        final List<TerminalSnapshot> received = new ArrayList<>();
        final CountDownLatch captured = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch closeReturned = new CountDownLatch(1);
        FutureTask<TerminalSubscription> registration = new FutureTask<TerminalSubscription>(
                new Callable<TerminalSubscription>() {
                    @Override
                    public TerminalSubscription call() {
                        return owner.subscribeWithReplayBarrierForTests(received::add, null,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        captured.countDown();
                                        try {
                                            if (!release.await(30, TimeUnit.SECONDS)) {
                                                throw new AssertionError("registration release timed out");
                                            }
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            throw new RuntimeException(e);
                                        }
                                    }
                                });
                    }
                });
        Thread registering = new Thread(registration, "detection-owner-registration-test");
        registering.setDaemon(true);
        Thread closer = new Thread(new Runnable() {
            @Override
            public void run() {
                owner.close();
                closeReturned.countDown();
            }
        }, "detection-owner-close-test");
        closer.setDaemon(true);
        TerminalSubscription subscription = null;
        try {
            registering.start();
            assertTrue(captured.await(30, TimeUnit.SECONDS));
            closer.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (closer.isAlive() && closer.getState() != Thread.State.WAITING
                    && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertTrue("owner close waits for registration", closer.isAlive());
            release.countDown();
            subscription = registration.get(30, TimeUnit.SECONDS);
            closer.join(30000);
            assertTrue(!closer.isAlive());
            assertEquals(0L, closeReturned.getCount());
            assertEquals("a concurrently closed owner suppresses replay", 0, received.size());

            caps.applyThemeChange(TerminalTheme.DARK);
            assertEquals(0, received.size());
        } finally {
            release.countDown();
            closer.join(30000);
            if (subscription != null) {
                subscription.close();
            }
            owner.close();
        }
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
