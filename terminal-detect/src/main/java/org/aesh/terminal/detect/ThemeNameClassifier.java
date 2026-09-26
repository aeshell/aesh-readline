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

/**
 * Classifies theme/color-scheme names as DARK or LIGHT based on known keywords.
 * <p>
 * Shared by the IDE settings-file probes in {@link IdeThemeDetector}
 * (JetBrains, VSCode, Windows Terminal).
 */
final class ThemeNameClassifier {

    private ThemeNameClassifier() {
    }

    // Light theme keywords -- checked first since "default" and "classic" are
    // common names that should map to light rather than unknown.
    private static final String[] LIGHT_KEYWORDS = {
            "solarized light", "solarized_light",
            "gruvbox light", "gruvbox_light",
            "github light",
            "quiet light",
            "default light",
            "visual studio light",
            "catppuccin-latte", "catppuccin latte",
            "atom one light",
            "one half light",
            "tango light",
            // JetBrains specific
            "intellijlaf", "intellij laf", "jetbrainslight",
    };

    // Dark theme keywords
    private static final String[] DARK_KEYWORDS = {
            "darcula",
            "dracula",
            "monokai",
            "nord",
            "gruvbox dark", "gruvbox_dark",
            "solarized dark", "solarized_dark",
            "tomorrow night",
            "material",
            "abyss",
            "cobalt",
            "synthwave",
            "palenight",
            "one dark", "one_dark",
            "atom one dark",
            "high contrast", "high_contrast", "highcontrast",
            "vintage",
            "campbell",
            "zenburn",
            "catppuccin-mocha", "catppuccin mocha",
            "catppuccin-macchiato", "catppuccin macchiato",
            "catppuccin-frappe", "catppuccin frappe",
            "tokyo-night", "tokyo night",
            "winter is coming",
            "github dark",
            "default dark",
            "visual studio dark",
    };

    /**
     * Classify a theme or color scheme name as DARK, LIGHT, or UNKNOWN.
     * <p>
     * The classification is case-insensitive and checks for known keywords.
     * More specific keywords (like "solarized light") are checked before
     * generic ones (like "light") to avoid false matches.
     *
     * @param themeName the theme or color scheme name
     * @return the classified theme, or UNKNOWN if not recognized
     */
    static TerminalTheme classify(String themeName) {
        if (themeName == null || themeName.isEmpty()) {
            return TerminalTheme.UNKNOWN;
        }

        String lower = themeName.toLowerCase().trim();

        // Check specific light keywords first (before generic "light")
        for (String keyword : LIGHT_KEYWORDS) {
            if (lower.contains(keyword)) {
                return TerminalTheme.LIGHT;
            }
        }

        // Check specific dark keywords first (before generic "dark")
        for (String keyword : DARK_KEYWORDS) {
            if (lower.contains(keyword)) {
                return TerminalTheme.DARK;
            }
        }

        // Generic checks last
        if (lower.contains("light") || lower.equals("classic") ||
                lower.equals("intellij") || lower.equals("default")) {
            return TerminalTheme.LIGHT;
        }

        if (lower.contains("dark") || lower.contains("night")) {
            return TerminalTheme.DARK;
        }

        return TerminalTheme.UNKNOWN;
    }
}
