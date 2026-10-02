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
package org.aesh.terminal.detect;

import java.util.Locale;
import java.util.Map;

/**
 * All terminal detection logic in a single class.
 * Reads environment variables once and caches the results.
 */
final class TerminalDetector {

    // Environment variables (injected map for testability; production
    // passes System.getenv(), tests pass fixture maps)
    private final Map<String, String> env;

    // Operating system name (injected for testability; production passes
    // the real os.name, tests pass literals like "Windows 11"). Drives the
    // IDE layout selection and the OS theme branch.
    private final String osName;

    // Environment variables
    private final String term;
    private final String termProgram;
    private final String terminalEmulator;
    private final String colorterm;
    private final String kittyWindowId;
    private final String ghosttyResourcesDir;
    private final String weztermPane;
    private final String itermSessionId;
    private final String wtSession;
    private final String wtProfileId;
    private final String alacrittySocket;
    private final String colorFgBg;
    private final String appleInterfaceStyle;
    private final boolean inTmux;
    private final boolean inScreen;

    // Computed results
    final String terminalName;
    final boolean trueColor;
    final boolean colors256;
    final boolean supportsColor;
    final ImageProtocol imageProtocol;
    final TerminalTheme theme;

    TerminalDetector() {
        this(System.getenv());
    }

    TerminalDetector(Map<String, String> env) {
        this(env, System.getProperty("os.name", ""));
    }

    TerminalDetector(Map<String, String> env, String osName) {
        this.env = env;
        this.osName = osName;
        this.term = env.get("TERM");
        this.termProgram = env.get("TERM_PROGRAM");
        this.terminalEmulator = env.get("TERMINAL_EMULATOR");
        this.colorterm = env.get("COLORTERM");
        this.kittyWindowId = env.get("KITTY_WINDOW_ID");
        this.ghosttyResourcesDir = env.get("GHOSTTY_RESOURCES_DIR");
        this.weztermPane = env.get("WEZTERM_PANE");
        this.itermSessionId = env.get("ITERM_SESSION_ID");
        this.wtSession = sessionMarker(env.get("WT_SESSION"));
        this.wtProfileId = sessionMarker(env.get("WT_PROFILE_ID"));
        this.alacrittySocket = env.get("ALACRITTY_SOCKET");
        this.colorFgBg = env.get("COLORFGBG");
        this.appleInterfaceStyle = env.get("APPLE_INTERFACE_STYLE");
        String tmux = env.get("TMUX");
        this.inTmux = tmux != null && !tmux.isEmpty();
        this.inScreen = term != null && term.toLowerCase().startsWith("screen");

        this.terminalName = detectTerminalName();
        this.trueColor = detectTrueColor();
        this.colors256 = trueColor || detect256Colors();
        // Every recognized terminal supports at least basic 8-color
        // output; unknown ones fail safe to false.
        this.supportsColor = colors256 || !"unknown".equals(terminalName);
        this.imageProtocol = detectImageProtocol();
        this.theme = detectTheme();
    }

    // ==================== Terminal Name Detection ====================

    private static String sessionMarker(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }

    private String detectTerminalName() {
        if (isJetBrains())
            return "jetbrains";
        // IDE terminals can inherit session markers from their launcher.
        if (termProgram != null && termProgram.toLowerCase(Locale.ROOT).contains("vscode"))
            return "vscode";
        if (kittyWindowId != null)
            return "kitty";
        if (ghosttyResourcesDir != null)
            return "ghostty";
        if (weztermPane != null)
            return "wezterm";
        if (itermSessionId != null)
            return "iterm2";
        if (isWindowsTerminal())
            return "windows-terminal";
        if (alacrittySocket != null)
            return "alacritty";

        if (termProgram != null) {
            String lower = termProgram.toLowerCase();
            if (lower.contains("iterm"))
                return "iterm2";
            if (lower.contains("apple_terminal") || lower.contains("terminal.app"))
                return "apple-terminal";
            if (lower.contains("hyper"))
                return "hyper";
            if (lower.contains("tabby") || lower.contains("terminus"))
                return "tabby";
            if (lower.contains("mintty"))
                return "mintty";
        }

        if (term != null) {
            String lower = term.toLowerCase();
            // Multiplexers only when no outer-terminal signal matched
            // above: their TERM prefix is the honest identity, since live
            // queries cannot see past them by design.
            if (lower.startsWith("tmux"))
                return "tmux";
            if (lower.startsWith("screen"))
                return "screen";
            if (lower.contains("kitty"))
                return "kitty";
            if (lower.contains("ghostty"))
                return "ghostty";
            if (lower.contains("foot"))
                return "foot";
            if (lower.contains("contour"))
                return "contour";
            if (lower.contains("konsole"))
                return "konsole";
            if (lower.startsWith("xterm"))
                return "xterm";
            if (lower.equals("linux"))
                return "linux-console";
        }

        return "unknown";
    }

    private boolean isJetBrains() {
        return terminalEmulator != null &&
                (terminalEmulator.contains("JetBrains") || terminalEmulator.contains("JediTerm"));
    }

