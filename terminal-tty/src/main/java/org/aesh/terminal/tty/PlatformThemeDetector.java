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
package org.aesh.terminal.tty;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.aesh.terminal.tty.utils.ColorUtils;
import org.aesh.terminal.tty.utils.ProcessHelper;
import org.aesh.terminal.tty.utils.ThemeNameClassifier;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.TerminalEnvironment;
import org.aesh.terminal.utils.TerminalTheme;

/**
 * Detects the terminal theme by reading platform-specific configuration files
 * and querying OS settings.
 * <p>
 * Supports: Windows Terminal, ConEmu/Cmder, macOS dark mode, Alacritty,
 * VSCode, JetBrains IDEs, and Windows legacy console.
 *
 * @author Ståle W. Pedersen
 */
public final class PlatformThemeDetector {

    private static final Logger LOGGER = LoggerUtil.getLogger(PlatformThemeDetector.class.getName());

    private PlatformThemeDetector() {
    }

    /**
     * Detect the terminal theme using platform-specific methods.
     * <p>
     * This checks IDE configuration files, OS dark mode settings, and
     * terminal-specific configuration files based on the detected environment.
     *
     * @param env the terminal environment
     * @return the detected theme, or UNKNOWN if not detectable
     */
    public static TerminalTheme detectPlatformTheme(TerminalEnvironment env) {
        // JetBrains IDEs: check config files
        if (env.isJetBrains()) {
            TerminalTheme theme = detectJetBrainsTheme();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }
        }

