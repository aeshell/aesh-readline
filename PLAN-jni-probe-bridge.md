# JNI Win32 probe bridge for standalone detection without FFM, #389

Status: contract proposed, not implemented. Drives pre-connection live
queries on Windows runtimes where the Java 22 FFM overlays cannot load:
JDK 21 JVMs (the Quarkus 4 target) and 21-lineage native images. This is
a transport addition under the existing single-owned-round policy, not a
new prober, scheduler, or mediation framework. Quarkus owns cross-library
console mediation; this repository supplies the facts.

## Why standalone probing cannot stay FFM-only

Terminal queries need raw-mode flips plus timed console reads. On POSIX the
mode configuration delegates to the stock `stty` utility, so plain Java
I/O suffices on every JDK. Windows guarantees no equivalent tool, and pure
`java.base` cannot change console modes or time a console read. The FFM
probe transports (`FfmProbeTransport`, `Win32ProbeTransport`) live in the
Java 22 MRJAR overlay and are unloadable below 22 — selection swallows the
`LinkageError` and falls through to `SttyProbeTransport`, which has nothing
to open on Windows. Field evidence: JDK 21 runs on conhost, Windows
Terminal, and mintty all report `measuredImageProtocol=null` with unknown
background, resolving theme from the registry only.

The bridge already exists: `WinConsoleNativeJni` binds the full probe
syscall set, including `WaitForSingleObject` for the inter-byte timeout,
so no C changes are needed. What is missing is its placement: it lives in
`terminal-tty` behind an eager static initializer, unreachable from the
zero-dependency probe path in `terminal-detect` without inverting the
module direction (`terminal-api` → `terminal-detect` ← `terminal-tty`).

## Contract decisions

1. The JNI bridge (declarations, committed DLL, extraction, GraalVM JNI
   metadata) moves to `terminal-detect`; `terminal-tty` consumes it through
   the existing facade shape. One bridge serves the pump and the probe —
   no duplicated bindings. The committed DLL covers x86_64 only; ARM64
   Windows declines to hints with no load attempt (documented, same as the
   pump today).
2. Loading is lazy by contract, not by accident. Availability is answered
   from OS/arch/file facts without touching the bridge class, so headless
   and declined runs never extract or load a DLL. Loading happens inside
   `open()` only, and `UnsatisfiedLinkError` degrades to decline, never to
   a cached failure or a retried probe. The current eager static
   initializer must not move as-is.
3. Selection order is FFM, then JNI, then decline — FFM first where native
   access is enabled, since it binds system libraries with no extraction.
   A measured DA1 reply outranks hints under the rule already implemented
   for `#382`; the JNI transport reports through the same
   `measuredImageProtocol` seam, so no precedence code changes.
4. The bounded contract is unchanged: deadline-aware open, permissions
   checked before transport selection, cleanup always runs, no listener
   under publication monitors. The probe opens one session per round and
   never competes with a running connection reader.
5. `terminal-detect` keeps zero Java runtime dependencies and Java 8
   bytecode. Shipping a Windows-only native payload does not add a
   dependency, but it ends the module's pure-bytecode property — stated
   explicitly so the tradeoff is reviewable. The FFM overlays stay as the
   preferred path on 22+.

## Staged implementation and checks

1. Relocate the bridge with zero behavior change: declarations, DLL,
   extraction, and `jni-config.json` move; `terminal-tty` consumes the
   moved bridge through the facade. The pump, provider admission, and all
   existing Windows tests must pass unmodified on Linux CI and the Windows
   CI job. Red proof: point the facade at a missing library name and show
   the pump tests failing to load.
2. Split loading from declaration: availability without class touch,
   load-on-open, link-error-to-decline. Unit-test the decline matrix
   headless (non-Windows OS, missing arch, missing resource) without ever
   loading a library — these legs must also pass in the native image,
   which cannot rely on extraction timing.
3. Add the JNI `BoundedProbeTransport` and register it after FFM in the
   built-in order. No selection-code changes beyond ordering; the
   `setProbeTransport` injection semantics stay as-is.
4. Validate behavior where it can run: Windows CI job (headless, expects
   clean decline, no console, no hang) plus the `WindowsEndpointLiveCheck`
   field harness on a real Windows JDK 21 console (expects measured
   background where hints previously stood alone, and a bounded total).
   Re-run the same harness on JDK 25 to confirm FFM still wins where
   native access is enabled and results agree.
5. Record the ARM64 decline, the headless no-load guarantee, and the DLL
   rebuild process (`-Pcross-windows`, mingw) so a binding change does not
   silently ship a stale binary. The prebuilt-DLL-in-git practice stays,
   but the plan names who rebuilds it and when.

## Verification requirements

- Full JVM reactor green with no new skips; the endpoint, attachment,
  theme, and bounded suites green on JDK 11, 21, and 25.
- Isolated behavior mutations: selection with FFM present still prefers
  FFM; with FFM absent the JNI transport answers; with neither, hints
  apply and no library loads (assert via a load hook or extraction
  marker, not elapsed time).
- Field evidence from the harness on real conhost and Windows Terminal
  under JDK 21: measured background present, theme consistent with the
  registry-or-measured rule, bounded total time, raw mode restored.
- Native-image legs per #377; guard failures in #388 stay separate and
  are not skipped to get past them.
