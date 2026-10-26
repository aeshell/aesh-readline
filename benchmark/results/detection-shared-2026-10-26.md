# Shared discovery publication, 2026-10-26

Baseline facade: `7073fb85`. This #378 slice publishes pending environment hints
before full detection I/O and coalesces full/async callers onto one round.
Transport selection, fd checks, connection wiring, and passive owner factories
are unchanged. It does not implement bounded enrichment or cancellation.

## Behavior and proof

The publication monitor now protects only reference transitions and environment
construction. Synchronous producers compute inline outside it; async producers
start the existing daemon outside it. Both use the same computation. Passive
shared reads and async callers can consume pending synchronous results without
joining transport I/O. Theme changes and default replacement can also proceed.

Full detection still waits for completion. As approved, an interrupted waiter
finishes joining the same round and restores its interrupt flag instead of
starting another probe. Completion records failure separately: an unchecked
failure releases waiters but cannot become cached success or an automatic retry.
An explicit invalidation can request a new generation. Retired producers only
fill their captured result, never overwrite a replacement default.

- Seven public-interface regressions fail behaviorally against isolated baseline
  classes on JDK 11.0.30, 21.0.6, and 25.0.4. They cover pending hints, async reuse
  of synchronous work, theme-event precedence, invalidation, supplied-default
  replacement, retained failure, and interrupted joins. They compile against
  both implementations; no repository production file was swapped.
- The final shared/core/cold/no-I/O selection executes 51 tests on each JVM, with
  zero failures, ignored tests, or assumption aborts. Operation-count assertions
  require one availability check, open, write, and close per completed round.
- The fresh-child fixture releases eight mixed full/async callers before any
  facade factory/cache preflight. Class-init traces on all three JVMs place
  facade initialization after the barrier marker. Ten additional fresh races
  per JVM, constrained to two CPUs, each return one measured, closed session.
  Fixture transport setup initializes `TerminalColorQuery` before the gate, so
  this is cold facade publication coverage, not every class in the module cold.
- JDK 21 and 25 detection suites execute 209 tests without skips. The full JDK 25
  reactor reports 2018 tests, no failures/errors, and 50 unrelated platform skips.
- GraalVM CE 25.0.1 executes 197 native tests successfully and explicitly aborts
  12 JVM-only cases. Those include the new child-JVM cold-start test. They are
  not native cold-initialization or operation-spy coverage.
- Public bytecode signatures are identical. The facade/private helpers remain
  Java 8 bytecode, the MRJAR validates, and dependencies remain JDK-only,
  `java.base` and `java.logging`.

## Warm reads and allocation

Linux x86-64, Ryzen 7 7800X3D, Temurin 25.0.4, JMH 1.37. Each result has three
forks, five one-second warmups, ten one-second measurements, one thread, 256 MB
heaps, and `-prof gc`. Baseline/candidate order alternates between methods.
Trial setup and teardown verify exactly one discovery session. These are
optimized loop costs, not isolated API-call latencies.

Mean plus/minus JMH 99.9% error, nanoseconds per operation:

| Operation | Baseline | Candidate | Bytes/op, both |
| --- | ---: | ---: | ---: |
| Shared lookup | 0.2994 +/- 0.0018 | 0.3036 +/- 0.0033 | <0.001 |
| Completed full lookup | 0.4343 +/- 0.0201 | 0.4173 +/- 0.0021 | <0.001 |
| Completed async lookup | 0.3858 +/- 0.0019 | 0.3933 +/- 0.0023 | <0.001 |
| Independent environment result | 381.65 +/- 4.70 | 390.33 +/- 3.68 | 1128 |
| Captured owner-reference read | 0.3657 +/- 0.0067 | 0.3510 +/- 0.0040 | <0.001 |

The shared/full intervals overlap. The other comparisons do not: async lookup
is about 0.0075 ns higher, environment construction about 8.7 ns higher, and
captured-reference reads about 0.015 ns lower in this series. The last control's
implementation did not change. These observations do not establish a mechanism
or justify a blanket speedup/neutrality claim. No per-call allocation appears
on the cached reads; environment allocation is unchanged. New completion/failure
state belongs to round creation, which this warm table does not measure.

