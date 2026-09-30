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
package org.aesh.terminal.tty.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.aesh.terminal.tty.TerminalConnection;

/**
 * Opt-in field check for held-key repetition on Windows.
 * <p>
 * Launch directly from an interactive console (conhost or Windows
 * Terminal), never under Surefire. Requires Java 22+ at runtime so the
 * FFM bindings serve instead of the JNI DLL. Never calls
 * System.console().
 * <p>
 * Recipe (from the module directory, after {@code mvn test-compile}):
 *
 * <pre>
 *   java -cp target/classes;target/test-classes;../terminal-api/target/classes \
 *       org.aesh.terminal.tty.impl.Win32KeyRepeatLiveCheck
 * </pre>
 * <p>
 * When prompted, hold the {@code A} key for about two seconds, then
 * release and wait for the dump. Windows coalesces the hold into
 * KEY_EVENT records with repeatCount greater than one; the dump must
 * show uninterrupted runs of {@code 41} (no interleaving, no drops).
 * A single stray record with a higher count instead of a run is also
 * correct behavior on some Windows builds.
 */
public final class Win32KeyRepeatLiveCheck {

    private Win32KeyRepeatLiveCheck() {
    }

    /**
     * Record raw input code points for a few seconds and dump them as hex.
     *
     * @param args ignored
     * @throws Exception if terminal construction fails
     */
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new IllegalStateException("Run from an interactive Windows console");
        }
        if (!isJava22OrLater()) {
            throw new IllegalStateException("Requires Java 22+ for the FFM bindings");
        }
        WinSysTerminal terminal = new WinSysTerminal("key-repeat-check", false, SignalHandlers.SIG_DFL);
        TerminalConnection connection = new TerminalConnection(terminal);
        List<int[]> received = new ArrayList<>();
        try {
            connection.setStdinHandler(new Consumer<int[]>() {
                @Override
                public void accept(int[] input) {
                    received.add(input.clone());
                }
            });
            connection.openNonBlocking();
            System.err.println("Hold the A key for about two seconds, then release...");
            Thread.sleep(8000);
        } finally {
            connection.close();
        }
        StringBuilder hex = new StringBuilder();
        for (int[] chunk : received) {
            for (int cp : chunk) {
                hex.append(Integer.toHexString(cp)).append(' ');
            }
        }
        System.err.println("received code points: " + hex);
        System.err.println("KEY-REPEAT-CHECK-OK");
    }

    /**
     * Whether the runtime is Java 22 or later, parsed from the
     * specification version without post-8 APIs (this file compiles
     * at release 8).
     *
     * @return true on Java 22+
     */
    private static boolean isJava22OrLater() {
        String spec = System.getProperty("java.specification.version", "8");
        try {
            if (spec.startsWith("1.")) {
                return Integer.parseInt(spec.substring(2)) >= 22;
            }
            return Integer.parseInt(spec) >= 22;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
