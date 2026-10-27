# Endpoint context contract, #382

Status: interface approved on 2026-10-26. The creator-supplied context foundation,
the earlier stdin-or-stdout auto-attach rule, existing local/remote resize
population, and setup/accept-time population are implemented. Constructor and
provider configuration plus context-aware selection are still pending.
This does not complete
https://github.com/aeshell/aesh-readline/issues/382.

## Automatic attachment rule

- On first `TerminalConnection.terminal()` access without an explicit detection
  attachment, retain the shared capabilities when process stdin or stdout is
  a TTY. Otherwise capture independent environment hints.
- Keep explicit `attachDetection(owner)` authoritative. Neither automatic
  branch starts discovery; `TerminalCapabilities.getInstance()` already uses
  environment-only `detect()` on a cache miss.
- Leave independent fd 0/1/2 checks and provider handle/device validation alone.
  A lookup can populate an uninitialized `TtyDetect` cache. This change is not
  proof of fewer subprocesses or lower startup cost.
- Preserve the requested legacy ambient theme helper for unattached connections
  and ambient multiplexer wrapping in the connectionless `SixelImage` encoder.

Process descriptors do not identify arbitrary caller streams. In particular,
a `TerminalConnection` wrapping supplied streams or an existing `Terminal`
cannot be declared local merely because its Java class is `TerminalConnection`.
The current gate must not be presented as resolving that ambiguity.

## Implemented context foundation

`terminal-api` owns the immutable `EndpointContext`, exposed through the default
`Connection.endpointContext()` method. The default returns a shared unknown
context without probing. Repeated reads allocate nothing. Third-party
connections therefore keep working without inventing authoritative facts.

Creators build a context and supply it with `AbstractConnection.setEndpointContext`.
This is available on all concrete built-in connections. Passing null restores
unknown facts, and writes after close are ignored. Stream-backed constructors
also accept facts before setup so the handler observes them; the accepting
transports observe their own remote origin and need no creator parameter.
Consumers use the `Connection` getter rather than casts or reflection. A custom
connection may override it; a delegate forwards its wrapped connection's current
context instead of caching a snapshot.

Keep this context separate from `TerminalDetection`:

- `TerminalDetection` is the captured capability result used by passive reads.
- Endpoint context states which endpoint and streams those facts describe.
- Neither context nor capabilities grants input ownership or permission to run
  a query. Existing transport validation and enrichment permissions still apply.

The approved image precedence also needs an implementation check: device hints
beat attached hints, but measured replies should outrank both. The current
`getImageProtocol()` returns a non-NONE device protocol before inspecting the
attachment, so it does not yet implement the latter rule or expose the
provenance needed to apply it.

The reviewed interface does not add a process-global locality flag, infer origin
from implementation class, or retain a registry of connections. It is not yet
consumed by provider selection or the fd-default attachment rule above.

## Fact semantics

| Fact | Authoritative source | Unknown behavior |
| --- | --- | --- |
| Locality: local, remote, unknown | The accepting server or creator identifying its endpoint | Arbitrary streams and custom connections stay unknown. `SSH_TTY` alone does not mean the process serves a remote client. |
| Terminal status for each input/output/error stream | Caller-supplied facts about those exact streams, or observations on their actual fd/handle bindings | Missing facts stay unknown, not false or interactive. Process stdio facts are not substituted for unrelated streams. |
| Native console availability | The matching native-console transport observation | A failed Win32 console check does not rule out a mintty PTY. |
| ANSI/color capability | Endpoint device, configured capabilities, or measured replies with their existing precedence | It is not interchangeable with interactivity, locality, or a native-console predicate. |
| Dimensions and provenance | Negotiated remote size or local resize observation, then hints, then an explicitly identified fallback | An unknown measurement stays distinguishable from a default such as 120 by 40. |

Do not collapse a caller assertion, a transport observation, and an environment
hint into the same boolean. The interface should preserve the distinction needed by
consumers without exposing a generic evidence/revision framework prematurely.

