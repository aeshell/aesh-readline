#!/usr/bin/env python3
"""Fresh-process baseline collection. No user terminal is inherited."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import random
import signal
import statistics
import subprocess
import time


SCENARIOS = ("noop", "detect", "shared", "full-canned", "async-canned",
             "unavailable", "full-auto", "connection")
TERMINAL_KEYS = ("TERM", "TERM_PROGRAM", "TERM_PROGRAM_VERSION", "TERMINAL_EMULATOR",
                 "COLORTERM", "KITTY_WINDOW_ID", "GHOSTTY_RESOURCES_DIR", "WEZTERM_PANE",
                 "ITERM_SESSION_ID", "WT_SESSION", "WT_PROFILE_ID", "ALACRITTY_SOCKET",
                 "COLORFGBG", "APPLE_INTERFACE_STYLE", "TMUX", "STY", "TMUX_PASSTHROUGH")


def run_child(command, environment):
    start = time.perf_counter_ns()
    process = subprocess.Popen(command, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, env=environment,
                               start_new_session=os.name == "posix")
    try:
        stdout, stderr = process.communicate(timeout=30)
    except subprocess.TimeoutExpired:
        try:
            if os.name == "posix":
                os.killpg(process.pid, signal.SIGKILL)
            else:
                process.kill()
        except ProcessLookupError:
            # Preserve the original timeout if the process just exited.
            process.poll()
        process.communicate()
        raise
    elapsed = time.perf_counter_ns() - start
    if process.returncode:
        raise RuntimeError(f"{command}: exit {process.returncode}\n"
                           f"stdout: {stdout.decode(errors='replace')}\n"
                           f"stderr: {stderr.decode(errors='replace')}")
    record = json.loads(stdout)
    record["processNanos"] = elapsed
    record["stderr"] = stderr.decode(errors="replace")
    return record


def controlled_environment():
    """Use the same terminal hints for cold collection and warm JMH runs."""
    environment = dict(os.environ)
    for key in TERMINAL_KEYS:
        environment.pop(key, None)
    environment.update(TERM="xterm-256color", COLORFGBG="15;0")
    return environment


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java", default="java")
    parser.add_argument("--jar", type=Path, default=Path("benchmark/target/benchmarks.jar"))
    parser.add_argument("--native", type=Path)
    parser.add_argument("--jvm-arg", action="append", default=[])
    parser.add_argument("--samples", type=int, default=20)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.samples <= 0:
        parser.error("samples must be positive")
    if args.native and args.jvm_arg:
        parser.error("--jvm-arg is not applied to a native executable")
    args.output.mkdir(parents=True, exist_ok=False)
    environment = controlled_environment()
    java_args = ["-Xms128m", "-Xmx128m", *args.jvm_arg]
    command = ([str(args.native.resolve())] if args.native else
               [args.java, *java_args, "-cp", str(args.jar.resolve()),
                "org.aesh.readline.benchmark.DetectionBaseline"])
    version = subprocess.run([args.java, "-version"], capture_output=True, text=True, check=True)
    cpu_model = platform.processor()
    cpu_info = Path("/proc/cpuinfo")
    if cpu_info.is_file():
        cpu_model = next((line.split(":", 1)[1].strip() for line in cpu_info.read_text().splitlines()
                          if line.startswith("model name")), cpu_model)
    metadata = {
        "revision": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
        "workingTree": subprocess.check_output(["git", "status", "--porcelain"], text=True),
        "platform": platform.platform(), "machine": platform.machine(),
        "cpuCount": os.cpu_count(), "cpuModel": cpu_model,
        "affinity": sorted(os.sched_getaffinity(0)) if hasattr(os, "sched_getaffinity") else None,
        "javaVersion": version.stderr.strip(), "commandPrefix": command,
        "javaOptionEnvironment": {key: environment.get(key) for key in
                                  ("JAVA_HOME", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS")},
        "jarSha256": hashlib.sha256(args.jar.read_bytes()).hexdigest(),
        "nativeSha256": hashlib.sha256(args.native.read_bytes()).hexdigest() if args.native else None,
        "terminalEnvironment": {key: environment.get(key) for key in TERMINAL_KEYS},
        "descriptors": "stdin=null; stdout/stderr=pipes; detached session on POSIX",
        "orderSeed": 379, "samplesPerScenario": args.samples,
        "collectorSha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
    }
    (args.output / "collector.py").write_text(Path(__file__).read_text())
    (args.output / "metadata.json").write_text(json.dumps(metadata, indent=2))
    schedule = list(SCENARIOS) * args.samples
    random.Random(379).shuffle(schedule)
    records = []
    with (args.output / "raw.jsonl").open("w") as partial:
        for scenario in schedule:
            record = run_child([*command, scenario], environment)
            if record["scenario"] != scenario:
                raise RuntimeError(f"requested {scenario}, received {record['scenario']}")
            records.append(record)
            partial.write(json.dumps(record) + "\n")
            partial.flush()
    (args.output / "raw.json").write_text(json.dumps({"metadata": metadata, "records": records}, indent=2))
    summary = {}
    for scenario in SCENARIOS:
        group = [row for row in records if row["scenario"] == scenario]
        summary[scenario] = {}
        for metric in ("processNanos", "setupNanos", "firstNanos", "completionNanos",
                       "cachedNanos", "readerSubmitNanos", "closeNanos"):
            values = sorted(row[metric] for row in group)
            summary[scenario][metric] = {"median": statistics.median(values),
                                         "min": values[0], "max": values[-1]}
        print(f"{scenario:14s} process median={summary[scenario]['processNanos']['median'] / 1e6:8.3f}ms"
              f" first-call median={summary[scenario]['firstNanos']['median'] / 1e6:8.3f}ms")
    (args.output / "summary.json").write_text(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
