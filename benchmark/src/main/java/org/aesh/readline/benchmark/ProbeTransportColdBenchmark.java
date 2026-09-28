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
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * First probe-session open/close in fresh JVM forks. No warmup is deliberate:
 * every measurement is the first call in its fork. For FFM the parameter
 * compares a wholly cold Linker to a JVM where another caller already
 * initialized the Linker (but not the probe's downcall handles).
 * This does not measure terminal-response latency or OSC parsing.
 */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(10)
public class ProbeTransportColdBenchmark {

    @State(Scope.Thread)
    public static class FfmColdState {
        @Param({ "false", "true" })
        public boolean linkerInitialized;

        TerminalProbeTransport transport;

        @Setup(Level.Trial)
        public void setup() throws ReflectiveOperationException {
            transport = ProbeTransportBenchmarkSupport.ffm();
            if (linkerInitialized) {
                ProbeTransportBenchmarkSupport.initializeLinker();
            }
        }
    }

    @Benchmark
    public boolean sttyCold(ProbeTransportWarmBenchmark.SttyState state) throws IOException {
        try (TerminalProbeSession session = state.transport.open()) {
            return session.input() != null;
        }
    }

    @Benchmark
    public boolean ffmCold(FfmColdState state) throws IOException {
        try (TerminalProbeSession session = state.transport.open()) {
            return session.input() != null;
        }
    }
}