    private boolean isWindowsTerminal() {
        // Installed applications and default-terminal preferences do not
        // identify the host of this process. Only session facts apply here.
        return wtSession != null || wtProfileId != null;
    }

    boolean isInMultiplexer() {
        if (inTmux || inScreen)
            return true;
        if (term == null)
            return false;
        String lower = term.toLowerCase();
        return lower.startsWith("tmux") || lower.startsWith("screen");
    }

    // ==================== Color Depth Detection ====================

    private boolean detectTrueColor() {
        if (colorterm != null) {
            String lower = colorterm.toLowerCase();
            if ("truecolor".equals(lower) || "24bit".equals(lower))
                return true;
        }
        if (term != null) {
            String lower = term.toLowerCase();
            if (lower.contains("truecolor") || lower.contains("24bit") || lower.contains("direct"))
                return true;
        }
        return isKnownTrueColorTerminal();
    }

    private boolean detect256Colors() {
        if (term != null) {
            String lower = term.toLowerCase();
            if (lower.contains("256color") || lower.contains("256-color"))
                return true;
        }
        return isKnown256ColorTerminal();
    }

    private boolean isKnownTrueColorTerminal() {
        switch (terminalName) {
            case "kitty":
            case "ghostty":
            case "wezterm":
            case "iterm2":
            case "alacritty":
            case "vscode":
            case "windows-terminal":
            case "foot":
            case "contour":
            case "konsole":
            case "hyper":
            case "tabby":
            case "mintty":
                return true;
            default:
                return false;
        }
    }

    private boolean isKnown256ColorTerminal() {
        switch (terminalName) {
            case "xterm":
            case "apple-terminal":
            case "jetbrains":
                return true;
            default:
                return false;
        }
    }

    // ==================== Image Protocol Detection ====================

    private ImageProtocol detectImageProtocol() {
        boolean inMux = isInMultiplexer();

        if (ghosttyResourcesDir != null) {
            return inMux ? ImageProtocol.SIXEL : ImageProtocol.KITTY;
        }
        if (kittyWindowId != null) {
            return inMux ? ImageProtocol.NONE : ImageProtocol.KITTY;
        }
        if (itermSessionId != null || weztermPane != null) {
            return ImageProtocol.ITERM2;
        }

        ImageProtocol fromName = imageProtocolForTerminal(terminalName);
        if (fromName != ImageProtocol.NONE) {
            if (inMux && fromName == ImageProtocol.KITTY) {
                return multiplexerFallback(terminalName);
            }
            return fromName;
        }

        if (term != null) {
            return imageProtocolFromTermType(term);
        }

        return ImageProtocol.NONE;
    }

    private static ImageProtocol imageProtocolForTerminal(String name) {
        switch (name) {
            case "kitty":
            case "ghostty":
            case "konsole":
                return ImageProtocol.KITTY;
            case "iterm2":
            case "wezterm":
            case "mintty":
            case "vscode":
            case "tabby":
            case "hyper":
                return ImageProtocol.ITERM2;
            case "foot":
            case "contour":
            case "windows-terminal":
                return ImageProtocol.SIXEL;
            default:
                return ImageProtocol.NONE;
        }
    }

    private static ImageProtocol multiplexerFallback(String name) {
        switch (name) {
            case "ghostty":
            case "konsole":
                return ImageProtocol.SIXEL;
            default:
                return ImageProtocol.NONE;
        }
    }

    private static ImageProtocol imageProtocolFromTermType(String termType) {
        String lower = termType.toLowerCase();
        if (lower.contains("kitty") || lower.contains("ghostty") || lower.contains("konsole"))
            return ImageProtocol.KITTY;
        if (lower.contains("iterm") || lower.contains("wezterm") || lower.contains("mintty") ||
                lower.contains("vscode") || lower.contains("tabby") || lower.contains("hyper"))
            return ImageProtocol.ITERM2;
        if (lower.contains("mlterm") || lower.contains("foot") || lower.contains("contour") ||
                lower.contains("yaft") || lower.contains("ctx") || lower.contains("darktile"))
            return ImageProtocol.SIXEL;
        return ImageProtocol.NONE;
    }

    // ==================== Theme Detection ====================

