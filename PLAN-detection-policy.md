# Detection policy and ownership, #378

Status: passive core owner implemented; bounded enrichment and connection
wiring remain proposals. The agreed first production slice is core-only, with
public factories and passive reads as its test seams. Standalone defaults and
connection behavior remain unchanged. The baseline prerequisite #379 is closed;
continuing platform and integration checks remain in #377.

Production baseline: `782160ca`, with measurements of `c779c1e6` in
[`benchmark/results/detection-local-2026-10-24.md`](benchmark/results/detection-local-2026-10-24.md).
Keep Java 8 support and zero runtime dependencies in `terminal-detect`.

## Current work and side effects

| Entry or read | Work reachable today | Consequence for policy |
| --- | --- | --- |
| `TerminalCapabilities.detect()` | Environment/property reads and value construction. No terminal, file, process, native transport, or worker. Does not publish into the shared instance. | Retain as the independent no-I/O path on every OS. Environment hints do not prove an endpoint is interactive. |
| `getInstance()` | Returns the cached reference; on a miss, enters the class monitor and calls `detect()`. | A cold lookup can wait behind synchronous full detection holding that monitor. Do not promise nonblocking managed access through this miss path. |
| `invalidate()` / shared theme notification | Updates shared state under the class monitor; theme notification also locks the selected result. | These transitions can wait behind full detection too. Keep publication/update transitions separate from the I/O lifetime. |
| Capability scalar/RGB/palette getters | Reads captured fields; RGB/palette getters make defensive copies. | Missing facts must remain missing, never trigger an upgrade. Keep scalar reads O(1), and avoid repeated whole-model conversion. |
| `TerminalColorCapability.from(caps)` | Reads supplied capability values and copies/converts RGB/palette data. | No discovery, but not allocation-free. Reuse the converted value per revision rather than per log line. |
| `detectFull()` | Shares completed detection, joins async completion without a timeout, or computes full detection under the class monitor. | Publication must not own the I/O wait. A caller wait and a discovery deadline are different contracts. |
| `detectAsync()` | Environment scan, latch, and one daemon worker per shared async round. Worker runs terminal queries and optional IDE/OS fallback. | Quick return is not zero work or bounded completion. Preserve async-to-full reuse, not a second scheduler. |
| `TerminalColorQuery.queryFull()` | Selects an exclusive custom transport, or FFM, Win32, then stty. Availability/selection can perform filesystem checks and native binding initialization. Serializes the session under `PROBE_LOCK`. | Check permissions before loading or constructing transports, including before `isAvailable()`. The monitor acquisition has no deadline today. |
| Probe session | Opens an endpoint, saves/changes raw mode, writes mode/DA1/color queries, reads replies, possibly probes grapheme width, restores cursor/state, closes. | One round needs one deadline and one I/O owner. Current stream timeouts are not an overall deadline. |
| `TerminalDetector.detectIdeOrPlatformTheme()` | IDE/config filesystem lookups and reads, then platform subprocesses if unresolved. Linux can try two commands. | Permissions and remaining budget must cover fallback too. Unknown or a missing RGB value is not permission to try another source. |
| `ProcessRunner.execute()` | Charges launch, execution, and drain completion to one normal-work budget. Excess output is discarded while draining; the owned drainer does not wait for descendant EOF. Merges stderr. | A separate existing five-second cleanup allowance is shared by drain/reap. OS launch and pipe operations are not forcibly interruptible. This is not yet the hard overall enrichment budget. |
| `SttyProbeTransport` | Device checks, save/setup/restore subprocesses, and terminal file streams. | Each command currently has its own wait. Restoration is necessary cleanup, not permission to begin a new enrichment attempt. |
| `TerminalConnection` / `TerminalBuilder` | Live fd/handle checks, provider admission, PTY/console creation, attributes, device/size setup; explicit open starts the reader. | Necessary connection setup is not optional appearance discovery. A no-probe capability policy cannot promise that an explicitly requested interactive connection performs no transport work. |
| Device / `InfoCmp` initialization | Bundled resources, direct terminfo database reads, and an `infocmp` subprocess fallback on the full lookup path. Even `getDefaultInfoCmp()` can read an installed database for unbundled names. | Do not construct a device or load terminfo merely to consume supplied capability facts. |
| `TerminalConnection.terminal()` | Seeds shared modes and color depth into its existing `TerminalFeatures`. | Reuse the seeding. #381 must attach consumption to the selected owner rather than repeatedly consult a mutable process default. |
| `TerminalEnvironment` singleton | Captures startup color depth from the shared capability lookup. Injected environments use their own map. | Managed consumption must avoid a cold global lookup or environment reconstruction for missing supplied facts. |
| `TerminalFeatures.colorCapability()` | Converts color depth and environment appearance to a color model. | Finish supplied appearance/RGB/palette consumption in #381; conversion should occur once per revision. |
| `TerminalFeatures.getImageProtocol()` | Consults the device and can issue a DA1 query automatically. | This automatic fallback must obey managed suppression. Explicit `query*` remains a live query when permitted, never silently a cached answer. |
| `TerminalFeatures.query*()` | Uses the running connection reader, handler leases/theme handlers, query writes, waits, and raw-mode/restoration where needed. | Mediate through the current I/O owner or decline. Never respond to an owned reader by opening another standalone reader. |

