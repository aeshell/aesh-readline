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

import java.io.InputStream;

import org.aesh.terminal.Attributes;

/**
 * Opt-in field check for Quick Edit suppression in raw mode.
 * <p>
 * Launch directly from an interactive console with Quick Edit enabled
 * (conhost defaults; Windows Terminal: settings), never under Surefire.
 * Requires Java 22+ at runtime so the FFM bindings serve instead of the
 * JNI DLL. Never calls System.console().
 * <p>
 * Recipe (from the module directory, after {@code mvn test-compile}):
 *
 * <pre>
 *   java -cp target/classes;target/test-classes;../terminal-api/target/classes \
 *       org.aesh.terminal.tty.impl.Win32QuickEditLiveCheck
 * </pre>
 * <p>
 * The harness enters raw mode, verifies the composed word carries
 * {@code ENABLE_EXTENDED_FLAGS} without {@code ENABLE_QUICK_EDIT_MODE},
 * then asks the operator to select text with the mouse and tap any key:
 * input arriving during selection proves reads are not blocked. On exit
 * the exact saved mode must be restored.
 */
public final class Win32QuickEditLiveCheck {

    private static final int ENABLE_EXTENDED_FLAGS = 0x0080;
    private static final int ENABLE_QUICK_EDIT_MODE = 0x0040;

    private Win32QuickEditLiveCheck() {
    }

    /**
     * Enter raw mode on a Quick-Edit console, verify input flows during
     * selection, and verify exact mode restoration.
     *
     * @param args ignored
     * @throws Exception if a verdict mismatches
     */
    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            throw new IllegalStateException("Run from an interactive Windows console");
        }
        if (!isJava22OrLater()) {
            throw new IllegalStateException("Requires Java 22+ for the FFM bindings");
        }
        WinSysTerminal terminal = new WinSysTerminal("quick-edit-check", false, SignalHandlers.SIG_DFL);
        int saved = terminal.getConsoleMode();
        System.err.println("saved input mode: 0x" + Integer.toHexString(saved));
        try {
            terminal.setAttributes(new Attributes());
            int raw = terminal.getConsoleMode();
            System.err.println("raw input mode: 0x" + Integer.toHexString(raw));
            if ((raw & ENABLE_EXTENDED_FLAGS) == 0) {
                throw new IllegalStateException("raw mode lacks EXTENDED_FLAGS");
            }
            if ((raw & ENABLE_QUICK_EDIT_MODE) != 0) {
                throw new IllegalStateException("raw mode carries QUICK_EDIT");
            }
            System.err.println("Select text with the mouse NOW, then tap any key...");
            InputStream input = terminal.input();
            long deadline = System.currentTimeMillis() + 30000;
            StringBuilder hex = new StringBuilder();
            while (System.currentTimeMillis() < deadline && hex.length() == 0) {
                while (input.available() > 0) {
                    int b = input.read();
                    if (b >= 0) {
                        hex.append(Integer.toHexString(b)).append(' ');
                    }
                }
                if (hex.length() == 0) {
                    Thread.sleep(200);
                }
            }
            if (hex.length() == 0) {
                throw new IllegalStateException(
                        "no input arrived — selection blocked reads, or no key was tapped");
            }
            System.err.println("received during selection: " + hex);
        } finally {
            terminal.close();
        }
        int restored = terminal.getConsoleMode();
        System.err.println("restored input mode: 0x" + Integer.toHexString(restored));
        if (restored != saved) {
            throw new IllegalStateException("close() did not restore the exact saved mode");
        }
        System.err.println("QUICK-EDIT-CHECK-OK");
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