    private TerminalTheme detectTheme() {
        if (colorFgBg != null) {
            String[] parts = colorFgBg.split(";");
            if (parts.length >= 2) {
                try {
                    int bg = Integer.parseInt(parts[parts.length - 1].trim());
                    boolean isDark = bg < 7 || bg == 8;
                    return isDark ? TerminalTheme.DARK : TerminalTheme.LIGHT;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (appleInterfaceStyle != null) {
            return "Dark".equalsIgnoreCase(appleInterfaceStyle)
                    ? TerminalTheme.DARK
                    : TerminalTheme.LIGHT;
        }
        return TerminalTheme.UNKNOWN;
    }

    /**
     * Detect theme using platform-specific checks (subprocess/file I/O).
     * Called by detectFull() when fast env-var detection returns UNKNOWN.
     * <p>
     * IDE settings files are consulted first (JetBrains, VSCode, Windows
     * Terminal); the OS-level checks below apply when no IDE terminal
     * is detected or its settings yield nothing.
     * <p>
     * Runs on this detector's captured environment: callers reuse the
     * detector they already scanned instead of constructing a second one
     * (which would repeat registry identity subprocesses on Windows).
     */
    TerminalTheme detectIdeOrPlatformTheme() {
        TerminalTheme ide = IdeThemeDetector.detect(terminalName, env, osName);
        if (ide != TerminalTheme.UNKNOWN) {
            return ide;
        }
        return detectOsPlatformTheme(osName);
    }

    /**
     * OS-level theme checks dispatched on the captured OS name.
     *
     * @param osName the operating system name
     * @return the detected theme, or UNKNOWN if not determinable
     */
    private static TerminalTheme detectOsPlatformTheme(String osName) {
        String os = osName == null ? "" : osName.toLowerCase();
        try {
            if (os.contains("mac")) {
                return detectMacOsTheme();
            } else if (os.contains("windows")) {
                return detectWindowsTheme();
            } else {
                return detectLinuxDesktopTheme();
            }
        } catch (Exception ignored) {
            return TerminalTheme.UNKNOWN;
        }
    }

    private static TerminalTheme detectMacOsTheme() {
        String result = execCommand("defaults", "read", "-g", "AppleInterfaceStyle");
        if (result != null && result.trim().toLowerCase().contains("dark")) {
            return TerminalTheme.DARK;
        }
        if (result != null) {
            return TerminalTheme.LIGHT;
        }
        // "defaults read" exits non-zero when key doesn't exist (light mode)
        return TerminalTheme.LIGHT;
    }

    private static TerminalTheme detectWindowsTheme() {
        Integer value = regQueryDword(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "AppsUseLightTheme");
        return themeFromDword(value);
    }

    /**
     * Map a registry DWORD theme value to a theme. {@code AppsUseLightTheme}
     * reports {@code 0} for dark mode and nonzero for light mode; a missing
     * or malformed value resolves to UNKNOWN rather than a guessed theme.
     *
     * @param value the parsed DWORD value, or null if unavailable
     * @return the theme, or UNKNOWN if the value is missing
     */
    static TerminalTheme themeFromDword(Integer value) {
        if (value == null) {
            return TerminalTheme.UNKNOWN;
        }
        return value == 0 ? TerminalTheme.DARK : TerminalTheme.LIGHT;
    }

    private static TerminalTheme detectLinuxDesktopTheme() {
        // GNOME / freedesktop color-scheme
        String result = execCommand("gsettings", "get",
                "org.gnome.desktop.interface", "color-scheme");
        if (result != null) {
            String lower = result.trim().toLowerCase();
            if (lower.contains("dark"))
                return TerminalTheme.DARK;
            if (lower.contains("light") || lower.contains("default"))
                return TerminalTheme.LIGHT;
        }
        // KDE Plasma
        result = execCommand("kreadconfig5", "--group", "General",
                "--key", "ColorScheme");
        if (result != null) {
            String lower = result.trim().toLowerCase();
            if (lower.contains("dark"))
                return TerminalTheme.DARK;
            if (lower.contains("light") || !lower.isEmpty())
                return TerminalTheme.LIGHT;
        }
        return TerminalTheme.UNKNOWN;
    }

    /**
     * Run a command through the shared runner, capturing merged
     * stdout/stderr output.
     * <p>
     * Shared by the platform probes; lifecycle handling (deadline,
     * drain, destroy, reap) lives in {@code ProcessRunner}. Text is
     * decoded with the platform default charset, matching the previous
     * per-call behavior.
     *
     * @param cmd the command and arguments
     * @return the captured output, or null on failure or nonzero exit
     */
    private static String execCommand(String... cmd) {
        try {
            ProcessRunner.Result result = ProcessRunner.execute(cmd);
            if (result.timedOut() || result.exitCode() != 0) {
                return null;
            }
            return result.text(java.nio.charset.Charset.defaultCharset());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Query a REG_DWORD registry value.
     *
     * @param key the registry key
     * @param valueName the value name
     * @return the parsed value, or null if missing or malformed
     */
    private static Integer regQueryDword(String key, String valueName) {
        String output = execCommand("reg", "query", key, "/v", valueName);
        return parseRegDword(output, valueName);
    }

    /**
     * Parse a REG_DWORD value from {@code reg query} output. Only DWORD
     * lines match; string values for the same name are ignored. Package
     * visible for headless tests with fixture output.
     *
     * @param output the command output, or null if the command failed
     * @param valueName the value name to find
     * @return the parsed value, or null if missing or malformed
     */
    static Integer parseRegDword(String output, String valueName) {
        if (output == null || valueName == null) {
            return null;
        }
        for (String line : output.split("\\r?\\n")) {
            if (!line.contains(valueName)) {
                continue;
            }
            int idx = line.indexOf("REG_DWORD");
            if (idx < 0) {
                continue;
            }
            String[] tokens = line.substring(idx + 9).trim().split("\\s+");
            if (tokens.length == 0 || tokens[0].isEmpty()) {
                return null;
            }
            try {
                return Integer.decode(tokens[0]);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