This inventory covers the entry paths, not every syscall. Keep exact operation
counts in tests and keep required transport setup separate from discovery.
The runner's capture-cap and descendant-held-pipe defects are fixed in the
subprocess foundation below. Permission-aware round budgeting still must account
for its cleanup separately; a timeout on the work phase is not round completion.

## Smallest useful contract

Attach permission to the discovery owner for one endpoint generation. Do not
add a process-global enable/disable switch. #382 supplies authoritative endpoint
facts; #381 attaches the source and captured result to consumers. Neither needs
to invent another terminal prober.

There are three behaviors, not necessarily three enum constants:

1. Independent environment hints. `detect()` already supplies these without
   joining or publishing shared discovery. A caller that knows the endpoint is
   headless can choose this behavior without constructing providers.
2. Managed consumption. Read only the attached source's current result. Pending,
   absent, timed-out, and unsupported facts do not permit fallback. No source
   means unknown, not a lookup of an unrelated local singleton.
3. Explicit enrichment. The owner grants permitted work, selects the I/O route,
   and supplies one total budget. Starting it asynchronously and explicitly
   awaiting it are separate operations on the same round.

Permissions distinguish terminal queries from IDE/config reads and platform
subprocesses. Granting one does not implicitly grant all of them. Suppression
happens before provider/native transport loading, worker submission, or file
lookup. Consumer reads do not inspect permissions or allocate requests; they
read the result the owner already published.

Keep ownership and behavior separate. A complete attached result does not
grant input access; an unavailable attached transport does not authorize a
builtin fallback. `CI`, missing/dumb `TERM`, `MSYSTEM`, and POSIX `PWD` are
heuristics, never authoritative proof of absent TTYs. Standalone mintty must
continue to work without a Win32 console. Real fd 0/1/2 checks stay independent.

## Implemented passive core

`TerminalDetection` captures one result for a caller's terminal/session scope:

- `environmentOnly()` captures process hints using the existing fast detector,
  independently of shared discovery.
- `environmentOnly(map)` captures only the supplied map. It does not borrow
  process environment or properties, and later map changes do not affect it.
- `managed(result)` retains that exact reference. A pending producer may fill
  values later, but consumption never joins it or starts another round.
- `managed(null)` creates unknown facts from an empty environment. Null colors,
  empty palette, unknown theme and unqueried modes do not authorize fallback.
- `capabilities()` returns the stable reference in O(1), without allocation or
  I/O. Individual RGB/palette getters keep their existing defensive-copy costs.

Owners do not publish into, replace, or consult the default cache. They create
no scheduler, source/revision framework, subscription, or transport. A supplied
producer retains responsibility for its own work; capturing its result does not
grant input access or make several field reads an atomic revision snapshot.

Tests exercise the public factories and reads, including independent owners,
default replacement, pending-result consumption, and unrelated synchronous
full discovery held behind a gated read. The active fixture must execute and
finish exactly one availability check, session open, query write, and close.

