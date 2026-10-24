/*
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

import org.aesh.terminal.detect.TerminalCapabilities;
import org.aesh.terminal.tty.TerminalConnection;

/**
 * One operation in a fresh process. Print results only after timing; the
 * collector records process lifetime and runtime metadata separately.
 */
public final class DetectionBaseline {

    // Include one volatile publication in operation timings. Otherwise an
    // AOT compiler could eliminate an unused environment-only result.
    private static volatile Object observed;

    private DetectionBaseline() {
    }

    /**
     * Run one baseline scenario without a terminal preflight.
     *
     * @param args one scenario name
     * @throws Exception if setup, execution, or a coverage assertion fails
     */
    public static void main(String[] args) throws Exception {
        Sample sample = new Sample(args[0]);
        switch (sample.scenario) {
            case "noop":
                break;
            case "detect":
            case "shared":
                environment(sample);
                break;
            case "full-canned":
            case "async-canned":
            case "unavailable":
                canned(sample);
                break;
            case "full-auto":
                long start = System.nanoTime();
                observed = TerminalCapabilities.detectFull();
                sample.firstNanos = System.nanoTime() - start;
                break;
            case "connection":
                connection(sample);
                break;
            default:
                throw new IllegalArgumentException("unknown scenario: " + sample.scenario);
        }
        sample.print();
    }

    private static void environment(Sample sample) {
        boolean shared = "shared".equals(sample.scenario);
        long start = System.nanoTime();
        TerminalCapabilities caps = shared ? TerminalCapabilities.getInstance() : TerminalCapabilities.detect();
        observed = caps;
        sample.firstNanos = System.nanoTime() - start;
        if (shared) {
            start = System.nanoTime();
            TerminalCapabilities again = TerminalCapabilities.getInstance();
            observed = again;
            sample.cachedNanos = System.nanoTime() - start;
            if (again != caps) {
                throw new IllegalStateException("shared lookup did not reuse the instance");
            }
        }
    }

    private static void canned(Sample sample) {
        long setup = System.nanoTime();
        DetectionProbeFixture fixture = new DetectionProbeFixture(!"unavailable".equals(sample.scenario));
        TerminalCapabilities.setProbeTransport(fixture);
        sample.setupNanos = System.nanoTime() - setup;
        long start = System.nanoTime();
        TerminalCapabilities caps;
        if ("async-canned".equals(sample.scenario)) {
            caps = TerminalCapabilities.detectAsync();
            observed = caps;
            sample.firstNanos = System.nanoTime() - start;
            start = System.nanoTime();
            if (TerminalCapabilities.detectFull() != caps) {
                throw new IllegalStateException("full detection did not reuse async instance");
            }
            sample.completionNanos = System.nanoTime() - start;
        } else {
            caps = TerminalCapabilities.detectFull();
            observed = caps;
            sample.firstNanos = System.nanoTime() - start;
        }
        start = System.nanoTime();
        if (TerminalCapabilities.detectFull() != caps) {
            throw new IllegalStateException("repeat full detection did not reuse the instance");
        }
        sample.cachedNanos = System.nanoTime() - start;
        fixture.verify("unavailable".equals(sample.scenario) ? 0 : 1, caps);
        sample.opens = fixture.opens;
        sample.writes = fixture.writes;
        sample.closes = fixture.closes;
    }

    private static void connection(Sample sample) throws Exception {
        long start = System.nanoTime();
        TerminalConnection connection = new TerminalConnection();
        sample.firstNanos = System.nanoTime() - start;
        try {
            start = System.nanoTime();
            connection.openNonBlocking();
            sample.readerSubmitNanos = System.nanoTime() - start;
        } finally {
            start = System.nanoTime();
            connection.close();
            sample.closeNanos = System.nanoTime() - start;
        }
    }

    private static final class Sample {
        final String scenario;
        long firstNanos;
        long setupNanos;
        long completionNanos;
        long cachedNanos;
        long readerSubmitNanos;
        long closeNanos;
        int opens = -1;
        int writes = -1;
        int closes = -1;

        Sample(String scenario) {
            this.scenario = scenario;
        }

        void print() {
            // Scenario names are validated above. All remaining fields are
            // numbers or library-defined values, not raw environment text.
            // A count of -1 is uninstrumented, never evidence of zero work.
            TerminalCapabilities caps = observed instanceof TerminalCapabilities ? (TerminalCapabilities) observed : null;
            System.out.println("{\"scenario\":\"" + scenario + "\",\"firstNanos\":" + firstNanos
                    + ",\"setupNanos\":" + setupNanos + ",\"completionNanos\":" + completionNanos
                    + ",\"cachedNanos\":" + cachedNanos + ",\"readerSubmitNanos\":" + readerSubmitNanos
                    + ",\"closeNanos\":" + closeNanos + ",\"sessionOpens\":" + opens
                    + ",\"queryWrites\":" + writes + ",\"sessionCloses\":" + closes
                    + ",\"hasPublishedResult\":" + (observed != null)
                    + ",\"terminal\":\"" + (caps == null ? "unobserved" : caps.terminalName()) + "\""
                    + ",\"theme\":\"" + (caps == null ? "unobserved" : caps.theme().name()) + "\""
                    + ",\"trueColor\":" + (caps != null && caps.supportsTrueColor())
                    + ",\"nativeImage\":" + (System.getProperty("org.graalvm.nativeimage.imagecode") != null) + "}");
        }
    }
}
