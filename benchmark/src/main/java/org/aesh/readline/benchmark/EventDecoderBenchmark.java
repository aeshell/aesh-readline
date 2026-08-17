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

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.tty.Signal;
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
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks for EventDecoder signal extraction and DSR filtering.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@State(Scope.Thread)
public class EventDecoderBenchmark {

    // Normal text — no signals, no ESC (fast path)
    private static final int[] TEXT_SHORT = "hello".codePoints().toArray();
    private static final int[] TEXT_LINE = "The quick brown fox jumps over the lazy dog".codePoints().toArray();

    // Text with embedded signal (Ctrl+C = 3)
    private static final int[] TEXT_WITH_SIGNAL = "hel\003lo".codePoints().toArray();

    // Multiple signals in one chunk
    private static final int[] MULTIPLE_SIGNALS = "a\003b\004c\032d".codePoints().toArray();

    // Escape sequence — triggers DSR filter scan
    private static final int[] ESCAPE_SEQUENCE = { 27, 91, 65 }; // ESC [ A

    // Theme DSR sequence: CSI ? 997 ; 1 n
    private static final int[] THEME_DSR = { 27, 91, 63, 57, 57, 55, 59, 49, 110 };

    // Mixed: text + escape + text
    private static final int[] MIXED;
    static {
        String s = "hello\033[Aworld\033[B";
        MIXED = s.codePoints().toArray();
    }

    private EventDecoder decoder;
    private EventDecoder decoderWithDsr;
    private volatile int inputCount;
    private volatile int signalCount;

    @Setup(Level.Trial)
    public void setup() {
        // Construct decoders once — reuse across all invocations.
        // This measures per-accept() cost, not construction cost.
        decoder = new EventDecoder();
        decoder.setSignalHandler(s -> signalCount++);
        decoder.setInputHandler(input -> inputCount++);

        decoderWithDsr = new EventDecoder();
        decoderWithDsr.setSignalHandler(s -> signalCount++);
        decoderWithDsr.setInputHandler(input -> inputCount++);
        decoderWithDsr.setThemeChangeHandler(theme -> {});
    }

    @Setup(Level.Invocation)
    public void resetCounters() {
        inputCount = 0;
        signalCount = 0;
    }

    // ========== No signals (fast path) ==========

    @Benchmark
    public void textNoSignals(Blackhole bh) {
        decoder.accept(TEXT_SHORT);
        bh.consume(inputCount);
    }

    @Benchmark
    public void textLineNoSignals(Blackhole bh) {
        decoder.accept(TEXT_LINE);
        bh.consume(inputCount);
    }

    // ========== With signals ==========

    @Benchmark
    public void textWithSignal(Blackhole bh) {
        decoder.accept(TEXT_WITH_SIGNAL);
        bh.consume(inputCount + signalCount);
    }

    @Benchmark
    public void multipleSignals(Blackhole bh) {
        decoder.accept(MULTIPLE_SIGNALS);
        bh.consume(inputCount + signalCount);
    }

    // ========== DSR filter ==========

    @Benchmark
    public void textWithDsrFilter(Blackhole bh) {
        // Normal text through DSR filter — should hit fast path (no ESC)
        decoderWithDsr.accept(TEXT_SHORT);
        bh.consume(inputCount);
    }

    @Benchmark
    public void escapeSequenceWithDsrFilter(Blackhole bh) {
        // ESC sequence through DSR filter — triggers state machine
        decoderWithDsr.accept(ESCAPE_SEQUENCE);
        bh.consume(inputCount);
    }

    @Benchmark
    public void themeDsrSequence(Blackhole bh) {
        // Full theme DSR sequence — should be consumed by filter
        decoderWithDsr.accept(THEME_DSR);
        bh.consume(inputCount);
    }

    // ========== Mixed ==========

    @Benchmark
    public void mixedInput(Blackhole bh) {
        decoder.accept(MIXED);
        bh.consume(inputCount);
    }

    @Benchmark
    public void mixedInputWithDsr(Blackhole bh) {
        decoderWithDsr.accept(MIXED);
        bh.consume(inputCount);
    }

    // ========== Throughput ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void textThroughput(Blackhole bh) {
        decoder.accept(TEXT_LINE);
        bh.consume(inputCount);
    }
}
