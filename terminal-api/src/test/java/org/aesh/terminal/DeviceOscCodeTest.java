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
package org.aesh.terminal;

import static org.junit.Assert.*;

import java.util.EnumSet;
import java.util.Set;

import org.aesh.terminal.Device.OscCode;
import org.aesh.terminal.Device.TerminalType;
import org.aesh.terminal.utils.ColorDepth;
import org.junit.Test;

/**
 * Tests for Device.OscCode and Device.TerminalType enums.
 */
public class DeviceOscCodeTest {

    @Test
    public void testOscCodeValues() {
        assertEquals(4, OscCode.PALETTE.getCode());
        assertEquals(10, OscCode.FOREGROUND.getCode());
        assertEquals(11, OscCode.BACKGROUND.getCode());
        assertEquals(12, OscCode.CURSOR_COLOR.getCode());
        assertEquals(52, OscCode.CLIPBOARD.getCode());
    }

    @Test
    public void testJetBrainsTerminalType() {
        TerminalType jetbrains = TerminalType.JETBRAINS;

        // JetBrains does NOT support OSC 4 (palette)
        assertFalse("JetBrains should not support OSC 4",
                jetbrains.supports(OscCode.PALETTE));

        // But should support OSC 10/11
        assertTrue("JetBrains should support OSC 10",
                jetbrains.supports(OscCode.FOREGROUND));
        assertTrue("JetBrains should support OSC 11",
                jetbrains.supports(OscCode.BACKGROUND));

        // And not clipboard
        assertFalse("JetBrains should not support OSC 52",
                jetbrains.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testXtermTerminalType() {
        TerminalType xterm = TerminalType.XTERM;

        // xterm supports all OSC codes
        assertTrue(xterm.supports(OscCode.PALETTE));
        assertTrue(xterm.supports(OscCode.FOREGROUND));
        assertTrue(xterm.supports(OscCode.BACKGROUND));
        assertTrue(xterm.supports(OscCode.CURSOR_COLOR));
        assertTrue(xterm.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testLinuxConsoleTerminalType() {
        TerminalType linux = TerminalType.LINUX_CONSOLE;

        // Linux console supports no OSC codes
        assertFalse(linux.supports(OscCode.PALETTE));
        assertFalse(linux.supports(OscCode.FOREGROUND));
        assertFalse(linux.supports(OscCode.BACKGROUND));
        assertFalse(linux.supports(OscCode.CURSOR_COLOR));
        assertFalse(linux.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testUnknownTerminalType() {
        TerminalType unknown = TerminalType.UNKNOWN;

        // Unknown should support basic OSC 10/11 as a safe default
        assertTrue(unknown.supports(OscCode.FOREGROUND));
        assertTrue(unknown.supports(OscCode.BACKGROUND));

        // But not OSC 4 or 52 (more advanced features)
        assertFalse(unknown.supports(OscCode.PALETTE));
        assertFalse(unknown.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testTerminalTypeIdentifiers() {
        assertEquals("JetBrains-JediTerm", TerminalType.JETBRAINS.getIdentifier());
        assertEquals("xterm", TerminalType.XTERM.getIdentifier());
        assertEquals("linux", TerminalType.LINUX_CONSOLE.getIdentifier());
    }

    @Test
    public void testGetSupportedOscCodes() {
        Set<OscCode> jetbrainsCodes = TerminalType.JETBRAINS.getSupportedCodes();
        assertTrue(jetbrainsCodes.contains(OscCode.FOREGROUND));
        assertTrue(jetbrainsCodes.contains(OscCode.BACKGROUND));
        assertTrue(jetbrainsCodes.contains(OscCode.HYPERLINK));

        // xterm supports all OSC codes
        Set<OscCode> xtermCodes = TerminalType.XTERM.getSupportedCodes();
        assertEquals(EnumSet.allOf(OscCode.class), xtermCodes);
    }

    @Test
    public void testITermSupportsAllOscCodes() {
        TerminalType iterm = TerminalType.ITERM2;

        assertTrue(iterm.supports(OscCode.PALETTE));
        assertTrue(iterm.supports(OscCode.FOREGROUND));
        assertTrue(iterm.supports(OscCode.BACKGROUND));
        assertTrue(iterm.supports(OscCode.CURSOR_COLOR));
        assertTrue(iterm.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testVscodeTerminalType() {
        TerminalType vscode = TerminalType.VSCODE;

        // VS Code supports most OSC codes including clipboard
        assertTrue(vscode.supports(OscCode.FOREGROUND));
        assertTrue(vscode.supports(OscCode.BACKGROUND));
        assertTrue(vscode.supports(OscCode.CURSOR_COLOR));
        assertTrue(vscode.supports(OscCode.CLIPBOARD));

        // But may not support OSC 4 palette
        // (This can be updated if VS Code adds support)
    }

    @Test
    public void testAlacrittyTerminalType() {
        TerminalType alacritty = TerminalType.ALACRITTY;

        // Alacritty supports color queries
        assertTrue(alacritty.supports(OscCode.FOREGROUND));
        assertTrue(alacritty.supports(OscCode.BACKGROUND));
        assertTrue(alacritty.supports(OscCode.CURSOR_COLOR));

        // But not clipboard (security feature)
        assertFalse(alacritty.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testKittyTerminalType() {
        TerminalType kitty = TerminalType.KITTY;

        // Kitty supports all OSC codes
        assertTrue(kitty.supports(OscCode.PALETTE));
        assertTrue(kitty.supports(OscCode.FOREGROUND));
        assertTrue(kitty.supports(OscCode.BACKGROUND));
        assertTrue(kitty.supports(OscCode.CURSOR_COLOR));
        assertTrue(kitty.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testWeztermTerminalType() {
        TerminalType wezterm = TerminalType.WEZTERM;

        // WezTerm supports all OSC codes
        assertTrue(wezterm.supports(OscCode.PALETTE));
        assertTrue(wezterm.supports(OscCode.FOREGROUND));
        assertTrue(wezterm.supports(OscCode.BACKGROUND));
        assertTrue(wezterm.supports(OscCode.CURSOR_COLOR));
        assertTrue(wezterm.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testWindowsTerminalType() {
        TerminalType windowsTerminal = TerminalType.WINDOWS_TERMINAL;

        // Windows Terminal supports color queries
        assertTrue(windowsTerminal.supports(OscCode.FOREGROUND));
        assertTrue(windowsTerminal.supports(OscCode.BACKGROUND));
        assertTrue(windowsTerminal.supports(OscCode.CURSOR_COLOR));

        // But not palette or clipboard
        assertFalse(windowsTerminal.supports(OscCode.PALETTE));
        assertFalse(windowsTerminal.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testAppleTerminalType() {
        TerminalType appleTerminal = TerminalType.APPLE_TERMINAL;

        // Apple Terminal supports color queries
        assertTrue(appleTerminal.supports(OscCode.FOREGROUND));
        assertTrue(appleTerminal.supports(OscCode.BACKGROUND));
        assertTrue(appleTerminal.supports(OscCode.CURSOR_COLOR));

        // But not palette or clipboard
        assertFalse(appleTerminal.supports(OscCode.PALETTE));
        assertFalse(appleTerminal.supports(OscCode.CLIPBOARD));
    }

    // ==================== New Terminal Type Tests ====================

    @Test
    public void testGhosttyTerminalType() {
        TerminalType ghostty = TerminalType.GHOSTTY;

        // Ghostty supports all OSC codes
        assertTrue(ghostty.supports(OscCode.PALETTE));
        assertTrue(ghostty.supports(OscCode.FOREGROUND));
        assertTrue(ghostty.supports(OscCode.BACKGROUND));
        assertTrue(ghostty.supports(OscCode.CURSOR_COLOR));
        assertTrue(ghostty.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testKonsoleTerminalType() {
        TerminalType konsole = TerminalType.KONSOLE;

        // Konsole supports all OSC codes
        assertTrue(konsole.supports(OscCode.PALETTE));
        assertTrue(konsole.supports(OscCode.FOREGROUND));
        assertTrue(konsole.supports(OscCode.BACKGROUND));
        assertTrue(konsole.supports(OscCode.CURSOR_COLOR));
        assertTrue(konsole.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testGnomeTerminalType() {
        TerminalType gnome = TerminalType.GNOME_TERMINAL;

        // GNOME Terminal (VTE) supports all OSC codes
        assertTrue(gnome.supports(OscCode.PALETTE));
        assertTrue(gnome.supports(OscCode.FOREGROUND));
        assertTrue(gnome.supports(OscCode.BACKGROUND));
        assertTrue(gnome.supports(OscCode.CURSOR_COLOR));
        assertTrue(gnome.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testFootTerminalType() {
        TerminalType foot = TerminalType.FOOT;

        // Foot supports all OSC codes
        assertTrue(foot.supports(OscCode.PALETTE));
        assertTrue(foot.supports(OscCode.FOREGROUND));
        assertTrue(foot.supports(OscCode.BACKGROUND));
        assertTrue(foot.supports(OscCode.CURSOR_COLOR));
        assertTrue(foot.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testMinttyTerminalType() {
        TerminalType mintty = TerminalType.MINTTY;

        // Mintty supports all OSC codes
        assertTrue(mintty.supports(OscCode.PALETTE));
        assertTrue(mintty.supports(OscCode.FOREGROUND));
        assertTrue(mintty.supports(OscCode.BACKGROUND));
        assertTrue(mintty.supports(OscCode.CURSOR_COLOR));
        assertTrue(mintty.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testTerminalMultiplexers() {
        // tmux and screen don't support OSC queries by default
        TerminalType tmux = TerminalType.TMUX;
        assertFalse(tmux.supports(OscCode.PALETTE));
        assertFalse(tmux.supports(OscCode.FOREGROUND));
        assertFalse(tmux.supports(OscCode.BACKGROUND));
        assertFalse(tmux.supports(OscCode.CURSOR_COLOR));
        assertFalse(tmux.supports(OscCode.CLIPBOARD));

        TerminalType screen = TerminalType.SCREEN;
        assertFalse(screen.supports(OscCode.PALETTE));
        assertFalse(screen.supports(OscCode.FOREGROUND));
        assertFalse(screen.supports(OscCode.BACKGROUND));
    }

    @Test
    public void testRxvtTerminalType() {
        TerminalType rxvt = TerminalType.RXVT;

        // rxvt supports basic color queries
        assertTrue(rxvt.supports(OscCode.FOREGROUND));
        assertTrue(rxvt.supports(OscCode.BACKGROUND));
        assertTrue(rxvt.supports(OscCode.CURSOR_COLOR));

        // But not palette or clipboard
        assertFalse(rxvt.supports(OscCode.PALETTE));
        assertFalse(rxvt.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testHyperTerminalType() {
        TerminalType hyper = TerminalType.HYPER;

        // Hyper supports all OSC codes
        assertTrue(hyper.supports(OscCode.PALETTE));
        assertTrue(hyper.supports(OscCode.FOREGROUND));
        assertTrue(hyper.supports(OscCode.BACKGROUND));
        assertTrue(hyper.supports(OscCode.CURSOR_COLOR));
        assertTrue(hyper.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testConEmuTerminalType() {
        TerminalType conemu = TerminalType.CONEMU;

        // ConEmu supports basic color queries
        assertTrue(conemu.supports(OscCode.FOREGROUND));
        assertTrue(conemu.supports(OscCode.BACKGROUND));

        // But not advanced features
        assertFalse(conemu.supports(OscCode.PALETTE));
        assertFalse(conemu.supports(OscCode.CLIPBOARD));
    }

    @Test
    public void testAllTerminalTypesHaveIdentifier() {
        for (TerminalType type : TerminalType.values()) {
            assertNotNull("TerminalType " + type + " should have identifier",
                    type.getIdentifier());
            assertFalse("TerminalType " + type + " identifier should not be empty",
                    type.getIdentifier().isEmpty());
        }
    }

    @Test
    public void testAllTerminalTypesHaveSupportedCodes() {
        for (TerminalType type : TerminalType.values()) {
            assertNotNull("TerminalType " + type + " should have supported codes set",
                    type.getSupportedCodes());
        }
    }

    // ==================== Color Depth Tests ====================

    @Test
    public void testModernTerminalsSupportTrueColor() {
        // Modern GPU-accelerated terminals should support true color
        assertTrue(TerminalType.KITTY.supportsTrueColor());
        assertTrue(TerminalType.GHOSTTY.supportsTrueColor());
        assertTrue(TerminalType.ALACRITTY.supportsTrueColor());
        assertTrue(TerminalType.WEZTERM.supportsTrueColor());
        assertTrue(TerminalType.FOOT.supportsTrueColor());
        assertTrue(TerminalType.ITERM2.supportsTrueColor());
        assertTrue(TerminalType.WINDOWS_TERMINAL.supportsTrueColor());
    }

    @Test
    public void testLegacyTerminalsColorDepth() {
        // Legacy terminals typically support 256 colors
        assertEquals(ColorDepth.COLORS_256, TerminalType.XTERM.getDefaultColorDepth());
        assertEquals(ColorDepth.COLORS_256, TerminalType.RXVT.getDefaultColorDepth());
        assertEquals(ColorDepth.COLORS_256, TerminalType.APPLE_TERMINAL.getDefaultColorDepth());

        // Linux console is very limited
        assertEquals(ColorDepth.COLORS_8, TerminalType.LINUX_CONSOLE.getDefaultColorDepth());
    }

    @Test
    public void testTerminalMultiplexersColorDepth() {
        // Multiplexers pass through colors based on configuration, default to 256
        assertEquals(ColorDepth.COLORS_256, TerminalType.TMUX.getDefaultColorDepth());
        assertEquals(ColorDepth.COLORS_256, TerminalType.SCREEN.getDefaultColorDepth());
    }

    @Test
    public void testAllTerminalTypesHaveColorDepth() {
        for (TerminalType type : TerminalType.values()) {
            assertNotNull("TerminalType " + type + " should have color depth",
                    type.getDefaultColorDepth());
        }
    }

    @Test
    public void testIdeTerminalsSupportTrueColor() {
        // IDE terminals support true color
        assertTrue(TerminalType.JETBRAINS.supportsTrueColor());
        assertTrue(TerminalType.VSCODE.supportsTrueColor());
    }

    // ==================== Theme DSR (CSI ? 996 n) Support Tests ====================

    @Test
    public void testThemeDsrSupportedTerminals() {
        // Terminals known to support CSI ? 996 n
        assertTrue("Kitty supports theme DSR", TerminalType.KITTY.supportsThemeDsr());
        assertTrue("Ghostty supports theme DSR", TerminalType.GHOSTTY.supportsThemeDsr());
        assertTrue("Foot supports theme DSR", TerminalType.FOOT.supportsThemeDsr());
        assertTrue("Contour supports theme DSR", TerminalType.CONTOUR.supportsThemeDsr());
        assertTrue("GNOME Terminal supports theme DSR", TerminalType.GNOME_TERMINAL.supportsThemeDsr());
        assertTrue("tmux supports theme DSR", TerminalType.TMUX.supportsThemeDsr());
    }

    @Test
    public void testThemeDsrUnsupportedTerminals() {
        // Terminals that do NOT support CSI ? 996 n
        assertFalse("JetBrains does not support theme DSR", TerminalType.JETBRAINS.supportsThemeDsr());
        assertFalse("VS Code does not support theme DSR", TerminalType.VSCODE.supportsThemeDsr());
        assertFalse("iTerm2 does not support theme DSR", TerminalType.ITERM2.supportsThemeDsr());
        assertFalse("Alacritty does not support theme DSR", TerminalType.ALACRITTY.supportsThemeDsr());
        assertFalse("WezTerm does not support theme DSR", TerminalType.WEZTERM.supportsThemeDsr());
        assertFalse("xterm does not support theme DSR", TerminalType.XTERM.supportsThemeDsr());
        assertFalse("Konsole does not support theme DSR", TerminalType.KONSOLE.supportsThemeDsr());
        assertFalse("Screen does not support theme DSR", TerminalType.SCREEN.supportsThemeDsr());
        assertFalse("Linux console does not support theme DSR", TerminalType.LINUX_CONSOLE.supportsThemeDsr());
        assertFalse("Unknown does not support theme DSR", TerminalType.UNKNOWN.supportsThemeDsr());
    }

    @Test
    public void testAllTerminalTypesHaveThemeDsrValue() {
        // Ensure supportsThemeDsr() doesn't throw for any terminal type
        for (TerminalType type : TerminalType.values()) {
            // Just call it - should not throw
            type.supportsThemeDsr();
        }
    }
}
