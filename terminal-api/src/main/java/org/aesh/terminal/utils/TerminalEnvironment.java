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
package org.aesh.terminal.utils;

import org.aesh.terminal.Device;

/**
 * Centralized utility for detecting terminal environment.
 * <p>
 * This class parses terminal-related environment variables and provides
 * a single source of truth for terminal detection. All terminal detection
 * logic should use this class rather than directly checking environment
 * variables.
 * <p>
 * The environment is parsed lazily on first access and cached for the
 * lifetime of the JVM. Use {@link #refresh()} to force re-parsing if
 * environment variables change (rare in practice).
 * <p>
 * Thread-safe: all methods are safe to call from multiple threads.
 *
 * @author Ståle Pedersen
 */
public final class TerminalEnvironment {

    // Cached environment values (parsed lazily)
    private static volatile TerminalEnvironment instance;

    // Terminal identification environment variables
    private final String term;
    private final String termProgram;
    private final String terminalEmulator;
    private final String colorterm;

    // Terminal-specific environment variables
    private final String kittyWindowId;
    private final String ghosttyResourcesDir;
    private final String weztermPane;
    private final String itermSessionId;
    private final String wtSession;
    private final String conEmuPid;
    private final String conEmuAnsi;
    private final String alacrittySocket;
    private final String tmux;
    private final String tmuxPassthrough;

    // Theme/color environment variables
    private final String colorFgBg;
    private final String appleInterfaceStyle;

    // Cached derived values
    private final Device.TerminalType terminalType;
    private final ColorDepth defaultColorDepth;
    private final boolean inTmux;
    private final boolean inScreen;
    private final boolean tmuxPassthroughEnabled;

    /**
     * Private constructor - use {@link #getInstance()} or static methods.
     */
    private TerminalEnvironment() {
        // Parse all environment variables
        this.term = System.getenv("TERM");
        this.termProgram = System.getenv("TERM_PROGRAM");
        this.terminalEmulator = System.getenv("TERMINAL_EMULATOR");
        this.colorterm = System.getenv("COLORTERM");

        this.kittyWindowId = System.getenv("KITTY_WINDOW_ID");
        this.ghosttyResourcesDir = System.getenv("GHOSTTY_RESOURCES_DIR");
        this.weztermPane = System.getenv("WEZTERM_PANE");
        this.itermSessionId = System.getenv("ITERM_SESSION_ID");
        this.wtSession = System.getenv("WT_SESSION");
        this.conEmuPid = System.getenv("ConEmuPID");
        this.conEmuAnsi = System.getenv("ConEmuANSI");
        this.alacrittySocket = System.getenv("ALACRITTY_SOCKET");
        this.tmux = System.getenv("TMUX");
        this.tmuxPassthrough = System.getenv("TMUX_PASSTHROUGH");

        this.colorFgBg = System.getenv("COLORFGBG");
        this.appleInterfaceStyle = System.getenv("APPLE_INTERFACE_STYLE");

        // Compute derived values
        this.inTmux = tmux != null && !tmux.isEmpty();
        this.inScreen = term != null && term.toLowerCase().startsWith("screen");
        this.tmuxPassthroughEnabled = computeTmuxPassthrough();
        this.terminalType = computeTerminalType();
        this.defaultColorDepth = computeColorDepth();
    }

    /**
     * Get the singleton instance.
     *
     * @return the terminal environment instance
     */
    public static TerminalEnvironment getInstance() {
        if (instance == null) {
            synchronized (TerminalEnvironment.class) {
                if (instance == null) {
                    instance = new TerminalEnvironment();
                }
            }
        }
        return instance;
    }

    /**
     * Force re-parsing of environment variables.
     * <p>
     * This is rarely needed since environment variables typically don't
     * change during JVM lifetime. Useful for testing or when environment
     * is modified programmatically.
     */
    public static void refresh() {
        synchronized (TerminalEnvironment.class) {
            instance = new TerminalEnvironment();
        }
    }

    // ==================== Raw Environment Variable Accessors ====================

    /**
     * Get the TERM environment variable.
     *
     * @return TERM value, or null if not set
     */
    public String getTerm() {
        return term;
    }

    /**
     * Get the TERM_PROGRAM environment variable.
     *
     * @return TERM_PROGRAM value, or null if not set
     */
    public String getTermProgram() {
        return termProgram;
    }

    /**
     * Get the TERMINAL_EMULATOR environment variable (used by JetBrains IDEs).
     *
     * @return TERMINAL_EMULATOR value, or null if not set
     */
    public String getTerminalEmulator() {
        return terminalEmulator;
    }

