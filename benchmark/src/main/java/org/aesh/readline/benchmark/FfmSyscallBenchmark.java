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

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.impl.FfmPty;
import org.aesh.terminal.tty.impl.Pty;
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

/**
 * Per-syscall cost of the FFM PTY wrappers ({@code LibC.poll},
 * {@code LibC.read}, {@code LibC.ioctl}, {@code tcgetattr}/{@code tcsetattr}),
 * measured through the public {@link Pty} API.
 * <p>
 * Every wrapper allocates a confined arena plus an errno capture segment
 * per call, while no caller ever reads the captured errno. These legs
 * quantify that per-call overhead so capture removal can be judged with
 * numbers ({@code -prof gc} for allocation rate).
 * <p>
 * No leg stages input data: the read wrapper's allocation shape is
 * identical with or without bytes flowing, and staging bytes on a
 * PTY slave from inside the measured JVM is not possible. Requires a
 * controlling PTY and native access (run under {@code script}).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class FfmSyscallBenchmark {

    @State(Scope.Benchmark)
    public static class PtyState {
        Pty pty;
        Attributes attrs;

        @Setup(Level.Trial)
        public void setup() throws IOException {
            try {
                pty = FfmPty.current();
            } catch (IOException e) {
                throw new IllegalStateException(
                        "FFM PTY requires Java 22+, native access, and a controlling PTY", e);
            }
            attrs = pty.getAttr();
        }

        @TearDown(Level.Trial)
        public void teardown() throws IOException {
            pty.close();
        }
    }

    /**
     * Idle poll: {@code poll} with a zero timeout and no data waiting.
     * Exercises the full wrapper (arena, capture segment, downcall).
     */
    @Benchmark
    public int peekIdle(PtyState state) throws IOException {
        return state.pty.peek(0);
    }

    /**
     * Window-size query: {@code ioctl(TIOCGWINSZ)} wrapper per call.
     */
    @Benchmark
    public Size getSizeLoop(PtyState state) throws IOException {
        return state.pty.getSize();
    }

    /**
     * Attribute round-trip: {@code tcgetattr} plus {@code tcsetattr} of
     * the unchanged attributes. The native calls cannot be eliminated;
     * the read result is pinned through the blackhole.
     */
    @Benchmark
    public void attrCycle(PtyState state, Blackhole bh) throws IOException {
        Attributes current = state.pty.getAttr();
        state.pty.setAttr(current);
        bh.consume(current);
    }
}
