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

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.aesh.readline.Prompt;
import org.aesh.readline.Readline;
import org.aesh.readline.completion.CompleteOperation;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.history.InMemoryHistory;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.BaseDevice;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Device;
import org.aesh.terminal.EventDecoder;
import org.aesh.terminal.Key;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
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
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Benchmarks for Readline API performance.
 * <p>
 * Tests the full readline stack including history navigation,
 * completion handling, and line editing workflows.
 *
 * @author Aesh Team
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class ReadlineBenchmark {

    // Test inputs - varying complexity
    private static final int[] CHAR_SHORT = "ls".codePoints().toArray();
    private static final int[] CHAR_HELLO = "hello world".codePoints().toArray();
    private static final int[] CHAR_COMMAND = "ls -la /home/user".codePoints().toArray();
    private static final int[] CHAR_LONG_COMMAND = "find /var/log -name '*.log' -mtime +7 -exec gzip {} \\;".codePoints().toArray();
    private static final int[] CHAR_PIPELINE = "cat file.txt | grep pattern | sort | uniq -c".codePoints().toArray();
    private static final int[] CHAR_WITH_QUOTES = "echo \"Hello $USER\" && echo 'test'".codePoints().toArray();
    private static final int[] CHAR_PATH = "/home/user/projects/myapp/src/main/java/App.java".codePoints().toArray();
    private static final int[] CHAR_MULTIWORD = "one two three four five six seven eight nine ten".codePoints().toArray();

    // Control sequences
    private static final int[] ARROW_UP = new int[] { 27, 91, 65 };
    private static final int[] ARROW_DOWN = new int[] { 27, 91, 66 };
    private static final int[] ARROW_RIGHT = new int[] { 27, 91, 67 };
    private static final int[] ARROW_LEFT = new int[] { 27, 91, 68 };
    private static final int[] BACKSPACE = new int[] { 127 };
    private static final int[] DELETE = new int[] { 27, 91, 51, 126 };
    private static final int[] ENTER = new int[] { 10 };
    private static final int[] TAB = new int[] { 9 };
    private static final int[] HOME = new int[] { 27, 91, 72 };
    private static final int[] END = new int[] { 27, 91, 70 };

    // Emacs control keys
    private static final int[] CTRL_A = new int[] { 1 };   // beginning of line
    private static final int[] CTRL_B = new int[] { 2 };   // backward char
    private static final int[] CTRL_D = new int[] { 4 };   // delete char
    private static final int[] CTRL_E = new int[] { 5 };   // end of line
    private static final int[] CTRL_F = new int[] { 6 };   // forward char
    private static final int[] CTRL_K = new int[] { 11 };  // kill to end
    private static final int[] CTRL_U = new int[] { 21 };  // kill to start
    private static final int[] CTRL_W = new int[] { 23 };  // kill word backward
    private static final int[] CTRL_Y = new int[] { 25 };  // yank

    // Meta keys for word movement
    private static final int[] META_B = new int[] { 27, 98 };  // backward word
    private static final int[] META_F = new int[] { 27, 102 }; // forward word
    private static final int[] META_D = new int[] { 27, 100 }; // kill word forward

    private InMemoryHistory history;
    private List<Completion> completions;

    // Pre-created Readline instances (created once, reused across invocations)
    private Readline readlineDefault;
    private Readline readlineEmacs;
    private Readline readlineVi;
    private Readline readlineWithHistory;
    private Readline readlineWithCompletions;

    // Pre-created Connection (created once, reset for each invocation)
    private BenchmarkConnection connection;

    @Setup(Level.Trial)
    public void setupTrial() {
        // Setup completions
        completions = new ArrayList<>();
        completions.add(new Completion() {
            @Override
            public void complete(CompleteOperation completeOperation) {
                String input = completeOperation.getBuffer();
                if (input.startsWith("l")) {
                    completeOperation.addCompletionCandidate("ls");
                    completeOperation.addCompletionCandidate("less");
                    completeOperation.addCompletionCandidate("locate");
                }
                if (input.startsWith("c")) {
                    completeOperation.addCompletionCandidate("cd");
                    completeOperation.addCompletionCandidate("cat");
                    completeOperation.addCompletionCandidate("chmod");
                }
            }
        });

        // Create Readline instances once (simulates real application startup)
        readlineDefault = new Readline(EditModeBuilder.builder().build());
        readlineEmacs = new Readline(EditModeBuilder.builder(EditMode.Mode.EMACS).build());
        readlineVi = new Readline(EditModeBuilder.builder(EditMode.Mode.VI).build());

        // History for history-enabled readline
        history = new InMemoryHistory(100);
        history.enable();
        history.push("ls -la".codePoints().toArray());
        history.push("cd /home/user".codePoints().toArray());
        history.push("cat file.txt".codePoints().toArray());
        history.push("grep pattern file".codePoints().toArray());
        history.push("make build".codePoints().toArray());

        readlineWithHistory = new Readline(EditModeBuilder.builder().build(), history, null);
        readlineWithCompletions = new Readline(EditModeBuilder.builder().build());

        // Create connection once
        connection = new BenchmarkConnection();
    }

    @Setup(Level.Invocation)
    public void setupInvocation() {
        // Reset connection state for each invocation
        connection.reset();
    }

    // ========== Readline Workflow Benchmarks ==========

    @Benchmark
    public void readlineSimpleInput(Blackhole bh) {
        readlineDefault.readline(connection, new Prompt(": "), line -> bh.consume(line));
        connection.sendInput(CHAR_HELLO);
        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineLongCommand(Blackhole bh) {
        readlineDefault.readline(connection, new Prompt(": "), line -> bh.consume(line));
        connection.sendInput(CHAR_LONG_COMMAND);
        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlinePipelineCommand(Blackhole bh) {
        readlineDefault.readline(connection, new Prompt(": "), line -> bh.consume(line));
        connection.sendInput(CHAR_PIPELINE);
        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineWithBasicEditing(Blackhole bh) {
        readlineDefault.readline(connection, new Prompt(": "), line -> bh.consume(line));
        connection.sendInput(CHAR_HELLO);
        connection.sendInput(ARROW_LEFT);
        connection.sendInput(ARROW_LEFT);
        connection.sendInput(BACKSPACE);
        connection.sendInput(CTRL_A);  // beginning of line
        connection.sendInput(CTRL_E);  // end of line
        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineWithComplexEditing(Blackhole bh) {
        readlineDefault.readline(connection, new Prompt(": "), line -> bh.consume(line));
        connection.sendInput(CHAR_LONG_COMMAND);

        connection.sendInput(CTRL_A);
        connection.sendInput(META_F);
        connection.sendInput(META_F);
        connection.sendInput(META_F);

        connection.sendInput(META_D);

        connection.sendInput(META_B);
        connection.sendInput(META_B);

        connection.sendInput(CTRL_W);

        connection.sendInput(CTRL_E);
        connection.sendInput(BACKSPACE);
        connection.sendInput(BACKSPACE);

        connection.sendInput(" --verbose".codePoints().toArray());

        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineWithCursorJumping(Blackhole bh) {
        readlineDefault.readline(connection, new Prompt(": "), line -> bh.consume(line));

        // Type multiword input
        connection.sendInput(CHAR_MULTIWORD);

        // Jump around extensively
        connection.sendInput(CTRL_A);  // beginning
        connection.sendInput(CTRL_E);  // end
        connection.sendInput(CTRL_A);  // beginning
        connection.sendInput(CTRL_F);  // forward char
        connection.sendInput(CTRL_F);  // forward char
        connection.sendInput(CTRL_F);  // forward char
        connection.sendInput(CTRL_B);  // backward char
        connection.sendInput(META_F);  // forward word
        connection.sendInput(META_F);  // forward word
        connection.sendInput(META_B);  // backward word
        connection.sendInput(CTRL_E);  // end

        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineWithKillAndYank(Blackhole bh) {
        readlineDefault.readline(connection, new Prompt(": "), line -> bh.consume(line));

        // Type multiword input
        connection.sendInput(CHAR_MULTIWORD);

        // Kill to end, then yank it back
        connection.sendInput(CTRL_A);
        connection.sendInput(META_F);  // forward word
        connection.sendInput(META_F);  // forward word
        connection.sendInput(CTRL_K);  // kill to end
        connection.sendInput(CTRL_Y);  // yank back

        // Kill to start
        connection.sendInput(CTRL_A);
        connection.sendInput(META_F);
        connection.sendInput(CTRL_U);  // kill to start

        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineHistoryNavigation(Blackhole bh) {
        readlineWithHistory.readline(connection, new Prompt(": "), line -> bh.consume(line));

        // Navigate through history extensively
        connection.sendInput(ARROW_UP);
        connection.sendInput(ARROW_UP);
        connection.sendInput(ARROW_UP);
        connection.sendInput(ARROW_UP);
        connection.sendInput(ARROW_UP);
        connection.sendInput(ARROW_DOWN);
        connection.sendInput(ARROW_DOWN);
        connection.sendInput(ARROW_DOWN);
        connection.sendInput(ARROW_UP);
        connection.sendInput(ARROW_UP);
        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineHistoryWithEditing(Blackhole bh) {
        readlineWithHistory.readline(connection, new Prompt(": "), line -> bh.consume(line));

        // Get history entry and modify it
        connection.sendInput(ARROW_UP);
        connection.sendInput(ARROW_UP);

        // Edit the history entry
        connection.sendInput(CTRL_E);  // end of line
        connection.sendInput(" | head -10".codePoints().toArray());

        // More navigation
        connection.sendInput(CTRL_A);
        connection.sendInput(META_F);
        connection.sendInput(CTRL_D);  // delete char

        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineWithCompletion(Blackhole bh) {
        readlineWithCompletions.readline(connection, new Prompt(": "), line -> bh.consume(line), completions);

        // Type partial command and trigger completion
        connection.sendInput("l".codePoints().toArray());
        connection.sendInput(TAB);
        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineWithMultipleCompletions(Blackhole bh) {
        readlineWithCompletions.readline(connection, new Prompt(": "), line -> bh.consume(line), completions);

        // Type, complete, continue typing, complete again
        connection.sendInput("l".codePoints().toArray());
        connection.sendInput(TAB);
        connection.sendInput(" ".codePoints().toArray());
        connection.sendInput("c".codePoints().toArray());
        connection.sendInput(TAB);
        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineKillOperations(Blackhole bh) {
        readlineDefault.readline(connection, new Prompt(": "), line -> bh.consume(line));

        // Type, then use various kill operations
        connection.sendInput(CHAR_LONG_COMMAND);

        // Kill word backward multiple times
        connection.sendInput(CTRL_W);
        connection.sendInput(CTRL_W);
        connection.sendInput(CTRL_W);

        // Move and kill to end
        connection.sendInput(CTRL_A);
        connection.sendInput(META_F);
        connection.sendInput(CTRL_K);

        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineDeleteOperations(Blackhole bh) {
        readlineDefault.readline(connection, new Prompt(": "), line -> bh.consume(line));

        // Type command
        connection.sendInput(CHAR_COMMAND);

        // Use various delete operations
        connection.sendInput(BACKSPACE);
        connection.sendInput(BACKSPACE);
        connection.sendInput(CTRL_A);
        connection.sendInput(CTRL_D);  // delete forward
        connection.sendInput(CTRL_D);
        connection.sendInput(META_D);  // delete word forward

        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineViModeBasic(Blackhole bh) {
        readlineVi.readline(connection, new Prompt(": "), line -> bh.consume(line));

        // Simulate typing and enter in vi insert mode
        connection.sendInput(CHAR_COMMAND);
        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineViModeWithEditing(Blackhole bh) {
        readlineVi.readline(connection, new Prompt(": "), line -> bh.consume(line));

        // Type in insert mode
        connection.sendInput(CHAR_COMMAND);

        // Switch to command mode
        connection.sendInput(new int[] { 27 });  // ESC

        // Vi movement commands
        connection.sendInput(new int[] { 'h' });  // left
        connection.sendInput(new int[] { 'h' });  // left
        connection.sendInput(new int[] { 'l' });  // right
        connection.sendInput(new int[] { '0' });  // beginning of line
        connection.sendInput(new int[] { '$' });  // end of line
        connection.sendInput(new int[] { 'b' });  // back word
        connection.sendInput(new int[] { 'w' });  // forward word

        // Delete
        connection.sendInput(new int[] { 'x' });  // delete char

        // Back to insert mode and finish
        connection.sendInput(new int[] { 'a' });  // append
        connection.sendInput(ENTER);
    }

    @Benchmark
    public void readlineEmacsModeWithEditing(Blackhole bh) {
        readlineEmacs.readline(connection, new Prompt(": "), line -> bh.consume(line));

        // Full emacs editing workflow
        connection.sendInput(CHAR_LONG_COMMAND);

        // Navigation
        connection.sendInput(CTRL_A);
        connection.sendInput(CTRL_F);
        connection.sendInput(CTRL_F);
        connection.sendInput(CTRL_B);
        connection.sendInput(META_F);
        connection.sendInput(META_B);
        connection.sendInput(CTRL_E);

        // Editing
        connection.sendInput(CTRL_W);
        connection.sendInput(CTRL_Y);

        connection.sendInput(ENTER);
    }

    // ========== History Benchmarks ==========

    @Benchmark
    public void historyPush(Blackhole bh) {
        InMemoryHistory hist = new InMemoryHistory(500);
        hist.enable();
        for (int i = 0; i < 100; i++) {
            hist.push(("command " + i).codePoints().toArray());
        }
        bh.consume(hist.size());
    }

    @Benchmark
    public void historySearch(Blackhole bh) {
        // Search through history
        int[] result = history.find("cat file.txt".codePoints().toArray());
        bh.consume(result);
    }

    @Benchmark
    public void historyNavigation(Blackhole bh) {
        // Navigate up and down through history
        for (int i = 0; i < 5; i++) {
            bh.consume(history.previousFetch());
        }
        for (int i = 0; i < 3; i++) {
            bh.consume(history.nextFetch());
        }
    }

    // ========== EditMode Benchmarks ==========

    @Benchmark
    public void editModeEmacsCreate(Blackhole bh) {
        EditMode mode = EditModeBuilder.builder(EditMode.Mode.EMACS).build();
        bh.consume(mode);
    }

    @Benchmark
    public void editModeViCreate(Blackhole bh) {
        EditMode mode = EditModeBuilder.builder(EditMode.Mode.VI).build();
        bh.consume(mode);
    }

    @Benchmark
    public void editModeParse(Blackhole bh) {
        EditMode mode = EditModeBuilder.builder().build();
        bh.consume(mode.parse(Key.a));
        bh.consume(mode.parse(Key.CTRL_A));
        bh.consume(mode.parse(Key.UP));
    }

    // ========== Throughput Benchmarks ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void readlineSessionThroughput(Blackhole bh) {
        readlineDefault.readline(connection, new Prompt(": "), line -> bh.consume(line));
        connection.sendInput(CHAR_HELLO);
        connection.sendInput(ENTER);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void historyOperationsThroughput(Blackhole bh) {
        InMemoryHistory hist = new InMemoryHistory(100);
        hist.enable();
        hist.push("command".codePoints().toArray());
        bh.consume(hist.previousFetch());
    }

    /**
     * Minimal Connection implementation for benchmarking.
     */
    private static class BenchmarkConnection implements Connection {
        private final Device device = new BaseDevice("ansi");
        private final Size size = new Size(80, 24);
        private EventDecoder eventDecoder = new EventDecoder();
        private Consumer<int[]> stdoutHandler = ints -> {};
        private Consumer<Size> sizeHandler;
        private Consumer<Void> closeHandler;
        private Attributes attributes = new Attributes();

        /**
         * Reset connection state for reuse between benchmark iterations.
         */
        public void reset() {
            eventDecoder = new EventDecoder();
            stdoutHandler = ints -> {};
            sizeHandler = null;
            closeHandler = null;
            attributes = new Attributes();
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
            return Charset.defaultCharset();
        }

        @Override
        public Charset outputEncoding() {
            return Charset.defaultCharset();
        }

        @Override
        public boolean supportsAnsi() {
            return true;
        }

        public void sendInput(int[] data) {
            eventDecoder.accept(data);
        }
    }
}