## Cold processes

The existing collector runs 20 fresh processes per scenario and variant, with
11 scenarios, for 1760 samples across JDK 21, JDK 25, JDK 25 with native access,
and the native executable. Stdin is null, output is piped, and POSIX sessions
are detached. Hints are controlled; JVM heaps are 128 MB. Native helpers use
GraalVM CE 25.0.1 with a 128 MB maximum heap and the actual named main class.

Local medians in milliseconds, baseline to candidate:

| Runtime | Synthetic full call | Synthetic async return | Subsequent full completion wait |
| --- | ---: | ---: | ---: |
| JDK 21 | 3.412 to 3.509 | 2.105 to 1.764 | 2.484 to 2.348 |
| JDK 25 | 3.549 to 3.666 | 2.248 to 1.723 | 2.380 to 2.469 |
| JDK 25, native access | 3.477 to 3.602 | 2.218 to 1.676 | 2.524 to 2.415 |
| Native image | 0.181 to 0.178 | 0.148 to 0.129 | 0.120 to 0.116 |

Fixture preparation is timed separately. Synthetic cases validate actual parsed
RGB/palette/mode replies and one open/write/close, not real raw-mode/transport
latency. The unavailable injected transport validates zero sessions.

Complete helper-process medians are separate. For JDK 25 the synthetic full
process is 31.402 to 31.289 ms, while the no-op process is 21.010 to 20.654 ms.
Do not subtract these independent medians to claim library startup time. The
collector retains setup, first-call, completion, cached-read, connection-close,
and whole-process measurements with raw ranges; twenty samples are not a
portable timing gate.

Headless builtin full detection with native access still takes about 49 ms on
JDK 25. Separate class-init diagnostics confirm FFM handles initialize before
the missing controlling terminal is discovered, both before and after. That
early-selection cost is not fixed here. Native runtime controls change terminal,
theme, and color hints after image build; results follow those runtime hints.
The inherited Windows marker tests hints, not Windows transport execution.

## Reproduction and remaining work

Build with `./mvnw -Pbenchmark clean package -Dmaven.javadoc.skip=true`, then use
the collector and warm JMH commands in `benchmark/README.md`. Select
`TerminalDetectionBenchmark.(sharedInstance|cachedFull|cachedAsync|environment|scopedRead)$`
for the warm table. Compare against the baseline facade using the same harness.

Local artifacts:

- `/tmp/opencode/378-shared-proof/` contains baseline/candidate classes and
  packaged jars, source/artifact hashes, behavioral failures, no-skip counts,
  public API/dependency checks, and the test-reach report. Both measurement jars
  share all entries except the facade implementation and its two new private
  helper classes.
- `/tmp/opencode/378-shared-cold-final/` contains post-barrier class-init traces,
  constrained cold races, commands, and CPU affinity.
- `/tmp/opencode/378-shared-measurements/` contains the exact driver, JMH JSON,
  raw cold samples, runtime/CPU/hash metadata, native hint controls, and separate
  headless class-init diagnostics.
- `/tmp/opencode/378-shared-native-build/` and
  `/tmp/opencode/378-shared-native-baseline-build/` retain image-build commands
  and logs. `/tmp/opencode/378-shared-native-final.log` records native tests.
- `/tmp/opencode/378-shared-full-reactor-final.log` and
  `/tmp/opencode/378-shared-jdk21-final.log` record the final JVM builds.

Structural checks still report 11 gated findings, mostly test growth/overlap and
generic short-body clone matches. The child fixture's orchestration and longer
interrupt regression also carry readability costs. Annotation-driven tests,
JMH methods, and the child main execute despite static dead-code candidates.
Findings are not suppressed, and this is not a clean structural-quality report.
The name-based test-reach report retains gaps in legacy connection, example,
and manual paths; passing the reactor is not Windows or Quarkus field evidence.

#378 stays open. Next is the deadline/cancellation-aware transport contract,
including permission checks before transport loading and ownership retained
through cleanup. Connection/getter integration remains #381, endpoint facts
#382, and real Windows/mintty/macOS plus Quarkus validation remain in #377.
