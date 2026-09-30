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

import org.aesh.terminal.Terminal;
import org.aesh.terminal.utils.OSUtils;

/**
 * Opt-in field check for Cygwin/MSYS2 provider selection.
 * <p>
 * Launch from standalone mintty or Git Bash on Windows, never under
 * Surefire. Never calls System.console().
 * <p>
 * Recipes (from the module directory, after {@code mvn test-compile}):
 *
 * <pre>
 *   java -cp target/classes\;target/test-classes\;../terminal-api/target/classes \
 *       org.aesh.terminal.tty.CygwinLiveCheck
 *   echo hi | java -cp ... org.aesh.terminal.tty.CygwinLiveCheck
 * </pre>
 * <p>
 * Expected: interactive mintty builds a PosixSysTerminal over CygwinPty
 * (GetConsoleMode-based gating must not divert it to external); piped
 * stdin builds an external terminal.
 */
public final class CygwinLiveCheck {

    private CygwinLiveCheck() {
    }

    /**
     * Build the default terminal and report what the provider chain chose.
     *
     * @param args ignored
     * @throws Exception if terminal construction fails
     */
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new IllegalStateException("Run on Windows (mintty, Git Bash, or conhost)");
        }
        System.err.println("isCygwin=" + OSUtils.IS_CYGWIN);
        System.err.println("stdinTty=" + TtyDetect.isStdinTty());
        Terminal terminal = TerminalBuilder.builder().build();
        try {
            System.err.println("terminal=" + terminal.getClass().getName());
        } finally {
            terminal.close();
        }
        System.err.println("CYGWIN-CHECK-OK");
    }
}