The context has independent `input()`, `output()` and `error()` stream facts.
Each distinguishes UNKNOWN, YES and NO for terminal status and native-console
status, with separate provenance. A known answer requires CALLER, OBSERVED or
NEGOTIATED provenance. ENVIRONMENT and FALLBACK cannot assert either transport
predicate. These values report a creator's evidence; the context itself does
not validate it.

`dimensions()` is null when absent. An explicit size requires provenance, and
missing dimensions cannot be marked observed. `withDimensions(size, source)`
preserves other facts and leaves older snapshots unchanged. Caller-supplied,
observed and negotiated sizes outrank environment hints, which outrank fallbacks.
Equally authoritative updates replace the old size. A `Size` subclass may be
mutable despite the base type's final fields, so capture copies its values once;
reads retain the immutable copy.

## Population and lifecycle

1. Local system-terminal setup can declare its process endpoint local, even
   inside an SSH login. Reuse fd observations already available during setup.
2. Accepted SSH, Telnet, and HTTP/WebSocket connections declare remote origin
   in their accepting transport, not by inspecting host environment markers.
3. Stream-backed and externally supplied terminals default to unknown origin.
   Their creators may explicitly declare local redirection or a remote socket.
4. Supplied per-stream facts can avoid redundant classification only for the
   same bindings and lifetime. Opening a concrete PTY/console must still validate
   the device or handles it will actually use. No cached color or locality fact
   can replace that validation.
5. Reconnect/replacement gets a new context. Close hooks invalidate supplied
   facts on StreamConnection, TerminalConnection and the SSH/Telnet/HTTP
   connections. Late creator writes cannot revive them. Previously captured
   snapshots remain historical values, not permission to use the old endpoint.
   Custom AbstractConnection subclasses must call `closeEndpointContext()`
   during close; direct Connection implementations manage their own snapshots.

Keep dimensions in `terminal-api`, which already owns `Size`. Do not introduce
a reverse dependency from `terminal-detect`. Existing local WINCH and remote
resize handlers update only their endpoint's context; add no polling thread.
Reads return the current snapshot in O(1). Snapshot replacement may allocate
on creation or resize, never once per render.

## Staged implementation and checks

1. Done: review the context interface and unknown/provenance semantics above.
2. Implemented locally: creator-supplied context and safe third-party defaults.
   Tests use explicit-remote and unknown-origin streams, a custom Connection,
   wrapper forwarding, immutable snapshots and close invalidation. Supplying
   context remains separate from attaching capabilities or query permissions.
3. Close invalidation is wired in local, SSH, Telnet and HTTP connections and
   verified through each module's existing transport tests. Setup/accept-time
   population is implemented: stdio-built local connections declare LOCAL
   with observed per-fd facts (native console only when a console transport
   demonstrably owns the endpoint), while accepted SSH/Telnet/HTTP connections
   declare REMOTE with negotiated input/output facts. Stream-backed and
   externally supplied terminals stay unknown; stream-backed constructors
   accept creator facts before setup when the endpoint is known locally.
   Wrapper forwarding is covered for the delegating-override shape and the
   interface default inherited by direct Connection implementations.
4. Use the reviewed context for startup attachment and local-only theme
   forwarding. Declared LOCAL shares the startup default even fully
   redirected; declared REMOTE never does; unknown origins keep the
   fd gate. Only LOCAL endpoints forward theme reports to the shared
   default — unknown and remote reports still reach the application
   handler. Tested with conflicting local/remote facts; no remote event
   rewrites the local result.
5. Size provenance and snapshot replacement are implemented. Existing local
   WINCH and SSH/Telnet/WebSocket resize paths now publish observed or negotiated
   dimensions before the legacy callback. Initialization is specified, not
   queried: setup performs no size query (a local `getSize` can spawn subprocesses),
   so dimensions stay absent until the first size report; defaults such as 80x24
   are never recorded as measurements. Still test unknown versus fallback,
   independent client resize, and fresh dimensions after reconnect.
6. Preserve the per-fd matrix from #329 and Windows/mintty provider behavior.
   Add packaged JVM/native checks with runtime hints changed after image build.
   Record native aborts and field-only Windows cases rather than counting them
   as executed coverage. `WindowsEndpointLiveCheck` is the packaged-jar field
   harness colleagues run on real consoles (conhost, Windows Terminal, Git Bash,
   mintty, each plain and piped): per-fd TTY plus console-handle facts, provider
   pick, declared endpoint, attached theme/depth/image/size, a bounded full
   detection, a 30s theme-toggle observation, and a resize observation. Packaged
   jars are required — exploded classes hide the Java 22 FFM/Win32 overlays.

