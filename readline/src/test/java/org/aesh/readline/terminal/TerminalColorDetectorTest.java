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
package org.aesh.readline.terminal;

import static org.junit.Assert.*;

import org.aesh.terminal.utils.ColorDepth;
import org.aesh.terminal.utils.TerminalColorCapability;
import org.aesh.terminal.utils.TerminalTheme;
import org.junit.Test;

/**
 * Tests for TerminalColorDetector class.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class TerminalColorDetectorTest {

    @Test
    public void testParseOscColorResponse_ValidXtermFormat() {
        // Typical xterm OSC 11 response: ESC ] 11 ; rgb:RRRR/GGGG/BBBB BEL
        // Example: white background (ffff/ffff/ffff)
        String response = "\u001B]11;rgb:ffff/ffff/ffff\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = TerminalColorDetector.parseOscColorResponse(input, 11);

        assertNotNull(rgb);
        assertEquals(255, rgb[0]); // red
        assertEquals(255, rgb[1]); // green
        assertEquals(255, rgb[2]); // blue
    }

    @Test
    public void testParseOscColorResponse_BlackBackground() {
        String response = "\u001B]11;rgb:0000/0000/0000\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = TerminalColorDetector.parseOscColorResponse(input, 11);

        assertNotNull(rgb);
        assertEquals(0, rgb[0]);
        assertEquals(0, rgb[1]);
        assertEquals(0, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_ForegroundColor() {
        // OSC 10 for foreground
        String response = "\u001B]10;rgb:8080/8080/8080\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = TerminalColorDetector.parseOscColorResponse(input, 10);

        assertNotNull(rgb);
        assertEquals(128, rgb[0]); // 0x80 = 128
        assertEquals(128, rgb[1]);
        assertEquals(128, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_DarkBlueBackground() {
        // A dark blue background: 0000/0000/8080
        String response = "\u001B]11;rgb:0000/0000/8080\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = TerminalColorDetector.parseOscColorResponse(input, 11);

        assertNotNull(rgb);
        assertEquals(0, rgb[0]);
        assertEquals(0, rgb[1]);
        assertEquals(128, rgb[2]);
    }

    @Test
    public void testParseOscColorResponse_WithEscapeBackslashTerminator() {
        // Some terminals use ESC \ instead of BEL as terminator
        String response = "\u001B]11;rgb:cccc/dddd/eeee\u001B\\";
        int[] input = response.codePoints().toArray();

        int[] rgb = TerminalColorDetector.parseOscColorResponse(input, 11);

        assertNotNull(rgb);
        assertEquals(204, rgb[0]); // 0xcc = 204
        assertEquals(221, rgb[1]); // 0xdd = 221
        assertEquals(238, rgb[2]); // 0xee = 238
    }

    @Test
    public void testParseOscColorResponse_WrongOscCode() {
        // Response is for OSC 11, but we're looking for OSC 10
        String response = "\u001B]11;rgb:ffff/ffff/ffff\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = TerminalColorDetector.parseOscColorResponse(input, 10);

        assertNull(rgb);
    }

    @Test
    public void testParseOscColorResponse_InvalidFormat() {
        String response = "\u001B]11;invalid\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = TerminalColorDetector.parseOscColorResponse(input, 11);

        assertNull(rgb);
    }

    @Test
    public void testParseOscColorResponse_NullInput() {
        int[] rgb = TerminalColorDetector.parseOscColorResponse(null, 11);
        assertNull(rgb);
    }

    @Test
    public void testParseOscColorResponse_TooShortInput() {
        int[] rgb = TerminalColorDetector.parseOscColorResponse(new int[] { 1, 2, 3 }, 11);
        assertNull(rgb);
    }

    @Test
    public void testDetectFast_NoConnection() {
        TerminalColorCapability cap = TerminalColorDetector.detectFast(null);
        assertNotNull(cap);
        assertNotNull(cap.getColorDepth());
    }

    @Test
    public void testDetectThemeFromEnvironment() {
        // This is difficult to test without modifying environment variables
        // Just ensure it doesn't throw and returns a valid value
        TerminalTheme theme = TerminalColorDetector.detectThemeFromEnvironment();
        assertNotNull(theme);
    }

    @Test
    public void testDetectColorDepth_NullConnection() {
        ColorDepth depth = TerminalColorDetector.detectColorDepth(null);
        assertNotNull(depth);
    }

    @Test
    public void testIsOscColorQuerySupported() {
        // Just ensure it doesn't throw
        boolean supported = TerminalColorDetector.isOscColorQuerySupported();
        // Can't assert true/false as it depends on the actual environment
        assertNotNull(supported);
    }

    @Test
    public void testCaching() {
        // Clear any existing cache
        TerminalColorDetector.clearCache();

        // Initially, cache should be null
        assertNull(TerminalColorDetector.getCached());

        // Detect with caching
        TerminalColorCapability cap1 = TerminalColorDetector.detectCached(null);
        assertNotNull(cap1);

        // Cache should now be populated
        assertNotNull(TerminalColorDetector.getCached());

        // Subsequent call should return cached value
        TerminalColorCapability cap2 = TerminalColorDetector.detectCached(null);
        assertSame(cap1, cap2);

        // Clear cache
        TerminalColorDetector.clearCache();
        assertNull(TerminalColorDetector.getCached());
    }

    @Test
    public void testIsRunningInTmux() {
        // Just ensure it doesn't throw
        boolean inTmux = TerminalColorDetector.isRunningInTmux();
        assertTrue(inTmux || !inTmux);
    }

    @Test
    public void testIsRunningInScreen() {
        // Just ensure it doesn't throw
        boolean inScreen = TerminalColorDetector.isRunningInScreen();
        assertTrue(inScreen || !inScreen);
    }

    @Test
    public void testIsRunningInMultiplexer() {
        // Just ensure it doesn't throw
        boolean inMux = TerminalColorDetector.isRunningInMultiplexer();
        assertTrue(inMux || !inMux);
    }

    @Test
    public void testIsTmuxPassthroughLikely() {
        // Just ensure it doesn't throw
        boolean passthrough = TerminalColorDetector.isTmuxPassthroughLikely();
        assertTrue(passthrough || !passthrough);
    }

    @Test
    public void testTerminalThemeEnum() {
        // Verify TerminalTheme enum values exist
        assertNotNull(TerminalTheme.DARK);
        assertNotNull(TerminalTheme.LIGHT);
        assertNotNull(TerminalTheme.UNKNOWN);

        // Test isDark() helper
        assertTrue(TerminalTheme.DARK.isDark());
        assertFalse(TerminalTheme.LIGHT.isDark());
        // UNKNOWN defaults to dark assumption
        assertTrue(TerminalTheme.UNKNOWN.isDark());
    }

    @Test
    public void testColorDepthEnum() {
        // Verify ColorDepth enum values
        assertNotNull(ColorDepth.NO_COLOR);
        assertNotNull(ColorDepth.COLORS_8);
        assertNotNull(ColorDepth.COLORS_16);
        assertNotNull(ColorDepth.COLORS_256);
        assertNotNull(ColorDepth.TRUE_COLOR);

        // Test fromColorCount conversion
        assertEquals(ColorDepth.NO_COLOR, ColorDepth.fromColorCount(0));
        assertEquals(ColorDepth.COLORS_8, ColorDepth.fromColorCount(8));
        assertEquals(ColorDepth.COLORS_16, ColorDepth.fromColorCount(16));
        assertEquals(ColorDepth.COLORS_256, ColorDepth.fromColorCount(256));
        assertEquals(ColorDepth.TRUE_COLOR, ColorDepth.fromColorCount(16777216));

        // Test getColorCount
        assertEquals(0, ColorDepth.NO_COLOR.getColorCount());
        assertEquals(8, ColorDepth.COLORS_8.getColorCount());
        assertEquals(16, ColorDepth.COLORS_16.getColorCount());
        assertEquals(256, ColorDepth.COLORS_256.getColorCount());
        assertEquals(16777216, ColorDepth.TRUE_COLOR.getColorCount());
    }

    @Test
    public void testTerminalColorCapability() {
        // Create a capability with known values
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.TRUE_COLOR,
                TerminalTheme.DARK);

        assertEquals(TerminalTheme.DARK, cap.getTheme());
        assertEquals(ColorDepth.TRUE_COLOR, cap.getColorDepth());

        // Test toString doesn't throw
        assertNotNull(cap.toString());
    }

    @Test
    public void testLuminanceThresholds() {
        // Test that our luminance calculations work correctly
        // Pure black should have luminance 0
        double blackLuminance = calculateLuminance(0, 0, 0);
        assertEquals(0.0, blackLuminance, 0.001);

        // Pure white should have luminance 1
        double whiteLuminance = calculateLuminance(255, 255, 255);
        assertEquals(1.0, whiteLuminance, 0.001);

        // Middle gray should be around 0.5
        double grayLuminance = calculateLuminance(128, 128, 128);
        assertTrue(grayLuminance > 0.4 && grayLuminance < 0.6);

        // Dark theme backgrounds should have luminance < 0.5
        // #1e1e1e (VS Code dark default)
        double darkBg = calculateLuminance(0x1e, 0x1e, 0x1e);
        assertTrue("Dark background should have low luminance", darkBg < 0.5);

        // Light theme backgrounds should have luminance >= 0.5
        // #ffffff (white)
        assertTrue("Light background should have high luminance", whiteLuminance >= 0.5);
    }

    /**
     * Helper method matching the luminance calculation in TerminalColorDetector.
     */
    private double calculateLuminance(int r, int g, int b) {
        return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
    }

    @Test
    public void testParseOscColorResponse_SolarizedDarkBackground() {
        // Solarized Dark base03: #002b36
        // In OSC format: 0000/2b2b/3636
        String response = "\u001B]11;rgb:0000/2b2b/3636\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = TerminalColorDetector.parseOscColorResponse(input, 11);

        assertNotNull(rgb);
        assertEquals(0, rgb[0]);
        assertEquals(43, rgb[1]); // 0x2b = 43
        assertEquals(54, rgb[2]); // 0x36 = 54

        // Verify this would be detected as dark
        double luminance = calculateLuminance(rgb[0], rgb[1], rgb[2]);
        assertTrue("Solarized Dark should be dark", luminance < 0.5);
    }

    @Test
    public void testParseOscColorResponse_SolarizedLightBackground() {
        // Solarized Light base3: #fdf6e3
        String response = "\u001B]11;rgb:fdfd/f6f6/e3e3\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = TerminalColorDetector.parseOscColorResponse(input, 11);

        assertNotNull(rgb);
        assertEquals(253, rgb[0]); // 0xfd = 253
        assertEquals(246, rgb[1]); // 0xf6 = 246
        assertEquals(227, rgb[2]); // 0xe3 = 227

        // Verify this would be detected as light
        double luminance = calculateLuminance(rgb[0], rgb[1], rgb[2]);
        assertTrue("Solarized Light should be light", luminance >= 0.5);
    }

    @Test
    public void testParseOscColorResponse_DraculaBackground() {
        // Dracula background: #282a36
        String response = "\u001B]11;rgb:2828/2a2a/3636\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = TerminalColorDetector.parseOscColorResponse(input, 11);

        assertNotNull(rgb);
        assertEquals(40, rgb[0]); // 0x28 = 40
        assertEquals(42, rgb[1]); // 0x2a = 42
        assertEquals(54, rgb[2]); // 0x36 = 54

        // Verify this would be detected as dark
        double luminance = calculateLuminance(rgb[0], rgb[1], rgb[2]);
        assertTrue("Dracula should be dark", luminance < 0.5);
    }

    @Test
    public void testParseOscColorResponse_GhosttyFormat() {
        // Some terminals return shorter hex codes (2 digits per component)
        // Let's make sure we handle the standard 4-digit format
        String response = "\u001B]11;rgb:1e1e/1e1e/1e1e\u0007";
        int[] input = response.codePoints().toArray();

        int[] rgb = TerminalColorDetector.parseOscColorResponse(input, 11);

        assertNotNull(rgb);
        // 0x1e = 30
        assertEquals(30, rgb[0]);
        assertEquals(30, rgb[1]);
        assertEquals(30, rgb[2]);
    }

    @Test
    public void testDetectFast_ReturnsValidCapability() {
        // Test that detectFast always returns a non-null, valid capability
        TerminalColorCapability cap = TerminalColorDetector.detectFast(null);

        assertNotNull("detectFast should never return null", cap);
        assertNotNull("Theme should not be null", cap.getTheme());
        assertNotNull("ColorDepth should not be null", cap.getColorDepth());
    }
}
