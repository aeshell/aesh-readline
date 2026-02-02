# Aesh Readline Benchmarks

This module contains JMH (Java Microbenchmark Harness) benchmarks for measuring the performance of the Aesh Readline library.

## Building the Benchmarks

The benchmark module is not part of the default build. To build it, use the `benchmark` profile:

```bash
mvn package -Pbenchmark -DskipTests
```

Or build just the benchmark module with its dependencies:

```bash
mvn package -pl benchmark -am -DskipTests
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
```

### Run Specific Benchmark Methods

```bash
# Single benchmark
java -jar benchmark/target/benchmarks.jar ActionDecoderBenchmark.singleCharacter

# Multiple benchmarks using regex
java -jar benchmark/target/benchmarks.jar "ActionDecoderBenchmark.(singleCharacter|arrowKey)"
```

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

## Tips

1. **Consistent environment**: Close other applications, disable power management
2. **Warm up the JVM**: Use default warmup iterations or increase them
3. **Multiple forks**: Use at least 2 forks to account for JIT compilation variance
4. **Watch for outliers**: Large error margins may indicate environmental issues
5. **Profile hotspots**: Use `-prof stack` to identify optimization opportunities
