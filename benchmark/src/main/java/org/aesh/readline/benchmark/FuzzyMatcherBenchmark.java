/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.readline.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.aesh.readline.fuzzy.FuzzyAlgo;
import org.aesh.readline.fuzzy.FuzzyResult;
import org.aesh.readline.fuzzy.FuzzyScheme;
import org.aesh.readline.fuzzy.FuzzyScorer;
import org.aesh.terminal.utils.Parser;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * JMH benchmarks for the fuzzy matching library.
 * <p>
 * Measures the performance of:
 * <ul>
 *   <li>Single match (V1 and V2) with various text/pattern combinations</li>
 *   <li>Full history search (scoreAll) with realistic history sizes</li>
 *   <li>Narrowing (incremental filtering of previous results)</li>
 * </ul>
 * <p>
 * Run with: {@code java -jar benchmark/target/benchmarks.jar FuzzyMatcherBenchmark}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(2)
@State(Scope.Thread)
public class FuzzyMatcherBenchmark {

    // ---- Single match benchmarks ----

    private FuzzyAlgo algo;

    // Realistic command history entries
    private int[] shortText;   // "ls -la"
    private int[] mediumText;  // "mvn clean test -pl aesh -Dtest=ProcessorTest"
    private int[] longText;    // long path with arguments

    private int[] shortPattern;  // "l"
    private int[] mediumPattern; // "mct"
    private int[] longPattern;   // "mvnclean"

    // Non-matching pattern
    private int[] noMatchPattern;

    @Setup(Level.Trial)
    public void setup() {
        algo = new FuzzyAlgo(FuzzyScheme.HISTORY);

        shortText = Parser.toCodePoints("ls -la");
        mediumText = Parser.toCodePoints("mvn clean test -pl aesh -Dtest=ProcessorTest");
        longText = Parser.toCodePoints("cd /home/stalep/git/aesh-readline/readline/src/main/java/org/aesh/readline/action/mappings && grep -rn 'keepFocus' *.java | head -20");

        shortPattern = Parser.toCodePoints("l");
        mediumPattern = Parser.toCodePoints("mct");
        longPattern = Parser.toCodePoints("mvnclean");
        noMatchPattern = Parser.toCodePoints("xyz123");
    }

    @Benchmark
    public FuzzyResult matchShortTextShortPattern() {
        return algo.match(false, shortText, shortPattern, false);
    }

    @Benchmark
    public FuzzyResult matchMediumTextMediumPattern() {
        return algo.match(false, mediumText, mediumPattern, false);
    }

    @Benchmark
    public FuzzyResult matchLongTextLongPattern() {
        return algo.match(false, longText, longPattern, false);
    }

    @Benchmark
    public FuzzyResult matchMediumTextWithPositions() {
        return algo.match(false, mediumText, mediumPattern, true);
    }

    @Benchmark
    public FuzzyResult matchNoMatch() {
        return algo.match(false, mediumText, noMatchPattern, false);
    }

    // ---- Case sensitivity comparison ----

    @Benchmark
    public FuzzyResult matchCaseSensitive() {
        return algo.match(true, mediumText, mediumPattern, false);
    }

    @Benchmark
    public FuzzyResult matchCaseInsensitive() {
        return algo.match(false, mediumText, mediumPattern, false);
    }

    // ---- History search benchmarks ----

    @State(Scope.Thread)
    public static class HistoryState {

        @Param({"100", "500", "1000"})
        int historySize;

        FuzzyScorer scorer;
        List<int[]> history;
        int[] queryShort;
        int[] queryMedium;

        // For narrowing benchmark
        List<FuzzyScorer.ScoredEntry> broadResults;
        int[] narrowQuery;

        private static final String[] SAMPLE_COMMANDS = {
                "ls -la",
                "cd /home/user/projects",
                "git status",
                "git commit -m \"fix bug\"",
                "git push origin main",
                "git pull --rebase",
                "mvn clean test",
                "mvn clean install -DskipTests",
                "mvn clean test -pl readline -Dtest=HistoryTest",
                "grep -rn \"FuzzyMatch\" src/",
                "find . -name \"*.java\" -type f",
                "docker ps -a",
                "docker-compose up -d",
                "kubectl get pods -n production",
                "ssh user@server.example.com",
                "cat /etc/hosts",
                "vim ~/.bashrc",
                "python3 -m venv .venv",
                "npm install --save-dev typescript",
                "cargo build --release",
                "java -jar target/app.jar --port=8080",
                "curl -s https://api.example.com/health | jq .",
                "systemctl status nginx",
                "tail -f /var/log/syslog",
                "htop",
                "df -h",
                "du -sh *",
                "tar czf backup.tar.gz /data",
                "rsync -avz /src/ /dst/",
                "screen -r session1"
        };

        @Setup(Level.Trial)
        public void setup() {
            scorer = new FuzzyScorer(FuzzyScheme.HISTORY);
            history = new ArrayList<>();
            Random rng = new Random(42); // deterministic

            for (int i = 0; i < historySize; i++) {
                String cmd = SAMPLE_COMMANDS[rng.nextInt(SAMPLE_COMMANDS.length)];
                // Add some variation
                if (rng.nextBoolean()) {
                    cmd = cmd + " " + rng.nextInt(100);
                }
                history.add(Parser.toCodePoints(cmd));
            }

            queryShort = Parser.toCodePoints("g");
            queryMedium = Parser.toCodePoints("gcp");

            // Pre-compute broad results for narrowing benchmark
            broadResults = scorer.scoreAll(history, Parser.toCodePoints("g"), false);
            narrowQuery = Parser.toCodePoints("gc");
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public List<FuzzyScorer.ScoredEntry> scoreAllShortQuery(HistoryState state) {
        return state.scorer.scoreAll(state.history, state.queryShort, false);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public List<FuzzyScorer.ScoredEntry> scoreAllMediumQuery(HistoryState state) {
        return state.scorer.scoreAll(state.history, state.queryMedium, false);
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public List<FuzzyScorer.ScoredEntry> narrowResults(HistoryState state) {
        return state.scorer.narrow(state.broadResults, state.narrowQuery, false);
    }
}
