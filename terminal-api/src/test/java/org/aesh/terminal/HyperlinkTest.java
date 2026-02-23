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

import org.aesh.terminal.Device.OscCode;
import org.aesh.terminal.Device.TerminalType;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.Parser;
import org.junit.Test;

/**
 * Tests for OSC 8 clickable hyperlink support.
 */
public class HyperlinkTest {

    // ==================== ANSI Hyperlink Builder Tests ====================

    @Test
    public void testBuildHyperlinkStart() {
        String result = ANSI.buildHyperlinkStart("https://example.com", null);
        assertEquals("\u001B]8;;https://example.com\u001B\\", result);
    }

    @Test
    public void testBuildHyperlinkStartWithId() {
        String result = ANSI.buildHyperlinkStart("https://example.com", "link1");
        assertEquals("\u001B]8;id=link1;https://example.com\u001B\\", result);
    }

    @Test
    public void testBuildHyperlinkStartWithEmptyId() {
        String result = ANSI.buildHyperlinkStart("https://example.com", "");
        assertEquals("\u001B]8;;https://example.com\u001B\\", result);
    }

    @Test
    public void testBuildHyperlinkEnd() {
        String result = ANSI.buildHyperlinkEnd();
        assertEquals("\u001B]8;;\u001B\\", result);
    }

    @Test
    public void testHyperlinkWrapsText() {
        String result = ANSI.hyperlink("https://example.com", "click here");
        String expected = "\u001B]8;;https://example.com\u001B\\click here\u001B]8;;\u001B\\";
        assertEquals(expected, result);
    }

    @Test
    public void testHyperlinkWithId() {
        String result = ANSI.hyperlink("https://example.com", "click here", "myid");
        String expected = "\u001B]8;id=myid;https://example.com\u001B\\click here\u001B]8;;\u001B\\";
        assertEquals(expected, result);
    }

    @Test
    public void testHyperlinkWithoutId() {
        // Two-arg version should produce same result as three-arg with null id
        String twoArg = ANSI.hyperlink("https://example.com", "text");
        String threeArg = ANSI.hyperlink("https://example.com", "text", null);
        assertEquals(twoArg, threeArg);
    }

    // ==================== OscCode HYPERLINK Tests ====================

    @Test
    public void testOscCodeHyperlinkValue() {
        assertEquals(8, OscCode.HYPERLINK.getCode());
    }

    @Test
    public void testOscCodeHyperlinkOrdering() {
        // HYPERLINK (8) should be between PALETTE (4) and FOREGROUND (10)
        assertTrue(OscCode.PALETTE.getCode() < OscCode.HYPERLINK.getCode());
        assertTrue(OscCode.HYPERLINK.getCode() < OscCode.FOREGROUND.getCode());
    }

    // ==================== TerminalType.supportsHyperlinks() Tests ====================

    @Test
    public void testHyperlinkSupportedTerminals() {
        // Terminals using EnumSet.allOf should support hyperlinks
        assertTrue(TerminalType.ITERM2.supportsHyperlinks());
        assertTrue(TerminalType.KITTY.supportsHyperlinks());
        assertTrue(TerminalType.GHOSTTY.supportsHyperlinks());
        assertTrue(TerminalType.WEZTERM.supportsHyperlinks());
        assertTrue(TerminalType.FOOT.supportsHyperlinks());
        assertTrue(TerminalType.CONTOUR.supportsHyperlinks());
        assertTrue(TerminalType.RIO.supportsHyperlinks());
        assertTrue(TerminalType.WARP.supportsHyperlinks());
        assertTrue(TerminalType.WAVE.supportsHyperlinks());
        assertTrue(TerminalType.HYPER.supportsHyperlinks());
        assertTrue(TerminalType.TABBY.supportsHyperlinks());
        assertTrue(TerminalType.EXTRATERM.supportsHyperlinks());
        assertTrue(TerminalType.GNOME_TERMINAL.supportsHyperlinks());
        assertTrue(TerminalType.KONSOLE.supportsHyperlinks());
        assertTrue(TerminalType.MINTTY.supportsHyperlinks());
        assertTrue(TerminalType.XTERM.supportsHyperlinks());

        // Explicitly added terminals
        assertTrue(TerminalType.JETBRAINS.supportsHyperlinks());
        assertTrue(TerminalType.VSCODE.supportsHyperlinks());
        assertTrue(TerminalType.ALACRITTY.supportsHyperlinks());
        assertTrue(TerminalType.WINDOWS_TERMINAL.supportsHyperlinks());
    }

