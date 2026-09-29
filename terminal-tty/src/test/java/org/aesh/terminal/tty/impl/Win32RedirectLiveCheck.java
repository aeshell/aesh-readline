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

import java.nio.charset.StandardCharsets;

import org.aesh.terminal.tty.TerminalConnection;

/**
 * Opt-in transport check for redirected Windows output.
 * <p>
 * Launch directly from an interactive console (conhost or Windows
 * Terminal), never under Surefire (which pipes standard handles).
 * Requires Java 22+ at runtime so the FFM bindings serve instead of
 * the JNI DLL. Never calls System.console().
 * <p>
 * Recipes (from the module directory, after {@code mvn test-compile}):
 *
 * <pre>
 *   java -cp target/classes;target/test-classes;../terminal-api/target/classes \
 *       org.aesh.terminal.tty.impl.Win32RedirectLiveCheck redirected > out.txt
 *   java -cp ... Win32RedirectLiveCheck redirected | findstr MARKER
 *   java -cp ... Win32RedirectLiveCheck console
 * </pre>
 * <p>
 * In {@code redirected} mode stdout must be a file or pipe: stdin stays
 * a console (GetConsoleMode succeeds), stdout must fail it (-1), the
 * terminal must select the Encoder byte path (null codepoint consumer),
 * and the marker plus non-ASCII line must land intact downstream —
 * verify against the hex printed on stderr. In {@code console} mode
 * (no redirection) the WriteConsoleW path must be selected instead.
 */
public final class Win32RedirectLiveCheck {

    private static final String MARKER = "MARKER-aesh-redirect-check";
    private static final String TEXT = MARKER + " h\u00E9llo \u4E16\u754C";

    private Win32RedirectLiveCheck() {
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

    /**
     * Verify output transport selection for the launch redirection.
     *
     * @param args exactly one argument: "redirected" or "console"
     * @throws Exception if a verdict mismatches its scenario
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || (!"redirected".equals(args[0]) && !"console".equals(args[0]))) {
            throw new IllegalArgumentException("Pass redirected or console as the sole argument");
        }
        boolean redirected = "redirected".equals(args[0]);
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new IllegalStateException("Run from an interactive Windows console");
        }
        if (!isJava22OrLater()) {
            throw new IllegalStateException("Requires Java 22+ for the FFM bindings");
        }

        long input = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
        long output = WinConsoleNative.getStdHandle(WinConsoleNative.STD_OUTPUT_HANDLE);
        int inputMode = WinConsoleNative.getConsoleMode(input);
        int outputMode = WinConsoleNative.getConsoleMode(output);
        System.err.println("stdin GetConsoleMode: " + inputMode + " (expect console)");
        System.err.println("stdout GetConsoleMode: " + outputMode + " (expect "
                + (redirected ? "-1, redirected" : "a console mode") + ")");
        if (inputMode == -1) {
            throw new IllegalStateException("stdin is not a console; launch from one");
        }
        if (redirected && outputMode != -1) {
            throw new IllegalStateException("stdout looks like a console; redirect it");
        }
        if (!redirected && outputMode == -1) {
            throw new IllegalStateException("stdout is redirected; run unredirected");
        }

        WinSysTerminal terminal = new WinSysTerminal("redirect-check", false, SignalHandlers.SIG_DFL);
        TerminalConnection connection = new TerminalConnection(terminal);
        try {
            boolean bytePath = terminal.getCodePointConsumer() == null;
            System.err.println("byte path selected: " + bytePath + " (expect " + redirected + ")");
            if (bytePath != redirected) {
                throw new IllegalStateException("transport verdict mismatches scenario");
            }
            connection.write(TEXT + "\n");
        } finally {
            connection.close();
        }

        StringBuilder hex = new StringBuilder();
        for (byte b : TEXT.getBytes(StandardCharsets.UTF_8)) {
            hex.append(String.format("%02x ", b));
        }
        System.err.println("wrote marker line; UTF-8 bytes for downstream comparison: " + hex);
        System.err.println("REDIRECT-CHECK-OK");
    }
}