Measure added per-connection memory and construction cost against #377's
baseline once the representation exists. The fd-gating tests alone neither
complete #382 nor establish metadata-read, stream-origin, or resize guarantees.

## Verification of the context foundation

Baseline is `319b01cf`. The packaged JDK 25 reactor
(`-Pbenchmark clean package`) reports 2079 tests with no failures or errors
and 50 existing platform skips. The selected foundation set is 15
`EndpointContextTest`, 9 `NonBlockingApiTest` and 5
`TerminalConnectionEndpointTest` (29 total), plus 8 real-transport
close-invalidation cases (2 each in `NettyWebsocketTtyTest`,
`NettyAsciiTelnetTtyTest`, `NettyBinaryTelnetTtyTest` and `NettySshTtyTest`),
all executed with no skips.

Six isolated behavior mutations (origin collapse, size-priority ignore,
close-retain, local-default inference, hint-as-transport accept,
error-drop-on-resize) fail 1 to 4 assertions each on JDK 11, 21 and 25;
the candidate passes 29 of 29 on all three. Four transport
no-invalidation controls fail both close tests per transport while
candidates pass. Repository production sources are never replaced for
these controls. Logs are under `/tmp/opencode/382-context-final/behavior`
and `/tmp/opencode/382-context-final/transport-behavior`.

JMH on Temurin 25.0.4 (Ryzen 7 7800X3D, 3 forks, 5 warmup and 10
measurement iterations, `-prof gc`, JSON output):

- `createStream` baseline 186.271 +/- 3.920 ns/op versus candidate
  191.672 +/- 5.934 ns/op. Confidence intervals overlap, so no construction
  difference is resolved. Both allocate 4296 B/op.
- `readUnknown` 0.404 +/- 0.030 ns/op and `readDeclared`
  0.549 +/- 0.012 ns/op, both 0 B/op.
- `createContext` 4.327 +/- 0.839 ns/op and 64 B/op.

Raw JSON and logs are under `/tmp/opencode/382-context-final/jmh/`.

Shallow sizes via `Instrumentation.getObjectSize` (compressed oops,
256m heap, final jars): `StreamConnection` is 104 bytes before and after;
`EndpointContext` is 40, `StreamFacts` 32 and `Size` 24. The default
per-connection cost is the reference slot; a supplied context retains
40 plus 24 for the copied size and any referenced facts.

The named native probe rebuilt from the final jars passes UNKNOWN, LOCAL
and REMOTE creator cases with changed runtime SSH, terminal and theme
markers. The probe performs no terminal I/O; supplied facts are fixtures,
not live observations. Build log and results are under
`/tmp/opencode/382-context-final/native-probe-build.log` and
`native-probe.json`.
Packaged MRJARs validate through the reactor build. The full native
suites remain red on the clean tree for the two baseline artifact-check
failures described above, so they are not claimed as green.

## Verification of the fd-default slice

Baseline is `319b01cf`. The packaged JDK 25 reactor reports 2054 tests with no
failures/errors and 50 existing platform skips. The four new endpoint tests run
without ignored tests or assumption aborts on JDK 11, 21 and 25. Isolated,
compiled always-managed and always-hints mutations each fail exactly two
identity assertions on all three runtimes. Repository production source is not
replaced for those controls. The producer fixture must deliver its measured
reply and finish one availability check, open, write and close before teardown
restores globals. Both packaged terminal MRJARs validate.

The same four tests also pass in the native tty image built with mise's
GraalVM CE 25.0.4.1.1 and native-maven-plugin 1.1.16. The full native runs remain
red, including on an unchanged `319b01cf` worktree:

- `terminal-detect` fails `testPinnedHoldersExistAndHoldDowncalls` when reflective
  enumeration cannot find a static MethodHandle field on `FfmPosix$Handles`.
