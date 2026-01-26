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

import org.junit.Test;

/**
 * Tests for TerminalColorCapability class.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class TerminalColorCapabilityTest {

    @Test
    public void testDefaultCapability() {
        TerminalColorCapability cap = TerminalColorCapability.defaultCapability();
        assertEquals(ColorDepth.COLORS_8, cap.getColorDepth());
        assertEquals(TerminalTheme.DARK, cap.getTheme());
        assertNull(cap.getForegroundRGB());
        assertNull(cap.getBackgroundRGB());
        assertFalse(cap.hasForegroundColor());
        assertFalse(cap.hasBackgroundColor());
    }

    @Test
    public void testConstructorWithAllParams() {
        int[] fg = { 255, 255, 255 };
        int[] bg = { 0, 0, 0 };
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.DARK, fg, bg);

        assertEquals(ColorDepth.COLORS_256, cap.getColorDepth());
        assertEquals(TerminalTheme.DARK, cap.getTheme());
        assertTrue(cap.hasForegroundColor());
        assertTrue(cap.hasBackgroundColor());
        assertArrayEquals(fg, cap.getForegroundRGB());
        assertArrayEquals(bg, cap.getBackgroundRGB());
    }

    @Test
    public void testConstructorWithDepthAndTheme() {
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.TRUE_COLOR, TerminalTheme.LIGHT);

        assertEquals(ColorDepth.TRUE_COLOR, cap.getColorDepth());
        assertEquals(TerminalTheme.LIGHT, cap.getTheme());
        assertFalse(cap.hasForegroundColor());
        assertFalse(cap.hasBackgroundColor());
    }

    @Test
    public void testConstructorWithDepthOnly() {
        TerminalColorCapability cap = new TerminalColorCapability(ColorDepth.COLORS_16);

        assertEquals(ColorDepth.COLORS_16, cap.getColorDepth());
        assertEquals(TerminalTheme.UNKNOWN, cap.getTheme());
    }

    @Test
    public void testConstructorNullDefaults() {
        TerminalColorCapability cap = new TerminalColorCapability(null, null, null, null);

        assertEquals(ColorDepth.COLORS_8, cap.getColorDepth());
        assertEquals(TerminalTheme.UNKNOWN, cap.getTheme());
    }

    @Test
    public void testSupportsAnsiColors() {
        assertTrue(new TerminalColorCapability(ColorDepth.COLORS_8).supportsAnsiColors());
        assertTrue(new TerminalColorCapability(ColorDepth.COLORS_256).supportsAnsiColors());
        assertTrue(new TerminalColorCapability(ColorDepth.TRUE_COLOR).supportsAnsiColors());
        assertFalse(new TerminalColorCapability(ColorDepth.NO_COLOR).supportsAnsiColors());
    }

    @Test
    public void testSuggestedColorsForDarkTheme() {
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.DARK);

        assertEquals(37, cap.getSuggestedForegroundCode()); // white for dark bg
        assertEquals(91, cap.getSuggestedErrorCode()); // bright red for dark bg
        assertEquals(92, cap.getSuggestedSuccessCode()); // bright green for dark bg
        assertEquals(93, cap.getSuggestedWarningCode()); // bright yellow for dark bg
        assertEquals(94, cap.getSuggestedInfoCode()); // bright blue for dark bg
    }

    @Test
    public void testSuggestedColorsForLightTheme() {
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.LIGHT);

        assertEquals(30, cap.getSuggestedForegroundCode()); // black for light bg
        assertEquals(31, cap.getSuggestedErrorCode()); // dark red for light bg
        assertEquals(32, cap.getSuggestedSuccessCode()); // dark green for light bg
        assertEquals(33, cap.getSuggestedWarningCode()); // dark yellow for light bg
        assertEquals(34, cap.getSuggestedInfoCode()); // dark blue for light bg
    }

    @Test
    public void testSuggestedColorsForUnknownTheme() {
        // UNKNOWN should default to dark theme behavior
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.COLORS_8, TerminalTheme.UNKNOWN);

        assertEquals(37, cap.getSuggestedForegroundCode()); // white (dark theme default)
        assertEquals(91, cap.getSuggestedErrorCode()); // bright red
    }

    @Test
    public void testRGBArraysAreDefensiveCopies() {
        int[] fg = { 255, 255, 255 };
        int[] bg = { 0, 0, 0 };
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.DARK, fg, bg);

        // Modify the returned arrays
        int[] returnedFg = cap.getForegroundRGB();
        int[] returnedBg = cap.getBackgroundRGB();
        returnedFg[0] = 100;
        returnedBg[0] = 100;

        // Original values should be unchanged
        assertArrayEquals(new int[] { 255, 255, 255 }, cap.getForegroundRGB());
        assertArrayEquals(new int[] { 0, 0, 0 }, cap.getBackgroundRGB());
    }

    @Test
    public void testToString() {
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.DARK,
                new int[] { 255, 255, 255 }, new int[] { 0, 0, 0 });

        String str = cap.toString();
        assertTrue(str.contains("COLORS_256"));
        assertTrue(str.contains("DARK"));
        assertTrue(str.contains("#FFFFFF")); // foreground hex
        assertTrue(str.contains("#000000")); // background hex
    }

    @Test
    public void testForegroundHex() {
        int[] fg = { 255, 87, 51 }; // #FF5733
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.TRUE_COLOR, TerminalTheme.DARK, fg, null);

        assertEquals("#FF5733", cap.getForegroundHex());
        assertNull(cap.getBackgroundHex());
    }

    @Test
    public void testBackgroundHex() {
        int[] bg = { 30, 30, 30 }; // #1E1E1E
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.TRUE_COLOR, TerminalTheme.DARK, null, bg);

        assertNull(cap.getForegroundHex());
        assertEquals("#1E1E1E", cap.getBackgroundHex());
    }

    @Test
    public void testForegroundHexValue() {
        int[] fg = { 255, 87, 51 }; // 0xFF5733
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.TRUE_COLOR, TerminalTheme.DARK, fg, null);

        assertEquals(0xFF5733, cap.getForegroundHexValue());
        assertEquals(-1, cap.getBackgroundHexValue());
    }

    @Test
    public void testBackgroundHexValue() {
        int[] bg = { 30, 30, 30 }; // 0x1E1E1E
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.TRUE_COLOR, TerminalTheme.DARK, null, bg);

        assertEquals(-1, cap.getForegroundHexValue());
        assertEquals(0x1E1E1E, cap.getBackgroundHexValue());
    }

    @Test
    public void testHexToRgbString() {
        // With hash
        assertArrayEquals(new int[] { 255, 87, 51 }, TerminalColorCapability.hexToRgb("#FF5733"));
        // Without hash
        assertArrayEquals(new int[] { 255, 87, 51 }, TerminalColorCapability.hexToRgb("FF5733"));
        // Lowercase
        assertArrayEquals(new int[] { 255, 87, 51 }, TerminalColorCapability.hexToRgb("ff5733"));
        // Shorthand format
        assertArrayEquals(new int[] { 255, 255, 255 }, TerminalColorCapability.hexToRgb("#FFF"));
        assertArrayEquals(new int[] { 0, 0, 0 }, TerminalColorCapability.hexToRgb("000"));
        // Black and white
        assertArrayEquals(new int[] { 0, 0, 0 }, TerminalColorCapability.hexToRgb("#000000"));
        assertArrayEquals(new int[] { 255, 255, 255 }, TerminalColorCapability.hexToRgb("#FFFFFF"));
    }

    @Test
    public void testHexToRgbStringInvalid() {
        assertNull(TerminalColorCapability.hexToRgb(null));
        assertNull(TerminalColorCapability.hexToRgb(""));
        assertNull(TerminalColorCapability.hexToRgb("invalid"));
        assertNull(TerminalColorCapability.hexToRgb("#GG0000")); // invalid hex chars
        assertNull(TerminalColorCapability.hexToRgb("12345")); // wrong length
    }

    @Test
    public void testHexToRgbInt() {
        assertArrayEquals(new int[] { 255, 87, 51 }, TerminalColorCapability.hexToRgb(0xFF5733));
        assertArrayEquals(new int[] { 0, 0, 0 }, TerminalColorCapability.hexToRgb(0x000000));
        assertArrayEquals(new int[] { 255, 255, 255 }, TerminalColorCapability.hexToRgb(0xFFFFFF));
        assertArrayEquals(new int[] { 30, 30, 30 }, TerminalColorCapability.hexToRgb(0x1E1E1E));
    }

    @Test
    public void testHexRoundTrip() {
        // Test that we can convert from RGB to hex and back
        int[] original = { 128, 64, 192 };
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.TRUE_COLOR, TerminalTheme.DARK, original, null);

        String hex = cap.getForegroundHex();
        int[] converted = TerminalColorCapability.hexToRgb(hex);
        assertArrayEquals(original, converted);

        int hexValue = cap.getForegroundHexValue();
        int[] convertedFromInt = TerminalColorCapability.hexToRgb(hexValue);
        assertArrayEquals(original, convertedFromInt);
    }

    @Test
    public void testDetectFromEnvironment() {
        // This test just ensures the method doesn't throw
        TerminalColorCapability cap = TerminalColorCapability.detectFromEnvironment();
        assertNotNull(cap);
        assertNotNull(cap.getColorDepth());
        assertNotNull(cap.getTheme());
    }
}
