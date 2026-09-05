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
package org.aesh.terminal.utils;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.aesh.terminal.Device;
import org.junit.Test;

/**
 * Tests for TerminalEnvironment class.
 * <p>
 * Note: Many tests here verify behavior based on the actual environment,
 * so assertions are kept general to pass in various CI/local environments.
 */
public class TerminalEnvironmentTest {

    @Test
    public void testSingletonInstance() {
        TerminalEnvironment env1 = TerminalEnvironment.getInstance();
        TerminalEnvironment env2 = TerminalEnvironment.getInstance();

        assertNotNull("Instance should not be null", env1);
        assertSame("Should return same instance", env1, env2);
    }

    @Test
    public void testRefreshCreatesNewInstance() {
        TerminalEnvironment before = TerminalEnvironment.getInstance();
        TerminalEnvironment.refresh();
        TerminalEnvironment after = TerminalEnvironment.getInstance();

        // After refresh, the instance fields are repopulated
        // The instance itself may or may not be the same object
        // depending on the implementation details, but the values
        // should be consistent
        assertNotNull(after);
        assertNotNull(after.getTerminalType());
    }

    @Test
    public void testTerminalTypeNeverNull() {
        Device.TerminalType type = TerminalEnvironment.getInstance().getTerminalType();
        assertNotNull("Terminal type should never be null", type);
    }

    @Test
    public void testColorDepthNeverNull() {
        ColorDepth depth = TerminalEnvironment.getInstance().getDefaultColorDepth();
        assertNotNull("Color depth should never be null", depth);
    }

    @Test
    public void testStaticConvenienceMethods() {
        // Static methods should work and return consistent results
        Device.TerminalType type = TerminalEnvironment.detectTerminalType();
        ColorDepth depth = TerminalEnvironment.detectColorDepth();

        assertNotNull(type);
        assertNotNull(depth);

        // Should match instance methods
        assertEquals(type, TerminalEnvironment.getInstance().getTerminalType());
        assertEquals(depth, TerminalEnvironment.getInstance().getDefaultColorDepth());
    }

    @Test
    public void testIsJetBrainsTerminal() {
        // This should return a boolean, not throw
        boolean result = TerminalEnvironment.isJetBrainsTerminal();
        // Value depends on environment, just verify no exception
        assertTrue(result || !result);
    }

    @Test
    public void testIsOscSupported() {
        // This should return a boolean, not throw
        boolean result = TerminalEnvironment.isOscSupported();
        // Value depends on environment, just verify no exception
        assertTrue(result || !result);
    }

    @Test
    public void testIsInMultiplexer() {
        // This should return a boolean, not throw
        boolean result = TerminalEnvironment.getInstance().isInMultiplexer();
        // Value depends on environment, just verify no exception
        assertTrue(result || !result);
    }

    @Test
    public void testIsInTmux() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();
        boolean inTmux = env.isInTmux();

