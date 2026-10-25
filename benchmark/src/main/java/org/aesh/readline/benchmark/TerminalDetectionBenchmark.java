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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.detect.TerminalDetection;
import org.aesh.terminal.utils.TerminalColorCapability;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/** Warm CPU/allocation costs. Discovery setup is outside the timed operations. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgsAppend = { "-Xms256m", "-Xmx256m" })
public class TerminalDetectionBenchmark {

    @State(Scope.Benchmark)
    public static class CapabilitiesState {
        TerminalCapabilities caps;
        TerminalDetection owner;
        DetectionProbeFixture fixture;

        @Setup(Level.Trial)
        public void setup() {
            fixture = new DetectionProbeFixture(true);
            TerminalCapabilities.setProbeTransport(fixture);
            TerminalCapabilities.invalidate();
            caps = TerminalCapabilities.detectFull();
            owner = TerminalDetection.managed(caps);
            fixture.verify(1, caps);
        }

        @TearDown(Level.Trial)
        public void cleanup() {
            try {
                fixture.verify(1, caps);
            } finally {
                TerminalCapabilities.setProbeTransport(null);
                TerminalCapabilities.invalidate();
            }
        }
    }

    @Benchmark
    public TerminalCapabilities environment(CapabilitiesState state) {
        return TerminalCapabilities.detect();
    }

    @Benchmark
    public TerminalCapabilities sharedInstance(CapabilitiesState state) {
        return TerminalCapabilities.getInstance();
    }

    @Benchmark
    public void environmentOwner(CapabilitiesState state, Blackhole blackhole) {
        blackhole.consume(TerminalDetection.environmentOnly());
    }

    @Benchmark
    public void managedOwner(CapabilitiesState state, Blackhole blackhole) {
        blackhole.consume(TerminalDetection.managed(state.caps));
    }

    @Benchmark
    public void emptyManagedOwner(CapabilitiesState state, Blackhole blackhole) {
        blackhole.consume(TerminalDetection.managed(null));
    }

    @Benchmark
    public void scopedRead(CapabilitiesState state, Blackhole blackhole) {
        blackhole.consume(state.owner.capabilities());
    }

    /** One consumer reading seven scalar facts, not nanoseconds per fact. */
    @Benchmark
    public void scalarFacts(CapabilitiesState state, Blackhole blackhole) {
        blackhole.consume(state.caps.supportsTrueColor());
        blackhole.consume(state.caps.supports256Colors());
        blackhole.consume(state.caps.supportsColor());
        blackhole.consume(state.caps.theme());
        blackhole.consume(state.caps.imageProtocol());
        blackhole.consume(state.caps.synchronizedOutputSupport());
        blackhole.consume(state.caps.graphemeClusterSupport());
    }

    @Benchmark
    public int[] foregroundCopy(CapabilitiesState state) {
        return state.caps.foregroundRGB();
    }

    @Benchmark
    public Map<Integer, int[]> paletteCopy(CapabilitiesState state) {
        return state.caps.paletteColors();
    }

    @Benchmark
    public TerminalColorCapability colorModel(CapabilitiesState state) {
        return TerminalColorCapability.from(state.caps);
    }
}
