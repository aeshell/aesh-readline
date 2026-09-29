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

/**
 * Prints per-fd TTY answers for child-process redirection tests.
 * Launched with controlled stdin/stdout/stderr (PTY, file, or pipe) so
 * mixed combinations are observable; each run is a fresh JVM with cold
 * {@link TtyDetect} caches.
 */
public final class TtyFdProbe {

    private TtyFdProbe() {
    }

    /**
     * Print one line: {@code 0:true 1:false 2:false 999:false}.
     *
     * @param args ignored
     */
    public static void main(String[] args) {
        System.out.println("0:" + TtyDetect.isTty(0)
                + " 1:" + TtyDetect.isTty(1)
                + " 2:" + TtyDetect.isTty(2)
                + " 999:" + TtyDetect.isTty(999));
    }
}
