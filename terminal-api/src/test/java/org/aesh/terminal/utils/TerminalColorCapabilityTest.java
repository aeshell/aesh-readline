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
        assertEquals(92, cap.getSuggestedInfoCode()); // bright green for dark bg
        assertEquals(96, cap.getSuggestedDebugCode()); // bright cyan for dark bg
        assertEquals(90, cap.getSuggestedTraceCode()); // gray for all themes
        assertEquals(252, cap.getSuggestedTimestampCode()); // light gray for dark bg (~rgb 208,208,208)
        assertEquals(37, cap.getSuggestedMessageCode()); // white for dark bg
        assertEquals(94, cap.getSuggestedCategoryCode()); // bright blue for dark bg
        assertEquals(92, cap.getSuggestedThreadNameCode()); // bright green for dark bg
        assertEquals(91, cap.getSuggestedFatalCode()); // bright red for dark bg
    }

    @Test
    public void testSuggestedColorsForLightTheme() {
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.LIGHT);

        assertEquals(30, cap.getSuggestedForegroundCode()); // black for light bg
        assertEquals(31, cap.getSuggestedErrorCode()); // dark red for light bg
        assertEquals(32, cap.getSuggestedSuccessCode()); // dark green for light bg
        assertEquals(33, cap.getSuggestedWarningCode()); // dark yellow for light bg
        assertEquals(32, cap.getSuggestedInfoCode()); // dark green for light bg
        assertEquals(36, cap.getSuggestedDebugCode()); // dark cyan for light bg
        assertEquals(90, cap.getSuggestedTraceCode()); // gray for all themes
        assertEquals(240, cap.getSuggestedTimestampCode()); // dark gray for light bg (~rgb 88,88,88)
        assertEquals(30, cap.getSuggestedMessageCode()); // black for light bg
        assertEquals(34, cap.getSuggestedCategoryCode()); // dark blue for light bg
        assertEquals(32, cap.getSuggestedThreadNameCode()); // dark green for light bg
        assertEquals(31, cap.getSuggestedFatalCode()); // dark red for light bg
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

    @Test
    public void testSuggestedTimestampAndMessageCodesExample() {
        // Example: Colorizing log output with timestamps and messages
        // This demonstrates how to use the suggested color codes for log formatting

        // Detect terminal capabilities (or use a known configuration)
        TerminalColorCapability darkTerminal = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.DARK);
        TerminalColorCapability lightTerminal = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.LIGHT);

        // Build ANSI escape sequences for a dark terminal
        String timestamp = "2024-01-15 10:30:45";
        String message = "Application started successfully";

        int tsCode = darkTerminal.getSuggestedTimestampCode();
        int msgCode = darkTerminal.getSuggestedMessageCode();

        // Format: ESC[<code>m<text>ESC[0m
        String coloredTimestamp = "\u001B[" + tsCode + "m" + timestamp + "\u001B[0m";
        String coloredMessage = "\u001B[" + msgCode + "m" + message + "\u001B[0m";

        // Verify the codes are correct for dark theme
        assertEquals(252, tsCode); // light gray (~rgb 208,208,208)
        assertEquals(37, msgCode); // white

        // Verify the formatted strings contain the expected escape sequences
        assertTrue(coloredTimestamp.contains("\u001B[252m"));
        assertTrue(coloredMessage.contains("\u001B[37m"));

        // For light terminal, codes should be different (darker variants)
        assertEquals(240, lightTerminal.getSuggestedTimestampCode()); // dark gray (~rgb 88,88,88)
        assertEquals(30, lightTerminal.getSuggestedMessageCode()); // black
    }

    @Test
    public void testSuggestedDebugAndTraceCodesExample() {
        // Example: Colorizing log output with DEBUG and TRACE levels
        // This demonstrates how to use the suggested color codes for debug/trace formatting

        // Detect terminal capabilities (or use a known configuration)
        TerminalColorCapability darkTerminal = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.DARK);
        TerminalColorCapability lightTerminal = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.LIGHT);

        // Build ANSI escape sequences for a dark terminal
        String debugMsg = "Variable x = 42";
        String traceMsg = "Entering method foo()";

        int debugCode = darkTerminal.getSuggestedDebugCode();
        int traceCode = darkTerminal.getSuggestedTraceCode();

        // Verify the codes are correct for dark theme
        assertEquals(96, debugCode); // bright cyan
        assertEquals(90, traceCode); // gray for all themes

        // For light terminal, debug and trace use dark colors
        assertEquals(36, lightTerminal.getSuggestedDebugCode()); // dark cyan
        assertEquals(90, lightTerminal.getSuggestedTraceCode()); // gray (visible on light bg)

        // Log level color hierarchy follows JBoss LogManager spectrum (red to blue):
        // ERROR (91/31) > WARN (93/33) > INFO (92/32) > DEBUG (96/36) > TRACE (90)
        int errorCode = darkTerminal.getSuggestedErrorCode();
        int warnCode = darkTerminal.getSuggestedWarningCode();
        int infoCode = darkTerminal.getSuggestedInfoCode();

        // Verify the hierarchy - bright colors for dark theme
        assertEquals(91, errorCode); // bright red
        assertEquals(93, warnCode); // bright yellow
        assertEquals(92, infoCode); // bright green
        assertEquals(96, debugCode); // bright cyan
        assertEquals(90, traceCode); // gray (least prominent)
    }

    // ==================== Builder Tests ====================

    @Test
    public void testBuilderBasic() {
        TerminalColorCapability cap = TerminalColorCapability.builder()
                .colorDepth(ColorDepth.COLORS_256)
                .theme(TerminalTheme.DARK)
                .build();

        assertEquals(ColorDepth.COLORS_256, cap.getColorDepth());
        assertEquals(TerminalTheme.DARK, cap.getTheme());
        // Should still use theme-based defaults when no overrides set
        assertEquals(91, cap.getSuggestedErrorCode()); // bright red for dark
    }

    @Test
    public void testBuilderWithColorOverrides() {
        TerminalColorCapability cap = TerminalColorCapability.builder()
                .colorDepth(ColorDepth.COLORS_256)
                .theme(TerminalTheme.DARK)
                .errorCode(196) // Custom 256-color red
                .successCode(46) // Custom 256-color green
                .warningCode(208) // Custom 256-color orange
                .infoCode(39) // Custom 256-color blue
                .debugCode(250) // Custom 256-color light gray
                .traceCode(240) // Custom 256-color dark gray
                .timestampCode(244) // Custom 256-color gray
                .messageCode(255) // Custom 256-color white
                .foregroundCode(252) // Custom 256-color light gray
                .categoryCode(75) // Custom 256-color blue
                .threadNameCode(78) // Custom 256-color green
                .fatalCode(160) // Custom 256-color dark red
                .build();

        // All should return the overridden values
        assertEquals(196, cap.getSuggestedErrorCode());
        assertEquals(46, cap.getSuggestedSuccessCode());
        assertEquals(208, cap.getSuggestedWarningCode());
        assertEquals(39, cap.getSuggestedInfoCode());
        assertEquals(250, cap.getSuggestedDebugCode());
        assertEquals(240, cap.getSuggestedTraceCode());
        assertEquals(244, cap.getSuggestedTimestampCode());
        assertEquals(255, cap.getSuggestedMessageCode());
        assertEquals(252, cap.getSuggestedForegroundCode());
        assertEquals(75, cap.getSuggestedCategoryCode());
        assertEquals(78, cap.getSuggestedThreadNameCode());
        assertEquals(160, cap.getSuggestedFatalCode());
    }

    @Test
    public void testBuilderFromExistingCapability() {
        // Start with a detected capability
        TerminalColorCapability detected = new TerminalColorCapability(
                ColorDepth.TRUE_COLOR, TerminalTheme.DARK,
                new int[] { 255, 255, 255 }, new int[] { 30, 30, 30 });

        // Create a custom capability based on it, overriding just error color
        TerminalColorCapability custom = TerminalColorCapability.builder(detected)
                .errorCode(196)
                .build();

        // Should preserve original values
        assertEquals(ColorDepth.TRUE_COLOR, custom.getColorDepth());
        assertEquals(TerminalTheme.DARK, custom.getTheme());
        assertTrue(custom.hasForegroundColor());
        assertTrue(custom.hasBackgroundColor());

        // Error should be overridden
        assertEquals(196, custom.getSuggestedErrorCode());

        // Other colors should still use theme-based defaults
        assertEquals(92, custom.getSuggestedSuccessCode()); // bright green for dark
        assertEquals(252, custom.getSuggestedTimestampCode()); // light gray for dark
    }

    @Test
    public void testBuilderOverridesIgnoreTheme() {
        // When colors are overridden, theme should not affect them
        TerminalColorCapability lightCap = TerminalColorCapability.builder()
                .theme(TerminalTheme.LIGHT)
                .errorCode(196)
                .build();

        TerminalColorCapability darkCap = TerminalColorCapability.builder()
                .theme(TerminalTheme.DARK)
                .errorCode(196)
                .build();

        // Both should return the same override value regardless of theme
        assertEquals(196, lightCap.getSuggestedErrorCode());
        assertEquals(196, darkCap.getSuggestedErrorCode());

        // But non-overridden colors should still respect theme
        assertEquals(32, lightCap.getSuggestedSuccessCode()); // dark green for light
        assertEquals(92, darkCap.getSuggestedSuccessCode()); // bright green for dark
    }

    @Test
    public void testBuilderWithRgbColors() {
        int[] fg = { 200, 200, 200 };
        int[] bg = { 40, 40, 40 };

        TerminalColorCapability cap = TerminalColorCapability.builder()
                .colorDepth(ColorDepth.TRUE_COLOR)
                .theme(TerminalTheme.DARK)
                .foregroundRGB(fg)
                .backgroundRGB(bg)
                .build();

        assertTrue(cap.hasForegroundColor());
        assertTrue(cap.hasBackgroundColor());
        assertArrayEquals(fg, cap.getForegroundRGB());
        assertArrayEquals(bg, cap.getBackgroundRGB());
    }

    @Test
    public void testBuilderPreservesOverridesWhenCopying() {
        // Create a capability with overrides
        TerminalColorCapability original = TerminalColorCapability.builder()
                .theme(TerminalTheme.DARK)
                .errorCode(196)
                .successCode(46)
                .build();

        // Copy it and add more overrides
        TerminalColorCapability copy = TerminalColorCapability.builder(original)
                .warningCode(208)
                .build();

        // Original overrides should be preserved
        assertEquals(196, copy.getSuggestedErrorCode());
        assertEquals(46, copy.getSuggestedSuccessCode());
        // New override should be added
        assertEquals(208, copy.getSuggestedWarningCode());
    }

    @Test
    public void testBuilderWithNullBase() {
        // builder(null) should work and use defaults
        TerminalColorCapability cap = TerminalColorCapability.builder(null)
                .errorCode(196)
                .build();

        // Should use default color depth and theme
        assertEquals(ColorDepth.COLORS_8, cap.getColorDepth());
        assertEquals(TerminalTheme.UNKNOWN, cap.getTheme());
        // Override should work
        assertEquals(196, cap.getSuggestedErrorCode());
    }

    @Test
    public void testSuggestedCategoryThreadNameFatalCodes() {
        // Dark theme
        TerminalColorCapability dark = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.DARK);
        assertEquals(94, dark.getSuggestedCategoryCode()); // bright blue
        assertEquals(92, dark.getSuggestedThreadNameCode()); // bright green
        assertEquals(91, dark.getSuggestedFatalCode()); // bright red

        // Light theme
        TerminalColorCapability light = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.LIGHT);
        assertEquals(34, light.getSuggestedCategoryCode()); // dark blue
        assertEquals(32, light.getSuggestedThreadNameCode()); // dark green
        assertEquals(31, light.getSuggestedFatalCode()); // dark red

        // Unknown theme (defaults to dark behavior)
        TerminalColorCapability unknown = new TerminalColorCapability(
                ColorDepth.COLORS_8, TerminalTheme.UNKNOWN);
        assertEquals(94, unknown.getSuggestedCategoryCode()); // bright blue
        assertEquals(92, unknown.getSuggestedThreadNameCode()); // bright green
        assertEquals(91, unknown.getSuggestedFatalCode()); // bright red
    }

    @Test
    public void testBuilderCategoryThreadNameFatalOverrides() {
        TerminalColorCapability cap = TerminalColorCapability.builder()
                .theme(TerminalTheme.LIGHT)
                .categoryCode(75)
                .threadNameCode(78)
                .fatalCode(160)
                .build();

        // Overrides should take precedence over theme
        assertEquals(75, cap.getSuggestedCategoryCode());
        assertEquals(78, cap.getSuggestedThreadNameCode());
        assertEquals(160, cap.getSuggestedFatalCode());
    }

    @Test
    public void testBuilderPreservesCategoryThreadNameFatalOverrides() {
        // Create a capability with category/threadName/fatal overrides
        TerminalColorCapability original = TerminalColorCapability.builder()
                .theme(TerminalTheme.DARK)
                .categoryCode(75)
                .threadNameCode(78)
                .fatalCode(160)
                .build();

        // Copy and add different overrides
        TerminalColorCapability copy = TerminalColorCapability.builder(original)
                .errorCode(196)
                .build();

        // Original overrides should be preserved
        assertEquals(75, copy.getSuggestedCategoryCode());
        assertEquals(78, copy.getSuggestedThreadNameCode());
        assertEquals(160, copy.getSuggestedFatalCode());
        // New override should be added
        assertEquals(196, copy.getSuggestedErrorCode());
    }
}
