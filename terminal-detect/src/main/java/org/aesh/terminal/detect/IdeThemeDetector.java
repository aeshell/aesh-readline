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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Theme detection from IDE and terminal settings files.
 * <p>
 * Covers JetBrains IDEs ({@code laf.xml} / color schemes), VSCode
 * ({@code settings.json}) and Windows Terminal ({@code settings.json}).
 * Consulted by {@link TerminalDetector} when fast environment-variable
 * detection returns UNKNOWN, before the OS-level platform checks.
 * <p>
 * Only {@code java.base} APIs are used. Environment variables come from
 * the injected map (testable); {@code user.home} is read from system
 * properties. The OS name is an explicit parameter (production passes
 * {@code os.name}, tests pass fixture values) so every platform layout
 * is testable on any host.
 */
final class IdeThemeDetector {

    private IdeThemeDetector() {
    }

    /**
     * Detect the theme from settings files for a known IDE terminal.
     *
     * @param terminalName the detected terminal name (e.g. "jetbrains")
     * @param env the environment variables
     * @return the detected theme, or UNKNOWN if not determinable
     */
    static TerminalTheme detect(String terminalName, Map<String, String> env) {
        return detect(terminalName, env, System.getProperty("os.name", ""));
    }

    /**
     * Detect the theme from settings files for a known IDE terminal,
     * using an explicit OS name for platform layout selection.
     * Package-private so tests can cover every platform layout on
     * any host; production passes the real {@code os.name}.
     *
     * @param terminalName the detected terminal name (e.g. "jetbrains")
     * @param env the environment variables
     * @param osName the operating system name (e.g. "Linux", "Windows 11")
     * @return the detected theme, or UNKNOWN if not determinable
     */
    static TerminalTheme detect(String terminalName, Map<String, String> env, String osName) {
        String os = osName == null ? "" : osName.toLowerCase();
        switch (terminalName) {
            case "jetbrains":
                return detectJetBrainsTheme(env, os);
            case "vscode":
                return detectVSCodeTheme(env, os);
            case "windows-terminal":
                return detectWindowsTerminalTheme(env);
            default:
                return TerminalTheme.UNKNOWN;
        }
    }

    // ==================== Windows Terminal ====================

    private static TerminalTheme detectWindowsTerminalTheme(Map<String, String> env) {
        String localAppData = env.get("LOCALAPPDATA");
        if (localAppData == null) {
            return TerminalTheme.UNKNOWN;
        }

        File settingsFile = new File(localAppData,
                "Packages/Microsoft.WindowsTerminal_8wekyb3d8bbwe/LocalState/settings.json");

        if (!settingsFile.isFile()) {
            settingsFile = new File(localAppData,
                    "Packages/Microsoft.WindowsTerminalPreview_8wekyb3d8bbwe/LocalState/settings.json");
        }

        if (!settingsFile.isFile()) {
            settingsFile = new File(localAppData, "Microsoft/Windows Terminal/settings.json");
        }

        if (!settingsFile.isFile()) {
            return TerminalTheme.UNKNOWN;
        }

        try {
            return parseWindowsTerminalSettings(settingsFile);
        } catch (Exception e) {
            return TerminalTheme.UNKNOWN;
        }
    }