        // VSCode: check settings.json
        String termProgram = env.getTermProgram();
        if (termProgram != null && termProgram.toLowerCase().contains("vscode")) {
            TerminalTheme theme = detectVSCodeTheme();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }
        }

        // macOS: check system dark mode
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("mac") || osName.contains("darwin")) {
            TerminalTheme theme = detectMacOsDarkMode();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }
        }

        // Windows-specific detection
        if (osName.contains("win")) {
            // Windows Terminal
            if (env.isWindowsTerminal()) {
                TerminalTheme theme = detectWindowsTerminalTheme();
                if (theme != TerminalTheme.UNKNOWN) {
                    return theme;
                }
            }

            // ConEmu/Cmder
            if (env.isConEmu()) {
                TerminalTheme theme = detectConEmuTheme();
                if (theme != TerminalTheme.UNKNOWN) {
                    return theme;
                }
            }

            // Windows dark mode registry
            TerminalTheme theme = detectWindowsAppsDarkMode();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }

            // Legacy Windows console
            theme = detectWindowsConsoleTheme();
            if (theme != TerminalTheme.UNKNOWN) {
                return theme;
            }
        }

        // Alacritty: check config file
        if (env.isAlacritty()) {
            return detectAlacrittyTheme();
        }

        return TerminalTheme.UNKNOWN;
    }

    // ==================== Windows Detection ====================

    /**
     * Get the Windows build number from the registry.
     *
     * @return the build number, or 0 if not determinable
     */
    public static int getWindowsBuildNumber() {
        ProcessHelper.ProcessResult result = ProcessHelper.execute("reg", "query",
                "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
                "/v", "CurrentBuildNumber");

        if (result.success()) {
            Pattern pattern = Pattern.compile("CurrentBuildNumber\\s+REG_SZ\\s+(\\d+)");
            Matcher matcher = pattern.matcher(result.output());
            if (matcher.find()) {
                int buildNumber = Integer.parseInt(matcher.group(1));
                LOGGER.log(Level.FINE, "Windows build number from registry: " + buildNumber);
                return buildNumber;
            }
        }

        // Fallback: try to parse from os.version
        String osVersion = System.getProperty("os.version", "");
        String[] parts = osVersion.split("\\.");
        if (parts.length >= 3) {
            try {
                return Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
            }
        }

        return 0;
    }

    private static TerminalTheme detectWindowsConsoleTheme() {
        ProcessHelper.ProcessResult result = ProcessHelper.execute("reg", "query",
                "HKCU\\Console", "/v", "ScreenColors");

        if (result.success()) {
            Pattern pattern = Pattern.compile(
                    "ScreenColors\\s+REG_DWORD\\s+0x([0-9a-fA-F]+)");
            Matcher matcher = pattern.matcher(result.output());
            if (matcher.find()) {
                int screenColors = Integer.parseInt(matcher.group(1), 16);
                int bgColorIndex = (screenColors >> 4) & 0x0F;
                boolean isDark = (bgColorIndex <= 6) || (bgColorIndex == 8);

                LOGGER.log(Level.FINE, "Windows Console ScreenColors=0x" +
                        Integer.toHexString(screenColors) + " bgIndex=" + bgColorIndex +
                        " -> " + (isDark ? "DARK" : "LIGHT"));

                return isDark ? TerminalTheme.DARK : TerminalTheme.LIGHT;
            }
        }

        return TerminalTheme.UNKNOWN;
    }

    private static TerminalTheme detectWindowsTerminalTheme() {
        String localAppData = System.getenv("LOCALAPPDATA");
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
            LOGGER.log(Level.FINE, "Windows Terminal settings.json not found");
            return TerminalTheme.UNKNOWN;
        }

        try {
            return parseWindowsTerminalSettings(settingsFile);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to parse Windows Terminal settings", e);
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
            LOGGER.log(Level.FINE, "Failed to read Windows Terminal settings", e);
        }
        return TerminalTheme.UNKNOWN;
    }

    private static TerminalTheme detectConEmuTheme() {
        String conEmuDir = System.getenv("ConEmuDir");
        String conEmuBaseDir = System.getenv("ConEmuBaseDir");
        String conEmuPid = System.getenv("ConEmuPID");

        if (conEmuDir == null && conEmuBaseDir == null && conEmuPid == null) {
            return TerminalTheme.UNKNOWN;
        }

        List<File> candidates = new ArrayList<>();
        if (conEmuDir != null) {
            candidates.add(new File(conEmuDir, "ConEmu.xml"));
        }
        if (conEmuBaseDir != null) {
            candidates.add(new File(conEmuBaseDir, "ConEmu.xml"));
        }
        String appData = System.getenv("APPDATA");
        if (appData != null) {
            candidates.add(new File(appData, "ConEmu.xml"));
        }
        String userHome = System.getProperty("user.home");
        if (userHome != null) {
            candidates.add(new File(userHome, "ConEmu.xml"));
        }

        for (File configFile : candidates) {
            if (configFile.isFile()) {
                TerminalTheme theme = parseConEmuConfig(configFile);
                if (theme != TerminalTheme.UNKNOWN) {
                    return theme;
                }
            }
        }

        LOGGER.log(Level.FINE, "ConEmu detected but theme not determined, defaulting to dark");
        return TerminalTheme.DARK;
    }

    private static TerminalTheme parseConEmuConfig(File configFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim().toLowerCase();

                if (trimmed.contains("name=\"palettename\"") ||
                        trimmed.contains("name=\"schemename\"")) {
                    TerminalTheme classified = ThemeNameClassifier.classify(trimmed);
                    if (classified != TerminalTheme.UNKNOWN) {
                        LOGGER.log(Level.FINE, "ConEmu " + classified.name().toLowerCase() + " palette detected");
                        return classified;
                    }
                }

                // Look for background color (ColorTable00)
                if (trimmed.contains("colortable00") && trimmed.contains("data=\"")) {
                    Pattern p = Pattern.compile("data=\"([0-9a-fA-F]+)\"");
                    Matcher m = p.matcher(trimmed);
                    if (m.find()) {
                        String hex = m.group(1);
                        // ConEmu stores as BBGGRR (reversed)
                        if (hex.length() >= 6) {
                            String bgr = hex.length() > 6 ? hex.substring(hex.length() - 6) : hex;
                            int b = Integer.parseInt(bgr.substring(0, 2), 16);
                            int g = Integer.parseInt(bgr.substring(2, 4), 16);
                            int r = Integer.parseInt(bgr.substring(4, 6), 16);
                            boolean dark = ColorUtils.isDarkColor(new int[] { r, g, b });
                            TerminalTheme theme = dark ? TerminalTheme.DARK : TerminalTheme.LIGHT;
                            LOGGER.log(Level.FINE, "ConEmu background BGR=" + bgr + " -> " + theme);
                            return theme;
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to read ConEmu config", e);
        }
        return TerminalTheme.UNKNOWN;
    }

    private static TerminalTheme detectWindowsAppsDarkMode() {
        TerminalTheme theme = queryWindowsThemeRegistryKey("AppsUseLightTheme");
        if (theme != TerminalTheme.UNKNOWN) {
            return theme;
        }
        return queryWindowsThemeRegistryKey("SystemUsesLightTheme");
    }

    private static TerminalTheme queryWindowsThemeRegistryKey(String keyName) {
        ProcessHelper.ProcessResult result = ProcessHelper.execute("reg", "query",
                "HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                "/v", keyName);

        if (result.success()) {
            String output = result.output();
            if (output.contains("0x0") || output.contains("0x00000000")) {
                LOGGER.log(Level.FINE, "Windows " + keyName + " = 0 (dark mode)");
                return TerminalTheme.DARK;
            } else if (output.contains("0x1") || output.contains("0x00000001")) {
                LOGGER.log(Level.FINE, "Windows " + keyName + " = 1 (light mode)");
                return TerminalTheme.LIGHT;
            }
        }
        return TerminalTheme.UNKNOWN;
    }

    // ==================== macOS Detection ====================

    static TerminalTheme detectMacOsDarkMode() {
        ProcessHelper.ProcessResult result = ProcessHelper.execute("defaults", "read", "-g", "AppleInterfaceStyle");

        if (result.success() && "Dark".equalsIgnoreCase(result.output())) {
            LOGGER.log(Level.FINE, "macOS dark mode detected via defaults read");
            return TerminalTheme.DARK;
        } else if (result.exitCode() != 0) {
            // Key not found means light mode
            LOGGER.log(Level.FINE, "macOS light mode detected via defaults read (key not found)");
            return TerminalTheme.LIGHT;
        } else {
            LOGGER.log(Level.FINE, "macOS light mode detected via defaults read: " + result.output());
            return TerminalTheme.LIGHT;
        }
    }

    // ==================== Alacritty Detection ====================

    private static TerminalTheme detectAlacrittyTheme() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            return TerminalTheme.UNKNOWN;
        }

        File configFile = getAlacrittyConfigFile(userHome);
        if (configFile == null || !configFile.isFile()) {
            LOGGER.log(Level.FINE, "Alacritty config not found");
            return TerminalTheme.UNKNOWN;
        }

        try {
            return parseAlacrittyConfig(configFile);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to parse Alacritty config", e);
            return TerminalTheme.UNKNOWN;
        }
    }

    private static File getAlacrittyConfigFile(String userHome) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        List<File> candidates = new ArrayList<>();

        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                candidates.add(new File(appData, "alacritty/alacritty.toml"));
                candidates.add(new File(appData, "alacritty/alacritty.yml"));
            }
        } else {
            candidates.add(new File(userHome, ".config/alacritty/alacritty.toml"));
            candidates.add(new File(userHome, ".config/alacritty/alacritty.yml"));
            String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
            if (xdgConfigHome != null) {
                candidates.add(new File(xdgConfigHome, "alacritty/alacritty.toml"));
                candidates.add(new File(xdgConfigHome, "alacritty/alacritty.yml"));
            }
            candidates.add(new File(userHome, ".alacritty.toml"));
            candidates.add(new File(userHome, ".alacritty.yml"));
        }

        for (File file : candidates) {
            if (file.isFile()) {
                return file;
            }
        }
        return null;
    }

    private static TerminalTheme parseAlacrittyConfig(File configFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            boolean inColors = false;
            boolean inPrimary = false;
            boolean isToml = configFile.getName().toLowerCase().endsWith(".toml");

            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim().toLowerCase();

                // Check for theme/scheme import
                if (trimmed.contains("import") || trimmed.contains("theme")) {
                    TerminalTheme classified = ThemeNameClassifier.classify(trimmed);
                    if (classified != TerminalTheme.UNKNOWN) {
                        LOGGER.log(Level.FINE,
                                "Alacritty " + classified.name().toLowerCase() + " theme detected from import/theme name");
                        return classified;
                    }
                }

                // Track section headers
                if (isToml) {
                    if (trimmed.startsWith("[colors")) {
                        inColors = true;
                        inPrimary = trimmed.contains("primary");
                        continue;
                    } else if (trimmed.startsWith("[") && !trimmed.startsWith("[colors")) {
                        inColors = false;
                        inPrimary = false;
                    }
                } else {
                    if (trimmed.equals("colors:")) {
                        inColors = true;
                        continue;
                    } else if (trimmed.equals("primary:") && inColors) {
                        inPrimary = true;
                        continue;
                    } else if (!trimmed.isEmpty() && !line.startsWith(" ") && !line.startsWith("\t") && inColors) {
                        inColors = false;
                        inPrimary = false;
                    }
                }

                // Look for background color
                if ((inColors && inPrimary) || trimmed.contains("background")) {
                    Pattern hexPattern = Pattern.compile("[\"']#?(?:0x)?([0-9a-fA-F]{6})[\"']");
                    Matcher matcher = hexPattern.matcher(trimmed);
                    if (matcher.find() && trimmed.contains("background")) {
                        String hex = matcher.group(1);
                        int r = Integer.parseInt(hex.substring(0, 2), 16);
                        int g = Integer.parseInt(hex.substring(2, 4), 16);
                        int b = Integer.parseInt(hex.substring(4, 6), 16);
                        boolean dark = ColorUtils.isDarkColor(new int[] { r, g, b });
                        TerminalTheme theme = dark ? TerminalTheme.DARK : TerminalTheme.LIGHT;
                        LOGGER.log(Level.FINE, "Alacritty background color #" + hex + " -> " + theme);
                        return theme;
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to read Alacritty config", e);
        }
        return TerminalTheme.UNKNOWN;
    }

    // ==================== VSCode Detection ====================

    private static TerminalTheme detectVSCodeTheme() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            return TerminalTheme.UNKNOWN;
        }

        File settingsFile = getVSCodeSettingsFile(userHome);
        if (settingsFile == null || !settingsFile.isFile()) {
            LOGGER.log(Level.FINE, "VSCode settings.json not found");
            return TerminalTheme.UNKNOWN;
        }

        try {
            return parseVSCodeSettings(settingsFile);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to parse VSCode settings", e);
            return TerminalTheme.UNKNOWN;
        }
    }

    private static File getVSCodeSettingsFile(String userHome) {
        String osName = System.getProperty("os.name", "").toLowerCase();
        File settingsFile = null;

        if (osName.contains("mac") || osName.contains("darwin")) {
            settingsFile = new File(userHome, "Library/Application Support/Code/User/settings.json");
            if (!settingsFile.isFile()) {
                settingsFile = new File(userHome, "Library/Application Support/Code - Insiders/User/settings.json");
            }
        } else if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
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
                String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
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
                LOGGER.log(Level.FINE, "VSCode theme detected: " + colorTheme + " -> " + theme);
                // Default to dark for unknown VSCode themes (most popular themes are dark)
                return theme != TerminalTheme.UNKNOWN ? theme : TerminalTheme.DARK;
            }

            String terminalTheme = extractJsonValue(json, "workbench.preferredDarkColorTheme");
            if (terminalTheme != null) {
                return TerminalTheme.DARK;
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to read VSCode settings", e);
        }
        return TerminalTheme.UNKNOWN;
    }

    // ==================== JetBrains Detection ====================

    private static TerminalTheme detectJetBrainsTheme() {
        String userHome = System.getProperty("user.home");
        if (userHome == null) {
            return TerminalTheme.UNKNOWN;
        }

        List<File> configDirs = getJetBrainsConfigDirectories(userHome);

        for (File jetbrainsDir : configDirs) {
            if (!jetbrainsDir.isDirectory()) {
                continue;
            }

            File[] productDirs;
            if (jetbrainsDir.getAbsolutePath().equals(userHome)) {
                productDirs = jetbrainsDir.listFiles(file -> file.isDirectory() && isLegacyJetBrainsDir(file.getName()));
            } else {
                productDirs = jetbrainsDir.listFiles(File::isDirectory);
            }

            if (productDirs == null || productDirs.length == 0) {
                continue;
            }

            Arrays.sort(productDirs, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

            for (File productDir : productDirs) {
                File lafFile = new File(productDir, "options/laf.xml");
                if (!lafFile.isFile()) {
                    lafFile = new File(productDir, "config/options/laf.xml");
                }
                if (lafFile.isFile()) {
                    TerminalTheme theme = parseJetBrainsLafFile(lafFile);
                    if (theme != TerminalTheme.UNKNOWN) {
                        LOGGER.log(Level.FINE, "Detected JetBrains theme from " + lafFile + ": " + theme);
                        return theme;
                    }
                }

                File colorsFile = new File(productDir, "options/colors.scheme.xml");
                if (!colorsFile.isFile()) {
                    colorsFile = new File(productDir, "config/options/colors.scheme.xml");
                }
                if (colorsFile.isFile()) {
                    TerminalTheme theme = parseJetBrainsColorScheme(colorsFile);
                    if (theme != TerminalTheme.UNKNOWN) {
                        LOGGER.log(Level.FINE, "Detected JetBrains theme from " + colorsFile + ": " + theme);
                        return theme;
                    }
                }
            }
        }

        LOGGER.log(Level.FINE, "Could not detect JetBrains theme from config files");
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
            LOGGER.log(Level.FINE, "Failed to read JetBrains laf.xml file", e);
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

    private static List<File> getJetBrainsConfigDirectories(String userHome) {
        List<File> dirs = new ArrayList<>();
        String osName = System.getProperty("os.name", "").toLowerCase();

        if (osName.contains("mac") || osName.contains("darwin")) {
            dirs.add(new File(userHome, "Library/Application Support/JetBrains"));
            dirs.add(new File(userHome, "Library/Preferences"));
        } else if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            if (appData != null) {
                dirs.add(new File(appData, "JetBrains"));
            }
            dirs.add(new File(userHome, "AppData/Roaming/JetBrains"));
        } else {
            dirs.add(new File(userHome, ".config/JetBrains"));
            String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
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
                    LOGGER.log(Level.FINE, "Unknown JetBrains color scheme: " + line.trim());
                    return TerminalTheme.DARK;
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Failed to read JetBrains color scheme file", e);
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
