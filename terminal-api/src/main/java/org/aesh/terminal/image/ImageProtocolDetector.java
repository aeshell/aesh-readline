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
package org.aesh.terminal.image;

import org.aesh.terminal.Device;
import org.aesh.terminal.DeviceAttributes;
import org.aesh.terminal.utils.TerminalEnvironment;

/**
 * Utility class for detecting terminal image protocol support.
 * <p>
 * Detection can be done in two ways:
 * <ul>
 * <li><b>Heuristic</b>: Based on TERM type and environment variables (fast, always available)</li>
 * <li><b>Authoritative</b>: Based on DA1 device attributes query (accurate, requires terminal query)</li>
 * </ul>
 * <p>
 * For best results, use {@link #detect(DeviceAttributes, String)} which combines both methods.
 * <p>
 * This class uses {@link TerminalEnvironment} for centralized environment detection.
 */
public final class ImageProtocolDetector {

    private ImageProtocolDetector() {
    }

    /**
     * Detect the image protocol using both device attributes and terminal type.
     * <p>
     * This method provides the most accurate detection by:
     * <ol>
     * <li>Checking DA1 attributes for Sixel support (authoritative)</li>
     * <li>Falling back to heuristic detection based on terminal type</li>
     * </ol>
     *
     * @param attrs the device attributes from DA1 query (may be null)
     * @param termType the terminal type string (may be null)
     * @return the detected protocol, or NONE if unknown
     */
    public static ImageProtocol detect(DeviceAttributes attrs, String termType) {
        ImageProtocol envProtocol = checkEnvironment();

        if (envProtocol == ImageProtocol.KITTY || envProtocol == ImageProtocol.ITERM2) {
            return envProtocol;
        }

        if (termType != null) {
            ImageProtocol typeProtocol = getProtocolForTermType(termType);
            if (typeProtocol != ImageProtocol.NONE) {
                return typeProtocol;
            }
        }

        if (attrs != null && attrs.supportsSixel()) {
            return ImageProtocol.SIXEL;
        }

        return envProtocol;
    }

    /**
     * Detect the image protocol based on the terminal type string.
     * <p>
     * This is a heuristic method that does not query the terminal.
     * For more accurate detection, use {@link #detect(DeviceAttributes, String)}.
     *
     * @param termType the terminal type (e.g., from TERM environment variable)
     * @return the detected protocol, or NONE if unknown
     */
    public static ImageProtocol detectFromTermType(String termType) {
        if (termType == null) {
            return checkEnvironment();
        }

        ImageProtocol typeProtocol = getProtocolForTermType(termType);
        if (typeProtocol != ImageProtocol.NONE) {
            return typeProtocol;
        }

        return checkEnvironment();
    }

    /**
     * Detect image protocol from the current terminal environment.
     * <p>
     * This method uses {@link TerminalEnvironment} to detect the terminal type
     * and determine image protocol support.
     *
     * @return the detected protocol, or NONE if unknown
     */
    public static ImageProtocol detectFromEnvironment() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();
        Device.TerminalType terminalType = env.getTerminalType();
        ImageProtocol protocol = getProtocolForTerminalType(terminalType);
        if (env.isInMultiplexer() && protocol == ImageProtocol.KITTY) {
            return multiplexerFallback(terminalType);
        }
        return protocol;
    }

    /**
     * Get the image protocol supported by a given terminal type.
     *
     * @param terminalType the detected terminal type
     * @return the image protocol supported by this terminal
     */
    public static ImageProtocol getProtocolForTerminalType(Device.TerminalType terminalType) {
        if (terminalType == null) {
            return ImageProtocol.NONE;
        }

        switch (terminalType) {
            // Kitty graphics protocol
            case KITTY:
            case GHOSTTY:
            case KONSOLE:
                return ImageProtocol.KITTY;

            // iTerm2 inline images protocol
            case ITERM2:
            case WEZTERM:
            case MINTTY:
            case VSCODE:
            case TABBY:
            case HYPER:
                return ImageProtocol.ITERM2;

            // Sixel graphics
            case FOOT:
            case CONTOUR:
            case WINDOWS_TERMINAL:
                return ImageProtocol.SIXEL;

            // These terminals may support images but detection is unreliable
            case XTERM:
            case GNOME_TERMINAL:
                // xterm can support Sixel but it depends on compile options
                // GNOME Terminal doesn't support images
                return ImageProtocol.NONE;

            default:
                return ImageProtocol.NONE;
        }
    }

    /**
     * Check environment variables to detect image protocol support.
     * <p>
     * This method uses {@link TerminalEnvironment} for centralized detection.
     * When running inside a terminal multiplexer (tmux/screen), the Kitty
     * graphics protocol (APC-based) cannot pass through. In that case,
     * terminals that also support Sixel (DCS-based, which multiplexers
     * can forward) are detected as SIXEL instead.
     *
     * @return the detected protocol based on environment, or NONE
     */
    public static ImageProtocol checkEnvironment() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();
        boolean inMultiplexer = env.isInMultiplexer();

        if (env.isGhostty()) {
            // Ghostty supports both Kitty and Sixel
            return inMultiplexer ? ImageProtocol.SIXEL : ImageProtocol.KITTY;
        }

        if (env.isKitty()) {
            // Kitty terminal does not support Sixel
            return inMultiplexer ? ImageProtocol.NONE : ImageProtocol.KITTY;
        }

        if (env.isITerm2() || env.isWezTerm()) {
            return ImageProtocol.ITERM2;
        }

        Device.TerminalType terminalType = env.getTerminalType();
        if (terminalType != Device.TerminalType.UNKNOWN) {
            ImageProtocol protocol = getProtocolForTerminalType(terminalType);
            if (inMultiplexer && protocol == ImageProtocol.KITTY) {
                return multiplexerFallback(terminalType);
            }
            return protocol;
        }

        return ImageProtocol.NONE;
    }

    /**
     * Determine the best image protocol for a terminal behind a multiplexer.
     * Kitty graphics (APC) cannot pass through tmux/screen, so terminals
     * that support both Kitty and Sixel fall back to Sixel (DCS-based).
     */
    private static ImageProtocol multiplexerFallback(Device.TerminalType terminalType) {
        switch (terminalType) {
            case GHOSTTY:
            case KONSOLE:
                return ImageProtocol.SIXEL;
            default:
                return ImageProtocol.NONE;
        }
    }

    /**
     * Get the image protocol based on a terminal type string.
     *
     * @param termType the terminal type string (e.g., from TERM)
     * @return the detected protocol, or NONE
     */
    private static ImageProtocol getProtocolForTermType(String termType) {
        String typeLower = termType.toLowerCase();

        if (typeLower.contains("kitty") || typeLower.contains("ghostty") ||
                typeLower.contains("konsole")) {
            return ImageProtocol.KITTY;
        }

        if (typeLower.contains("iterm") ||
                typeLower.contains("wezterm") ||
                typeLower.contains("mintty") ||
                typeLower.contains("vscode") ||
                typeLower.contains("tabby") ||
                typeLower.contains("hyper")) {
            return ImageProtocol.ITERM2;
        }

        if (typeLower.contains("mlterm") || typeLower.contains("foot") ||
                typeLower.contains("contour") || typeLower.contains("yaft") ||
                typeLower.contains("ctx") || typeLower.contains("darktile")) {
            return ImageProtocol.SIXEL;
        }

        return ImageProtocol.NONE;
    }
}
