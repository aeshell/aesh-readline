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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.EndpointContext;
import org.aesh.terminal.StreamConnection;
import org.aesh.terminal.tty.Size;
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

/** CPU/allocation only: no terminal setup, worker start or stream I/O. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(value = 3, jvmArgsAppend = { "-Xms256m", "-Xmx256m" })
public class EndpointContextBenchmark {

    /** Independent of the new interface, for constructor baseline comparisons. */
    @State(Scope.Thread)
    public static class ConstructionState {
        ByteArrayInputStream input;
        ByteArrayOutputStream output;

        @Setup(Level.Trial)
        public void setup() {
            input = new ByteArrayInputStream(new byte[0]);
            output = new ByteArrayOutputStream();
        }
    }

    @State(Scope.Thread)
    public static class ContextState {
        StreamConnection unknown;
        StreamConnection declared;
        EndpointContext.Locality locality;
        EndpointContext.StreamFacts input;
        Size dimensions;
        EndpointContext.Source source;

        @Setup(Level.Trial)
        public void setup() {
            unknown = new StreamConnection(StandardCharsets.UTF_8,
                    new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
            declared = new StreamConnection(StandardCharsets.UTF_8,
                    new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
            locality = EndpointContext.Locality.REMOTE;
            input = EndpointContext.StreamFacts.supplied(
                    EndpointContext.Truth.YES, EndpointContext.Truth.UNKNOWN);
            dimensions = new Size(160, 50);
            source = EndpointContext.Source.CALLER;
            declared.setEndpointContext(EndpointContext.builder()
                    .locality(locality).input(input).dimensions(dimensions, source).build());
        }

        @TearDown(Level.Trial)
        public void cleanup() {
            unknown.close();
            declared.close();
        }
    }

    @Benchmark
    public StreamConnection createStream(ConstructionState state) {
        // Construction starts no workers and owns no resources; these escaped
        // objects need no close to release the shared caller-owned byte streams.
        return new StreamConnection(StandardCharsets.UTF_8, state.input, state.output);
    }

    @Benchmark
    public EndpointContext readUnknown(ContextState state) {
        return state.unknown.endpointContext();
    }

    @Benchmark
    public EndpointContext readDeclared(ContextState state) {
        return state.declared.endpointContext();
    }

    @Benchmark
    public EndpointContext createContext(ContextState state) {
        return EndpointContext.builder().locality(state.locality).input(state.input)
                .dimensions(state.dimensions, state.source).build();
    }
}
