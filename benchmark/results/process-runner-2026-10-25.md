# Subprocess lifecycle foundation, 2026-10-25

Baseline: `fc4f67ea`. This local #378 slice hardens the existing
`ProcessRunner.execute(...)` interface without changing backend selection,
connection wiring, the passive core, or independent fd-specific TTY probes.

## Reproduced defects and changes

The original capture loop stopped reading after 1 MB. A real child JVM writing
3 MB to stderr reached its startup marker but timed out after ten seconds
instead of finishing. Capture now discards excess bytes while continuing to
drain. The same fixture completes normally, reports its completion marker,
and returns exactly the capture cap.

A second child launches a descendant with inherited output handles, verifies
that descendant started, then exits. The baseline returned after its five-second
join but left a drainer alive. The new drainer uses reported-available bytes
and an owner stop signal instead of waiting for descendant EOF. Readiness and
read share the stream monitor to exclude the JVM's exit-time drain between
those operations. The stop signal is observed before readiness, so a final
child write cannot be lost between an empty readiness check and an exit check.

Normal work now shares one monotonic budget across elapsed launch time, child
waiting, and drain completion. Timeout/interruption requests termination and
uses one cleanup allowance for both drainer join and child reap. Interrupt
status is retained, and unconfirmed cleanup throws rather than reporting success.

The existing five-second cleanup safety allowance is retained and documented
separately. OS launch and platform pipe operations cannot be forcibly
interrupted through this Java 8 interface. This is not a hard end-to-end
enrichment deadline; that still requires the owner/transport cancellation
contract and work off latency-critical caller threads.

## Execution evidence

- Eleven runner/lifecycle tests pass on JDK 11, 21, and 25. They cover normal
  exit, finite/infinite output, merged stderr, missing binaries, timeout,
  direct-child lifetime, descendant-held pipes, and repeated interruption.
- The two child-JVM regressions fail behaviorally on the isolated baseline
  classes on every JDK. A separate finite-over-cap POSIX shell regression also
  fails on the baseline and passes the candidate, including the native image.
  Missing binaries, skipped fixtures, and compilation failures do not satisfy
  these controls.
- Startup/completion bytes and process-lifetime file locks prove fixture
  execution and direct-child termination. Three interrupted attempts verify
  retained interrupt status and no new surviving runner threads.
- Java 8 cannot portably terminate an arbitrary grandchild tree. The test
  explicitly releases the descendant after checking the runner's result.
  That release is fixture cleanup, not a claim that production killed the
  descendant.
- The JDK 21 detection suite passes 202 tests without skips. The full JDK 25
  reactor reports 2011 tests, zero failures/errors, and 50 unrelated platform
  skips. Existing per-fd and transport tests remain in place.
- GraalVM CE 25.0.1 verification passes 191 tests and explicitly aborts eleven
  JVM-only cases: seven previous loader/instrumentation cases and four new
  child-JVM cases. The POSIX shell over-cap case executes natively. These
  results are not live Windows/mintty execution evidence.

## Ordinary command cost

`ProcessRunnerBaseline` measures real launch/capture wall time, not a CPU
microbenchmark or complete terminal startup. It verifies exact output after
each timed invocation and prints raw samples after all commands finish.

Linux x86-64 / Ryzen 7 7800X3D, Temurin JVMs, 128 MB heaps, no CPU pinning.
Each variant has five fresh parent JVMs, alternated in a seeded shuffled order.
The 13-byte case uses 100 warmups plus 200 samples per fork; the 16 KB case
uses 50 warmups plus 100 samples. Commands use `/bin/sh` builtin `printf`.

Mean of the five per-fork means, milliseconds:

| Output | JVM | Baseline | Candidate |
| --- | --- | ---: | ---: |
| 13 bytes | JDK 21.0.6 | 1.1442 | 1.1572 |
| 13 bytes | JDK 25.0.4 | 1.1591 | 1.1538 |
| 16 KB | JDK 21.0.6 | 1.5511 | 1.5932 |
| 16 KB | JDK 25.0.4 | 1.5551 | 1.5776 |

These local comparisons range from -0.005 to +0.042 ms per call. Most fork
ranges overlap; the JDK 21 16 KB fork means show an increase of about 0.042 ms,
roughly 2.7%. This is an observed cost, not a speed improvement or a portable
regression threshold. The tradeoff removes the reproduced output deadlock and
surviving drainer. Raw samples are retained rather than attributing the small
differences to an unmeasured mechanism.

## Reproduction and scope

The helper invocation is documented in `benchmark/README.md`. Local artifacts:

- `/tmp/opencode/378-runner-proof/`: isolated baseline/candidate/test classes,
  sources, per-JDK behavioral logs, and proof metadata.
- `/tmp/opencode/378-runner-timings-final/`: command/runtime metadata and raw
  per-operation/per-fork timings for both output sizes.
- `/tmp/opencode/378-runner-full-reactor.log` and
  `/tmp/opencode/378-runner-full-reactor-counts.json`: full JVM verification.
- `/tmp/opencode/378-runner-native-final.log`: native verification.

Quality review still reports fixture/assertion overlaps and growth of the
low-level runner. The partial-output behavior on concurrent pipe-close I/O
errors is retained intentionally. Findings are not suppressed, and this is
not a clean structural-quality report.

#378 remains open. Shared round deadlines, deadline-aware transport cancellation,
publication/coalescing changes, and actual Quarkus/platform integration remain.
Connection attachment and automatic-getter wiring stay in #381.