    /**
     * Get the COLORTERM environment variable.
     *
     * @return COLORTERM value, or null if not set
     */
    public String getColorterm() {
        return colorterm;
    }

    /**
     * Get the COLORFGBG environment variable (foreground/background colors).
     *
     * @return COLORFGBG value, or null if not set
     */
    public String getColorFgBg() {
        return colorFgBg;
    }

    /**
     * Get the APPLE_INTERFACE_STYLE environment variable (macOS dark mode).
     *
     * @return APPLE_INTERFACE_STYLE value, or null if not set
     */
    public String getAppleInterfaceStyle() {
        return appleInterfaceStyle;
    }

    // ==================== Terminal-Specific Checks ====================

    /**
     * Check if running in Kitty terminal.
     *
     * @return true if KITTY_WINDOW_ID is set
     */
    public boolean isKitty() {
        return kittyWindowId != null;
    }

    /**
     * Check if running in Ghostty terminal.
     *
     * @return true if GHOSTTY_RESOURCES_DIR is set
     */
    public boolean isGhostty() {
        return ghosttyResourcesDir != null;
    }

    /**
     * Check if running in WezTerm.
     *
     * @return true if WEZTERM_PANE is set
     */
    public boolean isWezTerm() {
        return weztermPane != null;
    }

    /**
     * Check if running in iTerm2.
     *
     * @return true if ITERM_SESSION_ID is set
     */
    public boolean isITerm2() {
        return itermSessionId != null;
    }

    /**
     * Check if running in Windows Terminal.
     *
     * @return true if WT_SESSION is set
     */
    public boolean isWindowsTerminal() {
        return wtSession != null;
    }

    /**
     * Check if running in ConEmu or Cmder.
     *
     * @return true if ConEmuPID or ConEmuANSI is set
     */
    public boolean isConEmu() {
        return conEmuPid != null || conEmuAnsi != null;
    }

    /**
     * Check if running in Alacritty.
     *
     * @return true if ALACRITTY_SOCKET is set
     */
    public boolean isAlacritty() {
        return alacrittySocket != null;
    }

    /**
     * Check if running in a JetBrains IDE terminal (IntelliJ, PyCharm, etc.).
     *
     * @return true if TERMINAL_EMULATOR contains "JetBrains" or "JediTerm"
     */
    public boolean isJetBrains() {
        if (terminalEmulator == null) {
            return false;
        }
        return terminalEmulator.contains("JetBrains") || terminalEmulator.contains("JediTerm");
    }

    /**
     * Check if running inside tmux.
     *
     * @return true if TMUX is set
     */
    public boolean isInTmux() {
        return inTmux;
    }

    /**
     * Check if running inside GNU Screen.
     *
     * @return true if TERM starts with "screen"
     */
    public boolean isInScreen() {
        return inScreen;
    }

    /**
     * Check if running inside a terminal multiplexer (tmux or screen).
     *
     * @return true if in tmux or screen
     */
    public boolean isInMultiplexer() {
        return inTmux || inScreen || isTermTypeMultiplexer();
    }

    /**
     * Check if tmux passthrough is enabled.
     *
     * @return true if TMUX_PASSTHROUGH indicates passthrough is enabled
     */
    public boolean isTmuxPassthroughEnabled() {
        return tmuxPassthroughEnabled;
    }

    /**
     * Check if true color is explicitly indicated by COLORTERM.
     *
     * @return true if COLORTERM is "truecolor" or "24bit"
     */
    public boolean isTrueColorIndicated() {
        if (colorterm == null) {
            return false;
        }
        String lower = colorterm.toLowerCase();
        return "truecolor".equals(lower) || "24bit".equals(lower);
    }

    /**
     * Check if macOS dark mode is enabled.
     *
     * @return true if APPLE_INTERFACE_STYLE is "Dark"
     */
    public boolean isMacOsDarkMode() {
        return "Dark".equalsIgnoreCase(appleInterfaceStyle);
    }

    // ==================== Derived Values ====================

    /**
     * Get the detected terminal type.
     * <p>
     * This is computed once from environment variables and cached.
     *
     * @return the detected terminal type
     */
    public Device.TerminalType getTerminalType() {
        return terminalType;
    }

    /**
     * Get the default color depth for this terminal.
     * <p>
     * This is based on COLORTERM, TERM, and terminal type detection.
     *
     * @return the detected color depth
     */
    public ColorDepth getDefaultColorDepth() {
        return defaultColorDepth;
    }