    private static TerminalTheme parseWindowsTerminalSettings(File settingsFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(settingsFile))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }

            String json = content.toString();

            String colorScheme = extractJsonValue(json, "colorScheme");
            if (colorScheme != null) {
                TerminalTheme theme = ThemeNameClassifier.classify(colorScheme);
                return theme != TerminalTheme.UNKNOWN ? theme : TerminalTheme.DARK;
            }

            String theme = extractJsonValue(json, "theme");
            if (theme != null) {
                if ("dark".equalsIgnoreCase(theme)) {
                    return TerminalTheme.DARK;
                }
                if ("light".equalsIgnoreCase(theme)) {
                    return TerminalTheme.LIGHT;
                }
            }
        } catch (IOException e) {
            return TerminalTheme.UNKNOWN;
        }
        return TerminalTheme.UNKNOWN;
    }

    // ==================== VSCode ====================

    private static TerminalTheme detectVSCodeTheme(Map<String, String> env, String os) {
        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            return TerminalTheme.UNKNOWN;
        }

        File settingsFile = getVSCodeSettingsFile(userHome, env, os);
        if (settingsFile == null || !settingsFile.isFile()) {
            return TerminalTheme.UNKNOWN;
        }

        try {
            return parseVSCodeSettings(settingsFile);
        } catch (Exception e) {
            return TerminalTheme.UNKNOWN;
        }
    }

    private static File getVSCodeSettingsFile(String userHome, Map<String, String> env, String os) {
        String osName = os;
        File settingsFile = null;

        if (osName.contains("mac") || osName.contains("darwin")) {
            settingsFile = new File(userHome, "Library/Application Support/Code/User/settings.json");
            if (!settingsFile.isFile()) {
                settingsFile = new File(userHome, "Library/Application Support/Code - Insiders/User/settings.json");
            }
        } else if (osName.contains("win")) {
            String appData = env.get("APPDATA");
            if (appData != null) {
                settingsFile = new File(appData, "Code/User/settings.json");
                if (!settingsFile.isFile()) {
                    settingsFile = new File(appData, "Code - Insiders/User/settings.json");
                }
            }
        } else {
            settingsFile = new File(userHome, ".config/Code/User/settings.json");
            if (!settingsFile.isFile()) {
                settingsFile = new File(userHome, ".config/Code - Insiders/User/settings.json");
            }
            if (!settingsFile.isFile()) {
                String xdgConfigHome = env.get("XDG_CONFIG_HOME");
                if (xdgConfigHome != null) {
                    settingsFile = new File(xdgConfigHome, "Code/User/settings.json");
                }
            }
        }

        return settingsFile;
    }

    private static TerminalTheme parseVSCodeSettings(File settingsFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(settingsFile))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }

            String json = content.toString();

            String colorTheme = extractJsonValue(json, "workbench.colorTheme");
            if (colorTheme != null) {
                TerminalTheme theme = ThemeNameClassifier.classify(colorTheme);
                // Default to dark for unknown VSCode themes (most popular themes are dark)
                return theme != TerminalTheme.UNKNOWN ? theme : TerminalTheme.DARK;
            }

            String terminalTheme = extractJsonValue(json, "workbench.preferredDarkColorTheme");
            if (terminalTheme != null) {
                return TerminalTheme.DARK;
            }
        } catch (IOException e) {
            return TerminalTheme.UNKNOWN;
        }
        return TerminalTheme.UNKNOWN;
    }

    // ==================== JetBrains ====================

    private static TerminalTheme detectJetBrainsTheme(Map<String, String> env, String os) {
        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            return TerminalTheme.UNKNOWN;
        }

        List<File> configDirs = getJetBrainsConfigDirectories(userHome, env, os);

        for (File jetbrainsDir : configDirs) {
            if (!jetbrainsDir.isDirectory()) {
                continue;
            }

            List<File> productDirs = new ArrayList<>();
            File[] children = jetbrainsDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (!child.isDirectory()) {
                        continue;
                    }
                    if (jetbrainsDir.getAbsolutePath().equals(userHome)
                            && !isLegacyJetBrainsDir(child.getName())) {
                        continue;
                    }
                    productDirs.add(child);
                }
            }

            if (productDirs.isEmpty()) {
                continue;
            }

            // Newest product directory first (most recently used install)
            while (!productDirs.isEmpty()) {
                File newest = null;
                for (File dir : productDirs) {
                    if (newest == null || dir.lastModified() > newest.lastModified()) {
                        newest = dir;
                    }
                }
                productDirs.remove(newest);

                File lafFile = new File(newest, "options/laf.xml");
                if (!lafFile.isFile()) {
                    lafFile = new File(newest, "config/options/laf.xml");
                }
                if (lafFile.isFile()) {
                    TerminalTheme theme = parseJetBrainsLafFile(lafFile);
                    if (theme != TerminalTheme.UNKNOWN) {
                        return theme;
                    }
                }

                File colorsFile = new File(newest, "options/colors.scheme.xml");
                if (!colorsFile.isFile()) {
                    colorsFile = new File(newest, "config/options/colors.scheme.xml");
                }
                if (colorsFile.isFile()) {
                    TerminalTheme theme = parseJetBrainsColorScheme(colorsFile);
                    if (theme != TerminalTheme.UNKNOWN) {
                        return theme;
                    }
                }
            }
        }

        return TerminalTheme.UNKNOWN;
    }

    private static TerminalTheme parseJetBrainsLafFile(File lafFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(lafFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String lower = line.toLowerCase();
                if (lower.contains("themeid=") || lower.contains("class-name=")) {
                    TerminalTheme theme = ThemeNameClassifier.classify(lower);
                    if (theme != TerminalTheme.UNKNOWN) {
                        return theme;
                    }
                    // Also check for platform-specific LAF names
                    if (lower.contains("windows") || lower.contains("gtk") || lower.contains("metal")) {
                        return TerminalTheme.LIGHT;
                    }
                }
            }
        } catch (IOException e) {
            return TerminalTheme.UNKNOWN;
        }
        return TerminalTheme.UNKNOWN;
    }

    private static boolean isLegacyJetBrainsDir(String name) {
        if (!name.startsWith(".")) {
            return false;
        }
        String[] products = {
                ".IntelliJIdea", ".IdeaIC",
                ".PyCharm", ".PyCharmCE",
                ".WebStorm", ".PhpStorm",
                ".RubyMine", ".CLion",
                ".GoLand", ".Rider",
                ".DataGrip", ".AppCode",
                ".AndroidStudio", ".DataSpell",
                ".Fleet", ".RustRover",
                ".Aqua", ".Writerside"
        };
        for (String product : products) {
            if (name.startsWith(product)) {
                return true;
            }
        }
        return false;
    }

    private static List<File> getJetBrainsConfigDirectories(String userHome, Map<String, String> env,
            String os) {
        List<File> dirs = new ArrayList<>();
        String osName = os;

        if (osName.contains("mac") || osName.contains("darwin")) {
            dirs.add(new File(userHome, "Library/Application Support/JetBrains"));
            dirs.add(new File(userHome, "Library/Preferences"));
        } else if (osName.contains("win")) {
            String appData = env.get("APPDATA");
            if (appData != null) {
                dirs.add(new File(appData, "JetBrains"));
            }
            dirs.add(new File(userHome, "AppData/Roaming/JetBrains"));
        } else {
            dirs.add(new File(userHome, ".config/JetBrains"));
            String xdgConfigHome = env.get("XDG_CONFIG_HOME");
            if (xdgConfigHome != null) {
                dirs.add(new File(xdgConfigHome, "JetBrains"));
            }
        }

        dirs.add(new File(userHome));
        return dirs;
    }

    private static TerminalTheme parseJetBrainsColorScheme(File colorsFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(colorsFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("global_color_scheme")) {
                    String lower = line.toLowerCase();
                    // Extract the name attribute value for classification
                    Pattern namePattern = Pattern.compile("name=\"([^\"]+)\"");
                    Matcher matcher = namePattern.matcher(lower);
                    if (matcher.find()) {
                        String schemeName = matcher.group(1);
                        TerminalTheme theme = ThemeNameClassifier.classify(schemeName);
                        if (theme != TerminalTheme.UNKNOWN) {
                            return theme;
                        }
                    }
                    // If we found the tag but couldn't determine the theme, assume dark
                    return TerminalTheme.DARK;
                }
            }
        } catch (IOException e) {
            return TerminalTheme.UNKNOWN;
        }
        return TerminalTheme.UNKNOWN;
    }

    // ==================== Shared Utilities ====================

    /**
     * Extract a simple string value from JSON.
     *
     * @param json the JSON string
     * @param key the key to find
     * @return the value, or null if not found
     */
    static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        Pattern p = Pattern.compile(pattern);
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }
}
