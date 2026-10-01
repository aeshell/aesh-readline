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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.tty.impl.LineDisciplineTerminal;
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
 * Bulk output through line-discipline terminals (#349).
 * <p>
 * Without OPOST/ONLCR postprocessing, bulk writes pass through in one
 * call; with both, newline runs batch around CR-LF expansions. Payloads
 * cover paste, log, and image sizes with mixed newlines and UTF-8 text.
 * Pure stream path -- no PTY or terminal needed.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Thread)
public class LineDisciplineOutputBenchmark {

    @Param({ "4096", "65536", "1048576" })
    int size;

    @Param({ "raw", "opost", "onlcr" })
    String mode;

    byte[] payload;
    LineDisciplineTerminal terminal;
    ByteArrayOutputStream sink;

    @Setup(Level.Trial)
    public void setup() throws IOException {
        StringBuilder text = new StringBuilder();
        while (text.length() < size) {
            text.append("log line with some content 0123456789\n");
            text.append("mixed \u00e9\u4e2d\ud83d\ude00 unicode tail\n");
        }
        byte[] full = text.toString().getBytes(StandardCharsets.UTF_8);
        payload = new byte[size];
        System.arraycopy(full, 0, payload, 0, size);
        sink = new ByteArrayOutputStream(size + 1024);
        terminal = new LineDisciplineTerminal("bench", "test", sink);
        Attributes attributes = terminal.getAttributes();
        attributes.setOutputFlag(Attributes.OutputFlag.OPOST, !mode.equals("raw"));
        attributes.setOutputFlag(Attributes.OutputFlag.ONLCR, mode.equals("onlcr"));
        terminal.setAttributes(attributes);
    }

    @Benchmark
    public int writeBulk() throws IOException {
        sink.reset();
        terminal.output().write(payload);
        return sink.size();
    }
}