    /**
     * Check if OSC queries are likely supported.
     * <p>
     * This considers JetBrains terminals (which don't support OSC),
     * multiplexers (which block OSC by default), and outer terminal
     * detection.
     *
     * @return true if OSC queries are likely supported
     */
    public boolean supportsOscQueries() {
        // JetBrains doesn't support OSC queries
        if (isJetBrains()) {
            return false;
        }

        // Check for outer terminal in nested sessions
        if (hasKnownOuterTerminal()) {
            return true;
        }

        // Multiplexers block OSC unless passthrough is enabled
        if (isInMultiplexer()) {
            return tmuxPassthroughEnabled;
        }

        // Check terminal type
        return terminalType.supports(Device.OscCode.FOREGROUND) &&
                terminalType.supports(Device.OscCode.BACKGROUND);
    }

    /**
     * Check if Mode 2027 (grapheme cluster segmentation) is likely supported.
     * <p>
     * This delegates to the detected terminal type's grapheme cluster mode support.
     *
     * @return true if Mode 2027 is likely supported
     */
    public boolean supportsGraphemeClusterMode() {
        return terminalType.supportsGraphemeClusterMode();
    }

    /**
     * Check if Mode 2026 (synchronized output) is likely supported.
     * <p>
     * This delegates to the detected terminal type's synchronized output support.
     *
     * @return true if Mode 2026 is likely supported
     */
    public boolean supportsSynchronizedOutput() {
        return terminalType.supportsSynchronizedOutput();
    }

    /**
     * Check if OSC 8 hyperlinks are likely supported.
     * <p>
     * This delegates to the detected terminal type's hyperlink support.
     *
     * @return true if OSC 8 hyperlinks are likely supported
     */
    public boolean supportsHyperlinks() {
        return terminalType.supportsHyperlinks();
    }

    /**
     * Check if a known OSC-capable outer terminal is detected.
     * <p>
     * This is useful for nested terminal sessions (e.g., tmux inside Kitty).
     *
     * @return true if a known outer terminal is detected
     */
    public boolean hasKnownOuterTerminal() {
        return isKitty() || isGhostty() || isWezTerm() ||
                isITerm2() || isAlacritty();
    }

    // ==================== Private Helper Methods ====================

    private boolean isTermTypeMultiplexer() {
        if (term == null) {
            return false;
        }
        String lower = term.toLowerCase();
        return lower.startsWith("tmux") || lower.startsWith("screen");
    }

    private boolean computeTmuxPassthrough() {
        if (!inTmux) {
            return false;
        }
        if (tmuxPassthrough == null) {
            return false;
        }
        return "1".equals(tmuxPassthrough) ||
                "true".equalsIgnoreCase(tmuxPassthrough) ||
                "on".equalsIgnoreCase(tmuxPassthrough);
    }

