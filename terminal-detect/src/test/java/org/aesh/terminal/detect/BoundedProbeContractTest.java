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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Deadline and permission contract for bounded terminal probing.
 * A legacy transport is declined without I/O; an expired budget opens
 * nothing; contention on the raw-mode lock declines instead of waiting
 * past the deadline; suppressed platform sources spawn nothing.
 */
public class BoundedProbeContractTest {

    private static final class BoundedFakeSession implements TerminalProbeSession {
        private final InputStream response;
        private final AtomicBoolean closed = new AtomicBoolean();

        BoundedFakeSession(InputStream response) {
            this.response = response;
        }

        @Override
        public void write(byte[] data) {
        }

        @Override
        public InputStream input() {
            return response;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    private static final class BoundedFakeTransport implements BoundedProbeTransport {
        final AtomicInteger opens = new AtomicInteger();
        final BoundedFakeSession session;
        final AtomicBoolean available = new AtomicBoolean(true);

        BoundedFakeTransport(InputStream response) {
            this.session = new BoundedFakeSession(response);
        }

        @Override
        public boolean isAvailable() {
            return available.get();
        }

        @Override
        public TerminalProbeSession open() {
            opens.incrementAndGet();
            return session;
        }

        @Override
        public TerminalProbeSession open(ProbeDeadline deadline) throws IOException {
            // Deliberately no expiry check here: the bounded query path
            // must decline before opening. This fake is the adversary that
            // would open past a deadline if the caller failed to check.
            opens.incrementAndGet();
            return session;
        }
    }

    private static final class LegacyTransport implements TerminalProbeTransport {
        final AtomicInteger opens = new AtomicInteger();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TerminalProbeSession open() {
            opens.incrementAndGet();
            return new BoundedFakeSession(new ByteArrayInputStream(new byte[0]));
        }
    }

    private static byte[] colorResponse() {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[?2026;1$y");
        sb.append("\033[?2027;0$y");
        sb.append("\033[?63;1;2;4c");
        sb.append("\033]10;rgb:ffff/ffff/ffff\007");
        sb.append("\033]11;rgb:0000/0000/0000\007");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    @Test
    public void testExpiredDeadlineDeclinesWithoutOpen() {
        BoundedFakeTransport transport = new BoundedFakeTransport(
                new ByteArrayInputStream(colorResponse()));
        assertNull(TerminalColorQuery.queryFullBounded(transport,
                ProbeDeadline.expired(), ProbePermissions.all()));
        assertEquals(0, transport.opens.get());
    }

    @Test
    public void testSuppressedTerminalQueryOpensNothing() {
        BoundedFakeTransport transport = new BoundedFakeTransport(
                new ByteArrayInputStream(colorResponse()));
        assertNull(TerminalColorQuery.queryFullBounded(transport,
                ProbeDeadline.after(5, TimeUnit.SECONDS), ProbePermissions.none()));
        assertEquals(0, transport.opens.get());
    }

    @Test
    public void testLegacyCustomTransportDeclinedWithoutOpen() {
        LegacyTransport legacy = new LegacyTransport();
        TerminalProbeTransport saved = null;
        try {
            // Read the current custom transport reflectively is unnecessary:
            // the bounded selection only consults the injected value.
            TerminalColorQuery.setTransport(legacy);
            assertNull(TerminalColorQuery.queryFirstAvailableBounded(
                    ProbeDeadline.after(5, TimeUnit.SECONDS), ProbePermissions.all()));
            assertEquals("legacy transport must never open on the bounded path",
                    0, legacy.opens.get());
        } finally {
            TerminalColorQuery.setTransport(saved);
        }
    }

    @Test
    public void testIncompleteReplyAbortsAtDeadlineAndCloses() {
        final AtomicInteger reads = new AtomicInteger();
        InputStream trickle = new InputStream() {
            @Override
            public int read() {
                byte[] one = new byte[1];
                int n = read(one, 0, 1);
                return n < 0 ? -1 : one[0] & 0xff;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                reads.incrementAndGet();
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
                b[off] = (byte) 'x';
                return 1;
            }
        };
        BoundedFakeTransport transport = new BoundedFakeTransport(trickle);
        long start = System.nanoTime();
        assertNull(TerminalColorQuery.queryFullBounded(transport,
                ProbeDeadline.after(200, TimeUnit.MILLISECONDS), ProbePermissions.all()));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue("bounded read must stop near the deadline, took " + elapsedMs + "ms",
                elapsedMs < 5000);
        assertTrue("session must close after a deadline abort", transport.session.closed.get());
        assertEquals(1, transport.opens.get());
        assertTrue(reads.get() > 0);
    }

    @Test
    public void testLockContentionDeclinesWithinDeadline() throws Exception {
        final CountDownLatch readEntered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        TerminalProbeTransport holder = new TerminalProbeTransport() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public TerminalProbeSession open() {
                return new TerminalProbeSession() {
                    @Override
                    public void write(byte[] data) {
                    }

                    @Override
                    public InputStream input() {
                        return new InputStream() {
                            @Override
                            public int read() throws IOException {
                                byte[] one = new byte[1];
                                int n = read(one, 0, 1);
                                return n < 0 ? -1 : one[0] & 0xff;
                            }

                            @Override
                            public int read(byte[] b, int off, int len) throws IOException {
                                readEntered.countDown();
                                try {
                                    if (!release.await(30, TimeUnit.SECONDS)) {
                                        throw new IOException("holder release timed out");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new RuntimeException(e);
                                }
                                return -1;
                            }
                        };
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        };
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread legacy = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    TerminalColorQuery.query(holder);
                } catch (Throwable e) {
                    failure.set(e);
                }
            }
        }, "bounded-contract-holder");
        legacy.setDaemon(true);
        BoundedFakeTransport bounded = new BoundedFakeTransport(
                new ByteArrayInputStream(colorResponse()));
        try {
            legacy.start();
            assertTrue("holder must enter its gated read",
                    readEntered.await(5, TimeUnit.SECONDS));
            long start = System.nanoTime();
            assertNull(TerminalColorQuery.queryFullBounded(bounded,
                    ProbeDeadline.after(200, TimeUnit.MILLISECONDS), ProbePermissions.all()));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue("contention must decline near the deadline, took " + elapsedMs + "ms",
                    elapsedMs < 5000);
            assertEquals("contended round must not open a second session",
                    0, bounded.opens.get());
        } finally {
            release.countDown();
            legacy.join(10000);
        }
        assertTrue("holder must finish after release", !legacy.isAlive());
        if (failure.get() != null) {
            fail("holder failed: " + failure.get());
        }
    }

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    @Test
    public void testSuppressedPlatformSpawnsNothing() {
        Map<String, String> env = new HashMap<>();
        env.put("TERM", "xterm");
        final List<String> commands = new ArrayList<>();
        ProcessRunner.setInstalled(new ProcessRunner.RunnerTransport() {
            @Override
            public ProcessRunner.Result run(ProcessBuilder starter, long timeoutMs) {
                commands.add(String.join(" ", starter.command()));
                return new ProcessRunner.Result(1, false, new byte[0]);
            }
        });
        try {
            TerminalDetector detector = new TerminalDetector(env, "Windows 11");
            assertEquals(TerminalTheme.UNKNOWN,
                    detector.detectIdeOrPlatformTheme(ProbeDeadline.expired(), ProbePermissions.all()));
            assertEquals(TerminalTheme.UNKNOWN,
                    detector.detectIdeOrPlatformTheme(
                            ProbeDeadline.after(5, TimeUnit.SECONDS), ProbePermissions.none()));
            assertTrue("suppressed fallback must not spawn: " + commands, commands.isEmpty());
        } finally {
            ProcessRunner.setInstalled(null);
        }
    }

    @Test
    public void testBoundedFallbackCountsOneRegistryQuery() {
        Map<String, String> env = new HashMap<>();
        env.put("TERM", "xterm");
        final List<String> commands = new ArrayList<>();
        final List<Long> budgets = new ArrayList<>();
        ProcessRunner.setInstalled(new ProcessRunner.RunnerTransport() {
            @Override
            public ProcessRunner.Result run(ProcessBuilder starter, long timeoutMs) {
                commands.add(String.join(" ", starter.command()));
                budgets.add(timeoutMs);
                return new ProcessRunner.Result(1, false, new byte[0]);
            }
        });
        try {
            assertEquals(TerminalTheme.UNKNOWN,
                    new TerminalDetector(env, "Windows 11").detectIdeOrPlatformTheme(
                            ProbeDeadline.after(5, TimeUnit.SECONDS), ProbePermissions.all()));
            assertEquals(1, commands.size());
            assertTrue(commands.get(0).endsWith("/v AppsUseLightTheme"));
            assertTrue(budgets.get(0) > 0);
        } finally {
            ProcessRunner.setInstalled(null);
        }
    }

    @Test
    public void testBoundedIdeFallbackResolvesFixture() throws Exception {
        File stateDir = new File(tmp.newFolder("localappdata"), "Microsoft/Windows Terminal");
        assertTrue(stateDir.mkdirs());
        write(new File(stateDir, "settings.json"), "{\"colorScheme\": \"Campbell\"}");
        Map<String, String> env = new HashMap<>();
        env.put("WT_SESSION", "some-session-id");
        env.put("LOCALAPPDATA", new File(tmp.getRoot(), "localappdata").getAbsolutePath());
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", tmp.newFolder("home").getAbsolutePath());
        try {
            TerminalDetector detector = new TerminalDetector(env, "Windows 11");
            assertEquals(TerminalTheme.DARK, detector.detectIdeOrPlatformTheme(
                    ProbeDeadline.after(5, TimeUnit.SECONDS), ProbePermissions.all()));
            final List<String> commands = new ArrayList<>();
            ProcessRunner.setInstalled(new ProcessRunner.RunnerTransport() {
                @Override
                public ProcessRunner.Result run(ProcessBuilder starter, long timeoutMs) {
                    commands.add(String.join(" ", starter.command()));
                    return new ProcessRunner.Result(1, false, new byte[0]);
                }
            });
            try {
                assertEquals("IDE suppression must fall through to the platform query",
                        TerminalTheme.UNKNOWN, detector.detectIdeOrPlatformTheme(
                                ProbeDeadline.after(5, TimeUnit.SECONDS),
                                ProbePermissions.of(true, false, true)));
                assertEquals(1, commands.size());
            } finally {
                ProcessRunner.setInstalled(null);
            }
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }
}
