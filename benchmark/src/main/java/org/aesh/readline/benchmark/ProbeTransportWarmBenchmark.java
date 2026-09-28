/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
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

import org.aesh.terminal.detect.TerminalProbeSession;
import org.aesh.terminal.detect.TerminalProbeTransport;
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
import org.openjdk.jmh.annotations.Warmup;

/**
 * Warm-session setup and restore for the built-in POSIX probe transports.
 * This measures raw-mode open/close, including stty subprocesses or FFM
 * syscalls, without terminal-response latency or OSC parsing.
 * Run under a controlling PTY and with native access enabled for FFM.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(3)
public class ProbeTransportWarmBenchmark {

    @State(Scope.Thread)
    public static class SttyState {
        TerminalProbeTransport transport;

        @Setup(Level.Trial)
        public void setup() throws ReflectiveOperationException {
            transport = ProbeTransportBenchmarkSupport.stty();
        }
    }

    @State(Scope.Thread)
    public static class FfmState {
        TerminalProbeTransport transport;

        @Setup(Level.Trial)
        public void setup() throws ReflectiveOperationException {
            transport = ProbeTransportBenchmarkSupport.ffm();
        }
    }

    @Benchmark
    public boolean sttySession(SttyState state) throws IOException {
        try (TerminalProbeSession session = state.transport.open()) {
            return session.input() != null;
        }
    }

    @Benchmark
    public boolean ffmSession(FfmState state) throws IOException {
        try (TerminalProbeSession session = state.transport.open()) {
            return session.input() != null;
        }
    }
}