    private Device.TerminalType computeTerminalType() {
        // Priority 1: JetBrains IDEs
        if (isJetBrains()) {
            return Device.TerminalType.JETBRAINS;
        }

        // Priority 2: Terminal-specific environment variables
        if (isKitty()) {
            return Device.TerminalType.KITTY;
        }
        if (isGhostty()) {
            return Device.TerminalType.GHOSTTY;
        }
        if (isWezTerm()) {
            return Device.TerminalType.WEZTERM;
        }
        if (isITerm2()) {
            return Device.TerminalType.ITERM2;
        }
        if (isWindowsTerminal()) {
            return Device.TerminalType.WINDOWS_TERMINAL;
        }
        if (isConEmu()) {
            return Device.TerminalType.CONEMU;
        }
        if (isAlacritty()) {
            return Device.TerminalType.ALACRITTY;
        }

        // Priority 3: TERM_PROGRAM
        if (termProgram != null) {
            String lower = termProgram.toLowerCase();
            if (lower.contains("iterm")) {
                return Device.TerminalType.ITERM2;
            }
            if (lower.contains("apple_terminal") || lower.contains("terminal.app")) {
                return Device.TerminalType.APPLE_TERMINAL;
            }
            if (lower.contains("vscode")) {
                return Device.TerminalType.VSCODE;
            }
            if (lower.contains("wezterm")) {
                return Device.TerminalType.WEZTERM;
            }
            if (lower.contains("alacritty")) {
                return Device.TerminalType.ALACRITTY;
            }
            if (lower.contains("kitty")) {
                return Device.TerminalType.KITTY;
            }
            if (lower.contains("ghostty")) {
                return Device.TerminalType.GHOSTTY;
            }
            if (lower.contains("hyper")) {
                return Device.TerminalType.HYPER;
            }
            if (lower.contains("tabby") || lower.contains("terminus")) {
                return Device.TerminalType.TABBY;
            }
            if (lower.contains("mintty")) {
                return Device.TerminalType.MINTTY;
            }
            if (lower.equals("tmux")) {
                return Device.TerminalType.TMUX;
            }
            if (lower.equals("screen")) {
                return Device.TerminalType.SCREEN;
            }
        }

        // Priority 4: TERM type
        if (term != null) {
            String termLower = term.toLowerCase();

            // Linux console
            if (termLower.equals("linux")) {
                return Device.TerminalType.LINUX_CONSOLE;
            }

            // Multiplexers
            if (termLower.startsWith("tmux")) {
                return Device.TerminalType.TMUX;
            }
            if (termLower.startsWith("screen")) {
                return Device.TerminalType.SCREEN;
            }

            // Modern terminals with specific TERM values
            if (termLower.contains("kitty")) {
                return Device.TerminalType.KITTY;
            }
            if (termLower.contains("ghostty")) {
                return Device.TerminalType.GHOSTTY;
            }
            if (termLower.contains("alacritty")) {
                return Device.TerminalType.ALACRITTY;
            }
            if (termLower.contains("wezterm")) {
                return Device.TerminalType.WEZTERM;
            }
            if (termLower.contains("foot")) {
                return Device.TerminalType.FOOT;
            }
            if (termLower.contains("contour")) {
                return Device.TerminalType.CONTOUR;
            }
            if (termLower.contains("rio")) {
                return Device.TerminalType.RIO;
            }

            // Linux desktop terminals
            if (termLower.contains("konsole")) {
                return Device.TerminalType.KONSOLE;
            }
            if (termLower.contains("vte") || termLower.contains("gnome")) {
                return Device.TerminalType.GNOME_TERMINAL;
            }
            if (termLower.contains("rxvt")) {
                return Device.TerminalType.RXVT;
            }

            // xterm-compatible (fallback for many terminals)
            if (termLower.startsWith("xterm") || termLower.contains("xterm")) {
                return Device.TerminalType.XTERM;
            }
        }

        return Device.TerminalType.UNKNOWN;
    }

    private ColorDepth computeColorDepth() {
        // Priority 1: COLORTERM
        if (isTrueColorIndicated()) {
            return ColorDepth.TRUE_COLOR;
        }

        // Priority 2: TERM suffix
        if (term != null) {
            String termLower = term.toLowerCase();
            if (termLower.contains("truecolor") || termLower.contains("24bit") ||
                    termLower.contains("direct")) {
                return ColorDepth.TRUE_COLOR;
            }
            if (termLower.contains("256color") || termLower.contains("256-color")) {
                return ColorDepth.COLORS_256;
            }
        }

        // Priority 3: Known terminal type
        if (terminalType != null && terminalType != Device.TerminalType.UNKNOWN) {
            return terminalType.getDefaultColorDepth();
        }

        // Priority 4: OS-based detection (modern Windows)
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("windows 10") || osName.contains("windows 11") ||
                osName.contains("windows server 2016") || osName.contains("windows server 2019") ||
                osName.contains("windows server 2022")) {
            return ColorDepth.TRUE_COLOR;
        }

        // Default
        return ColorDepth.COLORS_256;
    }

    // ==================== Static Convenience Methods ====================

    /**
     * Get the detected terminal type (convenience method).
     *
     * @return the detected terminal type
     */
    public static Device.TerminalType detectTerminalType() {
        return getInstance().getTerminalType();
    }

    /**
     * Get the detected color depth (convenience method).
     *
     * @return the detected color depth
     */
    public static ColorDepth detectColorDepth() {
        return getInstance().getDefaultColorDepth();
    }

    /**
     * Check if running in JetBrains IDE terminal (convenience method).
     *
     * @return true if in JetBrains terminal
     */
    public static boolean isJetBrainsTerminal() {
        return getInstance().isJetBrains();
    }

    /**
     * Check if OSC queries are supported (convenience method).
     *
     * @return true if OSC queries are likely supported
     */
    public static boolean isOscSupported() {
        return getInstance().supportsOscQueries();
    }

    @Override
    public String toString() {
        return "TerminalEnvironment{" +
                "terminalType=" + terminalType +
                ", colorDepth=" + defaultColorDepth +
                ", term='" + term + '\'' +
                ", termProgram='" + termProgram + '\'' +
                ", inTmux=" + inTmux +
                ", inScreen=" + inScreen +
                '}';
    }
}
