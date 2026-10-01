# Aesh Readline Benchmarks

This module contains JMH (Java Microbenchmark Harness) benchmarks for measuring the performance of the Aesh Readline library.

## Building the Benchmarks

The benchmark module is not part of the default build. To build it, use the `benchmark` profile:

```bash
mvn package -Pbenchmark -DskipTests
```

Or build just the benchmark module with its dependencies:

```bash
mvn package -Pbenchmark -pl benchmark -am -DskipTests
```

This creates an executable uber-jar at `benchmark/target/benchmarks.jar`.

## Running Benchmarks

### Run All Benchmarks

```bash
java -jar benchmark/target/benchmarks.jar
```

### Run Specific Benchmark Classes

```bash
# ActionDecoder benchmarks (key parsing)
java -jar benchmark/target/benchmarks.jar ActionDecoderBenchmark

# Readline API benchmarks
java -jar benchmark/target/benchmarks.jar ReadlineBenchmark

# TTY connection benchmarks
java -jar benchmark/target/benchmarks.jar TtyConnectionBenchmark

# Buffer operations benchmarks
java -jar benchmark/target/benchmarks.jar BufferBenchmark

# TUI output pipeline benchmarks
java -jar benchmark/target/benchmarks.jar TuiOutputBenchmark

# IntArrayBuilder benchmarks
java -jar benchmark/target/benchmarks.jar IntArrayBuilderBenchmark
```

### Run Specific Benchmark Methods

```bash
# Single benchmark
java -jar benchmark/target/benchmarks.jar ActionDecoderBenchmark.singleCharacter

# Multiple benchmarks using regex
java -jar benchmark/target/benchmarks.jar "ActionDecoderBenchmark.(singleCharacter|arrowKey)"
```

### POSIX terminal probe transports (#297)

`ProbeTransportWarmBenchmark` compares repeated stty and FFM raw-mode
session open/close. `ProbeTransportColdBenchmark` times one first session
per fresh JVM fork. Its FFM parameter initializes `Linker.nativeLinker()`
in trial setup when `linkerInitialized=true`, leaving the probe's own
downcall handles cold. Both require a controlling terminal; FFM also
requires Java 22+ and native access. They do not measure OSC response
latency or JVM/framework startup.

### Probe backend policy: one-shot versus reused startup (#351)

End-to-end `detectFull()` wall time per probe backend, forcing FFM or
stty through the public transport override (unresponsive PTY, theme
from environment, so both legs pay identical parse/theme costs).
One-shot CLI legs are fresh JVMs; warm legs re-probe in-process.
Measured on Linux x86_64, Temurin 25.0.4 (JVM) and GraalVM 25.0.1
(native image built from the packaged JARs):

| Workload | FFM (JVM) | stty (JVM) | FFM (native) | stty (native) |
|----------|-----------|------------|--------------|---------------|
| one-shot CLI `detectFull` | ~10 ms | ~10 ms | unavailable | ~2 ms |
| warm re-probe mean | 2-3 ms | 3-5 ms | unavailable | 1-2 ms |

Notes: the native FFM transport reports unavailable because
`Module.isNativeAccessEnabled()` is false inside the image (ABI and
`/dev/tty` gates pass), so native selection falls through to stty —
which works at ~2ms. Session-only rows are unchanged from #297
(cold FFM ~35ms with Linker/handles vs stty ~6ms; warm FFM 0.005ms
vs stty 2.43ms).