- `terminal-tty` fails `testWinConsoleNativeJniCoversFacade` on reflective JNI
  member lookup. The first missing member varies with enumeration order.

These are baseline artifact-check failures, not a green native verification or
proof of a new runtime binding defect. No assertion is suppressed or skipped to
get past them. The tty-only run uses previously installed unchanged detect/api
artifacts because the combined reactor stops at the detect failure.

Commands, source hashes, JVM logs, isolated mutation classes and the latest
native reports are retained under `/tmp/opencode/382-final-proof/`. Original
combined, clean-detect, and clean-tty native logs are
`/tmp/opencode/382-endpoint-native.log`, `/tmp/opencode/382-clean-native.log`,
and `/tmp/opencode/382-clean-tty-native.log`. The control worktree is
`/tmp/opencode/382-clean`.

## Verification of the setup/accept population slice

Baseline is `159e992f`. The packaged JDK 25 reactor reports 2109 tests with no
failures/errors and 50 existing platform skips. New coverage: local stdio setup
declares LOCAL with observed per-fd facts, stream-backed construction stays
unknown, and all four remote transports (SSH, both Telnet modes, WebSocket)
declare REMOTE with negotiated input/output facts through the shared
`TtyTestBase` assertion. Setup performs no size query, so fresh contexts carry
no dimensions until the first size report; local `getSize` can spawn
subprocesses, and transport defaults are not measurements.

Isolated mutations (declare-remote no-op, local-declaration no-op) fail the
matching tests while the stream-backed pin still passes; repository production
sources are never replaced for these controls. The endpoint suites and all
four remote-origin tests also pass on JDK 11.0.30 and 21.0.6 with no skips.
Structural review reports 2 gated findings: constructor-delegation shape and
test try/finally idiom duplication. Neither is a contract change, and the
`edit_check` pass reports no incompatible callers.

## Verification of the remaining contract items

Image precedence, dimension lifecycle, constructor supply, and wrapper-default
slices build on the baseline above. New coverage: a measured DA1 sixel reply
outranks a conflicting device identity while device hints still beat attached
unknowns without triggering a live query; defaults are never recorded as
measurements, resizes stay per-connection, and reconnects start dimension-free;
stream-backed constructors accept creator facts visible to the handler; direct
Connection implementations inherit the unknown default. The capture gate that
dropped measured sixel against non-NONE hints is removed: measured replies now
outrank hints by design, verified hermetically in this Ghostty-polluted shell
where the old gate discarded the fake reply.

Isolated mutations (image-precedence reorder, size-update no-op, constructor
drop, interface-default null) fail the matching tests. The full JDK 25 reactor
reports 2122 tests with no failures/errors and 50 existing platform skips; the
attachment/image/osc suites pass on JDK 11.0.30 and 21.0.6 with no skips.
`edit_check` reports no incompatible callers. `quality_delta` reports only
new-symbol test entry points, house device-mock and close-lifecycle
duplication idioms, and expected verbosity/churn — no complexity, public
parameter, or error-masking finding. This is not a clean structural-quality
report.

## Verification of the context-aware attachment and forwarding slice

Baseline is `decee4cd`. The packaged JDK 25 reactor reports 2113 tests with no
failures/errors and 50 existing platform skips. New coverage: declared LOCAL
shares the startup default even with both stdio fds pinned non-terminal, the
declared-context mapping outranks the fd gate in both directions, unknown
origins never rewrite the shared default while the application callback still
runs, and remote instance theme events stay off the process default. Existing
local-forwarding tests declare LOCAL explicitly under the new contract.
Isolated mutations (forward-regardless, fd-gate-only attachment) fail the
matching tests. The attachment, theme, and endpoint suites (19 tests) also pass
on JDK 11.0.30 and 21.0.6 with no skips.

Structural review reports 18 gated findings on the current tree: small
duplication groups in test setups, reflection helpers and the new immutable
copy-constructor shape, plus new-symbol size findings for the context,
benchmark and tests. No finding is suppressed or acked. None of the gated
rows is a contract change; the duplications are test/benchmark idioms and the
value-type copy shape, not shared logic to consolidate. The gated producer
test deliberately retains completion checks before restoring shared state.
This is not a clean structural-quality report.
