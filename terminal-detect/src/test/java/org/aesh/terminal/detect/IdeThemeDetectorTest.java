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
    public void testVSCodeUnknownThemeDefaultsDark() throws Exception {
        File home = tmp.newFolder("home");
        File userDir = new File(home, ".config/Code/User");
        assertTrue(userDir.mkdirs());
        write(new File(userDir, "settings.json"),
                "{\"workbench.colorTheme\": \"Some Obscure Theme\"}");

        String previousHome = useHome(home);
        try {
            assertEquals(TerminalTheme.DARK,
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
