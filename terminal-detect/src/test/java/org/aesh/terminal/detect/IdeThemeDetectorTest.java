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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Tests for IDE settings-file theme detection.
 * <p>
 * Fixture config files live under a temporary {@code user.home}
 * (always overridden — a real IDE installation on the test machine
 * must not leak into the results); environments are injected maps
 * and the OS name is an explicit parameter, so the Linux, Windows
 * and macOS layouts are all covered on any host with no real IDE
 * installation or environment variables needed.
 */
public class IdeThemeDetectorTest {

    private static final String LINUX = "Linux";
    private static final String WINDOWS = "Windows 11";
    private static final String MAC = "Mac OS X";

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testUnknownTerminalReturnsUnknown() {
        assertEquals(TerminalTheme.UNKNOWN,
                IdeThemeDetector.detect("xterm", new HashMap<String, String>(), LINUX));
        assertEquals(TerminalTheme.UNKNOWN,
                IdeThemeDetector.detect("unknown", new HashMap<String, String>(), LINUX));
    }

    @Test
    public void testJetBrainsDarkViaLaf() throws Exception {
        File home = tmp.newFolder("home");
        File options = new File(home, ".config/JetBrains/IntelliJIdea2024.1/options");
        assertTrue(options.mkdirs());
        write(new File(options, "laf.xml"),
                "<application><component name=\"LafManager\">"
                        + "<laf themeId=\"Darcula\" />"
                        + "</component></application>");

        String previousHome = useHome(home);
        try {
            assertEquals(TerminalTheme.DARK,
                    IdeThemeDetector.detect("jetbrains", new HashMap<String, String>(), LINUX));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testJetBrainsDarkViaColorScheme() throws Exception {
        File home = tmp.newFolder("home");
        File options = new File(home, ".config/JetBrains/PyCharm2024.1/options");
        assertTrue(options.mkdirs());
        write(new File(options, "colors.scheme.xml"),
                "<application><component name=\"EditorColorsManagerImpl\">"
                        + "<global_color_scheme name=\"Monokai\" />"
                        + "</component></application>");

        String previousHome = useHome(home);
        try {
            assertEquals(TerminalTheme.DARK,
                    IdeThemeDetector.detect("jetbrains", new HashMap<String, String>(), LINUX));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testJetBrainsWindowsLayout() throws Exception {
        File appData = tmp.newFolder("appdata");
        File options = new File(appData, "JetBrains/IntelliJIdea2024.1/options");
        assertTrue(options.mkdirs());
        write(new File(options, "laf.xml"),
                "<application><component name=\"LafManager\">"
                        + "<laf themeId=\"Darcula\" />"
                        + "</component></application>");

        Map<String, String> env = new HashMap<>();
        env.put("APPDATA", appData.getAbsolutePath());
        String previousHome = useHome(tmp.newFolder("home"));
        try {
            assertEquals(TerminalTheme.DARK,
                    IdeThemeDetector.detect("jetbrains", env, WINDOWS));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testJetBrainsMacLayout() throws Exception {
        File home = tmp.newFolder("home");
        File options = new File(home, "Library/Application Support/JetBrains/IntelliJIdea2024.1/options");
        assertTrue(options.mkdirs());
        write(new File(options, "laf.xml"),
                "<application><component name=\"LafManager\">"
                        + "<laf themeId=\"Darcula\" />"
                        + "</component></application>");

        String previousHome = useHome(home);
        try {
            assertEquals(TerminalTheme.DARK,
                    IdeThemeDetector.detect("jetbrains", new HashMap<String, String>(), MAC));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testJetBrainsWithoutConfigFallsThrough() throws Exception {
        // No config files anywhere: the IDE probe yields UNKNOWN.
        // user.home points at an empty dir so a real IDE installation
        // on the test machine cannot leak into the result.
        String previousHome = useHome(tmp.newFolder("empty-home"));
        try {
            Map<String, String> env = new HashMap<>();
            assertEquals(TerminalTheme.UNKNOWN, IdeThemeDetector.detect("jetbrains", env, LINUX));
            assertEquals(TerminalTheme.UNKNOWN, IdeThemeDetector.detect("jetbrains", env, WINDOWS));
            assertEquals(TerminalTheme.UNKNOWN, IdeThemeDetector.detect("jetbrains", env, MAC));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testVSCodeLight() throws Exception {
        File home = tmp.newFolder("home");
        File userDir = new File(home, ".config/Code/User");
        assertTrue(userDir.mkdirs());
        write(new File(userDir, "settings.json"),
                "{\"workbench.colorTheme\": \"Default Light+\"}");

        String previousHome = useHome(home);
        try {
            assertEquals(TerminalTheme.LIGHT,
                    IdeThemeDetector.detect("vscode", new HashMap<String, String>(), LINUX));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testVSCodeUnknownThemeReturnsUnknown() throws Exception {
        File home = tmp.newFolder("home");
        File userDir = new File(home, ".config/Code/User");
        assertTrue(userDir.mkdirs());
        write(new File(userDir, "settings.json"),
                "{\"workbench.colorTheme\": \"Some Obscure Theme\"}");

        String previousHome = useHome(home);
        try {
            assertEquals(TerminalTheme.UNKNOWN,
                    IdeThemeDetector.detect("vscode", new HashMap<String, String>(), LINUX));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testVSCodeWindowsLayout() throws Exception {
        File appData = tmp.newFolder("appdata");
        File userDir = new File(appData, "Code/User");
        assertTrue(userDir.mkdirs());
        write(new File(userDir, "settings.json"),
                "{\"workbench.colorTheme\": \"Default Dark+\"}");

        Map<String, String> env = new HashMap<>();
        env.put("APPDATA", appData.getAbsolutePath());
        String previousHome = useHome(tmp.newFolder("home"));
        try {
            assertEquals(TerminalTheme.DARK,
                    IdeThemeDetector.detect("vscode", env, WINDOWS));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testVSCodeMacLayout() throws Exception {
        File home = tmp.newFolder("home");
        File userDir = new File(home, "Library/Application Support/Code/User");
        assertTrue(userDir.mkdirs());
        write(new File(userDir, "settings.json"),
                "{\"workbench.colorTheme\": \"Default Light+\"}");

        String previousHome = useHome(home);
        try {
            assertEquals(TerminalTheme.LIGHT,
                    IdeThemeDetector.detect("vscode", new HashMap<String, String>(), MAC));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testVSCodeWithoutSettingsFallsThrough() throws Exception {
        String previousHome = useHome(tmp.newFolder("empty-home"));
        try {
            Map<String, String> env = new HashMap<>();
            assertEquals(TerminalTheme.UNKNOWN, IdeThemeDetector.detect("vscode", env, LINUX));
            assertEquals(TerminalTheme.UNKNOWN, IdeThemeDetector.detect("vscode", env, WINDOWS));
            assertEquals(TerminalTheme.UNKNOWN, IdeThemeDetector.detect("vscode", env, MAC));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testWindowsTerminalDarkScheme() throws Exception {
        File localAppData = tmp.newFolder("localappdata");
        File stateDir = new File(localAppData, "Microsoft/Windows Terminal");
        assertTrue(stateDir.mkdirs());
        write(new File(stateDir, "settings.json"), "{\"colorScheme\": \"Campbell\"}");

        Map<String, String> env = new HashMap<>();
        env.put("LOCALAPPDATA", localAppData.getAbsolutePath());
        String previousHome = useHome(tmp.newFolder("home"));
        try {
            assertEquals(TerminalTheme.DARK,
                    IdeThemeDetector.detect("windows-terminal", env, LINUX));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testWindowsTerminalWithoutSettingsFallsThrough() {
        Map<String, String> env = new HashMap<>();
        assertEquals(TerminalTheme.UNKNOWN, IdeThemeDetector.detect("windows-terminal", env, LINUX));
    }

    @Test
    public void testWindowsTerminalActiveProfileResolved() {
        String json = "{"
                + "\"profiles\": {"
                + "\"defaults\": {\"colorScheme\": \"Campbell\"},"
                + "\"list\": ["
                + "{\"guid\": \"{11111111-1111-1111-1111-111111111111}\","
                + " \"name\": \"PowerShell\", \"colorScheme\": \"Campbell\"},"
                + "{\"guid\": \"{22222222-2222-2222-2222-222222222222}\","
                + " \"name\": \"Ubuntu\", \"colorScheme\": \"One Half Light\"}"
                + "]}}";
        assertEquals(TerminalTheme.LIGHT, IdeThemeDetector.parseWindowsTerminalSettings(
                json, "{22222222-2222-2222-2222-222222222222}"));
        assertEquals(TerminalTheme.DARK, IdeThemeDetector.parseWindowsTerminalSettings(
                json, "{11111111-1111-1111-1111-111111111111}"));
    }

    @Test
    public void testWindowsTerminalDefaultsFallback() {
        String json = "{"
                + "\"profiles\": {"
                + "\"defaults\": {\"colorScheme\": \"Campbell\"},"
                + "\"list\": ["
                + "{\"guid\": \"{11111111-1111-1111-1111-111111111111}\","
                + " \"colorScheme\": \"One Half Light\"}"
                + "]}}";
        assertEquals("unmatched id falls back to defaults",
                TerminalTheme.DARK,
                IdeThemeDetector.parseWindowsTerminalSettings(json, "{99999999-9999-9999-9999-999999999999}"));
        assertEquals("missing id falls back to defaults",
                TerminalTheme.DARK, IdeThemeDetector.parseWindowsTerminalSettings(json, null));
    }

    @Test
    public void testWindowsTerminalIgnoresSchemeDefinitions() {
        // The only "colorScheme"-shaped content is a definition entry;
        // definitions must never satisfy the lookup.
        String json = "{\"colorSchemes\":"
                + " [{\"name\": \"Campbell\", \"background\": \"#0C0C0C\"}],"
                + " \"profiles\": {\"list\": []}}";
        assertEquals(TerminalTheme.UNKNOWN,
                IdeThemeDetector.parseWindowsTerminalSettings(json, null));
    }

    @Test
    public void testWindowsTerminalJsonComments() {
        String json = "{\n"
                + "/* \"colorScheme\": \"Campbell\" */\n"
                + "// \"colorScheme\": \"One Dark\"\n"
                + "\"colorScheme\": \"Default Light+\"\n"
                + "}";
        assertEquals(TerminalTheme.LIGHT,
                IdeThemeDetector.parseWindowsTerminalSettings(json, null));
    }

    @Test
    public void testWindowsTerminalChromeThemeIgnored() {
        assertEquals(TerminalTheme.UNKNOWN,
                IdeThemeDetector.parseWindowsTerminalSettings("{\"theme\": \"dark\"}", null));
    }

    @Test
    public void testWindowsTerminalUnknownSchemeReturnsUnknown() {
        assertEquals(TerminalTheme.UNKNOWN, IdeThemeDetector.parseWindowsTerminalSettings(
                "{\"colorScheme\": \"Mystery Scheme 3000\"}", null));
    }

    @Test
    public void testWindowsTerminalExplicitBackgroundWins() {
        String darkBg = "{\"colorSchemes\":"
                + " [{\"name\": \"Almost Light\", \"background\": \"#000000\"}],"
                + " \"profiles\": {\"defaults\": {\"colorScheme\": \"Almost Light\"}}}";
        assertEquals("explicit black background beats a light-sounding name",
                TerminalTheme.DARK, IdeThemeDetector.parseWindowsTerminalSettings(darkBg, null));

        String lightBg = "{\"colorSchemes\":"
                + " [{\"name\": \"Almost Dark\", \"background\": \"#FFFFFF\"}],"
                + " \"profiles\": {\"defaults\": {\"colorScheme\": \"Almost Dark\"}}}";
        assertEquals("explicit white background beats a dark-sounding name",
                TerminalTheme.LIGHT, IdeThemeDetector.parseWindowsTerminalSettings(lightBg, null));
    }

    @Test
    public void testWindowsTerminalProfileIdThreaded() throws Exception {
        File localAppData = tmp.newFolder("localappdata");
        File stateDir = new File(localAppData, "Microsoft/Windows Terminal");
        assertTrue(stateDir.mkdirs());
        write(new File(stateDir, "settings.json"), "{"
                + "\"profiles\": {"
                + "\"defaults\": {\"colorScheme\": \"Campbell\"},"
                + "\"list\": ["
                + "{\"guid\": \"{22222222-2222-2222-2222-222222222222}\","
                + " \"colorScheme\": \"One Half Light\"}"
                + "]}}");

        Map<String, String> env = new HashMap<>();
        env.put("LOCALAPPDATA", localAppData.getAbsolutePath());
        env.put("WT_PROFILE_ID", "{22222222-2222-2222-2222-222222222222}");
        assertEquals(TerminalTheme.LIGHT,
                IdeThemeDetector.detect("windows-terminal", env, LINUX));

        env.remove("WT_PROFILE_ID");
        assertEquals("without the profile id the defaults apply",
                TerminalTheme.DARK, IdeThemeDetector.detect("windows-terminal", env, LINUX));
    }

    @Test
    public void testVSCodePreferredDarkThemeIgnored() {
        assertEquals(TerminalTheme.UNKNOWN, IdeThemeDetector.parseVSCodeSettings(
                "{\"workbench.preferredDarkColorTheme\": \"Default Dark+\"}"));
    }

    @Test
    public void testVSCodeExplicitTerminalBackgroundWins() {
        assertEquals("black terminal background beats a light theme name",
                TerminalTheme.DARK, IdeThemeDetector.parseVSCodeSettings(
                        "{\"workbench.colorTheme\": \"Default Light+\","
                                + " \"workbench.colorCustomizations\":"
                                + " {\"terminal.background\": \"#000000\"}}"));
        assertEquals("white terminal background beats a dark theme name",
                TerminalTheme.LIGHT, IdeThemeDetector.parseVSCodeSettings(
                        "{\"workbench.colorTheme\": \"Default Dark+\","
                                + " \"workbench.colorCustomizations\":"
                                + " {\"terminal.background\": \"#FFFFFF\"}}"));
    }

    @Test
    public void testVSCodeJsonComments() {
        String json = "{\n"
                + "// \"workbench.colorTheme\": \"Default Dark+\"\n"
                + "\"workbench.colorTheme\": \"Default Light+\"\n"
                + "}";
        assertEquals(TerminalTheme.LIGHT, IdeThemeDetector.parseVSCodeSettings(json));
    }

    @Test
    public void testJetBrainsColorSchemeBeatsLaf() throws Exception {
        File home = tmp.newFolder("home");
        File options = new File(home, ".config/JetBrains/IntelliJIdea2024.1/options");
        assertTrue(options.mkdirs());
        write(new File(options, "laf.xml"),
                "<application><component name=\"LafManager\">"
                        + "<laf themeId=\"IntelliJ Light\" />"
                        + "</component></application>");
        write(new File(options, "colors.scheme.xml"),
                "<application><component name=\"EditorColorsManagerImpl\">"
                        + "<global_color_scheme name=\"Monokai\" />"
                        + "</component></application>");

        String previousHome = useHome(home);
        try {
            assertEquals(TerminalTheme.DARK,
                    IdeThemeDetector.detect("jetbrains", new HashMap<String, String>(), LINUX));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testJetBrainsPlatformLafReturnsUnknown() throws Exception {
        File home = tmp.newFolder("home");
        File options = new File(home, ".config/JetBrains/IntelliJIdea2024.1/options");
        assertTrue(options.mkdirs());
        write(new File(options, "laf.xml"),
                "<application><component name=\"LafManager\">"
                        + "<laf class-name=\"com.sun.java.swing.plaf.windows.WindowsLookAndFeel\" />"
                        + "</component></application>");

        String previousHome = useHome(home);
        try {
            assertEquals(TerminalTheme.UNKNOWN,
                    IdeThemeDetector.detect("jetbrains", new HashMap<String, String>(), LINUX));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testJetBrainsUnknownSchemeReturnsUnknown() throws Exception {
        File home = tmp.newFolder("home");
        File options = new File(home, ".config/JetBrains/IntelliJIdea2024.1/options");
        assertTrue(options.mkdirs());
        write(new File(options, "colors.scheme.xml"),
                "<application><component name=\"EditorColorsManagerImpl\">"
                        + "<global_color_scheme name=\"Mystery Scheme 3000\" />"
                        + "</component></application>");

        String previousHome = useHome(home);
        try {
            assertEquals(TerminalTheme.UNKNOWN,
                    IdeThemeDetector.detect("jetbrains", new HashMap<String, String>(), LINUX));
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testStripJsonComments() {
        String json = "{\n"
                + "// a line comment\n"
                + "\"kept\": \"http://example.com/x\", /* a block comment */\n"
                + "\"other\": \"value\"\n"
                + "}";
        String stripped = IdeThemeDetector.stripJsonComments(json);
        assertTrue(stripped.contains("http://example.com/x"));
        assertTrue(!stripped.contains("line comment"));
        assertTrue(!stripped.contains("block comment"));
        assertTrue(stripped.contains("\"other\""));
    }

    @Test
    public void testParseHexColor() {
        assertEquals(0, IdeThemeDetector.parseHexColor("#000000")[0]);
        assertEquals(255, IdeThemeDetector.parseHexColor("#FFFFFF")[0]);
        assertEquals(12, IdeThemeDetector.parseHexColor("#0C0C0C")[2]);
        assertEquals(255, IdeThemeDetector.parseHexColor("#fff")[0]);
        assertNull(IdeThemeDetector.parseHexColor(null));
        assertNull(IdeThemeDetector.parseHexColor("not-a-color"));
        assertNull(IdeThemeDetector.parseHexColor("#ZZZZZZ"));
    }

    @Test
    public void testClassifyKeywords() {
        assertEquals(TerminalTheme.DARK, ThemeNameClassifier.classify("Darcula"));
        assertEquals(TerminalTheme.DARK, ThemeNameClassifier.classify("One Half Dark"));
        assertEquals(TerminalTheme.LIGHT, ThemeNameClassifier.classify("Default Light+"));
        assertEquals(TerminalTheme.LIGHT, ThemeNameClassifier.classify("solarized light"));
        assertEquals(TerminalTheme.UNKNOWN, ThemeNameClassifier.classify("Mystery Scheme 3000"));
        assertEquals(TerminalTheme.UNKNOWN, ThemeNameClassifier.classify(null));
        assertEquals(TerminalTheme.UNKNOWN, ThemeNameClassifier.classify(""));
    }

    @Test
    public void testExtractJsonValue() {
        assertEquals("Darcula",
                IdeThemeDetector.extractJsonValue("{\"colorScheme\" : \"Darcula\"}", "colorScheme"));
        assertNull(IdeThemeDetector.extractJsonValue("{}", "colorScheme"));
    }

    private static void write(File file, String content) throws IOException {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }

    /**
     * Point {@code user.home} at a fixture directory.
     *
     * @param home the fake home directory
     * @return the previous value, for restoring in a finally block
     */
    private static String useHome(File home) {
        String previous = System.getProperty("user.home");
        System.setProperty("user.home", home.getAbsolutePath());
        return previous;
    }
}