Isolated test copies use ASM call-site spies for the operation catalog used by
the library: filesystem and resource access, subprocesses, terminal/session
I/O, native transport initialization, worker creation, waits, shared-default
access, and ambient metadata. Executable canaries verify each kind and confirm
that a caught exception cannot hide a request. No production bytecode is
rewritten; ASM is test-scoped and there is no runtime agent. JVM class loading
and the harness's own class-resource reads are outside that operation budget.
This is not an OS-wide syscall trace or a sandbox for arbitrary application code.

The JVM instrumentation tests explicitly abort in a native image. Separate
portable fail-on-use process/probe collaborators and the behavioral/concurrency
tests run there. Do not count the instrumentation aborts as native operation
coverage. Connection attachment/getter wiring stays in #381 as agreed.

Results and reproduction are in the
[passive core report](benchmark/results/detection-scoped-2026-10-24.md).

## One owned round

Reuse the current full/async computation and result application rules. Move
publication transitions outside I/O, with one in-flight round reference per
owner/generation. Concurrent requests join that round rather than selecting a
second transport or starting a worker for every caller.

The owner captures its transport and permissions when starting the round;
later changes to the legacy static transport setter cannot change that round
mid-flight. Existing static factories remain the simple standalone entry points.
Do not silently apply a caller's stricter policy to another owner's work.

A passive read never awaits a round. A bounded waiter can stop waiting without
cancelling a round owned by someone else. Refresh/replacement cancels through
the owner, and late publication must check generation identity. A timed-out
wait does not release raw-mode/input ownership or make a replacement reader
safe. Release ownership only after restoration/close actually finishes.

Preserve measured-RGB precedence, theme-event precedence, defensive array
ownership, and remote isolation. Do not introduce revision/subscription types
ahead of #381/#380. A static default belongs to its defining class loader; it
is not a cross-class-loader sharing mechanism.

## Subprocess lifecycle foundation

The existing `ProcessRunner.execute(...)` interface now keeps draining after
capture fills, discarding bytes beyond the cap instead of blocking a finite
child. Its drainer reads only reported-available bytes, excludes the JVM's
exit-time pipe drain between readiness and read, and stops after the direct
child exits without waiting for a descendant's EOF.

Launch elapsed time, child waiting, and drain completion share one monotonic
normal-work budget. Timeout/interruption retains the existing five-second
cleanup safety allowance, but drainer join and child reap share that allowance
rather than renewing it per phase. Unconfirmed cleanup throws instead of
reporting success. Interrupt status survives cleanup.

This is deliberately not presented as a hard end-to-end deadline. Java 8
cannot forcibly interrupt arbitrary OS launch/pipe operations or portably kill
an arbitrary grandchild tree. Permitted enrichment must run this work off the
latency-critical caller and retain ownership until actual cleanup completes.
The deadline/cancellation-aware transport contract remains a separate step.

Real child-JVM fixtures prove startup, output completion, direct-child lifetime,
and drainer termination. A deliberately surviving descendant holds inherited
pipes and a lifetime lock. The test explicitly stops that descendant afterward;
it does not claim the runner killed it. The JVM cases run on Windows as well
as POSIX, while a shell-only finite-over-cap regression also executes in the
POSIX native test image. Per-fd TTY probes remain on their independent path;
this runner's stderr merging must not be used to replace them.

Execution proof, limits, and local before/after command costs are in the
[subprocess report](benchmark/results/process-runner-2026-10-25.md).

## Budget requirements before adding a bounded factory

Create a monotonic deadline before transport selection. Pass its remaining
budget through lock acquisition, setup, reads, platform fallback, restoration,
and cleanup. Do not restart a timeout at each phase or round up an expired
budget into another attempt. Preserve interrupt status and always attempt
necessary restoration after a partial setup failure.

The existing `TerminalProbeTransport.open()` and `InputStream` contract cannot
express that guarantee. `isAvailable()`, `open()`, reads, and `close()` can each
block in a supplied implementation. Adding a timeout to `detectFull()` or
waiting on a future would bound only the caller while leaving work alive.

