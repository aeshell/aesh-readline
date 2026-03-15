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

import org.aesh.readline.action.ActionDecoder;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
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
 * Benchmarks for ActionDecoder key parsing performance.
 * <p>
 * The ActionDecoder is critical for input handling performance as it
 * parses every keystroke. The current implementation uses O(n*m) linear
 * search through all key mappings.
 *
 * @author Aesh Team
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class ActionDecoderBenchmark {

    // Simple character input
    private static final int[] CHAR_A = new int[] { 'a' };
    private static final int[] CHAR_HELLO = "hello".codePoints().toArray();

    // Escape sequences
    private static final int[] ARROW_UP = new int[] { 27, 91, 65 };        // ESC [ A
    private static final int[] ARROW_DOWN = new int[] { 27, 91, 66 };      // ESC [ B
    private static final int[] ARROW_RIGHT = new int[] { 27, 91, 67 };     // ESC [ C
    private static final int[] ARROW_LEFT = new int[] { 27, 91, 68 };      // ESC [ D

    // Function keys
    private static final int[] F1_KEY = new int[] { 27, 79, 80 };          // ESC O P
    private static final int[] F12_KEY = new int[] { 27, 91, 50, 52, 126 }; // ESC [ 2 4 ~

    // Control sequences
    private static final int[] CTRL_C = new int[] { 3 };
    private static final int[] CTRL_D = new int[] { 4 };
    private static final int[] ENTER = new int[] { 13 };
    private static final int[] TAB = new int[] { 9 };
    private static final int[] BACKSPACE = new int[] { 127 };

    private ActionDecoder decoderEmacs;
    private ActionDecoder decoderVi;

    // Pre-created EditModes (created once, reused across invocations)
    private EditMode emacsMode;
    private EditMode viMode;

    @Setup(Level.Trial)
    public void setup() {
        // Create EditModes once (simulates real application startup)
        emacsMode = EditModeBuilder.builder().build();
        viMode = EditModeBuilder.builder(EditMode.Mode.VI).build();

        // Decoder with Emacs mode mappings
        decoderEmacs = new ActionDecoder(emacsMode);

        // Decoder with Vi mode mappings
        decoderVi = new ActionDecoder(viMode);
    }

    @Setup(Level.Invocation)
    public void resetDecoder() {
        // Reset decoders before each invocation to clear buffer state
        decoderEmacs = new ActionDecoder(emacsMode);
        decoderVi = new ActionDecoder(viMode);
    }

    // ========== Single Character Benchmarks ==========

    // @Benchmark
    // public void singleCharacter(Blackhole bh) {
    //     decoderEmacs.add(CHAR_A);
    //     while (decoderEmacs.hasNext()) {
    //         bh.consume(decoderEmacs.next());
    //     }
    // }

    @Benchmark
    public void singleCharacterEmacs(Blackhole bh) {
        decoderEmacs.add(CHAR_A);
        while (decoderEmacs.hasNext()) {
            bh.consume(decoderEmacs.next());
        }
    }

    @Benchmark
    public void singleCharacterVi(Blackhole bh) {
        decoderVi.add(CHAR_A);
        while (decoderVi.hasNext()) {
            bh.consume(decoderVi.next());
        }
    }

    // ========== Escape Sequence Benchmarks ==========

    @Benchmark
    public void arrowKey(Blackhole bh) {
        decoderEmacs.add(ARROW_LEFT);
        decoderEmacs.add(ARROW_UP);
        while (decoderEmacs.hasNext()) {
            bh.consume(decoderEmacs.next());
        }
    }

    @Benchmark
    public void functionKey(Blackhole bh) {
        decoderEmacs.add(F1_KEY);
        decoderEmacs.add(F12_KEY);
        while (decoderEmacs.hasNext()) {
            bh.consume(decoderEmacs.next());
        }
    }

    // ========== Multiple Input Benchmarks ==========

    @Benchmark
    public void multipleCharacters(Blackhole bh) {
        decoderEmacs.add(CHAR_HELLO);
        while (decoderEmacs.hasNext()) {
            bh.consume(decoderEmacs.next());
        }
    }

    @Benchmark
    public void typingSimulation(Blackhole bh) {
        // Simulate realistic typing: chars + special keys
        decoderEmacs.add(CHAR_HELLO);
        decoderEmacs.add(ARROW_LEFT);
        decoderEmacs.add(ARROW_LEFT);
        decoderEmacs.add(ARROW_RIGHT);
        decoderEmacs.add(BACKSPACE);
        decoderEmacs.add(ENTER);

        while (decoderEmacs.hasNext()) {
            bh.consume(decoderEmacs.next());
        }
    }

    // ========== Control Key Benchmarks ==========

    @Benchmark
    public void controlKeys(Blackhole bh) {
        decoderEmacs.add(CTRL_C);
        while (decoderEmacs.hasNext()) {
            bh.consume(decoderEmacs.next());
        }
    }

    @Benchmark
    public void tabCompletion(Blackhole bh) {
        decoderEmacs.add(TAB);
        while (decoderEmacs.hasNext()) {
            bh.consume(decoderEmacs.next());
        }
    }

    // ========== Throughput Benchmark ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void keystrokeThroughput(Blackhole bh) {
        decoderEmacs.add(CHAR_A);
        while (decoderEmacs.hasNext()) {
            bh.consume(decoderEmacs.next());
        }
    }
}
