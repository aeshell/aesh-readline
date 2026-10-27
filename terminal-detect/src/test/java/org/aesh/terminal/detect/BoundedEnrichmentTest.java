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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Assume;
import org.junit.Test;

/**
 * Public bounded enrichment factory over the deadline contract.
 * Every result is independent: the factory never publishes, joins, or
 * waits for shared discovery. Decline and timeout degrade to
 * environment hints, never to null or a partial probe.
 */
public class BoundedEnrichmentTest {

    private static byte[] canned() {
        StringBuilder sb = new StringBuilder();
        sb.append("\033[?2026;1$y");
        sb.append("\033[?2027;1$y");
        sb.append("\033[?63;1;2;4c");
        sb.append("\033]10;rgb:ffff/ffff/ffff\007");
        sb.append("\033]11;rgb:0000/0000/0000\007");
        return sb.toString().getBytes(StandardCharsets.US_ASCII);
    }

    private static Map<String, String> xtermEnv() {
        Map<String, String> env = new HashMap<>();
        env.put("TERM", "xterm-256color");
        return env;
    }

    private static final class DualTransport implements BoundedProbeTransport {
        final AtomicInteger legacyOpens = new AtomicInteger();
        final AtomicInteger boundedOpens = new AtomicInteger();
        final AtomicInteger closes = new AtomicInteger();
        final byte[] reply;

        DualTransport(byte[] reply) {
            this.reply = reply.clone();
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TerminalProbeSession open() {
            legacyOpens.incrementAndGet();
            return session(new ByteArrayInputStream(reply));
        }

        @Override
        public TerminalProbeSession open(ProbeDeadline deadline) throws IOException {
            boundedOpens.incrementAndGet();
            return session(new ByteArrayInputStream(reply));
        }

        private TerminalProbeSession session(final InputStream in) {
            return new TerminalProbeSession() {
                @Override
                public void write(byte[] data) {
                }

                @Override
                public InputStream input() {
                    return in;
                }

                @Override
                public void close() {
                    closes.incrementAndGet();
                }
            };
        }
    }

    private static TerminalDetector detector(Map<String, String> env, String osName) {
        return new TerminalDetector(env, osName);
    }

    @Test
    public void testBoundedQueryMeasuresWhenPermitted() {
        DualTransport transport = new DualTransport(canned());
        TerminalColorQuery.setTransport(transport);
        try {
            TerminalCapabilities caps = TerminalCapabilities.computeBounded(
                    detector(xtermEnv(), "Linux"),
                    ProbeDeadline.after(5, TimeUnit.SECONDS), ProbePermissions.all());
            assertArrayEquals(new int[] { 0, 0, 0 }, caps.backgroundRGB());
            assertArrayEquals(new int[] { 255, 255, 255 }, caps.foregroundRGB());
            assertEquals(ModeSupport.SUPPORTED, caps.synchronizedOutputSupport());
            assertEquals(1, transport.boundedOpens.get());
            assertEquals("bounded path must not use the legacy open",
                    0, transport.legacyOpens.get());
            assertEquals(1, transport.closes.get());
        } finally {
            TerminalColorQuery.setTransport(null);
        }
    }

    @Test
    public void testExpiredDeadlineKeepsHints() {
        DualTransport transport = new DualTransport(canned());
        TerminalColorQuery.setTransport(transport);
        try {
            TerminalCapabilities caps = TerminalCapabilities.computeBounded(
                    detector(xtermEnv(), "Linux"),
                    ProbeDeadline.expired(), ProbePermissions.all());
            assertNull(caps.backgroundRGB());
            assertEquals(TerminalTheme.UNKNOWN, caps.theme());
            assertEquals(0, transport.boundedOpens.get());
            assertEquals(0, transport.legacyOpens.get());
        } finally {
            TerminalColorQuery.setTransport(null);
        }
    }

    @Test
    public void testSuppressedQueryKeepsHints() {
        DualTransport transport = new DualTransport(canned());
        TerminalColorQuery.setTransport(transport);
        try {
            TerminalCapabilities caps = TerminalCapabilities.computeBounded(
                    detector(xtermEnv(), "Linux"),
                    ProbeDeadline.after(5, TimeUnit.SECONDS), ProbePermissions.none());
            assertNull(caps.backgroundRGB());
            assertEquals(0, transport.boundedOpens.get());
            assertEquals(0, transport.legacyOpens.get());
        } finally {
            TerminalColorQuery.setTransport(null);
        }
    }

    @Test
    public void testMultiplexerSkipsBoundedQuery() {
        Map<String, String> env = xtermEnv();
        env.put("TMUX", "/tmp/tmux-0,default,1234");
        DualTransport transport = new DualTransport(canned());
        TerminalColorQuery.setTransport(transport);
        try {
            TerminalCapabilities caps = TerminalCapabilities.computeBounded(
                    detector(env, "Linux"),
                    ProbeDeadline.after(5, TimeUnit.SECONDS), ProbePermissions.all());
            assertNull("live queries cannot see past a multiplexer",
                    caps.backgroundRGB());
            assertEquals(0, transport.boundedOpens.get());
            assertEquals(0, transport.legacyOpens.get());
        } finally {
            TerminalColorQuery.setTransport(null);
        }
    }