Decision: no policy change. One-shot CLI time is dominated by JVM
startup (~100ms+), dwarfing the ~millisecond backend delta; warm
reuse already prefers FFM, which wins by ~2.4ms per probe; native
images take stty by necessity, so a prefer-stty property would be a
no-op there and prewarming has nothing to warm. Portability is
preserved by the existing fallback order, and no production property
is introduced (the test-only override proposal stays in #296).

```bash
mvn clean package -Pbenchmark -pl benchmark -am -DskipTests

# Linux: script provides a controlling PTY to JMH and its forked JVMs.
script -qefc 'java -jar benchmark/target/benchmarks.jar ProbeTransportWarmBenchmark \
  -jvmArgsAppend --enable-native-access=ALL-UNNAMED -prof gc \
  -rf json -rff probe-warm.json' /dev/null
script -qefc 'java -jar benchmark/target/benchmarks.jar ProbeTransportColdBenchmark \
  -jvmArgsAppend --enable-native-access=ALL-UNNAMED -prof gc \
  -rf json -rff probe-cold.json' /dev/null
```

For an existing interactive PTY, run the `java -jar` commands directly.
The shaded benchmark JAR carries `Multi-Release: true` so the Java 22
FFM transport loads from `META-INF/versions/22`.

Measured on Linux x86_64, Temurin 25.0.4, JMH 1.37 under a `script`
PTY, with `-prof gc` and the benchmark annotations' fork/warmup settings:

| Session open/close | Mean ± JMH error (ms/op) |
|--------------------|--------------------------|
| stty, first use (10 forks) | 6.073 ± 0.395 |
| FFM, first use (10 forks) | 35.085 ± 1.167 |
| FFM, Linker initialized elsewhere (10 forks) | 30.214 ± 1.453 |
| stty, warm (3 forks, 10 measured iterations) | 2.432 ± 0.012 |
| FFM, warm (3 forks, 10 measured iterations) | 0.0050 ± 0.0001 |

The Linker accounts for only part of the measured cold FFM cost here;
the probe still creates its downcall handles on first use. Repeat these
measurements on the deployment JVM rather than treating this machine's
numbers as a startup budget for another process.

### FfmSyscallBenchmark (per-syscall wrapper cost, #348)

Measures the FFM PTY wrappers through the public `Pty` API: idle
`poll` (`peekIdle`), `ioctl` (`getSizeLoop`), and a
`tcgetattr`/`tcsetattr` round-trip (`attrCycle`). Every wrapper used to
allocate a confined arena plus an errno capture segment per call while
no caller ever read the captured errno. Run under a controlling PTY:

```bash
script -qefc 'java -jar benchmark/target/benchmarks.jar FfmSyscallBenchmark \
  -jvmArgsAppend --enable-native-access=ALL-UNNAMED -prof gc \
  -rf json -rff ffm-syscall.json' /dev/null
```

Measured on Linux x86_64, Temurin 25.0.4, JMH 1.37 (3 forks,
10 measured iterations, 99.9% CIs), before/after dropping the unread
capture (kept only for `open`, which now reports errno):

| Leg | Before (µs/op) | After (µs/op) | Alloc before → after (B/op) |
|-----|----------------|---------------|------------------------------|
| peekIdle | 0.5064 ± 0.0030 | 0.4913 ± 0.0021 | 0.02 → 0.02 |
| getSizeLoop | 0.4416 ± 0.0053 | 0.4225 ± 0.0042 | 72.0 → 72.0 |
| attrCycle | 2.2388 ± 0.0069 | 2.1580 ± 0.0093 | 2024.0 → 1712.0 |

Per-call allocation was already ~zero on HotSpot (escape analysis
scalarizes the non-escaping confined arenas — confirmed by a 25s
async-profiler allocation profile of the peek loop with zero samples),
so the gain is a few nanoseconds of eliminated setup per call plus
312 B/op on the attribute round-trip. The remaining bytes are the
`Attributes` object graph and the returned `Size`, which legitimately
escape. No input-data leg exists: the read wrapper's allocation shape
is identical with or without bytes flowing, and staging bytes on a PTY
slave from inside the measured JVM is not possible.

### LineDisciplineOutputBenchmark (bulk output batching, #349)

Bulk writes through line-discipline terminals into a byte-array
backend (the conservative case -- a pipe or socket backend pays a
syscall per call instead of a method call). Payloads cover paste,
log, and image sizes with mixed newlines and UTF-8 text; modes are
`raw` (OPOST off), `opost` (OPOST without ONLCR), and `onlcr`
(OPOST with ONLCR). No PTY needed:

```bash
java -jar benchmark/target/benchmarks.jar LineDisciplineOutputBenchmark \
  -prof gc -rf json -rff line-out.json
```

Measured on Linux x86_64, Temurin 25.0.4, JMH 1.37 (3 forks,
10 measured iterations, 99.9% CIs), before/after batching:

| Mode | Size | Before (µs/op) | After (µs/op) |
|------|------|----------------|---------------|
| raw | 4 KB | 20.069 ± 0.154 | 0.050 ± 0.002 |
| raw | 64 KB | 289.988 ± 1.815 | 0.900 ± 0.004 |
| raw | 1 MB | 4508.260 ± 9.045 | 17.421 ± 0.166 |
| opost | 1 MB | 4582.077 ± 18.075 | 17.355 ± 0.154 |
| onlcr | 4 KB | 18.383 ± 0.066 | 2.107 ± 0.010 |
| onlcr | 64 KB | 302.357 ± 1.833 | 40.161 ± 0.144 |
| onlcr | 1 MB | 4800.805 ± 20.505 | 621.699 ± 2.337 |

### Common Options

```bash
# Quick run (fewer iterations)
java -jar benchmark/target/benchmarks.jar -wi 2 -i 3 -f 1

# List available benchmarks
java -jar benchmark/target/benchmarks.jar -l

# Output results to JSON
java -jar benchmark/target/benchmarks.jar -rf json -rff results.json

# Output results to CSV
java -jar benchmark/target/benchmarks.jar -rf csv -rff results.csv
```

### JMH Options Reference

| Option | Description |
|--------|-------------|
| `-wi <int>` | Warmup iterations (default: 5) |
| `-i <int>` | Measurement iterations (default: 10) |
| `-f <int>` | Number of forks (default: 2) |
| `-t <int>` | Number of threads |
| `-l` | List available benchmarks |
| `-rf <type>` | Result format: text, csv, json, scsv |
| `-rff <file>` | Result file path |
| `-prof <profiler>` | Use profiler: gc, stack, perf, async |

## Benchmark Classes

### FfmSyscallBenchmark

Per-syscall cost of the FFM PTY wrappers through the public `Pty` API
(see the results table above). Requires a controlling PTY and native
access. No input-data leg: the read wrapper's allocation shape is
identical with or without bytes flowing.

| Benchmark | Description |
|-----------|-------------|
| `peekIdle` | Idle `poll` with zero timeout, no data waiting |
| `getSizeLoop` | Window-size `ioctl(TIOCGWINSZ)` per call |
| `attrCycle` | `tcgetattr` plus `tcsetattr` round-trip |

### LineDisciplineOutputBenchmark

Bulk output through line-discipline terminals across OPOST modes
and paste/log/image sizes (see the results table above). No PTY needed.

| Benchmark | Description |
|-----------|-------------|
| `writeBulk` | Bulk `write(byte[])` at the parameterized size and mode |

### SixelEncodeBenchmark

Sixel encoding across image shapes (photo, logo, narrow, large
fixtures PNG-encoded once in setup; every invocation encodes fresh,
bypassing the output cache). Measured on Linux x86_64, Temurin
25.0.4, JMH 1.37 (2 forks, 5 measured iterations, 99.9% CIs),
before/after the sampling, memo, banding, and presence changes:

| Fixture | Before (ms/op) | After (ms/op) |
|---------|----------------|---------------|
| photo 640x480 gradient+noise | 168.47 ± 2.14 | 121.84 ± 1.62 |
| logo 640x480 flat blocks | 11.90 ± 0.19 | 9.74 ± 0.12 |
| narrow 120x1200 stripes | 5.49 ± 0.05 | 3.59 ± 0.05 |
| large 1280x800 gradient | 271.32 ± 6.66 | 48.59 ± 0.99 |

### ActionDecoderBenchmark

Measures the performance of key sequence parsing in `ActionDecoder`. This is critical for input handling as every keystroke goes through this path.

| Benchmark | Description |
|-----------|-------------|
| `singleCharacter` | Single printable character (default mappings) |
| `singleCharacterEmacs` | Single character with Emacs mode |
| `singleCharacterVi` | Single character with Vi mode |
| `arrowKey` | Arrow key escape sequence (ESC [ A) |
| `arrowKeyEmacs` | Arrow key with Emacs mode |
| `functionKey` | Function key sequence (F12) |
| `multipleCharacters` | Word input ("hello") |
| `typingSimulation` | Realistic typing with edits |
| `controlKeys` | Control key (Ctrl+C) |
| `tabCompletion` | Tab key for completion |
| `keystrokeThroughput` | Throughput measurement |

### ReadlineBenchmark

Measures the performance of the full Readline API including line editing, history, and completion.

| Benchmark | Description |
|-----------|-------------|
| `readlineSimpleInput` | Basic typing and enter |
| `readlineWithEditing` | Typing with cursor movement |
| `readlineHistoryNavigation` | History up/down navigation |
| `readlineWithCompletion` | Tab completion workflow |
| `readlineKillLine` | Ctrl+K kill to end of line |
| `readlineKillWord` | Ctrl+W backward word kill |
| `readlineViMode` | Input in Vi mode |
| `readlineEmacsMode` | Input in Emacs mode |
| `historyPush` | Adding entries to history |
| `historySearch` | Finding entries by content |
| `historyNavigation` | Navigating through history |
| `editModeEmacsCreate` | Emacs mode creation |
| `editModeViCreate` | Vi mode creation |
| `editModeParse` | Key parsing in edit mode |

### TtyConnectionBenchmark

Measures the performance of terminal I/O operations including encoding, decoding, and event processing.

| Benchmark | Description |
|-----------|-------------|
| `decoderSimpleText` | Decode short text (5 chars) |
| `decoderMediumText` | Decode medium text (~45 chars) |
| `decoderLongText` | Decode long text (~180 chars) |
| `decoderUnicodeText` | Decode multi-language Unicode |
| `decoderEscapeSequence` | Decode single escape sequence |
| `decoderMultipleEscapeSequences` | Decode multiple arrow keys |
| `decoderMixedInput` | Decode text with escape sequences |
| `encoderSimpleText` | Encode short text |
| `encoderMediumText` | Encode medium text |
| `encoderLongText` | Encode long text |
| `encoderUnicodeText` | Encode Unicode text |
| `eventDecoderSingleKey` | Process single key event |
| `eventDecoderControlKey` | Process control key |
| `eventDecoderArrowKey` | Process arrow key |
| `ansiStripCodes` | Strip ANSI escape codes |
| `parserToCodePoints` | String to code points |
| `parserFromCodePoints` | Code points to string |
| `simulatedTypingSession` | Realistic typing simulation |
| `simulatedCommandLine` | Full command line simulation |
| `writeOverheadDirectShort` | Direct accept with pre-converted code points (baseline) |
| `writeOverheadConvertShort` | Connection.write() path with String conversion |
| `writeOverheadDirectMedium` | Direct accept medium text (baseline) |
| `writeOverheadConvertMedium` | Connection.write() path medium text |
| `writeOverheadDirectLong` | Direct accept long text (baseline) |
| `writeOverheadConvertLong` | Connection.write() path long text |
| `writeOverheadDirectVeryLong` | Direct accept ~1KB text (baseline) |
| `writeOverheadConvertVeryLong` | Connection.write() path ~1KB text |

### BufferBenchmark

Measures the performance of buffer operations used for line editing.

| Benchmark | Description |
|-----------|-------------|
| `insertSingleCharacter` | Insert one character |
| `insertWord` | Insert a word |
| `insertCharacterByCharacter` | Insert text char by char |
| `insertAtBeginning` | Insert at buffer start |
| `insertAtEnd` | Insert at buffer end |
| `insertInMiddle` | Insert in middle of buffer |
| `moveCursorLeft/Right` | Cursor movement |
| `deleteBackward/Forward` | Delete operations |
| `deleteWord` | Delete word backward |
| `clearBuffer` | Clear entire buffer |
| `copyBuffer` | Copy buffer contents |

### TuiOutputBenchmark

Measures the full TUI output pipeline: Buffer ANSI generation -> IntArrayBuilder -> Encoder -> OutputStream. Existing benchmarks use `NO_OP_CONSUMER` for output, so the output pipeline is never measured. These benchmarks exercise it with realistic TUI workloads.

| Benchmark | Description |
|-----------|-------------|
| `bufferInsertWithCapture` | Buffer.insert() with int[] capture consumer |
| `bufferInsertWithEncoding` | Buffer.insert() chained through Encoder |
| `bufferInsertWithEncodingAndStream` | Full pipeline to ByteArrayOutputStream |
| `encoderAnsiHeavy` | Pre-built ANSI-dense int[] fed to Encoder |
| `fullScreenRedraw` | 24 lines of colored text with cursor positioning |
| `partialScreenUpdate` | Update 3 of 24 lines (dirty region pattern) |
| `rapidRedraws` | 10 consecutive full redraws (scrolling simulation) |
| `largeOutputBurst` | Write ~4KB ANSI block in one shot |
| `writeUnbuffered` | 24 lines via separate Encoder.accept() calls |
| `writeWithBufferedStream` | Same with BufferedOutputStream(8192) |
| `writeBatchedIntArrays` | All 24 lines batched into single Encoder.accept() |
| `writeManySmallWrites` | Per-character writes through Connection.write() |
| `bufferReplaceEntireLine` | Replace full 80-char line (status bar pattern) |
| `bufferReplaceWithEncoding` | Same with full encoding pipeline |
| `fullScreenRedrawThroughput` | Throughput variant of fullScreenRedraw |

### IntArrayBuilderBenchmark

Micro-benchmarks for `IntArrayBuilder`, the dynamic int[] builder used in all ANSI sequence construction. Quantifies the cost of the default growth strategy (capacity 1, grows by `2*len + 2`) versus pre-sized builders.

| Benchmark | Description |
|-----------|-------------|
| `appendSingleIntsFromEmpty` | 100 single-int appends to default builder |
| `appendSmallArraysFromEmpty` | 20 five-int array appends (100 total ints) |
| `appendOneArrayFromEmpty` | Single 100-int array append |
| `appendSingleIntsPreSized` | 100 single-int appends to pre-sized(100) builder |
| `appendSmallArraysPreSized` | 20 five-int arrays to pre-sized builder |
| `appendOneArrayPreSized` | Single 100-int array to pre-sized builder |
| `simulatePromptOutput` | Prompt ANSI + content + cursor sync (default) |
| `simulatePromptOutputPreSized` | Same, pre-sized to 200 |
| `simulateFullLinePrint` | 80-column line with color codes (default) |
| `simulateFullLinePrintPreSized` | Same, pre-sized to 128 |
| `toArraySmall` | toArray() with 10 ints |
| `toArrayMedium` | toArray() with 100 ints |
| `toArrayLarge` | toArray() with 1000 ints |
| `growFromEmptyParameterized` | Growth from empty (parameterized: 10-1000) |
| `growPreSizedParameterized` | Growth pre-sized (parameterized: 10-1000) |

## Comparing Results

To compare performance before and after changes:

```bash
# Run baseline
git checkout main
mvn package -pl benchmark -am -DskipTests
java -jar benchmark/target/benchmarks.jar -rf json -rff baseline.json

# Run with changes
git checkout feature-branch
mvn package -pl benchmark -am -DskipTests
java -jar benchmark/target/benchmarks.jar -rf json -rff feature.json

# Compare using JMH Compare (if installed)
# Or analyze the JSON files manually
```

## Profiling

JMH supports various profilers:

```bash
# GC profiler - shows allocation rates
java -jar benchmark/target/benchmarks.jar -prof gc

# Stack profiler - shows hot methods
java -jar benchmark/target/benchmarks.jar -prof stack

# Linux perf profiler (requires perf)
java -jar benchmark/target/benchmarks.jar -prof perf

# Async profiler (requires async-profiler)
java -jar benchmark/target/benchmarks.jar -prof async
```

## Interpreting Results

JMH outputs results in the following format:

```
Benchmark                              Mode  Cnt    Score    Error  Units
ActionDecoderBenchmark.singleCharacter avgt   20  189.860 ± 12.345  ns/op
```

- **Mode**: `avgt` = average time, `thrpt` = throughput
- **Cnt**: Number of measurement iterations
- **Score**: The measured value
- **Error**: 99.9% confidence interval
- **Units**: `ns/op` = nanoseconds per operation, `ops/ms` = operations per millisecond

Lower is better for `avgt` mode, higher is better for `thrpt` mode.

## Profiling for TUI Bottleneck Analysis

The TUI output benchmarks are designed for use with profilers to identify the root cause of TUI rendering lag.

### Recommended Profiling Commands

```bash
# CPU profiling with async-profiler flame graph
java -jar benchmark/target/benchmarks.jar "TuiOutputBenchmark.fullScreenRedraw$" \
  -prof "async:libPath=/path/to/libasyncProfiler.so;output=flamegraph;dir=profile-results"

# Allocation profiling
java -jar benchmark/target/benchmarks.jar "TuiOutputBenchmark.fullScreenRedraw$" \
  -prof "async:libPath=/path/to/libasyncProfiler.so;event=alloc;output=flamegraph;dir=profile-results"

# GC pressure (built-in, no external deps)
java -jar benchmark/target/benchmarks.jar "TuiOutputBenchmark.*" -prof gc

# Stack profiling (built-in)
java -jar benchmark/target/benchmarks.jar "TuiOutputBenchmark.fullScreenRedraw$" -prof stack
```

### Recommended Analysis Sequence

1. Run `fullScreenRedraw` vs `partialScreenUpdate` to measure absolute redraw cost
2. Run `writeBatchedIntArrays` vs `writeUnbuffered` to test if batching helps
3. Run `growFromEmpty*` vs `*PreSized*` to quantify IntArrayBuilder resize overhead
4. Run with `-prof gc` to check GC pressure from allocations
5. Use async-profiler allocation profiling to find the biggest allocators

### Quick Smoke Test

```bash
java -jar benchmark/target/benchmarks.jar "TuiOutputBenchmark.*" -wi 1 -i 2 -f 1
java -jar benchmark/target/benchmarks.jar "IntArrayBuilderBenchmark.*" -wi 1 -i 2 -f 1
```

### Full Run with JSON Output

```bash
java -jar benchmark/target/benchmarks.jar "TuiOutputBenchmark.*" -rf json -rff tui-results.json
java -jar benchmark/target/benchmarks.jar "IntArrayBuilderBenchmark.*" -rf json -rff builder-results.json
```

## Tips

1. **Consistent environment**: Close other applications, disable power management
2. **Warm up the JVM**: Use default warmup iterations or increase them
3. **Multiple forks**: Use at least 2 forks to account for JIT compilation variance
4. **Watch for outliers**: Large error margins may indicate environmental issues
5. **Profile hotspots**: Use `-prof stack` to identify optimization opportunities
