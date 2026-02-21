/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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
package org.aesh.terminal;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Consumer;

import org.aesh.terminal.image.ImageProtocol;
import org.aesh.terminal.image.ImageProtocolDetector;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.utils.ColorDepth;

/**
 * Contains info and capabilities for the device we are connected to.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public interface Device {

    /**
     * OSC (Operating System Command) codes that can be queried/set.
     */
    enum OscCode {
        /** OSC 4 - Query/set palette colors */
        PALETTE(4),
        /** OSC 10 - Query/set foreground color */
        FOREGROUND(10),
        /** OSC 11 - Query/set background color */
        BACKGROUND(11),
        /** OSC 12 - Query/set cursor color */
        CURSOR_COLOR(12),
        /** OSC 52 - Clipboard access */
        CLIPBOARD(52);

        private final int code;

        OscCode(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    /**
     * Known terminal types and their capabilities.
     * <p>
     * Each terminal type includes:
     * <ul>
     * <li>An identifier string</li>
     * <li>Supported OSC codes</li>
     * <li>Default/native color depth</li>
     * <li>Whether CSI ? 996 n theme DSR queries are supported</li>
     * </ul>
     */
    enum TerminalType {
        // ==================== IDE Terminals ====================

        /** JetBrains IDEs (IntelliJ, etc.) using JediTerm */
        JETBRAINS("JetBrains-JediTerm", EnumSet.of(OscCode.FOREGROUND, OscCode.BACKGROUND), ColorDepth.TRUE_COLOR, false),

        /** Visual Studio Code integrated terminal */
        VSCODE("vscode", EnumSet.of(OscCode.FOREGROUND, OscCode.BACKGROUND, OscCode.CURSOR_COLOR, OscCode.CLIPBOARD),
                ColorDepth.TRUE_COLOR, false),

        // ==================== macOS Terminals ====================

        /** Apple Terminal */
        APPLE_TERMINAL("Apple_Terminal", EnumSet.of(OscCode.FOREGROUND, OscCode.BACKGROUND, OscCode.CURSOR_COLOR),
                ColorDepth.COLORS_256, false),

        /** iTerm2 */
        ITERM2("iTerm.app", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, false),

        // ==================== Cross-Platform Modern Terminals ====================

        /** Kitty terminal - GPU-accelerated, uses Kitty graphics protocol. Supports CSI ? 996 n since 0.38.1. */
        KITTY("kitty", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, true),

        /** Ghostty - Fast GPU-accelerated terminal, uses Kitty graphics protocol. Supports CSI ? 996 n since 1.0.0. */
        GHOSTTY("ghostty", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, true),

        /** Alacritty - GPU-accelerated terminal */
        ALACRITTY("alacritty", EnumSet.of(OscCode.FOREGROUND, OscCode.BACKGROUND, OscCode.CURSOR_COLOR), ColorDepth.TRUE_COLOR,
                false),

        /** WezTerm - GPU-accelerated terminal with multiplexing */
        WEZTERM("WezTerm", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, false),

        /** Foot - Fast, lightweight Wayland terminal (VTE-based, supports CSI ? 996 n) */
        FOOT("foot", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, true),

        /** Contour - Modern terminal emulator. Origin of the CSI ? 996 n extension since 0.4.0. */
        CONTOUR("contour", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, true),

        /** Rio - Hardware-accelerated GPU terminal */
        RIO("rio", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, false),

        /** Warp - Modern terminal with AI features */
        WARP("warp", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, false),

        /** Wave - Modern terminal */
        WAVE("wave", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, false),

        // ==================== Electron-Based Terminals ====================

        /** Hyper - Electron-based terminal */
        HYPER("hyper", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, false),

        /** Terminus/Tabby - Electron-based terminal */
        TABBY("tabby", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, false),

        /** Extraterm - Electron-based terminal */
        EXTRATERM("extraterm", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, false),

        // ==================== Linux Desktop Terminals ====================

        /** GNOME Terminal and other VTE-based terminals. Supports CSI ? 996 n since VTE 0.82.0. */
        GNOME_TERMINAL("gnome-terminal", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, true),

        /** Konsole - KDE terminal emulator */
        KONSOLE("konsole", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, false),

        /** rxvt and urxvt */
        RXVT("rxvt", EnumSet.of(OscCode.FOREGROUND, OscCode.BACKGROUND, OscCode.CURSOR_COLOR), ColorDepth.COLORS_256, false),

        // ==================== Windows Terminals ====================

        /** Windows Terminal */
        WINDOWS_TERMINAL("Windows Terminal", EnumSet.of(OscCode.FOREGROUND, OscCode.BACKGROUND, OscCode.CURSOR_COLOR),
                ColorDepth.TRUE_COLOR, false),

        /** Mintty - Default terminal for Git Bash, Cygwin, MSYS2 */
        MINTTY("mintty", EnumSet.allOf(OscCode.class), ColorDepth.TRUE_COLOR, false),

        /** ConEmu/Cmder - Windows console emulator */
        CONEMU("ConEmu", EnumSet.of(OscCode.FOREGROUND, OscCode.BACKGROUND), ColorDepth.TRUE_COLOR, false),

        // ==================== Terminal Multiplexers ====================

        /** tmux - Terminal multiplexer. Supports CSI ? 996 n passthrough. */
        TMUX("tmux", EnumSet.noneOf(OscCode.class), ColorDepth.COLORS_256, true),

        /** GNU Screen - Terminal multiplexer (no OSC passthrough by default) */
        SCREEN("screen", EnumSet.noneOf(OscCode.class), ColorDepth.COLORS_256, false),

        // ==================== Classic/Legacy Terminals ====================

        /** xterm and compatible */
        XTERM("xterm", EnumSet.allOf(OscCode.class), ColorDepth.COLORS_256, false),

        /** Linux console (no OSC query support) */
        LINUX_CONSOLE("linux", EnumSet.noneOf(OscCode.class), ColorDepth.COLORS_8, false),

        // ==================== Fallback ====================

        /** Unknown terminal - assume basic support */
        UNKNOWN("unknown", EnumSet.of(OscCode.FOREGROUND, OscCode.BACKGROUND), ColorDepth.COLORS_256, false);

        private final String identifier;
        private final Set<OscCode> supportedCodes;
        private final ColorDepth defaultColorDepth;
        private final boolean supportsThemeDsr;

        TerminalType(String identifier, Set<OscCode> supportedCodes, ColorDepth defaultColorDepth,
                boolean supportsThemeDsr) {
            this.identifier = identifier;
            this.supportedCodes = Collections.unmodifiableSet(supportedCodes);
            this.defaultColorDepth = defaultColorDepth;
            this.supportsThemeDsr = supportsThemeDsr;
        }

        public String getIdentifier() {
            return identifier;
        }

        public Set<OscCode> getSupportedCodes() {
            return supportedCodes;
        }

        public boolean supports(OscCode code) {
            return supportedCodes.contains(code);
        }

        /**
         * Get the default/native color depth for this terminal type.
         * <p>
         * This is the terminal's native capability. The actual color depth
         * may be different depending on TERM/COLORTERM settings.
         *
         * @return the default color depth
         */
        public ColorDepth getDefaultColorDepth() {
            return defaultColorDepth;
        }

        /**
         * Check if this terminal natively supports true color (24-bit).
         *
         * @return true if the terminal supports true color
         */
        public boolean supportsTrueColor() {
            return defaultColorDepth == ColorDepth.TRUE_COLOR;
        }

        /**
         * Check if this terminal supports the CSI ? 996 n theme mode DSR query.
         * <p>
         * When supported, the terminal can be queried for its current dark/light
         * mode preference using {@code CSI ? 996 n}, and responds with
         * {@code CSI ? 997 ; 1 n} (dark) or {@code CSI ? 997 ; 2 n} (light).
         * <p>
         * Additionally, unsolicited notifications can be enabled with
         * {@code CSI ? 2031 h} so the terminal reports theme changes automatically.
         * <p>
         * Ref: <a href="https://contour-terminal.org/vt-extensions/color-palette-update-notifications/">
         * Contour VT extension</a>
         *
         * @return true if CSI ? 996 n theme DSR is supported
         */
        public boolean supportsThemeDsr() {
            return supportsThemeDsr;
        }

        /**
         * Get the expected DA1 features for this terminal type.
         * <p>
         * This returns the features that this terminal type is expected to report
         * when queried via DA1. This can be used to validate DA1 responses or
         * to infer capabilities without querying.
         * <p>
         * Note: Actual features may vary based on terminal version and configuration.
         *
         * @return set of expected features, never null
         */
        public Set<DeviceAttributes.Feature> getExpectedFeatures() {
            EnumSet<DeviceAttributes.Feature> features = EnumSet.noneOf(DeviceAttributes.Feature.class);

            // Most modern terminals support ANSI color
            if (supportsTrueColor() || defaultColorDepth == ColorDepth.COLORS_256) {
                features.add(DeviceAttributes.Feature.ANSI_COLOR);
            }

            // Add terminal-specific features
            switch (this) {
                case KITTY:
                case GHOSTTY:
                case FOOT:
                case CONTOUR:
                    // These terminals support Sixel graphics
                    features.add(DeviceAttributes.Feature.SIXEL);
                    features.add(DeviceAttributes.Feature.ANSI_TEXT_LOCATOR);
                    break;

                case ITERM2:
                case WEZTERM:
                case MINTTY:
                    // iTerm2 protocol terminals - typically support mouse
                    features.add(DeviceAttributes.Feature.ANSI_TEXT_LOCATOR);
                    break;

                case XTERM:
                case GNOME_TERMINAL:
                case KONSOLE:
                    // Full-featured terminals
                    features.add(DeviceAttributes.Feature.ANSI_TEXT_LOCATOR);
                    features.add(DeviceAttributes.Feature.RECTANGULAR_EDITING);
                    break;

                case LINUX_CONSOLE:
                    // Very limited feature set
                    features.clear();
                    break;

                case TMUX:
                case SCREEN:
                    // Multiplexers pass through features from underlying terminal
                    features.add(DeviceAttributes.Feature.ANSI_COLOR);
                    break;

                default:
                    // Default: just ANSI color for modern terminals
                    break;
            }

            return Collections.unmodifiableSet(features);
        }

        /**
         * Check if this terminal type is expected to support Sixel graphics.
         *
         * @return true if Sixel is expected to be supported
         */
        public boolean expectsSixel() {
            return getExpectedFeatures().contains(DeviceAttributes.Feature.SIXEL);
        }

        /**
         * Check if this terminal type is expected to support mouse input.
         *
         * @return true if mouse support is expected
         */
        public boolean expectsMouse() {
            Set<DeviceAttributes.Feature> features = getExpectedFeatures();
            return features.contains(DeviceAttributes.Feature.LOCATOR) ||
                    features.contains(DeviceAttributes.Feature.ANSI_TEXT_LOCATOR);
        }
    }

    /**
     * Returns the terminal type identifier for this device.
     *
     * @return the terminal type (e.g., "xterm", "ansi", "vt100")
     */
    String type();

    /**
     * Checks if this device has the specified boolean capability.
     *
     * @param capability the capability to check
     * @return true if the capability is supported, false otherwise
     */
    boolean getBooleanCapability(Capability capability);

    /**
     * Gets the value of a numeric capability.
     *
     * @param capability the capability to retrieve
     * @return the numeric value, or null if the capability is not set
     */
    Integer getNumericCapability(Capability capability);

    /**
     * Gets the value of a string capability.
     *
     * @param capability the capability to retrieve
     * @return the string value, or null if the capability is not set
     */
    String getStringCapability(Capability capability);

    /**
     * Gets a string capability as an array of code points, with parameter substitution.
     *
     * @param capability the capability to retrieve
     * @param params optional parameters to substitute into the capability string
     * @return the capability string as an int array of code points, or null if not set
     */
    int[] getStringCapabilityAsInts(Capability capability, Object... params);

    /**
     * Outputs a capability string to the given consumer with parameter substitution.
     *
     * @param output the consumer to receive the output as an int array
     * @param capability the capability to output
     * @param params optional parameters to substitute into the capability string
     * @return true if the capability was found and output, false otherwise
     */
    boolean puts(Consumer<int[]> output, Capability capability, Object... params);

    /**
     * Outputs a capability string to the given consumer with parameter substitution.
     *
     * @param output the consumer to receive the output as an int array
     * @param capability the capability name to look up and output
     * @param params optional parameters to substitute into the capability string
     * @return true if the capability was found and output, false otherwise
     */
    boolean puts(Consumer<int[]> output, String capability, Object... params);

    /**
     * Check if this device supports OSC (Operating System Command) queries.
     * <p>
     * OSC queries like OSC 10/11 are used to query foreground/background colors.
     * Not all terminals support these queries, and some terminal multiplexers
     * (like tmux, screen) may intercept or block them.
     * <p>
     * This method uses {@link org.aesh.terminal.utils.TerminalEnvironment} for
     * environment-based detection.
     *
     * @return true if OSC queries are likely supported
     */
    default boolean supportsOscQueries() {
        return org.aesh.terminal.utils.TerminalEnvironment.getInstance().supportsOscQueries();
    }

    /**
     * Detect the terminal type from environment variables and TERM type.
     * <p>
     * This method uses {@link org.aesh.terminal.utils.TerminalEnvironment}
     * which parses environment variables once and caches the result.
     * <p>
     * Detection priority:
     * <ol>
     * <li>IDE-specific environment variables (TERMINAL_EMULATOR)</li>
     * <li>Terminal-specific environment variables (KITTY_WINDOW_ID, etc.)</li>
     * <li>TERM_PROGRAM environment variable</li>
     * <li>TERM type string</li>
     * </ol>
     *
     * @return the detected terminal type
     */
    default TerminalType detectTerminalType() {
        return org.aesh.terminal.utils.TerminalEnvironment.getInstance().getTerminalType();
    }

    /**
     * Check if the current terminal supports a specific OSC code.
     *
     * @param oscCode the OSC code to check
     * @return true if the terminal likely supports this OSC code
     */
    default boolean supportsOscCode(OscCode oscCode) {
        return detectTerminalType().supports(oscCode);
    }

    /**
     * Check if the current terminal supports the CSI ? 996 n theme mode query.
     * <p>
     * When supported, the terminal can be queried for its current dark/light
     * mode preference, and can subscribe to unsolicited theme change notifications.
     * <p>
     * This delegates to {@link TerminalType#supportsThemeDsr()}.
     *
     * @return true if the terminal supports CSI ? 996 n theme DSR queries
     * @see TerminalType#supportsThemeDsr()
     */
    default boolean supportsThemeQuery() {
        return detectTerminalType().supportsThemeDsr();
    }

    /**
     * Check if the current terminal is a JetBrains IDE terminal.
     *
     * @return true if running in a JetBrains IDE terminal
     */
    default boolean isJetBrainsTerminal() {
        return detectTerminalType() == TerminalType.JETBRAINS;
    }

    /**
     * Check if tmux passthrough is enabled.
     * <p>
     * When running inside tmux, OSC sequences are only passed through to the
     * outer terminal if allow-passthrough is enabled.
     *
     * @return true if running in tmux with passthrough likely enabled
     */
    default boolean isTmuxPassthroughEnabled() {
        return org.aesh.terminal.utils.TerminalEnvironment.getInstance().isTmuxPassthroughEnabled();
    }

    /**
     * Get the color depth of this device based on terminfo capabilities.
     * <p>
     * Falls back to environment-based detection if terminfo doesn't provide
     * color information.
     *
     * @return the detected color depth
     */
    default ColorDepth getColorDepth() {
        Integer maxColors = getNumericCapability(Capability.max_colors);
        if (maxColors != null) {
            return ColorDepth.fromColorCount(maxColors);
        }
        // Fallback to environment-based detection
        return org.aesh.terminal.utils.TerminalEnvironment.getInstance().getDefaultColorDepth();
    }

    /**
     * Check if this device is running inside a terminal multiplexer
     * (like tmux or screen).
     *
     * @return true if running inside a multiplexer
     */
    default boolean isMultiplexer() {
        return org.aesh.terminal.utils.TerminalEnvironment.getInstance().isInMultiplexer();
    }

    /**
     * Check if this device supports displaying images.
     *
     * @return true if images can be displayed
     */
    default boolean supportsImages() {
        return getImageProtocol() != ImageProtocol.NONE;
    }

    /**
     * Get the image protocol supported by this device.
     * <p>
     * Detection is based on terminal type and environment variables.
     * Supported protocols (in priority order):
     * <ul>
     * <li>KITTY - Kitty, Ghostty, Konsole</li>
     * <li>ITERM2 - iTerm2, WezTerm, Mintty, VSCode, Tabby, Hyper</li>
     * <li>SIXEL - mlterm, foot, contour (fallback)</li>
     * </ul>
     *
     * @return the detected image protocol, or NONE if not supported
     */
    default ImageProtocol getImageProtocol() {
        return ImageProtocolDetector.detectFromTermType(type());
    }
}
