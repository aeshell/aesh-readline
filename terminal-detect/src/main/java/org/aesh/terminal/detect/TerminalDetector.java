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

import java.util.Map;

/**
 * All terminal detection logic in a single class.
 * Reads environment variables once and caches the results.
 */
final class TerminalDetector {

    // Environment variables (injected map for testability; production
    // passes System.getenv(), tests pass fixture maps)
    private final Map<String, String> env;

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
    final ImageProtocol imageProtocol;
    final TerminalTheme theme;

    TerminalDetector() {
        this(System.getenv());
    }

    TerminalDetector(Map<String, String> env) {
        this.env = env;
        this.term = env.get("TERM");
        this.termProgram = env.get("TERM_PROGRAM");
        this.terminalEmulator = env.get("TERMINAL_EMULATOR");
        this.colorterm = env.get("COLORTERM");
        this.kittyWindowId = env.get("KITTY_WINDOW_ID");
        this.ghosttyResourcesDir = env.get("GHOSTTY_RESOURCES_DIR");
        this.weztermPane = env.get("WEZTERM_PANE");
        this.itermSessionId = env.get("ITERM_SESSION_ID");
        this.wtSession = env.get("WT_SESSION");
        this.wtProfileId = env.get("WT_PROFILE_ID");
        this.alacrittySocket = env.get("ALACRITTY_SOCKET");
        this.colorFgBg = env.get("COLORFGBG");
        this.appleInterfaceStyle = env.get("APPLE_INTERFACE_STYLE");
        String tmux = env.get("TMUX");
        this.inTmux = tmux != null && !tmux.isEmpty();
        this.inScreen = term != null && term.toLowerCase().startsWith("screen");

        this.terminalName = detectTerminalName();
        this.trueColor = detectTrueColor();
        this.colors256 = trueColor || detect256Colors();
        this.imageProtocol = detectImageProtocol();
        this.theme = detectTheme();
    }

    // ==================== Terminal Name Detection ====================

    private String detectTerminalName() {
        if (isJetBrains())
            return "jetbrains";
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
            if (lower.contains("vscode"))
                return "vscode";
            if (lower.contains("hyper"))
                return "hyper";
            if (lower.contains("tabby") || lower.contains("terminus"))
                return "tabby";
            if (lower.contains("mintty"))
                return "mintty";
        }

        if (term != null) {
            String lower = term.toLowerCase();
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
        if (wtSession != null || wtProfileId != null) {
            return true;
        }
        return detectWindowsTerminalByRegistry();
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
     */
    static TerminalTheme detectPlatformTheme() {
        return new TerminalDetector().detectIdeOrPlatformTheme();
    }

    /**
     * Two-stage platform theme detection for a detector with known
     * environment: IDE settings files first, OS-level checks second.
     * Package-private for tests with fixture environments.
     *
     * @return the detected theme, or UNKNOWN if not determinable
     */
    TerminalTheme detectIdeOrPlatformTheme() {
        TerminalTheme ide = IdeThemeDetector.detect(terminalName, env);
        if (ide != TerminalTheme.UNKNOWN) {
            return ide;
        }
        return detectOsPlatformTheme();
    }

    private static TerminalTheme detectOsPlatformTheme() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        try {
            if (osName.contains("mac")) {
                return detectMacOsTheme();
            } else if (osName.contains("windows")) {
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
        String value = regQuery(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "AppsUseLightTheme");
        if (value != null) {
            return "0".equals(value.trim()) ? TerminalTheme.DARK : TerminalTheme.LIGHT;
        }
        return TerminalTheme.UNKNOWN;
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

    private static String execCommand(String... cmd) {
        Process process = null;
        try {
            process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true).start();
            byte[] buf = new byte[1024];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = process.getInputStream().read(buf)) != -1) {
                sb.append(new String(buf, 0, n));
            }
            process.waitFor();
            if (process.exitValue() != 0)
                return null;
            return sb.toString();
        } catch (Exception ignored) {
            return null;
        } finally {
            if (process != null)
                process.destroy();
        }
    }

    // ==================== Windows Terminal Registry Detection ====================

    private static boolean detectWindowsTerminalByRegistry() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("windows")) {
            return false;
        }
        try {
            String delegation = regQuery("HKCU\\Console\\%%Startup", "DelegationTerminal");
            if (delegation == null)
                return false;
            String lower = delegation.toLowerCase();
            if (lower.contains("{2eaca947-7f5f-4cfa-ba87-8f7fbeefbe69}") ||
                    lower.contains("{e12cff52-a866-4c77-9a90-f570a7aa2c6b}"))
                return true;
            if (lower.contains("{00000000-0000-0000-0000-000000000000}"))
                return isWindowsTerminalInstalled();
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean isWindowsTerminalInstalled() {
        String path = regQuery(
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\App Paths\\wt.exe", "Path");
        return path != null && !path.trim().isEmpty();
    }

    private static String regQuery(String key, String valueName) {
        Process process = null;
        try {
            process = new ProcessBuilder("reg", "query", key, "/v", valueName)
                    .redirectErrorStream(true).start();
            byte[] buf = new byte[1024];
            StringBuilder sb = new StringBuilder();
            int n;
            while ((n = process.getInputStream().read(buf)) != -1) {
                sb.append(new String(buf, 0, n));
            }
            process.waitFor();
            if (process.exitValue() != 0)
                return null;
            for (String line : sb.toString().split("\\r?\\n")) {
                int idx = line.indexOf("REG_SZ");
                if (idx >= 0)
                    return line.substring(idx + 6).trim();
            }
            return null;
        } catch (Exception ignored) {
            return null;
        } finally {
            if (process != null)
                process.destroy();
        }
    }
}
