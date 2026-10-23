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
package org.aesh.terminal.tty;

import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;

/** Child JVM for probe tests: marks execution and holds a lock until exit. */
public final class ProbeChild {

    private ProbeChild() {
    }

    /**
     * Mark startup, then either stall with output pipes open or exit.
     *
     * @param args marker file and either {@code hold} or an exit status
     * @throws Exception if the fixture cannot initialize
     */
    public static void main(String[] args) throws Exception {
        boolean hold = "hold".equals(args[1]);
        int exitCode = hold ? 3 : Integer.parseInt(args[1]);
        try (RandomAccessFile marker = new RandomAccessFile(args[0], "rw");
                FileLock lock = marker.getChannel().lock()) {
            marker.write("started\n".getBytes(StandardCharsets.US_ASCII));
            marker.getFD().sync();
            if (hold) {
                // Backstop for broken parent cleanup. Old unbounded probe
                // code waits for this expiry and returns false, not unknown.
                Thread.sleep(30000);
            }
        }
        System.exit(exitCode);
    }
}
