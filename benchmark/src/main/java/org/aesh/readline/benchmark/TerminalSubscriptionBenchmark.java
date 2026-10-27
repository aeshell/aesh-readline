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
package org.aesh.readline.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.detect.TerminalSnapshot;
import org.aesh.terminal.detect.TerminalSubscription;
import org.aesh.terminal.detect.TerminalTheme;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/** Publication and caller-supplied-executor costs, with no terminal I/O. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgsAppend = { "-Xms256m", "-Xmx256m" })
public class TerminalSubscriptionBenchmark {

    @State(Scope.Benchmark)
    public static class PublicationState {
        @Param({ "0", "1", "4" })
        public int subscribers;

        TerminalCapabilities caps;
        TerminalTheme nextTheme;
        TerminalSnapshot latest;
        List<TerminalSubscription> subscriptions;

        @Setup(Level.Trial)
        public void setup() {
            caps = TerminalCapabilities.detect();
            nextTheme = TerminalTheme.LIGHT;
            subscriptions = new ArrayList<TerminalSubscription>();
            for (int i = 0; i < subscribers; i++) {
                subscriptions.add(caps.subscribe(new Consumer<TerminalSnapshot>() {
                    @Override
                    public void accept(TerminalSnapshot snapshot) {
                        latest = snapshot;
                    }
                }));
            }
        }

        @TearDown(Level.Trial)
        public void cleanup() {
            for (TerminalSubscription subscription : subscriptions) {
                subscription.close();
            }
        }
    }

    @State(Scope.Benchmark)
    public static class HandoffState {
        TerminalCapabilities caps;
        TerminalTheme nextTheme;
        ExecutorService executor;
        TerminalSubscription subscription;
        BlockingQueue<TerminalSnapshot> handoff;

        @Setup(Level.Trial)
        public void setup() {
            caps = TerminalCapabilities.detect();
            nextTheme = TerminalTheme.LIGHT;
            handoff = new ArrayBlockingQueue<TerminalSnapshot>(1);
            executor = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "subscription-handoff-benchmark");
                thread.setDaemon(true);
                return thread;
            });
            subscription = caps.subscribe(new Consumer<TerminalSnapshot>() {
                @Override
                public void accept(TerminalSnapshot snapshot) {
                    if (snapshot.revision() > 0 && !handoff.offer(snapshot)) {
                        throw new IllegalStateException("unconsumed handoff from an earlier operation");
                    }
                }
            }, executor);
        }

        @TearDown(Level.Trial)
        public void cleanup() {
            subscription.close();
            executor.shutdownNow();
        }
    }

    /** Synchronous publication through the existing theme-mutation path. */
    @Benchmark
    public TerminalSnapshot publish(PublicationState state) {
        state.nextTheme = state.nextTheme == TerminalTheme.DARK
                ? TerminalTheme.LIGHT : TerminalTheme.DARK;
        state.caps.applyThemeChange(state.nextTheme);
        return state.latest;
    }

    /** One producer-to-executor rendezvous; completion is inside the timing. */
    @Benchmark
    public TerminalSnapshot readerHandoff(HandoffState state) throws InterruptedException {
        state.nextTheme = state.nextTheme == TerminalTheme.DARK
                ? TerminalTheme.LIGHT : TerminalTheme.DARK;
        state.caps.applyThemeChange(state.nextTheme);
        TerminalSnapshot delivered = state.handoff.poll(30, TimeUnit.SECONDS);
        if (delivered == null) {
            throw new IllegalStateException("executor delivery did not complete");
        }
        return delivered;
    }
}
