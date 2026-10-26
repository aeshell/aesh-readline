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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.aesh.terminal.detect.ProcessRunner;

/** Real child-process wall time, not a CPU microbenchmark or terminal query. */
public final class ProcessRunnerBaseline {

    private ProcessRunnerBaseline() {
    }

    /**
     * Time repeated commands with post-timing output verification.
     *
     * @param args warmup count, sample count, expected UTF-8 output, command and arguments
     * @throws Exception if execution or verification fails
     */
    public static void main(String[] args) throws Exception {
        int warmups = Integer.parseInt(args[0]);
        int samples = Integer.parseInt(args[1]);
        byte[] expected = args[2].getBytes(StandardCharsets.UTF_8);
        String[] command = Arrays.copyOfRange(args, 3, args.length);
        if (warmups < 0 || samples <= 0 || command.length == 0) {
            throw new IllegalArgumentException("expected warmups >= 0, samples > 0, and a command");
        }
        long[] nanos = new long[samples];
        for (int i = -warmups; i < samples; i++) {
            long start = System.nanoTime();
            ProcessRunner.Result result = ProcessRunner.execute(new ProcessBuilder(command), 5000);
            long elapsed = System.nanoTime() - start;
            if (result.timedOut() || result.exitCode() != 0 || !Arrays.equals(expected, result.output())) {
                throw new IllegalStateException("command did not complete with the expected output");
            }
            if (i >= 0) {
                nanos[i] = elapsed;
            }
        }
        System.out.println("{\"warmups\":" + warmups + ",\"samples\":" + samples
                + ",\"outputBytes\":" + expected.length + ",\"nanos\":" + Arrays.toString(nanos) + "}");
    }
}
