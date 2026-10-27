# Scoped capability subscriptions, #380

Status: library contract, existing publication paths, connection lifetime,
resize bridging, example, and warm benchmark are implemented. Actual Quarkus
integration and field validation remain under #377. The full native suites are
not claimed green while the baseline artifact guards in #388 remain open.

## API and ownership

`TerminalCapabilities.subscribe(listener)` and its `Executor` overload return a
`TerminalSubscription`. Registration replays one immutable `TerminalSnapshot`
synchronously. Later events use the publisher thread by default or the supplied
executor. The snapshot carries the owning result's revision and coherent theme,
RGB, palette, mode, and grapheme facts.

`TerminalDetection.subscribe(...)` offers the same operations through a scoped
owner. Closing an owner closes only subscriptions made through that owner.
Several owners can share one capability result and dispose their subscriptions
independently. Use a distinct owner for each independently closed connection or
application. Closing an owner does not invalidate passive reads or cancel
discovery. `TerminalDetection` and `TerminalSubscription` are `AutoCloseable`.

`AbstractConnection.attachDetection(...)` gives the attached owner the
connection's lifetime. Replacement or connection close detaches and closes its
subscriptions outside the connection monitor. Direct subscriptions on
`TerminalCapabilities` remain caller-owned and must be closed by their caller.

## Delivery rules

- Replay is synchronous even with an executor. A producer arriving between
  snapshot capture and replay queues its newer revision; it cannot overtake
  replay or be overwritten by it.
- Later revisions always use the selected executor, including mutations made
  reentrantly by the replay listener.
- Each mutation captures one snapshot shared by that revision's subscribers.
  Per-subscriber depth-one coalescing drops older pending revisions and keeps
  the newest. RGB and palette reads return defensive copies.
- Theme changes clear stale foreground/background RGB in the same revision.
  Other mode and palette facts remain. Consumers must not start a query to fill
  missing values.
- Listener exceptions are isolated from the producer and other subscribers.
- Close is idempotent and drops queued work. After close returns, no new
  callback begins. Close from another thread waits for a callback already
  selected for delivery; reentrant close does not wait and stops later events.
- Zero-subscriber mutations skip snapshot construction. Subscribing starts no
  reader, thread, timer, protocol notification, or discovery.
- The legacy `Connection.setThemeChangeHandler` slot remains independent.
  `TerminalConnection.GlobalThemeForwarder` keeps its existing local-only path.
  `TerminalCapabilities.applyThemeChange(...)` updates only its own instance;
  the static `onThemeChanged(...)` updates the process default.

Resize remains a separate `Connection` event. Existing local WINCH handling
records `OBSERVED` dimensions in `EndpointContext`; SSH, Telnet, and WebSocket
resize handling records `NEGOTIATED` dimensions. Both update context before the
legacy size callback and reuse the transport's size without querying again.

Protocol notification ownership stays with the application. Passive
registration does not enable notifications. The application calls
`enableThemeChangeNotification()` and `disableThemeChangeNotification()` and
closes its detection scope on shutdown or dev-mode restart.

`examples/.../TerminalSubscriptionExample.java` subscribes a logger and a TUI
status line together on one caller-supplied executor. Its resource scope closes
both subscriptions before stopping that executor. The example compiles but has
not been run as an interactive terminal or Quarkus application here.

## Verification

- Full JDK 25 reactor: 2103 tests, 0 failures/errors, 50 existing platform
  skips. Log: `/tmp/opencode/380-jvm-reactor.log`.
- Selected subscription, owner, attachment, and local theme suites: 40 tests
  each on Temurin 11.0.30, 21.0.6, and 25.0.4, with no skips. The older runs
  disable the formatter profile because that build plugin requires Java 17.
  Logs: `/tmp/opencode/380-jdk11.0.30-tem.log` and
  `/tmp/opencode/380-jdk21.0.6-tem.log`.
- Regression reds before the replay/close fix: producer-during-registration,
  executor-during-replay, close-during-callback, and shared-palette mutation
  all failed; the queued-close control passed. Resize assertions failed before
  the transport wiring and with a no-op update mutation.
- Targeted native images built with GraalVM CE 25.0.4.1.1 execute 28 detection
  tests and 10 tty tests, all with no skips or failures. Logs:
  `/tmp/opencode/380-native-detect.log` and
  `/tmp/opencode/380-native-tty.log`. These are selected suites, not a full
  native-suite verdict; #388 remains separate.
- `TerminalSubscriptionBenchmark`, Temurin 25.0.4 on Linux x86_64, JMH 1.37,
  three forks, five warmups, ten measurements, 256 MB heaps and `-prof gc`:
  zero subscribers 5.228 +/- 0.020 ns/op with no measured allocation;
  one subscriber 49.665 +/- 0.624 ns/op and 96.000 +/- 0.001 B/op;
  four subscribers 139.316 +/- 1.374 ns/op and 104.001 +/- 0.001 B/op;
  one executor rendezvous 7200.700 +/- 43.901 ns/op and
  192.035 +/- 7.685 B/op. These are optimized loop costs, including the
  handoff queue, not isolated API latency or Quarkus startup measurements.
  Raw results: `/tmp/opencode/380-subscription-jmh.json` and `.log`.

A test-order issue found on JDK 11 was fixed in the test, not hidden:
`awaitColors()` can return before completion listeners run, so the replacement
isolation test now awaits both a listener latch and the producer latch.

The final `edit_check` pass reports no incompatible callers. The lifecycle
`quality_delta` reports 21 gated findings: new API and JMH entry points treated
as dead code, test and close-lifecycle boilerplate, and expected feature
verbosity/churn. No finding is suppressed or described as a clean structural
report. The report contains no new complexity, parameter-count, or
error-masking finding. After that commit, the remaining example/benchmark/docs
delta reports zero gated findings and eight new-symbol JMH/example findings.
