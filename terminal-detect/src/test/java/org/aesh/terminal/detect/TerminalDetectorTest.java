/*
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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Environment-driven detection facts: terminal identity and the basic
 * color-depth ladder. All environments are injected maps, so no real
 * terminal or environment variables are needed.
 */
public class TerminalDetectorTest {

    private static TerminalDetector detector(String... pairs) {
        Map<String, String> env = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            env.put(pairs[i], pairs[i + 1]);
        }
        return new TerminalDetector(env);
    }

    @Test
    public void testLinuxConsoleSupportsBasicColor() {
        TerminalDetector detector = detector("TERM", "linux");
        assertEquals("linux-console", detector.terminalName);
        assertTrue(detector.supportsColor);
        assertFalse(detector.colors256);
        assertFalse(detector.trueColor);
    }

    @Test
    public void testDumbTerminalSupportsNoColor() {
        TerminalDetector detector = detector("TERM", "dumb");
        assertEquals("unknown", detector.terminalName);
        assertFalse(detector.supportsColor);
        assertFalse(detector.colors256);
        assertFalse(detector.trueColor);
    }

    @Test
    public void testEmptyEnvironmentSupportsNoColor() {
        TerminalDetector detector = detector();
        assertEquals("unknown", detector.terminalName);
        assertFalse(detector.supportsColor);
    }

    @Test
    public void testXterm256Color() {
        TerminalDetector detector = detector("TERM", "xterm-256color");
        assertEquals("xterm", detector.terminalName);
        assertTrue(detector.supportsColor);
        assertTrue(detector.colors256);
    }

    @Test
    public void testTmuxIdentitySupportsBasicColorOnly() {
        TerminalDetector detector = detector("TERM", "tmux");
        assertEquals("tmux", detector.terminalName);
        assertTrue(detector.supportsColor);
        assertFalse(detector.colors256);
        assertFalse(detector.trueColor);
    }

    @Test
    public void testTmux256ColorKeeps256WithoutTrueColor() {
        TerminalDetector detector = detector("TERM", "tmux-256color");
        assertEquals("tmux", detector.terminalName);
        assertTrue(detector.supportsColor);
        assertTrue(detector.colors256);
        assertFalse("tmux truecolor needs an explicit marker, not inference",
                detector.trueColor);
    }

    @Test
    public void testScreenIdentity() {
        TerminalDetector detector = detector("TERM", "screen");
        assertEquals("screen", detector.terminalName);
        assertTrue(detector.supportsColor);
        assertFalse(detector.colors256);
    }

    @Test
    public void testScreenQualifiedTermStaysScreen() {
        TerminalDetector detector = detector("TERM", "screen.xterm-256color");
        assertEquals("screen", detector.terminalName);
        assertTrue(detector.colors256);
    }

    @Test
    public void testOuterIdentitySurvivesTmuxTerm() {
        TerminalDetector detector = detector("TERM_PROGRAM", "iTerm.app", "TERM", "tmux-256color");
        assertEquals("iterm2", detector.terminalName);
    }

    @Test
    public void testParseRegDwordHexValues() {
        String output = "\nHKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion"
                + "\\Themes\\Personalize\n"
                + "    AppsUseLightTheme    REG_DWORD    0x0\n";
        assertEquals(Integer.valueOf(0), TerminalDetector.parseRegDword(output, "AppsUseLightTheme"));

        String light = output.replace("0x0", "0x1");
        assertEquals(Integer.valueOf(1), TerminalDetector.parseRegDword(light, "AppsUseLightTheme"));
    }

    @Test
    public void testParseRegDwordDecimalAndWhitespace() {
        String output = "HKEY_CURRENT_USER\\Software\\X\n"
                + "\tAppsUseLightTheme\tREG_DWORD\t1\n";
        assertEquals(Integer.valueOf(1), TerminalDetector.parseRegDword(output, "AppsUseLightTheme"));
    }

    @Test
    public void testParseRegDwordIgnoresOtherTypesAndNames() {
        String output = "HKEY_CURRENT_USER\\Software\\X\n"
                + "    AppsUseLightTheme    REG_SZ    0x0\n"
                + "    OtherValue    REG_DWORD    0x1\n";
        assertNull("string-typed values must not parse as DWORD",
                TerminalDetector.parseRegDword(output, "AppsUseLightTheme"));
        assertNull("other value names must not match",
                TerminalDetector.parseRegDword(output, "MissingValue"));
    }

    @Test
    public void testParseRegDwordMalformed() {
        assertNull(TerminalDetector.parseRegDword(null, "AppsUseLightTheme"));
        assertNull(TerminalDetector.parseRegDword("", "AppsUseLightTheme"));
        assertNull(TerminalDetector.parseRegDword(
                "    AppsUseLightTheme    REG_DWORD\n", "AppsUseLightTheme"));
        assertNull(TerminalDetector.parseRegDword(
                "    AppsUseLightTheme    REG_DWORD    0xZZ\n", "AppsUseLightTheme"));
    }

    @Test
    public void testThemeFromDword() {
        assertEquals(TerminalTheme.DARK, TerminalDetector.themeFromDword(0));
        assertEquals(TerminalTheme.LIGHT, TerminalDetector.themeFromDword(1));
        assertEquals(TerminalTheme.LIGHT, TerminalDetector.themeFromDword(0x1));
        assertEquals(TerminalTheme.UNKNOWN, TerminalDetector.themeFromDword(null));
    }

    // ==================== Captured environment reuse (#347) ====================

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static void write(File file, String content) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        }
    }

    @Test
    public void testPlatformThemeReusesCapturedEnvironment() throws Exception {
        File stateDir = new File(tmp.newFolder("localappdata"), "Microsoft/Windows Terminal");
        assertTrue(stateDir.mkdirs());
        write(new File(stateDir, "settings.json"), "{\"colorScheme\": \"Campbell\"}");

        Map<String, String> env = new HashMap<>();
        env.put("WT_SESSION", "some-session-id");
        env.put("LOCALAPPDATA", new File(tmp.getRoot(), "localappdata").getAbsolutePath());
        String previousHome = System.getProperty("user.home");
        System.setProperty("user.home", tmp.newFolder("home").getAbsolutePath());
        try {
            TerminalDetector detector = new TerminalDetector(env, "Windows 11");
            assertEquals("windows-terminal", detector.terminalName);
            assertEquals("captured WT settings must resolve through the same detector",
                    TerminalTheme.DARK, detector.detectIdeOrPlatformTheme());
        } finally {
            System.setProperty("user.home", previousHome);
        }
    }

    @Test
    public void testPlatformThemeWithoutFixtureFallsThrough() throws Exception {
        Map<String, String> env = new HashMap<>();
        env.put("WT_SESSION", "some-session-id");
        TerminalDetector.setProcessRunner(new TerminalDetector.ProcessRunner() {
            @Override
            public String run(String... cmd) {
                return null;
            }
        });
        String previousHome = System.getProperty("user.home");
        try {
            System.setProperty("user.home", tmp.newFolder("home").getAbsolutePath());
            assertEquals(TerminalTheme.UNKNOWN,
                    new TerminalDetector(env, "Windows 11").detectIdeOrPlatformTheme());
        } finally {
            System.setProperty("user.home", previousHome);
            TerminalDetector.setProcessRunner(null);
        }
    }

    @Test
    public void testNoDoubledIdentitySubprocesses() {
        // Without WT_SESSION the identity check runs a registry query;
        // the theme fallback runs another. Both must happen exactly once —
        // a second detector would repeat the identity query (#347).
        Map<String, String> env = new HashMap<>();
        env.put("TERM", "xterm");
        final List<String> commands = new ArrayList<>();
        TerminalDetector.setProcessRunner(new TerminalDetector.ProcessRunner() {
            @Override
            public String run(String... cmd) {
                StringBuilder joined = new StringBuilder();
                for (String part : cmd) {
                    if (joined.length() > 0) {
                        joined.append(' ');
                    }
                    joined.append(part);
                }
                commands.add(joined.toString());
                return null;
            }
        });
        try {
            assertEquals(TerminalTheme.UNKNOWN,
                    new TerminalDetector(env, "Windows 11").detectIdeOrPlatformTheme());
            assertEquals("identity + theme probes must run exactly once each: " + commands,
                    2, commands.size());
            assertEquals("no subprocess may repeat: " + commands,
                    2, new HashSet<>(commands).size());
        } finally {
            TerminalDetector.setProcessRunner(null);
        }
    }
}