    @Test
    public void testBoundedResultIsIndependentOfSharedDefault() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities sentinel = TerminalCapabilities.detect();
        TerminalCapabilities.setInstance(sentinel);
        DualTransport transport = new DualTransport(canned());
        TerminalColorQuery.setTransport(transport);
        try {
            TerminalCapabilities caps = TerminalCapabilities.computeBounded(
                    detector(xtermEnv(), "Linux"),
                    ProbeDeadline.after(5, TimeUnit.SECONDS), ProbePermissions.all());
            assertTrue("bounded enrichment must produce its own result",
                    caps != sentinel);
            assertSame("bounded enrichment must not publish shared state",
                    sentinel, TerminalCapabilities.getInstance());
            assertArrayEquals(new int[] { 0, 0, 0 }, caps.backgroundRGB());
        } finally {
            TerminalColorQuery.setTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test(timeout = 45000)
    public void testBoundedDoesNotJoinBlockedSharedRound() throws Exception {
        Map<String, String> ambient = System.getenv();
        String tmux = ambient.get("TMUX");
        String term = ambient.get("TERM");
        Assume.assumeFalse("live query skipped in multiplexer",
                (tmux != null && !tmux.isEmpty())
                        || (term != null && (term.toLowerCase(java.util.Locale.ROOT).startsWith("tmux")
                                || term.toLowerCase(java.util.Locale.ROOT).startsWith("screen"))));
        final CountDownLatch readEntered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        TerminalProbeTransport gated = new TerminalProbeTransport() {
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
                                        throw new IOException("gated read release timed out");
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    throw new IOException("gated read interrupted", e);
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
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities.setProbeTransport(gated);
        TerminalCapabilities.invalidate();
        final AtomicReference<TerminalCapabilities> full = new AtomicReference<>();
        final AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread producer = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    full.set(TerminalCapabilities.detectFull());
                } catch (Throwable e) {
                    failure.set(e);
                }
            }
        }, "bounded-factory-shared-holder");
        producer.setDaemon(true);
        try {
            producer.start();
            assertTrue("shared round must block in its gated read",
                    readEntered.await(5, TimeUnit.SECONDS));
            long start = System.nanoTime();
            TerminalCapabilities caps = TerminalCapabilities.detectBounded(
                    ProbeDeadline.expired(), ProbePermissions.all());
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertTrue("bounded hints must not wait for shared I/O, took " + elapsedMs + "ms",
                    elapsedMs < 5000);
            assertNull(caps.backgroundRGB());
            assertTrue("bounded call must not join the blocked round", producer.isAlive());
        } finally {
            release.countDown();
            try {
                producer.join(30000);
            } finally {
                TerminalCapabilities.setProbeTransport(null);
                TerminalCapabilities.setInstance(saved);
            }
        }
        assertTrue("shared producer must finish after release", !producer.isAlive());
        if (failure.get() != null) {
            fail("shared producer failed: " + failure.get());
        }
        assertTrue(full.get() != null);
    }

    @Test
    public void testBoundedPlatformBudgetPassedThrough() {
        final List<String> commands = new ArrayList<>();
        final List<Long> budgets = new ArrayList<>();
        // Decline the live terminal query outright: its result depends on
        // whether the fork owns a live controlling terminal, while this test
        // covers only the platform fallback's share of the budget. A custom
        // legacy transport is declined without consulting it.
        TerminalCapabilities.setProbeTransport(new TerminalProbeTransport() {
            @Override
            public boolean isAvailable() {
                throw new AssertionError("declined custom transport must not be consulted");
            }

            @Override
            public TerminalProbeSession open() throws IOException {
                throw new AssertionError("declined custom transport must not be opened");
            }
        });
        ProcessRunner.setInstalled(new ProcessRunner.RunnerTransport() {
            @Override
            public ProcessRunner.Result run(ProcessBuilder starter, long timeoutMs) {
                commands.add(String.join(" ", starter.command()));
                budgets.add(timeoutMs);
                return new ProcessRunner.Result(1, false, new byte[0]);
            }
        });
        try {
            TerminalCapabilities caps = TerminalCapabilities.computeBounded(
                    detector(xtermEnv(), "Windows 11"),
                    ProbeDeadline.after(5, TimeUnit.SECONDS), ProbePermissions.all());
            assertEquals(TerminalTheme.UNKNOWN, caps.theme());
            assertEquals("exactly the OS theme query may run: " + commands,
                    1, commands.size());
            assertTrue(commands.get(0).endsWith("/v AppsUseLightTheme"));
            assertTrue("subprocess must share the remaining budget, got " + budgets,
                    budgets.get(0) > 0 && budgets.get(0) <= 5000);
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            ProcessRunner.setInstalled(null);
        }
    }

    @Test
    public void testBoundedNullArgumentsThrow() {
        try {
            TerminalCapabilities.detectBounded((ProbeDeadline) null, ProbePermissions.all());
            fail("null deadline must fail fast");
        } catch (NullPointerException expected) {
        }
        try {
            TerminalCapabilities.detectBounded(
                    ProbeDeadline.after(5, TimeUnit.SECONDS), (ProbePermissions) null);
            fail("null permissions must fail fast");
        } catch (NullPointerException expected) {
        }
    }
}
