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

import static org.aesh.terminal.utils.ANSIBuilder.Color.*;
import static org.junit.Assert.assertEquals;

import org.aesh.terminal.utils.ANSIBuilder.TextType;
import org.junit.Test;

/**
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class ANSIBuilderTest {

    private static final String COLOR_START = "\u001B[";
    private static final String RESET = "\u001B[0m";

    @Test
    public void testAnsiBuilder() {
        ANSIBuilder builder = ANSIBuilder.builder();

        // New simpler format: only outputs non-default values
        assertEquals(COLOR_START + YELLOW.text() + "m" + "FOO" + RESET,
                builder.yellowText().append("FOO").toString());

        builder.clear();
        assertEquals(COLOR_START + YELLOW.text() + "m" + "FOO" + RESET,
                builder.yellowText("FOO").toString());

        builder.clear();
        assertEquals("FOO" + COLOR_START + BLUE.bg() + "m" + " BAR" + RESET,
                builder.append("FOO").resetColors().blueBg().append(" BAR").toString());

        builder.clear();
        assertEquals("FOO" + COLOR_START + BLUE.bg() + "m" + " BAR" + RESET,
                builder.append("FOO").resetColors().blueBg(" BAR").toString());

        builder.clear();
        assertEquals(COLOR_START + TextType.BOLD.value() + "mFOO" +
                COLOR_START + TextType.BOLD_OFF.value() + "m BAR" + RESET,
                builder.bold("FOO").append(' ').append("BAR").toString());

        builder = ANSIBuilder.builder(false);
        assertEquals("FOO BAR", builder.bold("FOO").append(' ').blackBg("BAR").toString());
    }

    // ==================== Extended Color Tests ====================

    @Test
    public void testBrightColors() {
        ANSIBuilder builder = ANSIBuilder.builder();

        // Bright red should use code 91 (30 + 60 = 90 for black, 31 + 60 = 91 for red)
        String result = builder.bright().redText().append("Bright").toString();
        assertEquals(COLOR_START + "91mBright" + RESET, result);

        builder.clear();
        // Bright with background
        result = builder.bright().greenBg().append("BrightBg").toString();
        assertEquals(COLOR_START + "102mBrightBg" + RESET, result);
    }

    @Test
    public void testColor256() {
        ANSIBuilder builder = ANSIBuilder.builder();

        // 256-color foreground: ESC[38;5;208m
        String result = builder.color256(208).append("Orange").toString();
        assertEquals(COLOR_START + "38;5;208mOrange" + RESET, result);

        builder.clear();
        // 256-color background: ESC[48;5;21m
        result = builder.bg256(21).append("BlueBg").toString();
        assertEquals(COLOR_START + "48;5;21mBlueBg" + RESET, result);

        builder.clear();
        // Convenience method with text
        result = builder.color256(196, "Red256").toString();
        assertEquals(COLOR_START + "38;5;196mRed256" + RESET, result);
    }

    @Test
    public void testTrueColorRgb() {
        ANSIBuilder builder = ANSIBuilder.builder();

        // RGB foreground: ESC[38;2;255;100;50m
        String result = builder.rgb(255, 100, 50).append("Custom").toString();
        assertEquals(COLOR_START + "38;2;255;100;50mCustom" + RESET, result);

        builder.clear();
        // RGB background: ESC[48;2;30;30;30m
        result = builder.bgRgb(30, 30, 30).append("DarkBg").toString();
        assertEquals(COLOR_START + "48;2;30;30;30mDarkBg" + RESET, result);

        builder.clear();
        // Convenience method with text
        result = builder.rgb(0, 128, 255, "BlueGradient").toString();
        assertEquals(COLOR_START + "38;2;0;128;255mBlueGradient" + RESET, result);
    }

    @Test
    public void testHexColors() {
        ANSIBuilder builder = ANSIBuilder.builder();

        // Hex foreground
        String result = builder.hex("#FF5733").append("Orange").toString();
        assertEquals(COLOR_START + "38;2;255;87;51mOrange" + RESET, result);

        builder.clear();
        // Hex without hash
        result = builder.hex("00FF00").append("Green").toString();
        assertEquals(COLOR_START + "38;2;0;255;0mGreen" + RESET, result);

        builder.clear();
        // Hex background
        result = builder.bgHex("#1E1E1E").append("Dark").toString();
        assertEquals(COLOR_START + "48;2;30;30;30mDark" + RESET, result);
    }

    @Test
    public void testSemanticColorsWithoutCapability() {
        // Without capability, should use bright colors (dark theme default)
        ANSIBuilder builder = ANSIBuilder.builder();

        assertEquals(COLOR_START + "91mError" + RESET, builder.error("Error").toString());
        builder.clear();
        assertEquals(COLOR_START + "92mSuccess" + RESET, builder.success("Success").toString());
        builder.clear();
        assertEquals(COLOR_START + "93mWarning" + RESET, builder.warning("Warning").toString());
        builder.clear();
        assertEquals(COLOR_START + "94mInfo" + RESET, builder.info("Info").toString());
        builder.clear();
        assertEquals(COLOR_START + "96mTimestamp" + RESET, builder.timestamp("Timestamp").toString());
        builder.clear();
        assertEquals(COLOR_START + "95mMessage" + RESET, builder.message("Message").toString());
    }

    @Test
    public void testSemanticColorsWithDarkTheme() {
        TerminalColorCapability darkCap = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.DARK);
        ANSIBuilder builder = ANSIBuilder.builder(darkCap);

        // Dark theme should use bright colors
        assertEquals(COLOR_START + "91mError" + RESET, builder.error("Error").toString());
        builder.clear();
        assertEquals(COLOR_START + "92mSuccess" + RESET, builder.success("Success").toString());
        builder.clear();
        assertEquals(COLOR_START + "96mTimestamp" + RESET, builder.timestamp("Timestamp").toString());
        builder.clear();
        assertEquals(COLOR_START + "95mMessage" + RESET, builder.message("Message").toString());
    }

    @Test
    public void testSemanticColorsWithLightTheme() {
        TerminalColorCapability lightCap = new TerminalColorCapability(
                ColorDepth.COLORS_256, TerminalTheme.LIGHT);
        ANSIBuilder builder = ANSIBuilder.builder(lightCap);

        // Light theme should use darker colors
        assertEquals(COLOR_START + "31mError" + RESET, builder.error("Error").toString());
        builder.clear();
        assertEquals(COLOR_START + "32mSuccess" + RESET, builder.success("Success").toString());
        builder.clear();
        assertEquals(COLOR_START + "36mTimestamp" + RESET, builder.timestamp("Timestamp").toString());
        builder.clear();
        assertEquals(COLOR_START + "35mMessage" + RESET, builder.message("Message").toString());
    }

    @Test
    public void testCombinedFeatures() {
        ANSIBuilder builder = ANSIBuilder.builder();

        // Combine style with color
        String result = builder.bold().error("Bold Error").toString();
        assertEquals(COLOR_START + "1;91mBold Error" + RESET, result);

        builder.clear();
        // Multiple segments
        result = builder
                .timestamp("10:30:45").append(" ")
                .success("[INFO]").append(" ")
                .message("Started")
                .toString();
        assertEquals(
                COLOR_START + "96m10:30:45" + RESET + " " +
                        COLOR_START + "92m[INFO]" + RESET + " " +
                        COLOR_START + "95mStarted" + RESET,
                result);
    }

    @Test
    public void testRawColorCodes() {
        ANSIBuilder builder = ANSIBuilder.builder();

        // Direct ANSI code
        String result = builder.textCode(91).append("BrightRed").toString();
        assertEquals(COLOR_START + "91mBrightRed" + RESET, result);

        builder.clear();
        result = builder.bgCode(44).append("BlueBg").toString();
        assertEquals(COLOR_START + "44mBlueBg" + RESET, result);
    }

    @Test
    public void testNoColorCapability() {
        // When capability reports NO_COLOR, ANSI should be disabled
        TerminalColorCapability noColorCap = new TerminalColorCapability(ColorDepth.NO_COLOR);
        ANSIBuilder builder = ANSIBuilder.builder(noColorCap);

        // Should produce plain text without escape codes
        assertEquals("Error", builder.error("Error").toString());
        builder.clear();
        assertEquals("Bold", builder.bold("Bold").toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testColor256OutOfRange() {
        ANSIBuilder.builder().color256(256);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRgbOutOfRange() {
        ANSIBuilder.builder().rgb(300, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidHex() {
        ANSIBuilder.builder().hex("invalid");
    }

    @Test
    public void testToLine() {
        ANSIBuilder builder = ANSIBuilder.builder();

        // toLine() should append a newline after the formatted string
        String result = builder.redText("Error").toLine();
        assertEquals(COLOR_START + "31mError" + RESET + System.lineSeparator(), result);

        builder.reset();
        // bold("Title") sets BOLD then text then BOLD_OFF, but toLine() resets before BOLD_OFF is printed
        result = builder.bold("Title").toLine();
        assertEquals(COLOR_START + "1mTitle" + RESET + System.lineSeparator(), result);
    }

    @Test
    public void testAppendLine() {
        ANSIBuilder builder = ANSIBuilder.builder();

        // appendLine() should append text followed by newline, returning builder for chaining
        String result = builder.appendLine("First").appendLine("Second").toString();
        String nl = System.lineSeparator();
        assertEquals("First" + nl + "Second" + nl, result);

        builder.reset();
        // Combined with colors
        result = builder.redText().appendLine("Error line").toString();
        assertEquals(COLOR_START + "31mError line" + nl + RESET, result);
    }
}
