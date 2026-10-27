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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.Assume;
import org.junit.Test;

/**
 * Scoped capability subscriptions (#380).
 * <p>
 * Subscribers share one discovery result and later appearance changes
 * without polling, displacing each other, or triggering new queries.
 * Deterministic: latches and barriers only, no timing sleeps.
 */
public class TerminalSubscriptionTest {

    private static List<TerminalSnapshot> received() {
        return new CopyOnWriteArrayList<TerminalSnapshot>();
    }

    private static Consumer<TerminalSnapshot> recordInto(final List<TerminalSnapshot> received) {
        return new Consumer<TerminalSnapshot>() {
            @Override
            public void accept(TerminalSnapshot snapshot) {
                received.add(snapshot);
            }
        };
    }

    @Test
    public void testSubscribeReplaysCurrentState() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        List<TerminalSnapshot> received = received();
        TerminalSubscription subscription = caps.subscribe(recordInto(received));
        try {
            assertEquals("registration replays current state", 1, received.size());
            TerminalSnapshot replay = received.get(0);
            assertEquals(0L, replay.revision());
            assertEquals(caps.theme(), replay.theme());
            assertNull(replay.foregroundRGB());
            assertNull(replay.backgroundRGB());
        } finally {
            subscription.close();
        }
    }

    @Test
    public void testAppliedResultPublishesOneSnapshotToAllSubscribers() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        List<TerminalSnapshot> first = received();
        List<TerminalSnapshot> second = received();
        TerminalSubscription one = caps.subscribe(recordInto(first));
        TerminalSubscription two = caps.subscribe(recordInto(second));
        try {
            TerminalColorQuery measured = new TerminalColorQuery();
            measured.foreground = new int[] { 255, 255, 255 };
            measured.background = new int[] { 10, 10, 10 };
            measured.mode2026 = ModeSupport.SUPPORTED;
            caps.applyColorResult(measured);

            assertEquals(2, first.size());
            assertEquals(2, second.size());
            TerminalSnapshot update = first.get(1);
            assertEquals("one revision per applied result", 1L, update.revision());
            assertEquals(TerminalTheme.DARK, update.theme());
            assertEquals(ModeSupport.SUPPORTED, update.mode2026Support());
            assertSame("state update is computed once, not once per consumer",
                    update, second.get(1));
        } finally {
            one.close();
            two.close();
        }
    }

    @Test
    public void testClosedSubscriptionReceivesNothing() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        List<TerminalSnapshot> received = received();
        TerminalSubscription subscription = caps.subscribe(recordInto(received));
        assertEquals(1, received.size());
        subscription.close();
        subscription.close();

        TerminalColorQuery measured = new TerminalColorQuery();
        measured.background = new int[] { 10, 10, 10 };
        caps.applyColorResult(measured);

        assertEquals("close is idempotent and stops delivery", 1, received.size());
    }

    @Test
    public void testThrowingListenerDoesNotSuppressOthers() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        final List<TerminalSnapshot> second = received();
        Consumer<TerminalSnapshot> throwing = new Consumer<TerminalSnapshot>() {
            @Override
            public void accept(TerminalSnapshot snapshot) {
                throw new RuntimeException("listener bug must not escape");
            }
        };
        TerminalSubscription one = caps.subscribe(throwing);
        TerminalSubscription two = caps.subscribe(recordInto(second));
        try {
            TerminalColorQuery measured = new TerminalColorQuery();
            measured.background = new int[] { 10, 10, 10 };
            caps.applyColorResult(measured);

            assertEquals("second subscriber still receives its replay plus update",
                    2, second.size());
            assertEquals(1L, second.get(1).revision());
        } finally {
            one.close();
            two.close();
        }
    }

    @Test
    public void testCloseDuringDispatchStopsLaterEvents() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        final List<TerminalSnapshot> received = received();
        final TerminalSubscription[] self = new TerminalSubscription[1];
        self[0] = caps.subscribe(new Consumer<TerminalSnapshot>() {
            @Override
            public void accept(TerminalSnapshot snapshot) {
                received.add(snapshot);
                if (snapshot.revision() == 1) {
                    self[0].close();
                }
            }
        });

        TerminalColorQuery first = new TerminalColorQuery();
        first.background = new int[] { 10, 10, 10 };
        caps.applyColorResult(first);
        TerminalColorQuery second = new TerminalColorQuery();
        second.background = new int[] { 20, 20, 20 };
        caps.applyColorResult(second);

        assertEquals("reentrant close takes effect for subsequent events",
                2, received.size());
    }

    @Test
    public void testThemeChangePublishesCoherentSnapshot() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities caps = TerminalCapabilities.detect();
        List<TerminalSnapshot> received = received();
        TerminalSubscription subscription = caps.subscribe(recordInto(received));
        try {
            TerminalCapabilities.setInstance(caps);
            TerminalCapabilities.onThemeChanged(TerminalTheme.DARK);

            assertEquals(2, received.size());
            TerminalSnapshot update = received.get(1);
            assertEquals(1L, update.revision());
            assertEquals(TerminalTheme.DARK, update.theme());
            assertNull("theme event discards stale RGB in the same revision",
                    update.backgroundRGB());
            assertNull(update.foregroundRGB());
        } finally {
            TerminalCapabilities.setInstance(saved);
            subscription.close();
        }
    }

    @Test
    public void testLateColorResultAfterThemeEventStaysStale() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities caps = TerminalCapabilities.detect();
        List<TerminalSnapshot> received = received();
        TerminalSubscription subscription = caps.subscribe(recordInto(received));
        try {
            TerminalCapabilities.setInstance(caps);
            TerminalCapabilities.onThemeChanged(TerminalTheme.DARK);

            TerminalColorQuery late = new TerminalColorQuery();
            late.foreground = new int[] { 255, 255, 255 };
            late.background = new int[] { 255, 255, 255 };
            caps.applyColorResult(late);

            TerminalSnapshot latest = received.get(received.size() - 1);
            assertEquals("a theme change is never revoked by a late reply",
                    TerminalTheme.DARK, latest.theme());
            assertNull("stale RGB must not reappear in later revisions",
                    latest.backgroundRGB());
        } finally {
            TerminalCapabilities.setInstance(saved);
            subscription.close();
        }
    }

    @Test
    public void testAsyncCompletionPublishesToSubscriber() throws Exception {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        final CountDownLatch completed = new CountDownLatch(1);
        final List<TerminalSnapshot> received = received();
        try {
            TerminalCapabilities.invalidate();
            TerminalCapabilities.setProbeTransport(silentProbeTransport());
            TerminalCapabilities caps = TerminalCapabilities.detectAsync();
            TerminalSubscription subscription = caps.subscribe(new Consumer<TerminalSnapshot>() {
                @Override
                public void accept(TerminalSnapshot snapshot) {
                    received.add(snapshot);
                    if (received.size() >= 2) {
                        completed.countDown();
                    }
                }
            });
            try {
                assertTrue("shared discovery must complete",
                        completed.await(30, TimeUnit.SECONDS));
                assertTrue("producer must finish before restoring globals",
                        caps.awaitColors(30, TimeUnit.SECONDS));
                assertTrue(received.size() >= 2);
                long previous = -1;
                for (TerminalSnapshot snapshot : received) {
                    assertTrue("revisions arrive in order",
                            snapshot.revision() > previous);
                    previous = snapshot.revision();
                }
                TerminalSnapshot latest = received.get(received.size() - 1);
                assertEquals(caps.theme(), latest.theme());
                assertArrayEquals(caps.backgroundRGB(), latest.backgroundRGB());
            } finally {
                subscription.close();
            }
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testReplacementIsolatesLateResult() throws Exception {
        Map<String, String> env = System.getenv();
        String tmux = env.get("TMUX");
        String term = env.get("TERM");
        Assume.assumeFalse("live query skipped in multiplexer",
                (tmux != null && !tmux.isEmpty())
                        || (term != null && (term.toLowerCase(java.util.Locale.ROOT).startsWith("tmux")
                                || term.toLowerCase(java.util.Locale.ROOT).startsWith("screen"))));
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        final CountDownLatch opened = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch oldCompleted = new CountDownLatch(1);
        final List<TerminalSnapshot> oldReceived = received();
        final List<TerminalSnapshot> newReceived = received();
        TerminalCapabilities.setProbeTransport(new TerminalProbeTransport() {
            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public TerminalProbeSession open() throws IOException {
                opened.countDown();
                try {
                    if (!release.await(30, TimeUnit.SECONDS)) {
                        throw new IOException("probe open release timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
                return new TerminalProbeSession() {
                    @Override
                    public void write(byte[] data) {
                    }

                    @Override
                    public InputStream input() {
                        return new ByteArrayInputStream(new byte[0]);
                    }

                    @Override
                    public void close() {
                    }
                };
            }
        });
        try {
            TerminalCapabilities.invalidate();
            TerminalCapabilities old = TerminalCapabilities.detectAsync();
            TerminalSubscription oldSubscription = old.subscribe(new Consumer<TerminalSnapshot>() {
                @Override
                public void accept(TerminalSnapshot snapshot) {
                    oldReceived.add(snapshot);
                    if (snapshot.revision() > 0) {
                        oldCompleted.countDown();
                    }
                }
            });
            try {
                assertTrue("worker must reach the gated open",
                        opened.await(30, TimeUnit.SECONDS));
                TerminalCapabilities replacement = TerminalCapabilities.detect();
                TerminalCapabilities.setInstance(replacement);
                TerminalSubscription newSubscription = replacement.subscribe(recordInto(newReceived));
                try {
                    release.countDown();
                    assertTrue("late completion must reach the retired owner's subscriber",
                            oldCompleted.await(30, TimeUnit.SECONDS));
                    assertTrue("retired producer must finish",
                            old.awaitColors(30, TimeUnit.SECONDS));
                    assertTrue("late result still completes its own round",
                            oldReceived.size() >= 2);
                    assertEquals("replacement keeps only its replay",
                            1, newReceived.size());
                    assertEquals(0L, newReceived.get(0).revision());
                } finally {
                    newSubscription.close();
                }
            } finally {
                oldSubscription.close();
            }
        } finally {
            TerminalCapabilities.setProbeTransport(null);
            TerminalCapabilities.setInstance(saved);
        }
    }

    @Test
    public void testZeroSubscriberMutationsStayUnpublished() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        for (int i = 0; i < 3; i++) {
            TerminalColorQuery measured = new TerminalColorQuery();
            measured.background = new int[] { 10, 10, 10 };
            caps.applyColorResult(measured);
        }
        List<TerminalSnapshot> received = received();
        TerminalSubscription subscription = caps.subscribe(recordInto(received));
        try {
            assertEquals("unobserved mutations bump no revision", 0L,
                    received.get(0).revision());
        } finally {
            subscription.close();
        }
    }

    @Test
    public void testExecutorDeliveryRunsOffPublisherThread() throws Exception {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        final List<Thread> deliveryThreads = new CopyOnWriteArrayList<Thread>();
        final CountDownLatch delivered = new CountDownLatch(2);
        final List<TerminalSnapshot> received = received();
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            TerminalSubscription subscription = caps.subscribe(new Consumer<TerminalSnapshot>() {
                @Override
                public void accept(TerminalSnapshot snapshot) {
                    deliveryThreads.add(Thread.currentThread());
                    received.add(snapshot);
                    delivered.countDown();
                }
            }, executor);
            try {
                TerminalColorQuery measured = new TerminalColorQuery();
                measured.background = new int[] { 10, 10, 10 };
                caps.applyColorResult(measured);

                assertTrue("executor delivers the update after the synchronous replay",
                        delivered.await(30, TimeUnit.SECONDS));
                assertEquals(2, received.size());
                assertSame("registration replays on the caller thread",
                        Thread.currentThread(), deliveryThreads.get(0));
                assertTrue("later delivery runs on the executor thread",
                        deliveryThreads.get(1) != Thread.currentThread());
                assertSame(deliveryThreads.get(0), Thread.currentThread());
            } finally {
                subscription.close();
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testBurstsCoalesceToNewest() throws Exception {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        final java.util.concurrent.atomic.AtomicInteger executions = new java.util.concurrent.atomic.AtomicInteger();
        final List<Runnable> queued = new CopyOnWriteArrayList<Runnable>();
        final List<TerminalSnapshot> received = received();
        java.util.concurrent.Executor gating = new java.util.concurrent.Executor() {
            @Override
            public void execute(Runnable command) {
                executions.incrementAndGet();
                queued.add(command);
            }
        };
        TerminalSubscription subscription = caps.subscribe(recordInto(received), gating);
        try {
            for (int shade = 1; shade <= 3; shade++) {
                TerminalColorQuery measured = new TerminalColorQuery();
                measured.background = new int[] { shade, shade, shade };
                caps.applyColorResult(measured);
            }
            assertEquals("one pending delivery per subscriber", 1, queued.size());
            queued.get(0).run();

            assertEquals("replay plus the newest coalesced state", 2, received.size());
            assertEquals(0L, received.get(0).revision());
            assertArrayEquals("coalescing never loses the newest state",
                    new int[] { 3, 3, 3 }, received.get(1).backgroundRGB());
            assertEquals("bursts stay bounded", 1, executions.get());
        } finally {
            subscription.close();
        }
    }

    @Test
    public void testScopedThemeChangeStaysOffGlobalDefault() {
        TerminalCapabilities saved = TerminalCapabilities.getInstance();
        TerminalCapabilities global = TerminalCapabilities.detect();
        TerminalCapabilities scoped = TerminalCapabilities.detect();
        List<TerminalSnapshot> globalReceived = received();
        List<TerminalSnapshot> scopedReceived = received();
        TerminalSubscription globalSubscription = global.subscribe(recordInto(globalReceived));
        TerminalSubscription scopedSubscription = scoped.subscribe(recordInto(scopedReceived));
        try {
            TerminalCapabilities.setInstance(global);
            scoped.applyThemeChange(TerminalTheme.LIGHT);

            assertEquals("scoped update is delivered once", 2, scopedReceived.size());
            assertEquals(TerminalTheme.LIGHT, scopedReceived.get(1).theme());
            assertEquals("global subscriber sees no remote event", 1, globalReceived.size());
            assertEquals("global result keeps its own revision", 0L,
                    globalReceived.get(0).revision());
        } finally {
            TerminalCapabilities.setInstance(saved);
            globalSubscription.close();
            scopedSubscription.close();
        }
    }

    @Test
    public void testMutationDuringReplayKeepsRevisionOrder() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        final List<TerminalSnapshot> received = received();
        TerminalSubscription subscription = caps.subscribe(new Consumer<TerminalSnapshot>() {
            @Override
            public void accept(TerminalSnapshot snapshot) {
                received.add(snapshot);
                if (snapshot.revision() == 0) {
                    TerminalColorQuery measured = new TerminalColorQuery();
                    measured.background = new int[] { 10, 10, 10 };
                    caps.applyColorResult(measured);
                }
            }
        });
        try {
            assertEquals(2, received.size());
            assertEquals(0L, received.get(0).revision());
            assertEquals(1L, received.get(1).revision());
        } finally {
            subscription.close();
        }
    }

    @Test
    public void testProducerBetweenCaptureAndReplayIsNotLost() throws Exception {
        final TerminalCapabilities caps = TerminalCapabilities.detect();
        final List<TerminalSnapshot> received = received();
        final List<Runnable> queued = new CopyOnWriteArrayList<Runnable>();
        final CountDownLatch captured = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.Executor gating = new java.util.concurrent.Executor() {
            @Override
            public void execute(Runnable command) {
                queued.add(command);
            }
        };
        FutureTask<TerminalSubscription> registration = new FutureTask<TerminalSubscription>(
                new Callable<TerminalSubscription>() {
                    @Override
                    public TerminalSubscription call() {
                        return caps.subscribeWithReplayBarrierForTests(recordInto(received), gating,
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
        Thread registering = new Thread(registration, "subscription-registration-test");
        registering.setDaemon(true);
        TerminalSubscription subscription = null;
        try {
            registering.start();
            assertTrue("registration must reach the replay barrier",
                    captured.await(30, TimeUnit.SECONDS));
            TerminalColorQuery measured = new TerminalColorQuery();
            measured.background = new int[] { 10, 10, 10 };
            caps.applyColorResult(measured);
            release.countDown();
            subscription = registration.get(30, TimeUnit.SECONDS);

            assertEquals("the synchronous replay is not suppressed", 1, received.size());
            assertEquals(0L, received.get(0).revision());
            assertEquals("the later revision remains queued", 1, queued.size());
            queued.get(0).run();
            assertEquals("the later revision is not overwritten by replay", 2, received.size());
            assertEquals(0L, received.get(0).revision());
            assertEquals(1L, received.get(1).revision());
        } finally {
            release.countDown();
            if (subscription != null) {
                subscription.close();
            }
        }
    }

    @Test
    public void testMutationDuringReplayUsesSuppliedExecutor() throws Exception {
        final TerminalCapabilities caps = TerminalCapabilities.detect();
        final List<TerminalSnapshot> received = received();
        final List<Thread> deliveryThreads = new CopyOnWriteArrayList<Thread>();
        final CountDownLatch later = new CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        final TerminalSubscription subscription = caps.subscribe(new Consumer<TerminalSnapshot>() {
            @Override
            public void accept(TerminalSnapshot snapshot) {
                received.add(snapshot);
                deliveryThreads.add(Thread.currentThread());
                if (snapshot.revision() == 0) {
                    caps.applyThemeChange(TerminalTheme.DARK);
                } else {
                    later.countDown();
                }
            }
        }, executor);
        try {
            assertTrue("the later event must leave the replay thread",
                    later.await(30, TimeUnit.SECONDS));
            assertEquals(2, received.size());
            assertSame(Thread.currentThread(), deliveryThreads.get(0));
            assertTrue("a post-replay event must use the supplied executor",
                    deliveryThreads.get(1) != Thread.currentThread());
            assertEquals(0L, received.get(0).revision());
            assertEquals(1L, received.get(1).revision());
        } finally {
            subscription.close();
            executor.shutdownNow();
        }
    }

    @Test
    public void testCloseWaitsForRunningCallback() throws Exception {
        final TerminalCapabilities caps = TerminalCapabilities.detect();
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);
        final CountDownLatch closeReturned = new CountDownLatch(1);
        java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        final TerminalSubscription subscription = caps.subscribe(new Consumer<TerminalSnapshot>() {
            @Override
            public void accept(TerminalSnapshot snapshot) {
                if (snapshot.revision() == 0) {
                    return;
                }
                entered.countDown();
                try {
                    if (!release.await(30, TimeUnit.SECONDS)) {
                        throw new AssertionError("callback release timed out");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            }
        }, executor);
        Thread closer = new Thread(new Runnable() {
            @Override
            public void run() {
                subscription.close();
                closeReturned.countDown();
            }
        }, "subscription-close-test");
        closer.setDaemon(true);
        try {
            TerminalColorQuery measured = new TerminalColorQuery();
            measured.background = new int[] { 10, 10, 10 };
            caps.applyColorResult(measured);
            assertTrue("callback must start before close",
                    entered.await(30, TimeUnit.SECONDS));
            closer.start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (closer.isAlive() && closer.getState() != Thread.State.WAITING
                    && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertTrue("close must wait for the selected callback", closer.isAlive());
            assertEquals("close has not returned while the callback is blocked",
                    1L, closeReturned.getCount());
            release.countDown();
            closer.join(30000);
            assertTrue("close must return after callback completion", !closer.isAlive());
            assertEquals(0L, finished.getCount());
            assertEquals(0L, closeReturned.getCount());
        } finally {
            release.countDown();
            closer.join(30000);
            subscription.close();
            executor.shutdownNow();
        }
    }

    @Test
    public void testCloseDropsQueuedDelivery() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        final List<Runnable> queued = new CopyOnWriteArrayList<Runnable>();
        List<TerminalSnapshot> received = received();
        java.util.concurrent.Executor gating = new java.util.concurrent.Executor() {
            @Override
            public void execute(Runnable command) {
                queued.add(command);
            }
        };
        TerminalSubscription subscription = caps.subscribe(recordInto(received), gating);
        TerminalColorQuery measured = new TerminalColorQuery();
        measured.background = new int[] { 10, 10, 10 };
        caps.applyColorResult(measured);
        assertEquals(1, queued.size());

        subscription.close();
        queued.get(0).run();

        assertEquals("a closed subscription delivers no queued snapshot",
                1, received.size());
    }

    @Test
    public void testPaletteSnapshotDefendsSharedValues() {
        TerminalCapabilities caps = TerminalCapabilities.detect();
        List<TerminalSnapshot> first = received();
        List<TerminalSnapshot> second = received();
        TerminalSubscription one = caps.subscribe(recordInto(first));
        TerminalSubscription two = caps.subscribe(recordInto(second));
        try {
            Map<Integer, int[]> palette = new HashMap<Integer, int[]>();
            palette.put(0, new int[] { 1, 2, 3 });
            TerminalColorQuery measured = new TerminalColorQuery();
            measured.palette = palette;
            caps.applyColorResult(measured);

            TerminalSnapshot update = first.get(1);
            assertSame(update, second.get(1));
            update.paletteColors().get(0)[0] = 99;
            assertArrayEquals("one reader cannot mutate another reader's palette",
                    new int[] { 1, 2, 3 }, second.get(1).paletteColors().get(0));
        } finally {
            one.close();
            two.close();
        }
    }

    /** Probe transport that never answers, forcing the platform fallback. */
    private static TerminalProbeTransport silentProbeTransport() {
        return new TerminalProbeTransport() {
            @Override
            public boolean isAvailable() {
                return false;
            }

            @Override
            public TerminalProbeSession open() throws IOException {
                throw new IOException("unavailable");
            }
        };
    }
}
