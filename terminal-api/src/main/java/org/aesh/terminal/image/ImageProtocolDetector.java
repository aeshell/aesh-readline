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

/**
 * Utility class for detecting terminal image protocol support.
 */
public final class ImageProtocolDetector {

    private ImageProtocolDetector() {
        // Utility class
    }

    /**
     * Detect the image protocol based on the terminal type string.
     *
     * @param termType the terminal type (e.g., from TERM environment variable)
     * @return the detected protocol, or NONE if unknown
     */
    public static ImageProtocol detectFromTermType(String termType) {
        if (termType == null) {
            return checkEnvironment();
        }

        String typeLower = termType.toLowerCase();

        // These terminals use the Kitty graphics protocol
        if (typeLower.contains("kitty") || typeLower.contains("ghostty")) {
            return ImageProtocol.KITTY;
        }

        // These terminals support iTerm2 protocol
        if (typeLower.contains("iterm") ||
                typeLower.contains("wezterm") ||
                typeLower.contains("mintty") ||
                typeLower.contains("vscode") ||
                typeLower.contains("tabby") ||
                typeLower.contains("hyper")) {
            return ImageProtocol.ITERM2;
        }

        // Konsole has partial Kitty support
        if (typeLower.contains("konsole")) {
            return ImageProtocol.KITTY;
        }

        // Check environment for Kitty/iTerm2 first
        ImageProtocol envProtocol = checkEnvironment();
        if (envProtocol != ImageProtocol.NONE) {
            return envProtocol;
        }

        // Sixel as fallback - only for terminals that explicitly identify themselves
        // Note: We don't check for "xterm" because many terminals set TERM=xterm-256color
        // but don't actually support Sixel (e.g., Alacritty, many SSH clients)
        if (typeLower.contains("mlterm") ||
                typeLower.contains("foot") ||
                typeLower.contains("contour") ||
                typeLower.contains("yaft") ||
                typeLower.contains("ctx") ||
                typeLower.contains("darktile")) {
            return ImageProtocol.SIXEL;
        }

        return ImageProtocol.NONE;
    }

    /**
     * Check environment variables to detect image protocol support.
     *
     * @return the detected protocol based on environment, or NONE
     */
    public static ImageProtocol checkEnvironment() {
        // TERM_PROGRAM often indicates the actual terminal
        String termProgram = System.getenv("TERM_PROGRAM");
        if (termProgram != null) {
            String lower = termProgram.toLowerCase();
            if (lower.contains("iterm") || lower.contains("wezterm") ||
                    lower.contains("vscode") || lower.contains("tabby") ||
                    lower.contains("hyper")) {
                return ImageProtocol.ITERM2;
            }
            if (lower.contains("kitty") || lower.contains("ghostty")) {
                return ImageProtocol.KITTY;
            }
        }

        // KITTY_WINDOW_ID indicates Kitty terminal
        if (System.getenv("KITTY_WINDOW_ID") != null) {
            return ImageProtocol.KITTY;
        }

        // GHOSTTY_RESOURCES_DIR indicates Ghostty terminal
        if (System.getenv("GHOSTTY_RESOURCES_DIR") != null) {
            return ImageProtocol.KITTY;
        }

        // ITERM_SESSION_ID indicates iTerm2
        if (System.getenv("ITERM_SESSION_ID") != null) {
            return ImageProtocol.ITERM2;
        }

        // WEZTERM_PANE indicates WezTerm
        if (System.getenv("WEZTERM_PANE") != null) {
            return ImageProtocol.ITERM2;
        }

        return ImageProtocol.NONE;
    }
}
