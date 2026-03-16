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

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.io.Encoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.IntArrayBuilder;
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
 * Benchmarks for the TUI output pipeline.
 * <p>
 * Measures the full output path: Buffer ANSI generation -> IntArrayBuilder -> Encoder -> OutputStream.
 * Existing benchmarks use NO_OP_CONSUMER for output, so the output pipeline is never measured.
 * Since the output path is the suspected primary bottleneck for TUI lag, these benchmarks
 * exercise it with realistic TUI workloads.
 *
 * @author Aesh Team
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class TuiOutputBenchmark {

    private static final int TERMINAL_WIDTH = 80;
    private static final int TERMINAL_HEIGHT = 24;
    private static final Consumer<int[]> NO_OP_CONSUMER = data -> {};

    // Test data
    private static final int[] WORD = "hello".codePoints().toArray();
    private static final int[] LINE_80 = generateLine(80);
    private static final int[] COLORED_LINE = buildColoredLine();
    private static final int[] ANSI_HEAVY_BLOCK = buildAnsiHeavyBlock();
    private static final int[] LARGE_ANSI_BLOCK = buildLargeAnsiBlock();
    private static final int[][] SCREEN_LINES = buildScreenLines();
    private static final String[] SCREEN_LINE_STRINGS = buildScreenLineStrings();

    // Capture consumer that stores int[] output
    private int[] capturedOutput;
    private final Consumer<int[]> captureConsumer = data -> capturedOutput = data;

    // Encoder pipeline components
    private ByteArrayOutputStream byteStream;
    private Encoder encoder;
    private Consumer<int[]> encoderConsumer;

    // Connection for full-pipeline tests
    private TuiBenchmarkConnection connection;
    private TuiBenchmarkConnection connectionWithEncoder;

    private Buffer buffer;

    @Setup(Level.Invocation)
    public void setup() {
        capturedOutput = null;
        byteStream = new ByteArrayOutputStream(8192);
        encoder = new Encoder(StandardCharsets.UTF_8, bytes -> writeBytes(byteStream, bytes));
        encoderConsumer = encoder;

        buffer = new Buffer(new Prompt("> "));

        connection = new TuiBenchmarkConnection(ints -> {});
        connectionWithEncoder = new TuiBenchmarkConnection(encoder);
    }

    private static void writeBytes(ByteArrayOutputStream stream, byte[] bytes) {
        try {
            stream.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // ========== Output Pipeline Isolation ==========

    @Benchmark
    public void bufferInsertWithCapture(Blackhole bh) {
        Buffer buf = new Buffer(new Prompt("> "));
        buf.insert(captureConsumer, WORD, TERMINAL_WIDTH);
        bh.consume(capturedOutput);
    }

    @Benchmark
    public void bufferInsertWithEncoding(Blackhole bh) {
        byteStream.reset();
        Buffer buf = new Buffer(new Prompt("> "));
        buf.insert(encoderConsumer, WORD, TERMINAL_WIDTH);
        bh.consume(byteStream.size());
    }

    @Benchmark
    public void bufferInsertWithEncodingAndStream(Blackhole bh) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream(256);
        Encoder enc = new Encoder(StandardCharsets.UTF_8, bytes -> writeBytes(stream, bytes));
        Buffer buf = new Buffer(new Prompt("> "));
        buf.insert(enc, WORD, TERMINAL_WIDTH);
        bh.consume(stream.toByteArray());
    }

    @Benchmark
    public void encoderAnsiHeavy(Blackhole bh) {
        byteStream.reset();
        encoder.accept(ANSI_HEAVY_BLOCK);
        bh.consume(byteStream.size());
    }

    // ========== Full-Screen TUI Scenarios ==========

    @Benchmark
    public void fullScreenRedraw(Blackhole bh) {
        byteStream.reset();
        // Simulate writing 24 lines of colored text with cursor positioning
        for (int row = 0; row < TERMINAL_HEIGHT; row++) {
            // Move cursor to line start: ESC[row;1H
            int[] cursorMove = buildCursorPosition(row + 1, 1);
            encoder.accept(cursorMove);
            // Write colored line content
            encoder.accept(SCREEN_LINES[row]);
        }
        bh.consume(byteStream.size());
    }

    @Benchmark
    public void partialScreenUpdate(Blackhole bh) {
        byteStream.reset();
        // Update only 3 of 24 lines (dirty region pattern)
        int[] dirtyRows = {5, 12, 23};
        for (int row : dirtyRows) {
            int[] cursorMove = buildCursorPosition(row + 1, 1);
            encoder.accept(cursorMove);
            encoder.accept(SCREEN_LINES[row]);
        }
        bh.consume(byteStream.size());
    }

    @Benchmark
    public void rapidRedraws(Blackhole bh) {
        byteStream.reset();
        // 10 consecutive full redraws (simulates scrolling, measures cumulative GC pressure)
        for (int frame = 0; frame < 10; frame++) {
            for (int row = 0; row < TERMINAL_HEIGHT; row++) {
                int[] cursorMove = buildCursorPosition(row + 1, 1);
                encoder.accept(cursorMove);
                encoder.accept(SCREEN_LINES[row]);
            }
        }
        bh.consume(byteStream.size());
    }

    @Benchmark
    public void largeOutputBurst(Blackhole bh) {
        byteStream.reset();
        // Write ~4KB ANSI block in one shot
        encoder.accept(LARGE_ANSI_BLOCK);
        bh.consume(byteStream.size());
    }

    // ========== Buffering Strategy Comparison ==========

    @Benchmark
    public void writeUnbuffered(Blackhole bh) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream(8192);
        Encoder enc = new Encoder(StandardCharsets.UTF_8, bytes -> writeBytes(stream, bytes));
        // 24 lines, each as a separate Encoder.accept() + stream.write() (current behavior)
        for (int row = 0; row < TERMINAL_HEIGHT; row++) {
            enc.accept(SCREEN_LINES[row]);
        }
        bh.consume(stream.size());
    }

    @Benchmark
    public void writeWithBufferedStream(Blackhole bh) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream(8192);
        BufferedOutputStream buffered = new BufferedOutputStream(stream, 8192);
        Encoder enc = new Encoder(StandardCharsets.UTF_8, bytes -> {
            try {
                buffered.write(bytes);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        // Same 24 lines, but stream wrapped in BufferedOutputStream(8192)
        for (int row = 0; row < TERMINAL_HEIGHT; row++) {
            enc.accept(SCREEN_LINES[row]);
        }
        try {
            buffered.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        bh.consume(stream.size());
    }

    @Benchmark
    public void writeBatchedIntArrays(Blackhole bh) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream(8192);
        Encoder enc = new Encoder(StandardCharsets.UTF_8, bytes -> writeBytes(stream, bytes));
        // Collect all 24 lines into one IntArrayBuilder, single Encoder.accept()
        IntArrayBuilder builder = new IntArrayBuilder(TERMINAL_HEIGHT * 100);
        for (int row = 0; row < TERMINAL_HEIGHT; row++) {
            builder.append(SCREEN_LINES[row]);
        }
        enc.accept(builder.toArray());
        bh.consume(stream.size());
    }

    @Benchmark
    public void writeManySmallWrites(Blackhole bh) {
        // Per-character writes through Connection.write(String) (worst case baseline)
        String line = "This is a typical TUI line with some content padding.";
        for (int row = 0; row < TERMINAL_HEIGHT; row++) {
            for (int i = 0; i < line.length(); i++) {
                connectionWithEncoder.write(String.valueOf(line.charAt(i)));
            }
        }
        bh.consume(byteStream.size());
    }

    @Benchmark
    public void fullScreenRedrawViaConnectionWrite(Blackhole bh) {
        byteStream.reset();
        // Same as fullScreenRedraw but using connection.write(String) like tamboui's aesh backend
        for (int row = 0; row < TERMINAL_HEIGHT; row++) {
            connectionWithEncoder.write("\u001b[" + (row + 1) + ";1H");
            connectionWithEncoder.write(SCREEN_LINE_STRINGS[row]);
        }
        bh.consume(byteStream.size());
    }

    @Benchmark
    public void fullScreenRedrawBufferedThenWrite(Blackhole bh) {
        byteStream.reset();
        // Matches real tamboui pattern: buffer into StringBuilder, single connection.write()
        StringBuilder sb = new StringBuilder(4096);
        for (int row = 0; row < TERMINAL_HEIGHT; row++) {
            sb.append("\u001b[").append(row + 1).append(";1H");
            sb.append(SCREEN_LINE_STRINGS[row]);
        }
        connectionWithEncoder.write(sb.toString());
        bh.consume(byteStream.size());
    }

    @Benchmark
    public void fullScreenRedrawViaOldWritePath(Blackhole bh) {
        byteStream.reset();
        // Simulates the old connection.write(String) path: String → int[] → Encoder
        for (int row = 0; row < TERMINAL_HEIGHT; row++) {
            String cursor = "\u001b[" + (row + 1) + ";1H";
            encoder.accept(Parser.toCodePoints(cursor));
            encoder.accept(Parser.toCodePoints(SCREEN_LINE_STRINGS[row]));
        }
        bh.consume(byteStream.size());
    }

    // ========== Buffer.replace() (common TUI operation) ==========

    @Benchmark
    public void bufferReplaceEntireLine(Blackhole bh) {
        // Replace full 80-char line (status bar/progress bar pattern)
        Buffer buf = new Buffer(new Prompt(""));
        // First insert initial content
        buf.insert(NO_OP_CONSUMER, LINE_80, TERMINAL_WIDTH);
        // Now replace with new content
        String replacement = String.format("Progress: [%-50s] %3d%%", "##################################################", 100);
        buf.replace(captureConsumer, replacement, TERMINAL_WIDTH);
        bh.consume(capturedOutput);
    }

    @Benchmark
    public void bufferReplaceWithEncoding(Blackhole bh) {
        byteStream.reset();
        // Same but with full encoding pipeline
        Buffer buf = new Buffer(new Prompt(""));
        buf.insert(NO_OP_CONSUMER, LINE_80, TERMINAL_WIDTH);
        String replacement = String.format("Progress: [%-50s] %3d%%", "##################################################", 100);
        buf.replace(encoderConsumer, replacement, TERMINAL_WIDTH);
        bh.consume(byteStream.size());
    }

    // ========== Throughput ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void fullScreenRedrawThroughput(Blackhole bh) {
        byteStream.reset();
        for (int row = 0; row < TERMINAL_HEIGHT; row++) {
            int[] cursorMove = buildCursorPosition(row + 1, 1);
            encoder.accept(cursorMove);
            encoder.accept(SCREEN_LINES[row]);
        }
        bh.consume(byteStream.size());
    }

    // ========== Helper Methods ==========

    private static int[] generateLine(int length) {
        int[] line = new int[length];
        for (int i = 0; i < length; i++) {
            line[i] = 'A' + (i % 26);
        }
        return line;
    }

    private static int[] buildColoredLine() {
        // ESC[0;31m + "Red text" + ESC[0;32m + " Green text" + ESC[0m
        IntArrayBuilder builder = new IntArrayBuilder(80);
        builder.append(Parser.toCodePoints(ANSI.RED_TEXT));
        builder.append("Red text".codePoints().toArray());
        builder.append(Parser.toCodePoints(ANSI.GREEN_TEXT));
        builder.append(" Green text".codePoints().toArray());
        builder.append(Parser.toCodePoints(ANSI.RESET));
        return builder.toArray();
    }

    private static int[] buildAnsiHeavyBlock() {
        // Pre-built ANSI-dense int[] with many color codes (isolates Encoder cost)
        IntArrayBuilder builder = new IntArrayBuilder(500);
        String[] colors = {ANSI.RED_TEXT, ANSI.GREEN_TEXT, ANSI.BLUE_TEXT,
                ANSI.YELLOW_TEXT, ANSI.CYAN_TEXT, ANSI.MAGENTA_TEXT};
        for (int i = 0; i < 20; i++) {
            builder.append(Parser.toCodePoints(colors[i % colors.length]));
            builder.append(("Item " + i + " ").codePoints().toArray());
        }
        builder.append(Parser.toCodePoints(ANSI.RESET));
        return builder.toArray();
    }

    private static int[] buildLargeAnsiBlock() {
        // ~4KB ANSI block
        IntArrayBuilder builder = new IntArrayBuilder(4096);
        String[] colors = {ANSI.RED_TEXT, ANSI.GREEN_TEXT, ANSI.BLUE_TEXT,
                ANSI.YELLOW_TEXT, ANSI.CYAN_TEXT, ANSI.MAGENTA_TEXT, ANSI.WHITE_TEXT};
        for (int i = 0; i < 100; i++) {
            builder.append(Parser.toCodePoints(colors[i % colors.length]));
            String content = String.format("Line %03d: This is content with ANSI colors  ", i);
            builder.append(content.codePoints().toArray());
            builder.append(Parser.toCodePoints(ANSI.RESET));
            // Newline
            builder.append(new int[]{'\n'});
        }
        return builder.toArray();
    }

    private static int[][] buildScreenLines() {
        // Build 24 lines of colored text for full-screen TUI simulation
        int[][] lines = new int[TERMINAL_HEIGHT][];
        String[] colors = {ANSI.RED_TEXT, ANSI.GREEN_TEXT, ANSI.BLUE_TEXT,
                ANSI.YELLOW_TEXT, ANSI.CYAN_TEXT, ANSI.MAGENTA_TEXT};
        for (int row = 0; row < TERMINAL_HEIGHT; row++) {
            IntArrayBuilder builder = new IntArrayBuilder(100);
            builder.append(Parser.toCodePoints(colors[row % colors.length]));
            String content = String.format("%-76s", "Line " + (row + 1) + ": TUI content with color codes");
            builder.append(content.codePoints().toArray());
            builder.append(Parser.toCodePoints(ANSI.RESET));
            lines[row] = builder.toArray();
        }
        return lines;
    }

    private static String[] buildScreenLineStrings() {
        String[] colors = {ANSI.RED_TEXT, ANSI.GREEN_TEXT, ANSI.BLUE_TEXT,
                ANSI.YELLOW_TEXT, ANSI.CYAN_TEXT, ANSI.MAGENTA_TEXT};
        String[] lines = new String[TERMINAL_HEIGHT];
        for (int row = 0; row < TERMINAL_HEIGHT; row++) {
            String content = String.format("%-76s", "Line " + (row + 1) + ": TUI content with color codes");
            lines[row] = colors[row % colors.length] + content + ANSI.RESET;
        }
        return lines;
    }

    private static int[] buildCursorPosition(int row, int col) {
        return ANSI.cursorPosition(row, col);
    }

    // ========== TuiBenchmarkConnection ==========

    /**
     * Connection implementation for TUI benchmarks.
     * Adapted from ReadlineBenchmark.BenchmarkConnection but with configurable
     * stdoutHandler that can chain through an Encoder.
     */
    private static class TuiBenchmarkConnection implements Connection {
        private final Device device = new BaseDevice("ansi");
        private final Size size = new Size(TERMINAL_WIDTH, TERMINAL_HEIGHT);
        private EventDecoder eventDecoder = new EventDecoder();
        private Consumer<int[]> stdoutHandler;
        private Consumer<Size> sizeHandler;
        private Consumer<Void> closeHandler;
        private Attributes attributes = new Attributes();

        TuiBenchmarkConnection(Consumer<int[]> stdoutHandler) {
            this.stdoutHandler = stdoutHandler;
        }

        @Override
        public Device device() {
            return device;
        }

        @Override
        public Size size() {
            return size;
        }

        @Override
        public Consumer<Size> sizeHandler() {
            return sizeHandler;
        }

        @Override
        public void setSizeHandler(Consumer<Size> handler) {
            this.sizeHandler = handler;
        }

        @Override
        public Consumer<Signal> signalHandler() {
            return eventDecoder.getSignalHandler();
        }

        @Override
        public void setSignalHandler(Consumer<Signal> handler) {
            eventDecoder.setSignalHandler(handler);
        }

        @Override
        public Consumer<int[]> stdinHandler() {
            return eventDecoder.getInputHandler();
        }

        @Override
        public void setStdinHandler(Consumer<int[]> handler) {
            eventDecoder.setInputHandler(handler);
        }

        @Override
        public Consumer<int[]> stdoutHandler() {
            return stdoutHandler;
        }

        @Override
        public void setCloseHandler(Consumer<Void> closeHandler) {
            this.closeHandler = closeHandler;
        }

        @Override
        public Consumer<Void> closeHandler() {
            return closeHandler;
        }

        @Override
        public void close() {
            if (closeHandler != null) {
                closeHandler.accept(null);
            }
        }

        @Override
        public void openBlocking() {
        }

        @Override
        public void openNonBlocking() {
        }

        @Override
        public boolean put(Capability capability, Object... params) {
            return device.puts(stdoutHandler, capability, params);
        }

        @Override
        public Attributes attributes() {
            return attributes;
        }

        @Override
        public void setAttributes(Attributes attr) {
            this.attributes = attr;
        }

        @Override
        public Charset inputEncoding() {
            return StandardCharsets.UTF_8;
        }

        @Override
        public Charset outputEncoding() {
            return StandardCharsets.UTF_8;
        }

        @Override
        public boolean supportsAnsi() {
            return true;
        }
    }
}
