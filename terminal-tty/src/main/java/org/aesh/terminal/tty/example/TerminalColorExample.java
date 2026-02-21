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

import org.aesh.terminal.Device;
import org.aesh.terminal.tty.TerminalColorDetector;
import org.aesh.terminal.tty.TerminalConnection;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.ANSIBuilder;
import org.aesh.terminal.utils.ColorDepth;
import org.aesh.terminal.utils.TerminalColorCapability;
import org.aesh.terminal.utils.TerminalEnvironment;

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

    public TerminalColorExample() {
    }

    /**
     * Main entry point for the terminal color example.
     *
     * @param args command line arguments; use {@code -v} or {@code --verbose} to enable debug logging
     */
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

    /**
     * Runs the terminal color detection example.
     *
     * @param connection the terminal connection to use for output
     */
    private static void runExample(TerminalConnection connection) {
        // First, do environment-based detection to get capability for the builder
        long envStart = System.currentTimeMillis();
        TerminalColorCapability envCap = TerminalColorCapability.detectFromEnvironment();
        long envTime = System.currentTimeMillis() - envStart;

        // Fast detect (FG+BG only, for theme detection)
        long fastStart = System.currentTimeMillis();
        TerminalColorCapability fastCap = TerminalColorDetector.detect(connection);
        long fastTime = System.currentTimeMillis() - fastStart;

        // Full detect (FG+BG+cursor+palette)
        long fullStart = System.currentTimeMillis();
        TerminalColorCapability fullCap = TerminalColorDetector.detectFull(connection);
        long fullTime = System.currentTimeMillis() - fullStart;

        // Create a single reusable ANSIBuilder with the detected capability
        ANSIBuilder builder = ANSIBuilder.builder(fullCap);

        connection.write("\n");
        connection.write(builder.bold("=== Terminal Color Detection Example ===").toLine());
        connection.write("\n");

        // First, show environment-based detection (fast, no terminal query)
        builder.reset();
        connection.write(builder.bold("1. Environment-Based Detection").toLine());
        connection.write("   This uses environment variables only, no terminal queries.\n");
        connection.write("   Detection time: " + envTime + "ms\n");
        connection.write("\n");

        printCapability(connection, "   ", envCap);

        connection.write("\n");
        builder.reset();
        connection.write(builder.bold("2. Fast Detection (FG+BG only)").toLine());
        connection.write("   Queries only foreground and background colors (OSC 10/11).\n");
        connection.write("   Detection time: " + fastTime + "ms\n");
        connection.write("\n");

        printCapability(connection, "   ", fastCap);

        connection.write("\n");
        builder.reset();
        connection.write(builder.bold("3. Full Detection (FG+BG+cursor+palette)").toLine());
        connection.write("   Queries all colors including cursor and 16 palette colors.\n");
        connection.write("   Detection time: " + fullTime + "ms\n");
        connection.write("\n");

        printCapability(connection, "   ", fullCap);

        connection.write("\n");
        builder.reset();
        connection.write(builder.bold("4. Color Demonstration").toLine());
        connection.write("   Using suggested colors based on detected theme:\n");
        connection.write("\n");

        demonstrateColors(connection, builder);

        connection.write("\n");
        builder.reset();
        connection.write(builder.bold("5. Color Depth Capabilities").toLine());
        connection.write("\n");

        demonstrateColorDepth(connection, builder, fullCap.getColorDepth());

        connection.write("\n");
        builder.reset();
        connection.write(builder.bold("6. Environment Variables").toLine());
        connection.write("\n");
        printEnvironmentVariables(connection, builder);

        connection.write("\n");
    }

    /**
     * Prints the terminal color capability information.
     *
     * @param connection the terminal connection to use for output
     * @param indent the indentation string to prefix each line
     * @param cap the terminal color capability to print
     */
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

        if (cap.hasCursorColor()) {
            int[] cursor = cap.getCursorRGB();
            connection.write(indent + "Cursor:      RGB(" + cursor[0] + ", " + cursor[1] + ", " + cursor[2] + ") " +
                    formatColorSwatch(cursor) + "\n");
        } else {
            connection.write(indent + "Cursor:      Not detected\n");
        }

        if (cap.hasPaletteColors()) {
            java.util.Map<Integer, int[]> palette = cap.getPaletteColors();
            connection.write(indent + "Palette:     " + palette.size() + " colors detected\n");
            // Show the 8 standard colors in a compact format
            StringBuilder swatches = new StringBuilder(indent + "             ");
            String[] names = { "Blk", "Red", "Grn", "Yel", "Blu", "Mag", "Cyn", "Wht" };
            for (int i = 0; i < 8 && i < palette.size(); i++) {
                int[] color = palette.get(i);
                if (color != null) {
                    swatches.append(formatColorSwatch(color));
                }
            }
            connection.write(swatches + "\n");
            connection.write(indent + "             ");
            for (String name : names) {
                connection.write(name + " ");
            }
            connection.write("\n");
        } else {
            connection.write(indent + "Palette:     Not detected (OSC 4 not supported)\n");
        }
    }

    /**
     * Formats a color swatch using 24-bit color escape sequences.
     *
     * @param rgb the RGB color values as an array of three integers
     * @return an ANSI escape sequence that displays a colored block
     */
    private static String formatColorSwatch(int[] rgb) {
        // Create a colored block using 24-bit color if available
        return "\u001B[48;2;" + rgb[0] + ";" + rgb[1] + ";" + rgb[2] + "m    " + ANSI.RESET;
    }

    /**
     * Demonstrates suggested colors for various message types based on terminal theme.
     *
     * @param connection the terminal connection to use for output
     * @param builder the reusable ANSIBuilder instance
     */
    private static void demonstrateColors(TerminalConnection connection, ANSIBuilder builder) {
        String indent = "   ";

        // Normal text - use textCode for suggested foreground
        builder.reset();
        connection.write(builder.append(indent).textCode(37).append("Normal text (code 37)").toLine());

        // Error - uses bright red for dark themes, dark red for light themes
        builder.reset();
        connection.write(builder.append(indent).error("Error message").toLine());

        // Success - uses bright green for dark themes, dark green for light themes
        builder.reset();
        connection.write(builder.append(indent).success("Success message").toLine());

        // Warning - uses bright yellow for dark themes, dark yellow for light themes
        builder.reset();
        connection.write(builder.append(indent).warning("Warning message").toLine());

        // Info - uses bright blue for dark themes, dark blue for light themes
        builder.reset();
        connection.write(builder.append(indent).info("Info message").toLine());

        // Debug - uses white for dark themes, gray for light themes (subdued)
        builder.reset();
        connection.write(builder.append(indent).debug("Debug message").toLine());

        // Trace - uses gray for both themes (least prominent)
        builder.reset();
        connection.write(builder.append(indent).trace("Trace message").toLine());

        // Timestamp - uses bright cyan for dark themes, dark cyan for light themes
        builder.reset();
        connection.write(builder.append(indent).timestamp("Timestamp").toLine());

        // Message - uses bright magenta for dark themes, dark magenta for light themes
        builder.reset();
        connection.write(builder.append(indent).message("Highlighted message").toLine());

        // Combined log line examples using ANSIBuilder chaining
        connection.write("\n");
        builder.reset();
        connection.write(builder.append(indent).append("Example log output (all levels):\n").toString());

        // ERROR level
        builder.reset();
        connection.write(builder.append(indent)
                .timestamp("2024-01-15 10:30:45").append(" ")
                .error("[ERROR]").append(" ")
                .append("Connection to database failed")
                .toLine());

        // WARN level
        builder.reset();
        connection.write(builder.append(indent)
                .timestamp("2024-01-15 10:30:46").append(" ")
                .warning("[WARN]").append(" ")
                .append("Low memory condition detected")
                .toLine());

        // INFO level
        builder.reset();
        connection.write(builder.append(indent)
                .timestamp("2024-01-15 10:30:47").append(" ")
                .info("[INFO]").append(" ")
                .message("Application started successfully")
                .toLine());

        // DEBUG level
        builder.reset();
        connection.write(builder.append(indent)
                .timestamp("2024-01-15 10:30:48").append(" ")
                .debug("[DEBUG]").append(" ")
                .append("Configuration loaded from /etc/app.conf")
                .toLine());

        // TRACE level
        builder.reset();
        connection.write(builder.append(indent)
                .timestamp("2024-01-15 10:30:49").append(" ")
                .trace("[TRACE]").append(" ")
                .append("Entering method processRequest()")
                .toLine());
    }

    /**
     * Demonstrates the color depth capabilities of the terminal.
     *
     * @param connection the terminal connection to use for output
     * @param builder the reusable ANSIBuilder instance
     * @param depth the color depth capability of the terminal
     */
    private static void demonstrateColorDepth(TerminalConnection connection, ANSIBuilder builder, ColorDepth depth) {
        String indent = "   ";

        builder.reset();
        connection.write(builder.append(indent).append("Supports any color:  ")
                .append(depth.supportsColor() ? "Yes" : "No").toLine());
        builder.reset();
        connection.write(builder.append(indent).append("Supports 16 colors:  ")
                .append(depth.supports16Colors() ? "Yes" : "No").toLine());
        builder.reset();
        connection.write(builder.append(indent).append("Supports 256 colors: ")
                .append(depth.supports256Colors() ? "Yes" : "No").toLine());
        builder.reset();
        connection.write(builder.append(indent).append("Supports true color: ")
                .append(depth.supportsTrueColor() ? "Yes" : "No").toLine());

        if (depth.supports256Colors()) {
            connection.write("\n");
            builder.reset();
            connection.write(builder.append(indent).append("256-color palette sample:").toLine());
            // Build palette using bg256 for each color
            builder.reset();
            builder.append(indent);
            for (int i = 16; i < 52; i++) {
                builder.bg256(i).append("  ");
            }
            connection.write(builder.toLine());
        }

        if (depth.supportsTrueColor()) {
            connection.write("\n");
            builder.reset();
            connection.write(builder.append(indent).append("True color gradient sample:").toLine());
            // Build gradient using bgRgb for each color
            builder.reset();
            builder.append(indent);
            for (int i = 0; i < 36; i++) {
                int r = (int) (Math.sin(0.1 * i + 0) * 127 + 128);
                int g = (int) (Math.sin(0.1 * i + 2) * 127 + 128);
                int b = (int) (Math.sin(0.1 * i + 4) * 127 + 128);
                builder.bgRgb(r, g, b).append(" ");
            }
            connection.write(builder.toLine());
        }
    }

    /**
     * Prints relevant environment variables used for terminal detection.
     *
     * @param connection the terminal connection to use for output
     * @param builder the reusable ANSIBuilder instance
     */
    private static void printEnvironmentVariables(TerminalConnection connection, ANSIBuilder builder) {
        String indent = "   ";

        // First, show detected terminal type
        String detectedTerminal = detectTerminalType();
        builder.reset();
        connection.write(builder.append(indent).bold("Detected Terminal: " + detectedTerminal).toLine());
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
                builder.reset();
                connection.write(builder.append(indent).append(var).append(" = ").append(value).toLine());
            }
        }

        TerminalEnvironment env = TerminalEnvironment.getInstance();

        connection.write("\n");
        builder.reset();
        connection.write(builder.append(indent).append("OSC query supported: ")
                .append(env.supportsOscQueries() ? "Yes" : "No").toLine());
        builder.reset();
        connection.write(builder.append(indent).append("Running in multiplexer: ")
                .append(env.isInMultiplexer() ? "Yes" : "No").toLine());
        builder.reset();
        connection.write(builder.append(indent).append("Tmux passthrough: ")
                .append(env.isTmuxPassthroughEnabled() ? "Yes" : "No").toLine());

        Device.TerminalType termType = env.getTerminalType();
        builder.reset();
        connection.write(builder.append(indent).append("Theme mode query (CSI ? 996 n): ")
                .append(termType.supportsThemeDsr() ? "Yes" : "No").toLine());

        // Additional info for JetBrains
        if (env.isJetBrains()) {
            connection.write("\n");
            builder.reset();
            connection.write(builder.append(indent)
                    .faint("JetBrains IDE detected - theme read from config files").toLine());
        }

        // Additional info if in tmux
        if (env.isInTmux()) {
            connection.write("\n");
            builder.reset();
            connection.write(builder.append(indent)
                    .faint("Tip: If colors are not detected in tmux, try:").toLine());
            builder.reset();
            connection.write(builder.append(indent)
                    .faint("  1. tmux set -g allow-passthrough on").toLine());
            builder.reset();
            connection.write(builder.append(indent)
                    .faint("  2. export TMUX_PASSTHROUGH=1").toLine());
        }

        // Additional info for Windows cmd.exe
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("win") && !env.isWindowsTerminal() && !env.isConEmu()) {
            connection.write("\n");
            builder.reset();
            connection.write(builder.append(indent)
                    .faint("Windows Build: " + getWindowsBuildInfo()).toLine());
            builder.reset();
            connection.write(builder.append(indent)
                    .faint("Note: Windows cmd.exe does not support OSC color queries.").toLine());
            builder.reset();
            connection.write(builder.append(indent)
                    .faint("Theme detection uses Windows registry settings.").toLine());
            builder.reset();
            connection.write(builder.append(indent)
                    .faint("For better color support, consider using Windows Terminal.").toLine());
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

        int build = org.aesh.terminal.tty.PlatformThemeDetector.getWindowsBuildNumber();
        if (build > 0) {
            String vtSupport = build >= 14931 ? " (VT sequences supported)" : " (VT sequences NOT supported)";
            return osName + " " + osVersion + " Build " + build + vtSupport;
        }

        return osName + " " + osVersion;
    }

    /**
     * Detect the terminal emulator type based on environment variables.
     * <p>
     * Uses {@link TerminalEnvironment} for centralized detection and provides
     * human-readable names for display.
     *
     * @return a human-readable terminal name
     */
    private static String detectTerminalType() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();
        Device.TerminalType type = env.getTerminalType();

        // Map terminal types to human-readable names
        switch (type) {
            case JETBRAINS:
                return "JetBrains IDE (JediTerm)";
            case VSCODE:
                return "Visual Studio Code";
            case ITERM2:
                return "iTerm2";
            case KITTY:
                return "Kitty";
            case WEZTERM:
                return "WezTerm";
            case GHOSTTY:
                return "Ghostty";
            case ALACRITTY:
                return "Alacritty";
            case WINDOWS_TERMINAL:
                return "Windows Terminal";
            case CONEMU:
                return "ConEmu";
            case MINTTY:
                return "MinTTY / Git Bash";
            case TMUX:
                return "tmux";
            case SCREEN:
                return "GNU Screen";
            case APPLE_TERMINAL:
                return "Apple Terminal";
            case GNOME_TERMINAL:
                return "GNOME Terminal";
            case KONSOLE:
                return "Konsole";
            case FOOT:
                return "Foot";
            case CONTOUR:
                return "Contour";
            case RIO:
                return "Rio";
            case WARP:
                return "Warp";
            case WAVE:
                return "Wave";
            case HYPER:
                return "Hyper";
            case TABBY:
                return "Tabby";
            case EXTRATERM:
                return "Extraterm";
            case RXVT:
                return "rxvt/urxvt";
            case XTERM:
                return "xterm-compatible";
            case LINUX_CONSOLE:
                return "Linux Console";
            case UNKNOWN:
            default:
                // Fall back to TERM_PROGRAM or TERM if available
                String termProgram = env.getTermProgram();
                if (termProgram != null) {
                    return termProgram;
                }
                String term = env.getTerm();
                if (term != null) {
                    return term;
                }
                return "Unknown";
        }
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