        // If in tmux, isInMultiplexer should also be true
        if (inTmux) {
            assertTrue("If in tmux, should be in multiplexer", env.isInMultiplexer());
        }
    }

    @Test
    public void testIsInScreen() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();
        boolean inScreen = env.isInScreen();

        // If in screen, isInMultiplexer should also be true
        if (inScreen) {
            assertTrue("If in screen, should be in multiplexer", env.isInMultiplexer());
        }
    }

    @Test
    public void testTmuxPassthroughWhenNotInTmux() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (!env.isInTmux()) {
            assertFalse("Passthrough should be false when not in tmux",
                    env.isTmuxPassthroughEnabled());
        }
    }

    @Test
    public void testHasKnownOuterTerminal() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // If any specific terminal env var is set, hasKnownOuterTerminal should be true
        if (env.isKitty() || env.isGhostty() || env.isWezTerm() ||
                env.isITerm2() || env.isAlacritty()) {
            assertTrue("Should detect known outer terminal", env.hasKnownOuterTerminal());
        }
    }

    @Test
    public void testIsTrueColorIndicated() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // If true color is indicated, color depth should be true color
        if (env.isTrueColorIndicated()) {
            assertEquals(ColorDepth.TRUE_COLOR, env.getDefaultColorDepth());
        }
    }

    @Test
    public void testToStringContainsRelevantInfo() {
        String str = TerminalEnvironment.getInstance().toString();

        assertNotNull(str);
        assertTrue("Should contain terminalType", str.contains("terminalType"));
        assertTrue("Should contain colorDepth", str.contains("colorDepth"));
    }

    @Test
    public void testTermAccessors() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // These can return null if not set, just verify no exception
        String term = env.getTerm();
        String termProgram = env.getTermProgram();
        String terminalEmulator = env.getTerminalEmulator();
        String colorterm = env.getColorterm();
        String colorFgBg = env.getColorFgBg();

        // No assertions on values - they depend on environment
    }

    @Test
    public void testSupportsOscQueriesLogic() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // If JetBrains, OSC queries should not be supported
        if (env.isJetBrains()) {
            assertFalse("JetBrains should not support OSC queries",
                    env.supportsOscQueries());
        }

        // If has known outer terminal, should likely support OSC
        if (env.hasKnownOuterTerminal() && !env.isJetBrains()) {
            assertTrue("Known outer terminal should support OSC",
                    env.supportsOscQueries());
        }
    }

    // Note: These live-environment tests guard against JetBrains because its
    // TERMINAL_EMULATOR env var is inherited by child processes (including test
    // runners) alongside the host terminal's env vars (e.g., GHOSTTY_RESOURCES_DIR).
    // JetBrains has the highest detection priority, so when both are present,
    // getTerminalType() correctly returns JETBRAINS. The deterministic tests
    // below (testDetect*) cover the isolated detection logic.

    @Test
    public void testKittyDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isKitty() && !env.isJetBrains()) {
            assertEquals("Kitty should map to KITTY terminal type",
                    Device.TerminalType.KITTY, env.getTerminalType());
        }
    }

    @Test
    public void testGhosttyDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isGhostty() && !env.isJetBrains()) {
            assertEquals("Ghostty should map to GHOSTTY terminal type",
                    Device.TerminalType.GHOSTTY, env.getTerminalType());
        }
    }

    @Test
    public void testWezTermDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isWezTerm() && !env.isJetBrains()) {
            assertEquals("WezTerm should map to WEZTERM terminal type",
                    Device.TerminalType.WEZTERM, env.getTerminalType());
        }
    }

    @Test
    public void testITerm2Detection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isITerm2() && !env.isJetBrains()) {
            assertEquals("iTerm2 should map to ITERM2 terminal type",
                    Device.TerminalType.ITERM2, env.getTerminalType());
        }
    }

    @Test
    public void testWindowsTerminalDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isWindowsTerminal() && !env.isJetBrains()) {
            assertEquals("Windows Terminal should map to WINDOWS_TERMINAL type",
                    Device.TerminalType.WINDOWS_TERMINAL, env.getTerminalType());
        }
    }

    @Test
    public void testConEmuDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isConEmu() && !env.isJetBrains()) {
            assertEquals("ConEmu should map to CONEMU terminal type",
                    Device.TerminalType.CONEMU, env.getTerminalType());
        }
    }

    @Test
    public void testAlacrittyDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isAlacritty() && !env.isJetBrains()) {
            assertEquals("Alacritty should map to ALACRITTY terminal type",
                    Device.TerminalType.ALACRITTY, env.getTerminalType());
        }
    }

    @Test
    public void testJetBrainsDetection() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        if (env.isJetBrains()) {
            assertEquals("JetBrains should map to JETBRAINS terminal type",
                    Device.TerminalType.JETBRAINS, env.getTerminalType());
        }
    }

    @Test
    public void testMacOsDarkMode() {
        TerminalEnvironment env = TerminalEnvironment.getInstance();

        // Just verify no exception
        boolean darkMode = env.isMacOsDarkMode();
        String style = env.getAppleInterfaceStyle();

        if ("Dark".equalsIgnoreCase(style)) {
            assertTrue("Dark style should indicate dark mode", darkMode);
        }
    }

    // ==================== Deterministic tests using injectable env ====================

    private static Map<String, String> env(String... pairs) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    // ---- Terminal type detection ----

    @Test
    public void testDetectKitty() {
        TerminalEnvironment env = new TerminalEnvironment(env("KITTY_WINDOW_ID", "1"));
        assertEquals(Device.TerminalType.KITTY, env.getTerminalType());
        assertTrue(env.isKitty());
    }

    @Test
    public void testDetectGhostty() {
        TerminalEnvironment env = new TerminalEnvironment(env("GHOSTTY_RESOURCES_DIR", "/usr/share/ghostty"));
        assertEquals(Device.TerminalType.GHOSTTY, env.getTerminalType());
        assertTrue(env.isGhostty());
    }

    @Test
    public void testDetectWezTerm() {
        TerminalEnvironment env = new TerminalEnvironment(env("WEZTERM_PANE", "0"));
        assertEquals(Device.TerminalType.WEZTERM, env.getTerminalType());
        assertTrue(env.isWezTerm());
    }

    @Test
    public void testDetectITerm2() {
        TerminalEnvironment env = new TerminalEnvironment(env("ITERM_SESSION_ID", "w0t0p0:ABC"));
        assertEquals(Device.TerminalType.ITERM2, env.getTerminalType());
        assertTrue(env.isITerm2());
    }

    @Test
    public void testDetectAlacritty() {
        TerminalEnvironment env = new TerminalEnvironment(env("ALACRITTY_SOCKET", "/tmp/alacritty.sock"));
        assertEquals(Device.TerminalType.ALACRITTY, env.getTerminalType());
        assertTrue(env.isAlacritty());
    }

    @Test
    public void testDetectConEmu() {
        TerminalEnvironment env = new TerminalEnvironment(env("ConEmuPID", "1234"));
        assertEquals(Device.TerminalType.CONEMU, env.getTerminalType());
        assertTrue(env.isConEmu());
    }

    @Test
    public void testDetectJetBrains() {
        TerminalEnvironment env = new TerminalEnvironment(env("TERMINAL_EMULATOR", "JetBrains-JediTerm"));
        assertEquals(Device.TerminalType.JETBRAINS, env.getTerminalType());
        assertTrue(env.isJetBrains());
    }

    @Test
    public void testDetectJetBrainsNoOsc() {
        TerminalEnvironment env = new TerminalEnvironment(env("TERMINAL_EMULATOR", "JetBrains-JediTerm"));
        assertFalse("JetBrains should not support OSC queries", env.supportsOscQueries());
    }

    // ---- TERM_PROGRAM based detection ----

    @Test
    public void testDetectViaTermProgram() {
        assertEquals(Device.TerminalType.ITERM2,
                new TerminalEnvironment(env("TERM_PROGRAM", "iTerm.app")).getTerminalType());
        assertEquals(Device.TerminalType.APPLE_TERMINAL,
                new TerminalEnvironment(env("TERM_PROGRAM", "Apple_Terminal")).getTerminalType());
        assertEquals(Device.TerminalType.VSCODE,
                new TerminalEnvironment(env("TERM_PROGRAM", "vscode")).getTerminalType());
        assertEquals(Device.TerminalType.HYPER,
                new TerminalEnvironment(env("TERM_PROGRAM", "Hyper")).getTerminalType());
    }

    // ---- TERM based detection ----

    @Test
    public void testDetectViaTerm() {
        assertEquals(Device.TerminalType.LINUX_CONSOLE,
                new TerminalEnvironment(env("TERM", "linux")).getTerminalType());
        assertEquals(Device.TerminalType.XTERM,
                new TerminalEnvironment(env("TERM", "xterm-256color")).getTerminalType());
        assertEquals(Device.TerminalType.TMUX,
                new TerminalEnvironment(env("TERM", "tmux-256color")).getTerminalType());
        assertEquals(Device.TerminalType.SCREEN,
                new TerminalEnvironment(env("TERM", "screen-256color")).getTerminalType());
        assertEquals(Device.TerminalType.FOOT,
                new TerminalEnvironment(env("TERM", "foot")).getTerminalType());
    }

    @Test
    public void testDetectUnknownTerm() {
        assertEquals(Device.TerminalType.UNKNOWN,
                new TerminalEnvironment(env("TERM", "dumb")).getTerminalType());
    }

    @Test
    public void testEmptyEnvironment() {
        TerminalEnvironment env = new TerminalEnvironment(Collections.emptyMap());
        assertEquals(Device.TerminalType.UNKNOWN, env.getTerminalType());
        assertFalse(env.isKitty());
        assertFalse(env.isGhostty());
        assertFalse(env.isInTmux());
        assertFalse(env.isInScreen());
        assertFalse(env.isInMultiplexer());
        assertFalse(env.isTrueColorIndicated());
        assertFalse(env.isMacOsDarkMode());
    }

    // ---- Priority: env var > TERM_PROGRAM > TERM ----

    @Test
    public void testEnvVarTakesPriorityOverTermProgram() {
        // KITTY_WINDOW_ID should win over TERM_PROGRAM=iTerm.app
        TerminalEnvironment env = new TerminalEnvironment(env(
                "KITTY_WINDOW_ID", "1",
                "TERM_PROGRAM", "iTerm.app"));
        assertEquals(Device.TerminalType.KITTY, env.getTerminalType());
    }

    @Test
    public void testTermProgramTakesPriorityOverTerm() {
        // TERM_PROGRAM=iTerm.app should win over TERM=xterm-256color
        TerminalEnvironment env = new TerminalEnvironment(env(
                "TERM_PROGRAM", "iTerm.app",
                "TERM", "xterm-256color"));
        assertEquals(Device.TerminalType.ITERM2, env.getTerminalType());
    }

    @Test
    public void testJetBrainsTakesPriorityOverAll() {
        // JetBrains should win over everything
        TerminalEnvironment env = new TerminalEnvironment(env(
                "TERMINAL_EMULATOR", "JetBrains-JediTerm",
                "KITTY_WINDOW_ID", "1",
                "TERM_PROGRAM", "iTerm.app",
                "TERM", "xterm-256color"));
        assertEquals(Device.TerminalType.JETBRAINS, env.getTerminalType());
    }

    // ---- Multiplexer detection ----

    @Test
    public void testTmuxDetection() {
        TerminalEnvironment env = new TerminalEnvironment(env("TMUX", "/tmp/tmux-1000/default,12345,0"));
        assertTrue(env.isInTmux());
        assertTrue(env.isInMultiplexer());
    }

    @Test
    public void testScreenDetection() {
        TerminalEnvironment env = new TerminalEnvironment(env("TERM", "screen"));
        assertTrue(env.isInScreen());
        assertTrue(env.isInMultiplexer());
    }

    @Test
    public void testTmuxPassthroughEnabled() {
        TerminalEnvironment env = new TerminalEnvironment(env(
                "TMUX", "/tmp/tmux-1000/default,12345,0",
                "TMUX_PASSTHROUGH", "1"));
        assertTrue(env.isTmuxPassthroughEnabled());
    }

    @Test
    public void testTmuxPassthroughDisabled() {
        TerminalEnvironment env = new TerminalEnvironment(env(
                "TMUX", "/tmp/tmux-1000/default,12345,0"));
        assertFalse(env.isTmuxPassthroughEnabled());
    }

    @Test
    public void testTmuxPassthroughNotInTmux() {
        // TMUX_PASSTHROUGH without TMUX should be false
        TerminalEnvironment env = new TerminalEnvironment(env("TMUX_PASSTHROUGH", "1"));
        assertFalse(env.isTmuxPassthroughEnabled());
    }

    // ---- Color detection ----

    @Test
    public void testTrueColorIndicated() {
        assertTrue(new TerminalEnvironment(env("COLORTERM", "truecolor")).isTrueColorIndicated());
        assertTrue(new TerminalEnvironment(env("COLORTERM", "24bit")).isTrueColorIndicated());
        assertTrue(new TerminalEnvironment(env("COLORTERM", "TrueColor")).isTrueColorIndicated());
        assertFalse(new TerminalEnvironment(env("COLORTERM", "256color")).isTrueColorIndicated());
    }

    @Test
    public void testMacOsDarkModeDetection() {
        assertTrue(new TerminalEnvironment(env("APPLE_INTERFACE_STYLE", "Dark")).isMacOsDarkMode());
        assertFalse(new TerminalEnvironment(env("APPLE_INTERFACE_STYLE", "Light")).isMacOsDarkMode());
        assertFalse(new TerminalEnvironment(Collections.emptyMap()).isMacOsDarkMode());
    }

    // ---- OSC query support ----

    @Test
    public void testOscSupportedWithKnownTerminal() {
        TerminalEnvironment env = new TerminalEnvironment(env(
                "KITTY_WINDOW_ID", "1",
                "TERM", "xterm-kitty"));
        assertTrue(env.supportsOscQueries());
    }

    @Test
    public void testOscNotSupportedInMultiplexerWithoutPassthrough() {
        TerminalEnvironment env = new TerminalEnvironment(env(
                "TMUX", "/tmp/tmux-1000/default,12345,0",
                "TERM", "tmux-256color"));
        // In tmux without passthrough and without a known outer terminal
        assertFalse(env.supportsOscQueries());
    }

    @Test
    public void testOscSupportedInTmuxWithKnownOuter() {
        TerminalEnvironment env = new TerminalEnvironment(env(
                "TMUX", "/tmp/tmux-1000/default,12345,0",
                "KITTY_WINDOW_ID", "1",
                "TERM", "tmux-256color"));
        // In tmux but with Kitty as outer terminal
        assertTrue(env.supportsOscQueries());
    }

    @Test
    public void testOscSupportedInTmuxWithPassthrough() {
        TerminalEnvironment env = new TerminalEnvironment(env(
                "TMUX", "/tmp/tmux-1000/default,12345,0",
                "TMUX_PASSTHROUGH", "on",
                "TERM", "tmux-256color"));
        assertTrue(env.supportsOscQueries());
    }

    // ---- Raw accessor consistency ----

    @Test
    public void testRawAccessorsReturnInjectedValues() {
        TerminalEnvironment env = new TerminalEnvironment(env(
                "TERM", "xterm-256color",
                "TERM_PROGRAM", "Ghostty",
                "COLORTERM", "truecolor",
                "COLORFGBG", "15;0"));
        assertEquals("xterm-256color", env.getTerm());
        assertEquals("Ghostty", env.getTermProgram());
        assertEquals("truecolor", env.getColorterm());
        assertEquals("15;0", env.getColorFgBg());
    }
}