    @Test
    public void testHyperlinkUnsupportedTerminals() {
        // Terminals that should NOT support hyperlinks
        assertFalse(TerminalType.APPLE_TERMINAL.supportsHyperlinks());
        assertFalse(TerminalType.RXVT.supportsHyperlinks());
        assertFalse(TerminalType.CONEMU.supportsHyperlinks());
        assertFalse(TerminalType.TMUX.supportsHyperlinks());
        assertFalse(TerminalType.SCREEN.supportsHyperlinks());
        assertFalse(TerminalType.LINUX_CONSOLE.supportsHyperlinks());
        assertFalse(TerminalType.UNKNOWN.supportsHyperlinks());
    }

    // ==================== Parser.stripAwayAnsiCodes() Tests ====================

    @Test
    public void testStripOsc8HyperlinksPreservesVisibleText() {
        String hyperlink = ANSI.hyperlink("https://example.com", "click here");
        String stripped = Parser.stripAwayAnsiCodes(hyperlink);
        assertEquals("click here", stripped);
    }

    @Test
    public void testStripOsc8HyperlinkWithId() {
        String hyperlink = ANSI.hyperlink("https://example.com", "link text", "id1");
        String stripped = Parser.stripAwayAnsiCodes(hyperlink);
        assertEquals("link text", stripped);
    }

    @Test
    public void testStripOsc8MixedWithCsi() {
        // Text with both CSI (bold) and OSC 8 (hyperlink) sequences
        String text = ANSI.BOLD + ANSI.hyperlink("https://example.com", "link") + ANSI.RESET;
        String stripped = Parser.stripAwayAnsiCodes(text);
        assertEquals("link", stripped);
    }

    @Test
    public void testStripCsiStillWorks() {
        // Regression: CSI sequences should still be stripped correctly
        String text = "\u001B[0;31mred text\u001B[0m";
        String stripped = Parser.stripAwayAnsiCodes(text);
        assertEquals("red text", stripped);
    }

    @Test
    public void testStripPlainTextUnchanged() {
        String text = "plain text with no escapes";
        String stripped = Parser.stripAwayAnsiCodes(text);
        assertEquals(text, stripped);
    }

    @Test
    public void testStripOsc8WithBelTerminator() {
        // OSC 8 with BEL (\x07) terminator instead of ST
        String start = "\u001B]8;;https://example.com\u0007";
        String end = "\u001B]8;;\u0007";
        String text = start + "visible" + end;
        String stripped = Parser.stripAwayAnsiCodes(text);
        assertEquals("visible", stripped);
    }

    @Test
    public void testStripMultipleHyperlinks() {
        String link1 = ANSI.hyperlink("https://one.com", "first");
        String link2 = ANSI.hyperlink("https://two.com", "second");
        String text = link1 + " and " + link2;
        String stripped = Parser.stripAwayAnsiCodes(text);
        assertEquals("first and second", stripped);
    }

    // ==================== Sanitization Tests ====================

    @Test
    public void testUrlWithEscIsStripped() {
        String result = ANSI.buildHyperlinkStart("https://example.com/\u001Bmalicious", null);
        // ESC should be removed from the URL
        assertFalse(result.contains("\u001Bmalicious"));
        assertTrue(result.contains("https://example.com/malicious"));
    }

    @Test
    public void testUrlWithBelIsStripped() {
        String result = ANSI.buildHyperlinkStart("https://example.com/\u0007bad", null);
        // BEL should be removed from the URL
        assertFalse(result.contains("\u0007bad"));
        assertTrue(result.contains("https://example.com/bad"));
    }

    @Test
    public void testIdWithControlCharsIsStripped() {
        String result = ANSI.buildHyperlinkStart("https://example.com", "my\u001Bid");
        assertTrue(result.contains("id=myid"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNullUrlThrows() {
        ANSI.buildHyperlinkStart(null, null);
    }

    // ==================== ANSI Constants Tests ====================

    @Test
    public void testOscHyperlinkConstant() {
        assertEquals(8, ANSI.OSC_HYPERLINK);
    }
}