Before exposing bounded enrichment:

- Specify a deadline/cancellation-aware transport contract and implement it in
  each allowed backend. Legacy custom transports must not be described as
  bounded merely because they implement today's interface.
- Replace untimed probe-lock acquisition on this path with an interruptible,
  deadline-aware acquisition. Keep process-wide raw-mode exclusion when two
  standalone owners address the same local terminal.
- Correct subprocess lifetime accounting, including exited children whose
  descendants retain output handles, full stderr, and repeated cancellation.
  A deadline check alone cannot interrupt `ProcessBuilder.start()`.
  Preserve independent fd inheritance; a runner that forces stderr merging
  cannot replace an fd-2 liveness check.
- Account for cleanup within the total budget, reserving time before beginning
  new work. Document OS/file/native operations that cannot be forcibly
  interrupted. Keep them off latency-critical caller threads. If cleanup
  overruns, retain ownership and expose incomplete cleanup; do not claim the
  total completion deadline succeeded or launch replacement workers/readers.
- Use the running connection reader when ownership is already held, with an
  explicit mediated query or a decline. `captureStdin()` is handler management,
  not framework-wide cross-library arbitration; Quarkus owns that arbitration.

The no-I/O paths have an exact zero-discovery budget. Numerical enrichment and
cleanup budgets need backend measurements, not an arbitrary universal timeout.
The local 49.5 ms headless FFM result is a reason to gate selection early, not
permission to change automatic backend preference from one Linux result.

## Initial guard slice and next implementation gates

The initial slice in `f7e80402` added a JVM regression guard for the existing
environment-only module split. An isolated loader allows only the facade, environment detector,
and values. It rejects optional discovery module requests even if production
catches the error. Literal Linux/macOS/Windows detector fixtures include
`TERM=xterm-256color` and Git-Bash/CI markers. Public facade and passive reads
also run in the isolated loader, using their actual process environment.
An active-discovery control must trip the same guard through the real full
computation. This is not a Windows/macOS transport execution test.

The loader guard proves that current file/process/native discovery modules are
not entered. It does not intercept arbitrary new JDK filesystem calls or
worker construction inside an allowed class. It explicitly skips in a native
image because that image has no JVM class-loading lifecycle. These limits do
not satisfy all of #378's no-I/O acceptance tests.

Behavioral controls were run on JDK 11, 21, and 25 from isolated classpaths.
Changing `detect()` to call full computation fails all three passive test
cases. Adding a live query to the RGB getter fails those same cases. Both
mutations compile, and the active-discovery control still passes. The original
classes pass all four tests on each JDK. Sources, classes, commands, and logs
are retained under `/tmp/opencode/378-guard-proof/`; repository production
methods were not replaced for these checks.

That initial slice passed 185 detection tests on JDK 21 and 25. GraalVM CE 25.0.1
verification passed 181 tests and explicitly aborted the four JVM loader
guards. The packaged MRJAR also passed `jar --validate`. Native guard skips
are not evidence for the proposed native no-I/O policy.

Next independently testable changes, in order:

1. Review connection attachment with #381/#382, reusing the passive core above.
   Settle the deadline-aware transport contract before adding bounded enrichment.
2. Preserve the scoped no-I/O spies and extend native operation coverage and
   known-headless/consume-only integration tests when wiring consumers.
3. Refactor shared publication and coalescing without changing standalone
   results. Re-run cold barrier tests, async/full reuse, and invalidation races.
4. Implement one deadline through the allowed transports and subprocess paths.
   Prove stalled/filled/descendant-held pipe cleanup and no accumulated workers,
   including behavioral tests that fail with the deadline removed.
5. Wire connection setup and automatic getters, then validate mixed fd layouts,
   explicit streams, reader ownership, mintty/native Windows, and real PTYs.
6. Repeat the packaged JVM/native baselines and actual Quarkus integration.
   Keep native runtime hints unfrozen and count executed fixture/backend paths.

Only passive owner factories are implemented here. There is no enrichment
factory, connection wiring, new scheduler, global policy, or backend-default
change.
