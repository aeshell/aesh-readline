# Local detection baseline, 2026-10-24

Production code is `c779c1e6`. The measurement-only sources in this change
add a fresh-process helper, a controlled reply fixture, and warm JMH tests.
No detection behavior or runtime dependencies changed.

This is a Linux x86-64 baseline on an AMD Ryzen 7 7800X3D with 16 logical
CPUs, normal boost behavior, and no CPU pinning. It is not a portable startup
budget or a Quarkus boot measurement.

## Cold processes

Each variant runs 20 fresh processes per scenario in seeded randomized order.
Stdin is null, stdout/stderr are pipes, and each POSIX child has a detached
session. Terminal hints are `TERM=xterm-256color` and `COLORFGBG=15;0`, with
other terminal identity/multiplexer variables removed. The filesystem cache
is warm. JVM heaps are 128 MB initial/maximum; the native image has a 128 MB
maximum heap and its default initial-heap behavior.

Values below are medians in milliseconds. Factory timers include result
publication where applicable. They do not include all process startup or
argument/output work.

| Operation | Temurin 21.0.6 | Temurin 25.0.4 | Temurin 25.0.4, native access enabled | GraalVM CE 25.0.1 executable |
| --- | ---: | ---: | ---: | ---: |
| Environment-only first call | 1.724 | 1.728 | 1.754 | 0.117 |
| First shared lookup | 1.731 | 1.743 | 1.751 | 0.123 |
| Builtin headless full detection | 12.191 | 9.747 | 49.465 | 1.167 |
| Connection construction | 49.606 | 36.720 | 35.648 | 3.532 |
| Reader submission | 1.073 | 1.273 | 1.279 | 0.016 |
| Connection close | 0.084 | 0.102 | 0.095 | 0.009 |
| No-op helper process, total lifetime | 32.984 | 20.891 | 22.856 | 1.772 |

Do not subtract independent medians from the process control and attribute
the remainder to a particular library method. The helper includes verification
and JSON output; process lifetime and in-main operation costs answer different
questions. Reader submission is not proof that the worker has started.

Synthetic full/async cases use immediate in-memory replies. Every full and
async-to-full case verified exactly one open, query write, and close, a
populated palette, and probed modes. The unavailable custom transport verified
zero sessions. Those counts do not instrument arbitrary subprocess/file/native
operations in the builtin paths, which retain an explicit unobserved count.

For the async synthetic case, median factory return/subsequent full-completion
costs were 2.076/2.510 ms on JDK 21, 2.250/2.544 ms on JDK 25, and
0.138/0.119 ms in the native image. Fixture construction/transport injection
has a separate setup timer and is excluded from those values. These are not
terminal-response latency measurements.

The native image was built from the packaged multi-release JAR with the same
named helper entry point, not from exploded classes. A separate runtime check
changed identity to Ghostty, appearance to light, and truecolor hints after
image creation; the executable observed all three changes.

## Warm CPU and allocation costs

Temurin 25.0.4, JMH 1.37, one worker, three independent forks, five one-second
warmups, ten one-second measurements, 256 MB heaps, and `-prof gc`.
The state contains fully populated synthetic results created before timing.
Each trial checked that its one discovery session was not repeated.

Errors are JMH's reported 99.9% confidence half-widths. Times are optimized
per-operation loop costs, not isolated end-to-end API latency. `scalarFacts`
is one block of seven getter calls.

| Operation | ns/op, score +/- error | Allocated B/op |
| --- | ---: | ---: |
| Shared instance lookup | 0.295 +/- 0.001 | No measured allocation |
| Seven scalar facts | 2.052 +/- 0.003 | No measured allocation |
| Foreground RGB defensive copy | 2.771 +/- 0.015 | 32 |
| Fresh environment detection | 380.782 +/- 3.180 | 1128 |
| Full palette defensive copy | 182.544 +/- 1.301 | 1472 |
| Conversion to `TerminalColorCapability` | 446.025 +/- 1.822 | 3488 |

Keep the cache and scalar path separate from snapshot conversion. Reusing a
detected instance has no measured allocation here; repeatedly detecting or
rebuilding a complete color model does. This supports conversion once per
capability revision, not removing defensive ownership to save copies.

## Diagnostics and regression controls

The headless JDK 25/native-access case initializes `FfmPosix$Handles` before
falling back to stty. A separate class-initialization trace confirms this.
The otherwise identical disabled-native-access control does not initialize
that holder. The source opens `/dev/tty` through those handles, so an absent
controlling terminal is discovered after binding initialization. This is a
specific cost to evaluate under #378, not a reason to change backend policy
without interactive/mintty coverage. Trace-run timings are excluded above.

Separate JFR allocation profiles point to snapshot construction for repeated
environment detection and to palette array copies/builders during color-model
conversion. Allocation samples are not a complete byte census; the table uses
the GC profiler's measured per-operation totals. Profile-run timing scores are
not mixed into the baseline.

JDK 21, JDK 25, and the native image reject an intentionally skipped synthetic
probe instead of reporting it as a fast successful round. The native runtime
environment check also guards against build-host terminal facts being frozen.

## Artifacts and reproduction

Build and invocation recipes are in `benchmark/README.md`, under "Running the
local detection baselines". Raw local artifacts are retained at:

- `/tmp/opencode/379-baseline-final/`: all four cold variants, metadata, raw
  samples, collector snapshots, and a copy of their packaged JAR.
- `/tmp/opencode/379-native-final-build/`: native executable, build log,
  command, and JAR hash.
- `/tmp/opencode/379-local-jdk25-warm/`: JMH JSON, full log, command, and
  environment metadata.
- `/tmp/opencode/379-local-jdk25-profile/`: JFR recordings and allocation
  summary. This is a separate profiling run.
- `/tmp/opencode/379-headless-class-init.log` and
  `/tmp/opencode/379-headless-no-native-class-init.log`: diagnostic controls.

Artifact hashes and runtime versions identify each run. The warm artifact's
helper diagnostics differ from the final cold helper; the benchmark methods,
reply fixture, and production code are unchanged.

Still unmeasured here: macOS/Windows normal-startup costs, a responsive real
terminal, remote endpoints, future subscription contracts, and actual Quarkus
startup. Do not apply this machine's sub-millisecond numbers as shared-CI gates.
