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
package org.aesh.readline;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks for Buffer operations.
 * <p>
 * The Buffer class handles all text editing operations including insertion,
 * deletion, and cursor movement. It's performance-critical as it processes
 * every character typed.
 *
 * @author Aesh Team
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class BufferBenchmark {

    // No-op consumer for buffer output (we're benchmarking buffer ops, not terminal output)
    private static final Consumer<int[]> NO_OP_CONSUMER = data -> {};

    // Standard terminal width
    private static final int TERMINAL_WIDTH = 80;

    private Buffer buffer;
    private Buffer largeBuffer;

    // Test strings
    private static final int[] WORD = "hello".codePoints().toArray();
    private static final int[] WIDE_CHARS = "你好世界".codePoints().toArray(); // CJK characters

    @State(Scope.Thread)
    public static class BufferState {
        @Param({"10", "100", "1000"})
        public int bufferSize;

        public Buffer buffer;

        @Setup(Level.Invocation)
        public void setup() {
            buffer = new Buffer(new Prompt("> "));
            // Pre-fill buffer to specified size
            for (int i = 0; i < bufferSize; i++) {
                buffer.insert(NO_OP_CONSUMER, 'x', TERMINAL_WIDTH);
            }
        }
    }

    @Setup(Level.Invocation)
    public void setup() {
        buffer = new Buffer(new Prompt("> "));

        // Create a large buffer with 5000 characters
        largeBuffer = new Buffer(new Prompt("> "));
        for (int i = 0; i < 5000; i++) {
            largeBuffer.insert(NO_OP_CONSUMER, 'x', TERMINAL_WIDTH);
        }
    }

    // ========== Insert Benchmarks ==========

    @Benchmark
    public void insertSingleCharacter(Blackhole bh) {
        buffer.insert(NO_OP_CONSUMER, 'a', TERMINAL_WIDTH);
        bh.consume(buffer.length());
    }

    @Benchmark
    public void insertWord(Blackhole bh) {
        buffer.insert(NO_OP_CONSUMER, WORD, TERMINAL_WIDTH);
        bh.consume(buffer.length());
    }

    @Benchmark
    public void insertCharacterByCharacter(Blackhole bh) {
        for (int c : WORD) {
            buffer.insert(NO_OP_CONSUMER, c, TERMINAL_WIDTH);
        }
        bh.consume(buffer.length());
    }

    @Benchmark
    public void insertAtBeginning(Blackhole bh, BufferState state) {
        // Move cursor to beginning, then insert (worst case)
        state.buffer.move(NO_OP_CONSUMER, -state.bufferSize, TERMINAL_WIDTH);
        state.buffer.insert(NO_OP_CONSUMER, 'x', TERMINAL_WIDTH);
        bh.consume(state.buffer.length());
    }

    @Benchmark
    public void insertAtEnd(Blackhole bh, BufferState state) {
        // Insert at end (best case)
        state.buffer.insert(NO_OP_CONSUMER, 'x', TERMINAL_WIDTH);
        bh.consume(state.buffer.length());
    }

    @Benchmark
    public void insertInMiddle(Blackhole bh, BufferState state) {
        // Move to middle, then insert
        state.buffer.move(NO_OP_CONSUMER, -(state.bufferSize / 2), TERMINAL_WIDTH);
        state.buffer.insert(NO_OP_CONSUMER, 'x', TERMINAL_WIDTH);
        bh.consume(state.buffer.length());
    }

    // ========== Delete Benchmarks ==========

    @Benchmark
    public void deleteBackward(Blackhole bh, BufferState state) {
        // Delete one character backward
        state.buffer.delete(NO_OP_CONSUMER, -1, TERMINAL_WIDTH);
        bh.consume(state.buffer.length());
    }

    @Benchmark
    public void deleteForward(Blackhole bh, BufferState state) {
        // Move to middle, delete forward
        state.buffer.move(NO_OP_CONSUMER, -(state.bufferSize / 2), TERMINAL_WIDTH);
        state.buffer.delete(NO_OP_CONSUMER, 1, TERMINAL_WIDTH);
        bh.consume(state.buffer.length());
    }

    @Benchmark
    public void deleteWord(Blackhole bh, BufferState state) {
        // Delete multiple characters (simulate delete-word)
        state.buffer.delete(NO_OP_CONSUMER, -5, TERMINAL_WIDTH);
        bh.consume(state.buffer.length());
    }

    // ========== Cursor Movement Benchmarks ==========

    @Benchmark
    public void moveCursorLeft(Blackhole bh, BufferState state) {
        state.buffer.move(NO_OP_CONSUMER, -1, TERMINAL_WIDTH);
        bh.consume(state.buffer.cursor());
    }

    @Benchmark
    public void moveCursorRight(Blackhole bh, BufferState state) {
        state.buffer.move(NO_OP_CONSUMER, -state.bufferSize / 2, TERMINAL_WIDTH); // First move to middle
        state.buffer.move(NO_OP_CONSUMER, 1, TERMINAL_WIDTH);
        bh.consume(state.buffer.cursor());
    }

    @Benchmark
    public void moveCursorToBeginning(Blackhole bh, BufferState state) {
        state.buffer.move(NO_OP_CONSUMER, -state.bufferSize, TERMINAL_WIDTH);
        bh.consume(state.buffer.cursor());
    }

    @Benchmark
    public void moveCursorToEnd(Blackhole bh, BufferState state) {
        state.buffer.move(NO_OP_CONSUMER, -state.bufferSize, TERMINAL_WIDTH); // First move to beginning
        state.buffer.move(NO_OP_CONSUMER, state.bufferSize, TERMINAL_WIDTH);  // Then to end
        bh.consume(state.buffer.cursor());
    }

    // ========== Wide Character Benchmarks ==========

    @Benchmark
    public void insertWideCharacters(Blackhole bh) {
        buffer.insert(NO_OP_CONSUMER, WIDE_CHARS, TERMINAL_WIDTH);
        bh.consume(buffer.length());
    }

    // ========== Large Buffer Benchmarks ==========

    @Benchmark
    public void insertIntoLargeBuffer(Blackhole bh) {
        largeBuffer.insert(NO_OP_CONSUMER, 'x', TERMINAL_WIDTH);
        bh.consume(largeBuffer.length());
    }

    @Benchmark
    public void insertAtBeginningOfLargeBuffer(Blackhole bh) {
        largeBuffer.move(NO_OP_CONSUMER, -5000, TERMINAL_WIDTH);
        largeBuffer.insert(NO_OP_CONSUMER, 'x', TERMINAL_WIDTH);
        bh.consume(largeBuffer.length());
    }

    // ========== Copy Buffer Benchmark ==========

    @Benchmark
    public void copyBuffer(Blackhole bh, BufferState state) {
        Buffer copy = new Buffer(state.buffer);
        bh.consume(copy.length());
    }

    // ========== Clear Buffer Benchmark ==========

    @Benchmark
    public void clearBuffer(Blackhole bh, BufferState state) {
        state.buffer.clear();
        bh.consume(state.buffer.length());
    }

    // ========== Throughput Benchmarks ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void insertThroughput(Blackhole bh) {
        buffer.insert(NO_OP_CONSUMER, 'a', TERMINAL_WIDTH);
        bh.consume(buffer.length());
    }
}
