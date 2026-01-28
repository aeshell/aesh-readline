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
package org.aesh.terminal.tty.example;

import java.io.IOException;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.tty.TerminalColorDetector;
import org.aesh.terminal.tty.TerminalConnection;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.ANSIBuilder;
import org.aesh.terminal.utils.ColorDepth;
import org.aesh.terminal.utils.TerminalColorCapability;

/**
 * Example program demonstrating the terminal color detection API.
 * <p>
 * This program detects and displays:
 * <ul>
 * <li>Terminal color depth (8, 16, 256, or true color)</li>
 * <li>Terminal theme (light or dark background)</li>
 * <li>Actual foreground and background RGB colors (if detectable)</li>
 * <li>Suggested color codes for various message types</li>
 * </ul>
 * <p>
 * Run with {@code -v} or {@code --verbose} flag to enable debug logging.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class TerminalColorExample {

    public static void main(String[] args) {
        boolean verbose = false;
        for (String arg : args) {
            if ("-v".equals(arg) || "--verbose".equals(arg)) {
                verbose = true;
                break;
            }
        }

        if (verbose) {
            enableDebugLogging();
        }

        try {
            TerminalConnection connection = new TerminalConnection();
            runExample(connection);
            connection.close();
        } catch (IOException e) {
            System.err.println("Error creating terminal connection: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void runExample(TerminalConnection connection) {
        connection.write("\n");
        connection.write(ANSIBuilder.builder().bold("=== Terminal Color Detection Example ===").toString() + "\n");
        connection.write("\n");

        // First, show environment-based detection (fast, no terminal query)
        connection.write(ANSIBuilder.builder().bold("1. Environment-Based Detection (Fast)").toString() + "\n");
        connection.write("   This uses environment variables only, no terminal queries.\n");
        connection.write("\n");

        TerminalColorCapability envCap = TerminalColorCapability.detectFromEnvironment();
        printCapability(connection, "   ", envCap);

        connection.write("\n");
        connection.write(ANSIBuilder.builder().bold("2. Full Detection with Terminal Query").toString() + "\n");
        connection.write("   This queries the terminal for actual colors using OSC sequences.\n");
        connection.write("\n");

        // Detect with OSC queries
        TerminalColorCapability fullCap = TerminalColorDetector.detect(connection);
        printCapability(connection, "   ", fullCap);

        connection.write("\n");
        connection.write(ANSIBuilder.builder().bold("3. Color Demonstration").toString() + "\n");
        connection.write("   Using suggested colors based on detected theme:\n");
        connection.write("\n");

        demonstrateColors(connection, fullCap);

        connection.write("\n");
        connection.write(ANSIBuilder.builder().bold("4. Color Depth Capabilities").toString() + "\n");
        connection.write("\n");

        demonstrateColorDepth(connection, fullCap.getColorDepth());

        connection.write("\n");
        connection.write(ANSIBuilder.builder().bold("5. Environment Variables").toString() + "\n");
        connection.write("\n");
        printEnvironmentVariables(connection);

        connection.write("\n");
    }

    private static void printCapability(TerminalConnection connection, String indent, TerminalColorCapability cap) {
        connection.write(indent + "Color Depth: " + cap.getColorDepth() +
                " (" + cap.getColorDepth().getColorCount() + " colors)\n");
        connection.write(indent + "Theme:       " + cap.getTheme() +
                (cap.getTheme().isDark() ? " (using light text colors)" : " (using dark text colors)") + "\n");

        if (cap.hasBackgroundColor()) {
            int[] bg = cap.getBackgroundRGB();
            connection.write(indent + "Background:  " + cap.getBackgroundHex() +
                    " RGB(" + bg[0] + ", " + bg[1] + ", " + bg[2] + ") " +
                    formatColorSwatch(bg) + "\n");
        } else {
            connection.write(indent + "Background:  Not detected\n");
        }

        if (cap.hasForegroundColor()) {
            int[] fg = cap.getForegroundRGB();
            connection.write(indent + "Foreground:  " + cap.getForegroundHex() +
                    " RGB(" + fg[0] + ", " + fg[1] + ", " + fg[2] + ") " +
                    formatColorSwatch(fg) + "\n");
        } else {
            connection.write(indent + "Foreground:  Not detected\n");
        }
    }

    private static String formatColorSwatch(int[] rgb) {
        // Create a colored block using 24-bit color if available
        return "\u001B[48;2;" + rgb[0] + ";" + rgb[1] + ";" + rgb[2] + "m    " + ANSI.RESET;
    }

    private static void demonstrateColors(TerminalConnection connection, TerminalColorCapability cap) {
        String indent = "   ";

        // Normal text
        int fg = cap.getSuggestedForegroundCode();
        connection.write(indent + "\u001B[" + fg + "mNormal text (code " + fg + ")" + ANSI.RESET + "\n");

        // Error
        int error = cap.getSuggestedErrorCode();
        connection.write(indent + "\u001B[" + error + "mError message (code " + error + ")" + ANSI.RESET + "\n");

        // Success
        int success = cap.getSuggestedSuccessCode();
        connection.write(indent + "\u001B[" + success + "mSuccess message (code " + success + ")" + ANSI.RESET + "\n");

        // Warning
        int warning = cap.getSuggestedWarningCode();
        connection.write(indent + "\u001B[" + warning + "mWarning message (code " + warning + ")" + ANSI.RESET + "\n");

        // Info
        int info = cap.getSuggestedInfoCode();
        connection.write(indent + "\u001B[" + info + "mInfo message (code " + info + ")" + ANSI.RESET + "\n");
    }

    private static void demonstrateColorDepth(TerminalConnection connection, ColorDepth depth) {
        String indent = "   ";

        connection.write(indent + "Supports any color:  " + (depth.supportsColor() ? "Yes" : "No") + "\n");
        connection.write(indent + "Supports 16 colors:  " + (depth.supports16Colors() ? "Yes" : "No") + "\n");
        connection.write(indent + "Supports 256 colors: " + (depth.supports256Colors() ? "Yes" : "No") + "\n");
        connection.write(indent + "Supports true color: " + (depth.supportsTrueColor() ? "Yes" : "No") + "\n");

        if (depth.supports256Colors()) {
            connection.write("\n");
            connection.write(indent + "256-color palette sample:\n");
            StringBuilder palette = new StringBuilder(indent);
            // Show a sample of the 256-color palette (colors 16-51)
            for (int i = 16; i < 52; i++) {
                palette.append("\u001B[48;5;").append(i).append("m  ");
            }
            palette.append(ANSI.RESET).append("\n");
            connection.write(palette.toString());
        }

        if (depth.supportsTrueColor()) {
            connection.write("\n");
            connection.write(indent + "True color gradient sample:\n");
            StringBuilder gradient = new StringBuilder(indent);
            // Show a rainbow gradient
            for (int i = 0; i < 36; i++) {
                int r = (int) (Math.sin(0.1 * i + 0) * 127 + 128);
                int g = (int) (Math.sin(0.1 * i + 2) * 127 + 128);
                int b = (int) (Math.sin(0.1 * i + 4) * 127 + 128);
                gradient.append("\u001B[48;2;").append(r).append(";").append(g).append(";").append(b).append("m ");
            }
            gradient.append(ANSI.RESET).append("\n");
            connection.write(gradient.toString());
        }
    }

    private static void printEnvironmentVariables(TerminalConnection connection) {
        String indent = "   ";

        // First, show detected terminal type
        String detectedTerminal = detectTerminalType();
        connection.write(indent + ANSIBuilder.builder().bold("Detected Terminal: " + detectedTerminal).toString() + "\n");
        connection.write("\n");

        String[] vars = { "TERM", "COLORTERM", "COLORFGBG", "TERM_PROGRAM",
                "TERMINAL_EMULATOR", "GHOSTTY_RESOURCES_DIR",
                "KITTY_WINDOW_ID", "WEZTERM_PANE", "ITERM_SESSION_ID",
                "APPLE_INTERFACE_STYLE", "TMUX", "ALACRITTY_SOCKET",
                "ConEmuPID", "WT_SESSION", "COMSPEC", "PROMPT",
                "MSYSTEM", "PSModulePath", "POWERSHELL_DISTRIBUTION_CHANNEL" };

        for (String var : vars) {
            String value = System.getenv(var);
            if (value != null) {
                // Truncate long values
                if (value.length() > 50) {
                    value = value.substring(0, 47) + "...";
                }
                connection.write(indent + var + " = " + value + "\n");
            }
        }

        connection.write("\n");
        connection.write(indent + "OSC query supported: " +
                (TerminalColorDetector.isOscColorQuerySupported() ? "Yes" : "No") + "\n");
        connection.write(indent + "Running in multiplexer: " +
                (TerminalColorDetector.isRunningInMultiplexer() ? "Yes" : "No") + "\n");
        connection.write(indent + "Tmux passthrough: " +
                (TerminalColorDetector.shouldUseTmuxPassthrough() ? "Yes" : "No") + "\n");

        // Additional info for JetBrains
        String terminalEmulator = System.getenv("TERMINAL_EMULATOR");
        if (terminalEmulator != null && terminalEmulator.toLowerCase().contains("jetbrains")) {
            connection.write("\n");
            connection.write(indent + ANSIBuilder.builder().faint("JetBrains IDE detected - theme read from config files")
                    .toString() + "\n");
        }

        // Additional info if in tmux
        if (TerminalColorDetector.isRunningInTmux()) {
            connection.write("\n");
            connection.write(indent + ANSIBuilder.builder().faint("Tip: If colors are not detected in tmux, try:")
                    .toString() + "\n");
            connection.write(indent + ANSIBuilder.builder().faint("  1. tmux set -g allow-passthrough on").toString() + "\n");
            connection.write(indent + ANSIBuilder.builder().faint("  2. export TMUX_PASSTHROUGH=1").toString() + "\n");
        }

        // Additional info for Windows cmd.exe
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win") && System.getenv("WT_SESSION") == null &&
                System.getenv("ConEmuPID") == null) {
            connection.write("\n");
            connection.write(indent + ANSIBuilder.builder().faint("Windows Build: " + getWindowsBuildInfo()).toString() + "\n");
            connection.write(
                    indent + ANSIBuilder.builder().faint("Note: Windows cmd.exe does not support OSC color queries.").toString()
                            + "\n");
            connection.write(
                    indent + ANSIBuilder.builder().faint("Theme detection uses Windows registry settings.").toString() + "\n");
            connection.write(indent
                    + ANSIBuilder.builder().faint("For better color support, consider using Windows Terminal.").toString()
                    + "\n");
        }
    }

    /**
     * Get Windows build information for display.
     *
     * @return a string with Windows build info
     */
    private static String getWindowsBuildInfo() {
        String osName = System.getProperty("os.name", "Unknown");
        String osVersion = System.getProperty("os.version", "Unknown");

        // Try to get more detailed build number from registry
        try {
            ProcessBuilder pb = new ProcessBuilder("reg", "query",
                    "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                    "/v", "CurrentBuildNumber");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[256];
            java.io.InputStream is = process.getInputStream();
            int read;
            while ((read = is.read(buffer)) != -1) {
                output.append(new String(buffer, 0, read));
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                String result = output.toString();
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("CurrentBuildNumber\\s+REG_SZ\\s+(\\d+)");
                java.util.regex.Matcher matcher = pattern.matcher(result);
                if (matcher.find()) {
                    String buildNumber = matcher.group(1);
                    int build = Integer.parseInt(buildNumber);
                    String vtSupport = build >= 14931 ? " (VT sequences supported)" : " (VT sequences NOT supported)";
                    return osName + " " + osVersion + " Build " + buildNumber + vtSupport;
                }
            }
        } catch (Exception e) {
            // Ignore and return basic info
        }

        return osName + " " + osVersion;
    }

    /**
     * Detect the terminal emulator type based on environment variables.
     *
     * @return a human-readable terminal name
     */
    private static String detectTerminalType() {
        // Check for specific terminal emulator environment variables

        // JetBrains IDEs
        String terminalEmulator = System.getenv("TERMINAL_EMULATOR");
        if (terminalEmulator != null && terminalEmulator.toLowerCase().contains("jetbrains")) {
            return "JetBrains IDE (JediTerm)";
        }

        // VSCode
        String termProgram = System.getenv("TERM_PROGRAM");
        if ("vscode".equalsIgnoreCase(termProgram)) {
            return "Visual Studio Code";
        }

        // iTerm2
        if (System.getenv("ITERM_SESSION_ID") != null) {
            return "iTerm2";
        }

        // Kitty
        if (System.getenv("KITTY_WINDOW_ID") != null) {
            return "Kitty";
        }

        // WezTerm
        if (System.getenv("WEZTERM_PANE") != null) {
            return "WezTerm";
        }

        // Ghostty
        if (System.getenv("GHOSTTY_RESOURCES_DIR") != null) {
            return "Ghostty";
        }

        // Alacritty
        String term = System.getenv("TERM");
        if (term != null && term.toLowerCase().contains("alacritty")) {
            return "Alacritty";
        }
        if (System.getenv("ALACRITTY_SOCKET") != null) {
            return "Alacritty";
        }

        // Windows Terminal
        if (System.getenv("WT_SESSION") != null) {
            return "Windows Terminal";
        }

        // ConEmu/Cmder
        if (System.getenv("ConEmuPID") != null) {
            String cmderRoot = System.getenv("CMDER_ROOT");
            if (cmderRoot != null) {
                return "Cmder (ConEmu)";
            }
            return "ConEmu";
        }

        // Windows cmd.exe or PowerShell detection
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win")) {
            // Check for PowerShell
            String psModulePath = System.getenv("PSModulePath");
            if (psModulePath != null) {
                // Check if running in PowerShell Core (pwsh) vs Windows PowerShell
                String pwshVersion = System.getenv("POWERSHELL_DISTRIBUTION_CHANNEL");
                if (pwshVersion != null) {
                    return "PowerShell Core";
                }
                // Could be Windows PowerShell or cmd.exe with PSModulePath inherited
            }

            // Check for MSYS2/Git Bash/Cygwin
            String msystem = System.getenv("MSYSTEM");
            if (msystem != null) {
                return "MSYS2 (" + msystem + ")";
            }
            if (System.getenv("CYGWIN") != null ||
                    (term != null && term.toLowerCase().contains("cygwin"))) {
                return "Cygwin";
            }

            // If TERM is set, likely running in a Unix-like environment
            if (term != null && !term.isEmpty()) {
                if (term.toLowerCase().contains("xterm")) {
                    return "Git Bash / MinTTY";
                }
            }

            // Check for Windows console host (conhost.exe)
            // This is plain cmd.exe or PowerShell running in the legacy console
            // We can detect this by the absence of other terminal indicators
            String comspec = System.getenv("COMSPEC");
            if (comspec != null && comspec.toLowerCase().contains("cmd.exe")) {
                // Check if running PowerShell in cmd window
                String prompt = System.getenv("PROMPT");
                if (prompt != null && prompt.contains("$P$G")) {
                    return "Windows cmd.exe";
                }
                // Default to cmd.exe for Windows
                return "Windows cmd.exe";
            }
        }

        // tmux
        if (TerminalColorDetector.isRunningInTmux()) {
            return "tmux";
        }

        // screen
        if (TerminalColorDetector.isRunningInScreen()) {
            return "GNU Screen";
        }

        // Apple Terminal
        if ("Apple_Terminal".equals(termProgram)) {
            return "Apple Terminal";
        }

        // GNOME Terminal
        if (System.getenv("GNOME_TERMINAL_SCREEN") != null) {
            return "GNOME Terminal";
        }

        // Konsole
        if (System.getenv("KONSOLE_VERSION") != null) {
            return "Konsole";
        }

        // xterm
        if (term != null && term.startsWith("xterm")) {
            String xtermVersion = System.getenv("XTERM_VERSION");
            if (xtermVersion != null) {
                return "XTerm";
            }
            return "xterm-compatible";
        }

        // Fall back to TERM_PROGRAM if set
        if (termProgram != null) {
            return termProgram;
        }

        // Fall back to TERM if set
        if (term != null) {
            return term;
        }

        return "Unknown";
    }

    /**
     * Enable debug logging for the TerminalColorDetector class.
     */
    private static void enableDebugLogging() {
        // Configure java.util.logging to show FINE level messages
        Logger logger = Logger.getLogger("org.aesh.terminal.tty.TerminalColorDetector");
        logger.setLevel(Level.FINE);

        // Also need a handler that will output FINE messages
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.FINE);
        logger.addHandler(handler);

        // Don't pass to parent handlers to avoid duplicate output
        logger.setUseParentHandlers(false);

        System.out.println(ANSIBuilder.builder().faint("[Debug logging enabled for TerminalColorDetector]").toString());
        System.out.println();
    }
}
