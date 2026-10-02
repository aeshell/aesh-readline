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
import java.util.HashMap;
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
                return detectWindowsTerminalTheme(env, env.get("WT_PROFILE_ID"));
            default:
                return TerminalTheme.UNKNOWN;
        }
    }

    // ==================== Windows Terminal ====================

    private static TerminalTheme detectWindowsTerminalTheme(Map<String, String> env, String profileId) {
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

        String content = readFileContent(settingsFile);
        if (content == null) {
            return TerminalTheme.UNKNOWN;
        }
        try {
            return parseWindowsTerminalSettings(content, profileId);
        } catch (Exception e) {
            return TerminalTheme.UNKNOWN;
        }
    }

    /**
     * Resolve the terminal theme from Windows Terminal settings content.
     * <p>
     * The active profile is resolved from {@code WT_PROFILE_ID} against
     * {@code profiles.list[].guid}; an unmatched or missing id falls back
     * to {@code profiles.defaults}. A profile background overrides the
     * inherited background and the selected scheme's background.
     * Scheme <em>definitions</em> in {@code schemes[]} never satisfy
     * the lookup. A bare application-chrome {@code theme} says nothing
     * about the terminal background. Package visible for headless tests
     * with fixture JSON.
     *
     * @param json the settings file content (comments allowed)
     * @param profileId the active profile id from WT_PROFILE_ID, or null
     * @return the detected theme, or UNKNOWN if not determinable
     */
    static TerminalTheme parseWindowsTerminalSettings(String json, String profileId) {
        if (json == null) {
            return TerminalTheme.UNKNOWN;
        }
        String clean = stripJsonComments(json);

        Map<String, int[]> backgrounds = new HashMap<>();
        int[] schemesRegion = findKeyedRegion(clean, "schemes", '[', ']');
        if (schemesRegion != null) {
            String schemesBody = clean.substring(schemesRegion[0] + 1, schemesRegion[1]);
            for (String scheme : splitTopLevelObjects(schemesBody)) {
                String name = extractJsonValue(scheme, "name");
                String background = extractJsonValue(scheme, "background");
                if (name != null && background != null) {
                    int[] rgb = parseHexColor(background);
                    if (rgb != null) {
                        backgrounds.put(name.toLowerCase(), rgb);
                    }
                }
            }
        }

        String schemeName = null;
        String background = null;
        int[] profilesRegion = findKeyedRegion(clean, "profiles", '{', '}');
        if (profilesRegion != null) {
            String profilesBody = clean.substring(profilesRegion[0] + 1, profilesRegion[1]);
            String defaultsScheme = null;
            String defaultsBackground = null;
            int[] defaultsRegion = findKeyedRegion(profilesBody, "defaults", '{', '}');
            if (defaultsRegion != null) {
                String defaults = profilesBody.substring(defaultsRegion[0] + 1, defaultsRegion[1]);
                defaultsScheme = profileValue(defaults, "colorScheme");
                defaultsBackground = profileValue(defaults, "background");
            }
            boolean matched = false;
            String matchedScheme = null;
            String matchedBackground = null;
            int[] listRegion = findKeyedRegion(profilesBody, "list", '[', ']');
            if (listRegion != null) {
                String listBody = profilesBody.substring(listRegion[0] + 1, listRegion[1]);
                for (String profile : splitTopLevelObjects(listBody)) {
                    String guid = profileValue(profile, "guid");
                    if (profileId != null && guid != null && guid.equalsIgnoreCase(profileId)) {
                        matched = true;
                        matchedScheme = profileValue(profile, "colorScheme");
                        matchedBackground = profileValue(profile, "background");
                        break;
                    }
                }
            }
            if (matched) {
                schemeName = matchedScheme != null ? matchedScheme : defaultsScheme;
                background = matchedBackground != null ? matchedBackground : defaultsBackground;
            } else {
                schemeName = defaultsScheme;
                background = defaultsBackground;
            }
        }
        if (background != null) {
            int[] rgb = parseHexColor(background);
            return rgb == null ? TerminalTheme.UNKNOWN : TerminalTheme.fromRGB(rgb[0], rgb[1], rgb[2]);
        }
        if (schemeName == null) {
            schemeName = topLevelColorScheme(clean, profilesRegion, schemesRegion);
        }
        if (schemeName == null) {
            return TerminalTheme.UNKNOWN;
        }
        int[] rgb = backgrounds.get(schemeName.toLowerCase());
        if (rgb != null) {
            return TerminalTheme.fromRGB(rgb[0], rgb[1], rgb[2]);
        }
        return ThemeNameClassifier.classify(schemeName);
    }

    /**
     * Find a top-level {@code colorScheme} usage: the document with the
     * profiles and schemes regions blanked, so neither the active
     * configuration nor a scheme definition can satisfy the lookup.
     *
     * @param clean comment-stripped settings content
     * @param profilesRegion the profiles region, or null if absent
     * @param schemesRegion the schemes region, or null if absent
     * @return the scheme name, or null if there is no top-level usage
     */
    private static String topLevelColorScheme(String clean, int[] profilesRegion,
            int[] schemesRegion) {
        StringBuilder usage = new StringBuilder(clean);
        blankRegion(usage, profilesRegion);
        blankRegion(usage, schemesRegion);
        return extractJsonValue(usage.toString(), "colorScheme");
    }

    private static void blankRegion(StringBuilder sb, int[] region) {
        if (region == null) {
            return;
        }
        for (int i = region[0]; i <= region[1] && i < sb.length(); i++) {
            sb.setCharAt(i, ' ');
        }
    }

    // Nested appearance/environment settings are not properties of the
    // profile itself. Mask them before using the existing string extractor.
    private static String profileValue(String json, String key) {
        String body = json.trim();
        StringBuilder direct = new StringBuilder(body);
        for (int i = body.startsWith("{") ? 1 : 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '"') {
                i = quotedEnd(body, i);
                if (i < 0) {
                    return null;
                }
            } else if (c == '{' || c == '[') {
                int end = matchDelimiter(body, i, c, c == '{' ? '}' : ']');
                if (end < 0) {
                    return null;
                }
                blankRegion(direct, new int[] { i, end });
                i = end;
            }
        }
        return extractJsonValue(direct.toString(), key);
    }

    private static int quotedEnd(String json, int start) {
        for (int i = start + 1; i < json.length(); i++) {
            if (json.charAt(i) == '\\') {
                i++;
            } else if (json.charAt(i) == '"') {
                return i;
            }
        }
        return -1;
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
        String content = readFileContent(settingsFile);
        if (content == null) {
            return TerminalTheme.UNKNOWN;
        }
        try {
            return parseVSCodeSettings(content);
        } catch (Exception e) {
            return TerminalTheme.UNKNOWN;
        }
    }

    /**
     * Resolve the terminal theme from VSCode settings content.
     * <p>
     * An explicit {@code terminal.background} wins over the theme name,
     * mirroring the measured-beats-hint rule. The active
     * {@code workbench.colorTheme} is classified as-is; unrecognized
     * names stay UNKNOWN, and a {@code preferredDarkColorTheme} alone
     * is not the active theme. Package visible for headless tests with
     * fixture JSON.
     *
     * @param json the settings file content (comments allowed)
     * @return the detected theme, or UNKNOWN if not determinable
     */
    static TerminalTheme parseVSCodeSettings(String json) {
        if (json == null) {
            return TerminalTheme.UNKNOWN;
        }
        String clean = stripJsonComments(json);
        String background = extractJsonValue(clean, "terminal.background");
        if (background != null) {
            int[] rgb = parseHexColor(background);
            if (rgb != null) {
                return TerminalTheme.fromRGB(rgb[0], rgb[1], rgb[2]);
            }
        }
        String colorTheme = extractJsonValue(clean, "workbench.colorTheme");
        if (colorTheme != null) {
            return ThemeNameClassifier.classify(colorTheme);
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

                // The editor color scheme determines the terminal
                // background; the look-and-feel is application chrome
                // and is only a fallback.
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
                    // A line can carry several name attributes (the
                    // component name shadows the scheme name); classify
                    // each and take the first recognizable one.
                    Pattern namePattern = Pattern.compile("name=\"([^\"]+)\"");
                    Matcher matcher = namePattern.matcher(lower);
                    while (matcher.find()) {
                        TerminalTheme theme = ThemeNameClassifier.classify(matcher.group(1));
                        if (theme != TerminalTheme.UNKNOWN) {
                            return theme;
                        }
                    }
                }
            }
        } catch (IOException e) {
            return TerminalTheme.UNKNOWN;
        }
        // A custom scheme name we cannot classify is ambiguous, not dark.
        return TerminalTheme.UNKNOWN;
    }

    // ==================== Shared Utilities ====================

    /**
     * Read a whole settings file, preserving line breaks so
     * line-comment stripping sees the original line structure.
     *
     * @param file the file to read
     * @return the content, or null if unreadable
     */
    private static String readFileContent(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append('\n');
            }
            return content.toString();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Strip {@code //} line comments and {@code /*} block comments
     * from JSON-with-comments settings content. String literals are
     * honored, so {@code //} inside a quoted value survives.
     *
     * @param json the raw file content, or null
     * @return the content without comments, or null if the input is null
     */
    static String stripJsonComments(String json) {
        if (json == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(json.length());
        boolean inString = false;
        boolean escaped = false;
        int i = 0;
        while (i < json.length()) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                i++;
            } else if (c == '"') {
                inString = true;
                out.append(c);
                i++;
            } else if (c == '/' && i + 1 < json.length() && json.charAt(i + 1) == '/') {
                i += 2;
                while (i < json.length() && json.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < json.length() && json.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < json.length()
                        && !(json.charAt(i) == '*' && json.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    /**
     * Parse a CSS-style hex color ({@code #rrggbb} or short {@code #rgb}).
     *
     * @param value the color value, or null
     * @return the RGB components, or null if malformed
     */
    static int[] parseHexColor(String value) {
        if (value == null) {
            return null;
        }
        String hex = value.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() == 3) {
            char r = hex.charAt(0);
            char g = hex.charAt(1);
            char b = hex.charAt(2);
            hex = "" + r + r + g + g + b + b;
        }
        if (hex.length() != 6) {
            return null;
        }
        try {
            return new int[] {
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16) };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Locate the delimited value of a {@code "key": {...}} or
     * {@code "key": [...]} pair. Delimiters inside string literals
     * do not count.
     *
     * @param json the content to search
     * @param key the key to find
     * @param open the opening delimiter
     * @param close the closing delimiter
     * @return the open/close delimiter indices, or null if absent
     */
    private static int[] findKeyedRegion(String json, String key, char open, char close) {
        Pattern keyPattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:");
        Matcher matcher = keyPattern.matcher(json);
        while (matcher.find()) {
            int i = matcher.end();
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
                i++;
            }
            if (i < json.length() && json.charAt(i) == open) {
                int end = matchDelimiter(json, i, open, close);
                if (end >= 0) {
                    return new int[] { i, end };
                }
                return null;
            }
        }
        return null;
    }

    private static int matchDelimiter(String json, int openIdx, char open, char close) {
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = openIdx; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static List<String> splitTopLevelObjects(String arrayBody) {
        List<String> objects = new ArrayList<>();
        int i = 0;
        while (i < arrayBody.length()) {
            if (arrayBody.charAt(i) == '{') {
                int end = matchDelimiter(arrayBody, i, '{', '}');
                if (end < 0) {
                    break;
                }
                objects.add(arrayBody.substring(i, end + 1));
                i = end + 1;
            } else {
                i++;
            }
        }
        return objects;
    }

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
