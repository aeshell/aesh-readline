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

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.Key;
import org.aesh.terminal.io.Decoder;
import org.aesh.terminal.io.Encoder;
import org.aesh.terminal.utils.Parser;
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
 * Benchmarks for TTY connection and terminal I/O performance.
 * <p>
 * Tests encoding/decoding operations, event handling, ANSI processing,
 * and terminal data throughput.
 *
 * @author Aesh Team
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class TtyConnectionBenchmark {

    // Test data - varying lengths
    private static final String SHORT_TEXT = "hello";
    private static final String MEDIUM_TEXT = "The quick brown fox jumps over the lazy dog.";
    private static final String LONG_TEXT = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. "
            + "Sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. "
            + "Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris.";

    // Very long text (~1KB)
    private static final String VERY_LONG_TEXT = buildVeryLongText();

    // Unicode variations
    private static final String UNICODE_TEXT = "Hello 世界! Привет мир! مرحبا بالعالم";
    private static final String UNICODE_HEAVY = "日本語テキスト 中文文本 한국어 텍스트 ελληνικά κείμενο "
            + "עברית טקסט ไทย ข้อความ हिंदी पाठ العربية النص";
    private static final String EMOJI_TEXT = "Hello 👋 World 🌍! Test 🧪 with emojis 😀🎉🚀💻";

    // Realistic command line inputs
    private static final String COMMAND_SIMPLE = "ls -la /home/user/documents";
    private static final String COMMAND_COMPLEX = "find /var/log -name '*.log' -mtime +7 -exec gzip {} \\; 2>/dev/null | head -100";
    private static final String COMMAND_PIPELINE = "cat access.log | grep 'ERROR' | awk '{print $1, $4, $9}' | sort | uniq -c | sort -rn | head -20";
    private static final String COMMAND_WITH_QUOTES = "echo \"Hello $USER, today is $(date '+%Y-%m-%d')\" && echo 'Single quoted: $NOT_EXPANDED'";

    // Structured data
    private static final String JSON_SMALL = "{\"name\":\"John\",\"age\":30,\"city\":\"New York\"}";
    private static final String JSON_MEDIUM = "{\"users\":[{\"id\":1,\"name\":\"Alice\",\"email\":\"alice@example.com\"},"
            + "{\"id\":2,\"name\":\"Bob\",\"email\":\"bob@example.com\"},{\"id\":3,\"name\":\"Charlie\",\"email\":\"charlie@example.com\"}],"
            + "\"total\":3,\"page\":1}";
    private static final String PATH_LONG = "/home/user/projects/company/application/src/main/java/com/example/service/impl/UserServiceImpl.java";

    // Mixed content
    private static final String MIXED_ASCII_UNICODE = "Configuration: 設定=enabled, Статус=active, الحالة=running, count=42";
    private static final String CODE_SNIPPET = "public static void main(String[] args) { System.out.println(\"Hello, World!\"); }";
    private static final String MULTILINE_TEXT = "Line 1: First line of text\nLine 2: Second line with more content\n"
            + "Line 3: Third line here\nLine 4: Fourth and final line";

    // Special characters
    private static final String SPECIAL_CHARS = "Tab:\there Backslash:\\ Quote:\" Apostrophe:' Ampersand:& Pipe:| Dollar:$";
    private static final String CONTROL_CHARS = "Bell:\u0007 Escape:\u001B Null-safe text after control";

    // ANSI escape sequences
    private static final String ANSI_COLORED = "\u001B[31mRed\u001B[0m \u001B[32mGreen\u001B[0m \u001B[34mBlue\u001B[0m";
    private static final String ANSI_COMPLEX = "\u001B[1;31;44mBold Red on Blue\u001B[0m\u001B[K";
    private static final String ANSI_FULL = "\u001B[1;4;31;42mBold Underline Red on Green\u001B[0m "
            + "\u001B[38;5;208mOrange 256-color\u001B[0m \u001B[38;2;255;100;50mTrue color\u001B[0m";

    private static String buildVeryLongText() {
        StringBuilder sb = new StringBuilder(1200);
        String paragraph = "This is a paragraph of text that will be repeated multiple times to create a very long string. "
                + "It contains various words and punctuation marks to simulate realistic text content. ";
        while (sb.length() < 1000) {
            sb.append(paragraph);
        }
        return sb.toString();
    }

    // Escape sequences as bytes
    private static final byte[] ARROW_UP_BYTES = new byte[] { 27, 91, 65 };
    private static final byte[] ARROW_SEQUENCE = new byte[] { 27, 91, 65, 27, 91, 66, 27, 91, 67, 27, 91, 68 };
    private static final byte[] MIXED_INPUT = "hello\u001B[Aworld\u001B[B".getBytes(StandardCharsets.UTF_8);

    private Decoder decoder;
    private Encoder encoder;
    private EventDecoder eventDecoder;
    private StringBuilder outputBuffer;
    private int[] receivedInput;

    @Setup(Level.Trial)
    public void setupTrial() {
        outputBuffer = new StringBuilder();
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        outputBuffer.setLength(0);
        receivedInput = null;

        eventDecoder = new EventDecoder();
        eventDecoder.setInputHandler(input -> receivedInput = input);

        decoder = new Decoder(512, StandardCharsets.UTF_8, eventDecoder);
        encoder = new Encoder(StandardCharsets.UTF_8, data -> outputBuffer.append(new String(data, StandardCharsets.UTF_8)));
    }

    // ========== Decoder Benchmarks ==========

    @Benchmark
    public void decoderSimpleText(Blackhole bh) {
        byte[] input = SHORT_TEXT.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);
        bh.consume(receivedInput);
    }

    @Benchmark
    public void decoderMediumText(Blackhole bh) {
        byte[] input = MEDIUM_TEXT.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);
        bh.consume(receivedInput);
    }

    @Benchmark
    public void decoderLongText(Blackhole bh) {
        byte[] input = LONG_TEXT.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);
        bh.consume(receivedInput);
    }

    @Benchmark
    public void decoderUnicodeText(Blackhole bh) {
        byte[] input = UNICODE_TEXT.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);
        bh.consume(receivedInput);
    }

    @Benchmark
    public void decoderEscapeSequence(Blackhole bh) {
        decoder.write(ARROW_UP_BYTES);
        bh.consume(receivedInput);
    }

    @Benchmark
    public void decoderMultipleEscapeSequences(Blackhole bh) {
        decoder.write(ARROW_SEQUENCE);
        bh.consume(receivedInput);
    }

    @Benchmark
    public void decoderMixedInput(Blackhole bh) {
        decoder.write(MIXED_INPUT);
        bh.consume(receivedInput);
    }

    // ========== Encoder Benchmarks ==========

    @Benchmark
    public void encoderSimpleText(Blackhole bh) {
        int[] codePoints = SHORT_TEXT.codePoints().toArray();
        encoder.accept(codePoints);
        bh.consume(outputBuffer.toString());
    }

    @Benchmark
    public void encoderMediumText(Blackhole bh) {
        int[] codePoints = MEDIUM_TEXT.codePoints().toArray();
        encoder.accept(codePoints);
        bh.consume(outputBuffer.toString());
    }

    @Benchmark
    public void encoderLongText(Blackhole bh) {
        int[] codePoints = LONG_TEXT.codePoints().toArray();
        encoder.accept(codePoints);
        bh.consume(outputBuffer.toString());
    }

    @Benchmark
    public void encoderUnicodeText(Blackhole bh) {
        int[] codePoints = UNICODE_TEXT.codePoints().toArray();
        encoder.accept(codePoints);
        bh.consume(outputBuffer.toString());
    }

    // ========== Event Decoder Benchmarks ==========

    @Benchmark
    public void eventDecoderSingleKey(Blackhole bh) {
        EventDecoder ed = new EventDecoder();
        ed.setInputHandler(input -> bh.consume(input));
        ed.accept(new int[] { 'a' });
    }

    @Benchmark
    public void eventDecoderControlKey(Blackhole bh) {
        EventDecoder ed = new EventDecoder();
        ed.setInputHandler(input -> bh.consume(input));
        ed.accept(Key.CTRL_C.getKeyValues());
    }

    @Benchmark
    public void eventDecoderArrowKey(Blackhole bh) {
        EventDecoder ed = new EventDecoder();
        ed.setInputHandler(input -> bh.consume(input));
        ed.accept(Key.UP.getKeyValues());
    }

    @Benchmark
    public void eventDecoderWithSignalHandler(Blackhole bh) {
        EventDecoder ed = new EventDecoder();
        ed.setInputHandler(input -> bh.consume(input));
        ed.setSignalHandler(signal -> bh.consume(signal));
        ed.accept(Key.CTRL_C.getKeyValues());
    }

    // ========== ANSI Processing Benchmarks ==========

    @Benchmark
    public void ansiStripCodes(Blackhole bh) {
        String result = Parser.stripAwayAnsiCodes(ANSI_COLORED);
        bh.consume(result);
    }

    @Benchmark
    public void ansiStripComplexCodes(Blackhole bh) {
        String result = Parser.stripAwayAnsiCodes(ANSI_COMPLEX);
        bh.consume(result);
    }

    @Benchmark
    public void ansiToCodePoints(Blackhole bh) {
        int[] result = Parser.toCodePoints(ANSI_COLORED);
        bh.consume(result);
    }

    @Benchmark
    public void ansiFromCodePoints(Blackhole bh) {
        int[] codePoints = ANSI_COLORED.codePoints().toArray();
        String result = Parser.fromCodePoints(codePoints);
        bh.consume(result);
    }

    // ========== Parser Utility Benchmarks ==========

    @Benchmark
    public void parserToCodePoints(Blackhole bh) {
        int[] result = Parser.toCodePoints(MEDIUM_TEXT);
        bh.consume(result);
    }

    @Benchmark
    public void parserToCodePointsVeryLong(Blackhole bh) {
        int[] result = Parser.toCodePoints(VERY_LONG_TEXT);
        bh.consume(result);
    }

    @Benchmark
    public void parserToCodePointsEmoji(Blackhole bh) {
        int[] result = Parser.toCodePoints(EMOJI_TEXT);
        bh.consume(result);
    }

    @Benchmark
    public void parserFromCodePoints(Blackhole bh) {
        int[] codePoints = MEDIUM_TEXT.codePoints().toArray();
        String result = Parser.fromCodePoints(codePoints);
        bh.consume(result);
    }

    @Benchmark
    public void parserTrim(Blackhole bh) {
        String input = "  " + MEDIUM_TEXT + "  ";
        String result = Parser.trim(input);
        bh.consume(result);
    }

    @Benchmark
    public void parserContainsNonEscapedDollar(Blackhole bh) {
        boolean result = Parser.containsNonEscapedDollar("echo $HOME and \\$ESCAPED");
        bh.consume(result);
    }

    @Benchmark
    public void parserContainsNonEscapedDollarComplex(Blackhole bh) {
        boolean result = Parser.containsNonEscapedDollar(COMMAND_WITH_QUOTES);
        bh.consume(result);
    }

    // ========== Decoder Varied Input Benchmarks ==========

    @Benchmark
    public void decoderVeryLongText(Blackhole bh) {
        byte[] input = VERY_LONG_TEXT.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);
        bh.consume(receivedInput);
    }

    @Benchmark
    public void decoderUnicodeHeavy(Blackhole bh) {
        byte[] input = UNICODE_HEAVY.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);
        bh.consume(receivedInput);
    }

    @Benchmark
    public void decoderEmoji(Blackhole bh) {
        byte[] input = EMOJI_TEXT.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);
        bh.consume(receivedInput);
    }

    @Benchmark
    public void decoderCommandPipeline(Blackhole bh) {
        byte[] input = COMMAND_PIPELINE.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);
        bh.consume(receivedInput);
    }

    @Benchmark
    public void decoderJson(Blackhole bh) {
        byte[] input = JSON_MEDIUM.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);
        bh.consume(receivedInput);
    }

    @Benchmark
    public void decoderMultiline(Blackhole bh) {
        byte[] input = MULTILINE_TEXT.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);
        bh.consume(receivedInput);
    }

    // ========== Encoder Varied Input Benchmarks ==========

    @Benchmark
    public void encoderVeryLongText(Blackhole bh) {
        int[] codePoints = Parser.toCodePoints(VERY_LONG_TEXT);
        encoder.accept(codePoints);
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    public void encoderUnicodeHeavy(Blackhole bh) {
        int[] codePoints = Parser.toCodePoints(UNICODE_HEAVY);
        encoder.accept(codePoints);
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    public void encoderEmoji(Blackhole bh) {
        int[] codePoints = Parser.toCodePoints(EMOJI_TEXT);
        encoder.accept(codePoints);
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    public void encoderCommandPipeline(Blackhole bh) {
        int[] codePoints = Parser.toCodePoints(COMMAND_PIPELINE);
        encoder.accept(codePoints);
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    public void encoderJson(Blackhole bh) {
        int[] codePoints = Parser.toCodePoints(JSON_MEDIUM);
        encoder.accept(codePoints);
        bh.consume(outputBuffer.length());
    }

    // ========== ANSI Extended Benchmarks ==========

    @Benchmark
    public void ansiStripFullColor(Blackhole bh) {
        String result = Parser.stripAwayAnsiCodes(ANSI_FULL);
        bh.consume(result);
    }

    // ========== Throughput Benchmarks ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void decoderThroughput(Blackhole bh) {
        byte[] input = MEDIUM_TEXT.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);
        bh.consume(receivedInput);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void encoderThroughput(Blackhole bh) {
        int[] codePoints = MEDIUM_TEXT.codePoints().toArray();
        encoder.accept(codePoints);
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void eventProcessingThroughput(Blackhole bh) {
        EventDecoder ed = new EventDecoder();
        ed.setInputHandler(input -> bh.consume(input));
        ed.accept(Key.a.getKeyValues());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void fullPipelineThroughput(Blackhole bh) {
        // Simulate full input -> decode -> process -> encode -> output pipeline
        byte[] input = SHORT_TEXT.getBytes(StandardCharsets.UTF_8);
        decoder.write(input);

        if (receivedInput != null) {
            encoder.accept(receivedInput);
        }
        bh.consume(outputBuffer.length());
    }

    // ========== Simulated Typing Benchmarks ==========

    @Benchmark
    public void simulatedTypingSession(Blackhole bh) {
        // Simulate a realistic typing session with mixed input
        EventDecoder ed = new EventDecoder();
        final int[] lastInput = new int[1];
        ed.setInputHandler(input -> lastInput[0] = input.length);

        // Type "hello"
        for (char c : "hello".toCharArray()) {
            ed.accept(new int[] { c });
        }

        // Arrow keys
        ed.accept(Key.LEFT.getKeyValues());
        ed.accept(Key.LEFT.getKeyValues());

        // Backspace
        ed.accept(Key.BACKSPACE.getKeyValues());

        // More typing
        for (char c : " world".toCharArray()) {
            ed.accept(new int[] { c });
        }

        // Enter
        ed.accept(Key.ENTER.getKeyValues());

        bh.consume(lastInput[0]);
    }

    @Benchmark
    public void simulatedCommandLine(Blackhole bh) {
        // Simulate command line with history navigation
        EventDecoder ed = new EventDecoder();
        final int[] inputCount = {0};
        ed.setInputHandler(input -> inputCount[0]++);

        // Type command
        for (char c : "ls -la".toCharArray()) {
            ed.accept(new int[] { c });
        }

        // Navigate history
        ed.accept(Key.UP.getKeyValues());
        ed.accept(Key.UP.getKeyValues());
        ed.accept(Key.DOWN.getKeyValues());

        // Control sequences
        ed.accept(Key.CTRL_A.getKeyValues());  // Beginning of line
        ed.accept(Key.CTRL_E.getKeyValues());  // End of line
        ed.accept(Key.CTRL_K.getKeyValues());  // Kill to end

        // Tab completion trigger
        ed.accept(Key.CTRL_I.getKeyValues());

        // Enter
        ed.accept(Key.ENTER.getKeyValues());

        bh.consume(inputCount[0]);
    }

    // ========== Connection.write() Overhead Comparison ==========
    // Compare direct stdoutHandler().accept(int[]) vs Connection.write(String)
    // to measure the overhead of String-to-codePoints conversion.
    //
    // Run with: java -jar benchmarks.jar "writeOverhead.*"

    // Pre-converted code points (created once, reused)
    private static final int[] SHORT_TEXT_CP = Parser.toCodePoints(SHORT_TEXT);
    private static final int[] MEDIUM_TEXT_CP = Parser.toCodePoints(MEDIUM_TEXT);
    private static final int[] LONG_TEXT_CP = Parser.toCodePoints(LONG_TEXT);
    private static final int[] VERY_LONG_TEXT_CP = Parser.toCodePoints(VERY_LONG_TEXT);

    @Benchmark
    public void writeOverheadDirectShort(Blackhole bh) {
        // Baseline: direct accept with pre-converted code points
        encoder.accept(SHORT_TEXT_CP);
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    public void writeOverheadConvertShort(Blackhole bh) {
        // Connection.write() path: convert String then accept
        encoder.accept(Parser.toCodePoints(SHORT_TEXT));
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    public void writeOverheadDirectMedium(Blackhole bh) {
        encoder.accept(MEDIUM_TEXT_CP);
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    public void writeOverheadConvertMedium(Blackhole bh) {
        encoder.accept(Parser.toCodePoints(MEDIUM_TEXT));
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    public void writeOverheadDirectLong(Blackhole bh) {
        encoder.accept(LONG_TEXT_CP);
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    public void writeOverheadConvertLong(Blackhole bh) {
        encoder.accept(Parser.toCodePoints(LONG_TEXT));
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    public void writeOverheadDirectVeryLong(Blackhole bh) {
        encoder.accept(VERY_LONG_TEXT_CP);
        bh.consume(outputBuffer.length());
    }

    @Benchmark
    public void writeOverheadConvertVeryLong(Blackhole bh) {
        encoder.accept(Parser.toCodePoints(VERY_LONG_TEXT));
        bh.consume(outputBuffer.length());
    }
}
