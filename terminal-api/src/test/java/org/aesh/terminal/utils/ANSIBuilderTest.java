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
import static org.junit.Assert.assertTrue;

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
        assertEquals(COLOR_START + "92mInfo" + RESET, builder.info("Info").toString());
        builder.clear();
        assertEquals(COLOR_START + "96mDebug" + RESET, builder.debug("Debug").toString());
        builder.clear();
        assertEquals(COLOR_START + "90mTrace" + RESET, builder.trace("Trace").toString());
        builder.clear();
        assertEquals(COLOR_START + "38;5;252mTimestamp" + RESET, builder.timestamp("Timestamp").toString());
        builder.clear();
        assertEquals(COLOR_START + "37mMessage" + RESET, builder.message("Message").toString());
        builder.clear();
        assertEquals(COLOR_START + "94mCategory" + RESET, builder.category("Category").toString());
        builder.clear();
        assertEquals(COLOR_START + "92mThreadName" + RESET, builder.threadName("ThreadName").toString());
        builder.clear();
        assertEquals(COLOR_START + "91mFatal" + RESET, builder.fatal("Fatal").toString());
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
        assertEquals(COLOR_START + "96mDebug" + RESET, builder.debug("Debug").toString());
        builder.clear();
        assertEquals(COLOR_START + "90mTrace" + RESET, builder.trace("Trace").toString());
        builder.clear();
        assertEquals(COLOR_START + "38;5;252mTimestamp" + RESET, builder.timestamp("Timestamp").toString());
        builder.clear();
        assertEquals(COLOR_START + "37mMessage" + RESET, builder.message("Message").toString());
        builder.clear();
        assertEquals(COLOR_START + "94mCategory" + RESET, builder.category("Category").toString());
        builder.clear();
        assertEquals(COLOR_START + "92mThreadName" + RESET, builder.threadName("ThreadName").toString());
        builder.clear();
        assertEquals(COLOR_START + "91mFatal" + RESET, builder.fatal("Fatal").toString());
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
        assertEquals(COLOR_START + "36mDebug" + RESET, builder.debug("Debug").toString());
        builder.clear();
        assertEquals(COLOR_START + "90mTrace" + RESET, builder.trace("Trace").toString());
        builder.clear();
        assertEquals(COLOR_START + "38;5;240mTimestamp" + RESET, builder.timestamp("Timestamp").toString());
        builder.clear();
        assertEquals(COLOR_START + "30mMessage" + RESET, builder.message("Message").toString());
        builder.clear();
        assertEquals(COLOR_START + "34mCategory" + RESET, builder.category("Category").toString());
        builder.clear();
        assertEquals(COLOR_START + "32mThreadName" + RESET, builder.threadName("ThreadName").toString());
        builder.clear();
        assertEquals(COLOR_START + "31mFatal" + RESET, builder.fatal("Fatal").toString());
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
                COLOR_START + "38;5;252m10:30:45" + RESET + " " +
                        COLOR_START + "92m[INFO]" + RESET + " " +
                        COLOR_START + "37mStarted" + RESET,
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

    @Test
    public void testTextCodeAuto256Color() {
        ANSIBuilder builder = ANSIBuilder.builder();

        // Code 244 is outside basic ANSI range, should be treated as 256-color
        String result = builder.textCode(244).append("Gray text").toString();
        assertEquals(COLOR_START + "38;5;244mGray text" + RESET, result);

        builder.reset();
        // Code 196 (256-color bright red) should also use 256-color format
        result = builder.textCode(196).append("Red text").toString();
        assertEquals(COLOR_START + "38;5;196mRed text" + RESET, result);

        builder.reset();
        // Code 91 (bright red) is a valid basic ANSI code, should use basic format
        result = builder.textCode(91).append("Bright red").toString();
        assertEquals(COLOR_START + "91mBright red" + RESET, result);

        builder.reset();
        // Code 31 (red) is a valid basic ANSI code
        result = builder.textCode(31).append("Red").toString();
        assertEquals(COLOR_START + "31mRed" + RESET, result);
    }

    @Test
    public void testBgCodeAuto256Color() {
        ANSIBuilder builder = ANSIBuilder.builder();

        // Code 244 is outside basic ANSI range, should be treated as 256-color
        String result = builder.bgCode(244).append("Gray bg").toString();
        assertEquals(COLOR_START + "48;5;244mGray bg" + RESET, result);

        builder.reset();
        // Code 44 (cyan bg) is a valid basic ANSI code
        result = builder.bgCode(44).append("Cyan bg").toString();
        assertEquals(COLOR_START + "44mCyan bg" + RESET, result);

        builder.reset();
        // Code 104 (bright cyan bg) is a valid basic ANSI code
        result = builder.bgCode(104).append("Bright cyan bg").toString();
        assertEquals(COLOR_START + "104mBright cyan bg" + RESET, result);
    }

    @Test
    public void testSemanticColorsWith256Override() {
        // Create capability with 256-color overrides
        TerminalColorCapability cap = TerminalColorCapability.builder()
                .theme(TerminalTheme.DARK)
                .timestampCode(244) // 256-color gray
                .errorCode(196) // 256-color bright red
                .build();

        ANSIBuilder builder = ANSIBuilder.builder(cap);

        // timestamp with 256-color override
        String result = builder.timestamp("10:30:45").toString();
        assertEquals(COLOR_START + "38;5;244m10:30:45" + RESET, result);

        builder.reset();
        // error with 256-color override
        result = builder.error("Error!").toString();
        assertEquals(COLOR_START + "38;5;196mError!" + RESET, result);
    }

    @Test
    public void testBuilderLevelColorOverrides() {
        // Test overriding colors directly on the builder
        // Note: Avoid codes 30-37, 39, 90-97 which are basic ANSI codes
        ANSIBuilder builder = ANSIBuilder.builder()
                .errorCode(196) // 256-color bright red
                .successCode(46) // 256-color green
                .warningCode(208) // 256-color orange
                .infoCode(75) // 256-color cyan (not 39 which is basic ANSI)
                .debugCode(250) // 256-color light gray
                .traceCode(240) // 256-color dark gray
                .timestampCode(244) // 256-color gray
                .messageCode(201); // 256-color magenta

        // Test each override
        String result = builder.error("Error").toString();
        assertEquals(COLOR_START + "38;5;196mError" + RESET, result);

        builder.reset();
        result = builder.success("Success").toString();
        assertEquals(COLOR_START + "38;5;46mSuccess" + RESET, result);

        builder.reset();
        result = builder.warning("Warning").toString();
        assertEquals(COLOR_START + "38;5;208mWarning" + RESET, result);

        builder.reset();
        result = builder.info("Info").toString();
        assertEquals(COLOR_START + "38;5;75mInfo" + RESET, result);

        builder.reset();
        result = builder.debug("Debug").toString();
        assertEquals(COLOR_START + "38;5;250mDebug" + RESET, result);

        builder.reset();
        result = builder.trace("Trace").toString();
        assertEquals(COLOR_START + "38;5;240mTrace" + RESET, result);

        builder.reset();
        result = builder.timestamp("10:30").toString();
        assertEquals(COLOR_START + "38;5;244m10:30" + RESET, result);

        builder.reset();
        result = builder.message("Hello").toString();
        assertEquals(COLOR_START + "38;5;201mHello" + RESET, result);
    }

    @Test
    public void testBuilderOverrideTakesPrecedenceOverCapability() {
        // Capability sets error to 196
        TerminalColorCapability cap = TerminalColorCapability.builder()
                .theme(TerminalTheme.DARK)
                .errorCode(196)
                .build();

        // Builder overrides error to 202
        ANSIBuilder builder = ANSIBuilder.builder(cap)
                .errorCode(202);

        // Builder override should win
        String result = builder.error("Error").toString();
        assertEquals(COLOR_START + "38;5;202mError" + RESET, result);
    }

    @Test
    public void testBuilderOverrideWithBasicAnsiCodes() {
        // Override with basic ANSI codes (not 256-color)
        ANSIBuilder builder = ANSIBuilder.builder()
                .errorCode(31) // basic red
                .successCode(92); // bright green

        String result = builder.error("Error").toString();
        assertEquals(COLOR_START + "31mError" + RESET, result);

        builder.reset();
        result = builder.success("OK").toString();
        assertEquals(COLOR_START + "92mOK" + RESET, result);
    }

    @Test
    public void testBuilderRgbOverrides() {
        // Test RGB overrides for semantic colors
        ANSIBuilder builder = ANSIBuilder.builder()
                .errorRgb(255, 0, 0) // Red
                .successRgb(0, 255, 0) // Green
                .warningRgb(255, 165, 0) // Orange
                .infoRgb(0, 191, 255) // Deep sky blue
                .debugRgb(200, 200, 200) // Light gray
                .traceRgb(128, 128, 128) // Gray
                .timestampRgb(100, 149, 237) // Cornflower blue
                .messageRgb(255, 0, 255); // Magenta

        // Test error RGB
        String result = builder.error("Error").toString();
        assertEquals(COLOR_START + "38;2;255;0;0mError" + RESET, result);

        builder.reset();
        result = builder.success("Success").toString();
        assertEquals(COLOR_START + "38;2;0;255;0mSuccess" + RESET, result);

        builder.reset();
        result = builder.warning("Warning").toString();
        assertEquals(COLOR_START + "38;2;255;165;0mWarning" + RESET, result);

        builder.reset();
        result = builder.info("Info").toString();
        assertEquals(COLOR_START + "38;2;0;191;255mInfo" + RESET, result);

        builder.reset();
        result = builder.debug("Debug").toString();
        assertEquals(COLOR_START + "38;2;200;200;200mDebug" + RESET, result);

        builder.reset();
        result = builder.trace("Trace").toString();
        assertEquals(COLOR_START + "38;2;128;128;128mTrace" + RESET, result);

        builder.reset();
        result = builder.timestamp("10:30").toString();
        assertEquals(COLOR_START + "38;2;100;149;237m10:30" + RESET, result);

        builder.reset();
        result = builder.message("Hello").toString();
        assertEquals(COLOR_START + "38;2;255;0;255mHello" + RESET, result);
    }

    @Test
    public void testBuilderHexOverrides() {
        // Test hex overrides for semantic colors
        ANSIBuilder builder = ANSIBuilder.builder()
                .errorHex("#FF5733") // Coral
                .successHex("00FF00"); // Green (without #)

        String result = builder.error("Error").toString();
        assertEquals(COLOR_START + "38;2;255;87;51mError" + RESET, result);

        builder.reset();
        result = builder.success("OK").toString();
        assertEquals(COLOR_START + "38;2;0;255;0mOK" + RESET, result);
    }

    @Test
    public void testRgbOverrideTakesPrecedenceOverCodeOverride() {
        // RGB override should take precedence over code override
        ANSIBuilder builder = ANSIBuilder.builder()
                .errorCode(196) // 256-color red
                .errorRgb(255, 0, 0); // RGB red (should win)

        String result = builder.error("Error").toString();
        assertEquals(COLOR_START + "38;2;255;0;0mError" + RESET, result);
    }

    @Test
    public void testBuilderLevelCategoryOverrides() {
        // Test 256-color code override
        ANSIBuilder builder = ANSIBuilder.builder()
                .categoryCode(75); // 256-color blue

        String result = builder.category("org.aesh").toString();
        assertEquals(COLOR_START + "38;5;75morg.aesh" + RESET, result);
    }

    @Test
    public void testBuilderLevelThreadNameOverrides() {
        // Test 256-color code override
        ANSIBuilder builder = ANSIBuilder.builder()
                .threadNameCode(78); // 256-color green

        String result = builder.threadName("main").toString();
        assertEquals(COLOR_START + "38;5;78mmain" + RESET, result);
    }

    @Test
    public void testBuilderLevelFatalOverrides() {
        // Test 256-color code override
        ANSIBuilder builder = ANSIBuilder.builder()
                .fatalCode(160); // 256-color dark red

        String result = builder.fatal("FATAL").toString();
        assertEquals(COLOR_START + "38;5;160mFATAL" + RESET, result);
    }

    @Test
    public void testCategoryRgbOverride() {
        ANSIBuilder builder = ANSIBuilder.builder()
                .categoryRgb(100, 149, 237); // Cornflower blue

        String result = builder.category("[org.aesh.App]").toString();
        assertEquals(COLOR_START + "38;2;100;149;237m[org.aesh.App]" + RESET, result);
    }

    @Test
    public void testThreadNameRgbOverride() {
        ANSIBuilder builder = ANSIBuilder.builder()
                .threadNameRgb(144, 238, 144); // Light green

        String result = builder.threadName("(main)").toString();
        assertEquals(COLOR_START + "38;2;144;238;144m(main)" + RESET, result);
    }

    @Test
    public void testFatalRgbOverride() {
        ANSIBuilder builder = ANSIBuilder.builder()
                .fatalRgb(220, 20, 60); // Crimson

        String result = builder.fatal("FATAL").toString();
        assertEquals(COLOR_START + "38;2;220;20;60mFATAL" + RESET, result);
    }

    @Test
    public void testCategoryHexOverride() {
        ANSIBuilder builder = ANSIBuilder.builder()
                .categoryHex("#6495ED"); // Cornflower blue

        String result = builder.category("pkg.Class").toString();
        assertEquals(COLOR_START + "38;2;100;149;237mpkg.Class" + RESET, result);
    }

    @Test
    public void testThreadNameHexOverride() {
        ANSIBuilder builder = ANSIBuilder.builder()
                .threadNameHex("90EE90"); // Light green

        String result = builder.threadName("worker-1").toString();
        assertEquals(COLOR_START + "38;2;144;238;144mworker-1" + RESET, result);
    }

    @Test
    public void testFatalHexOverride() {
        ANSIBuilder builder = ANSIBuilder.builder()
                .fatalHex("#DC143C"); // Crimson

        String result = builder.fatal("FATAL").toString();
        assertEquals(COLOR_START + "38;2;220;20;60mFATAL" + RESET, result);
    }

    @Test
    public void testCategoryRgbPrecedenceOverCode() {
        // RGB should take precedence over code
        ANSIBuilder builder = ANSIBuilder.builder()
                .categoryCode(75)
                .categoryRgb(100, 149, 237);

        String result = builder.category("pkg.Class").toString();
        assertEquals(COLOR_START + "38;2;100;149;237mpkg.Class" + RESET, result);
    }

    @Test
    public void testFullLogLineWithAllFields() {
        // Test composing a full log line: TIMESTAMP LEVEL [CATEGORY] (THREAD) MESSAGE
        ANSIBuilder builder = ANSIBuilder.builder();

        String result = builder
                .timestamp("2024-01-15 10:30:45").append(" ")
                .fatal("FATAL").append(" ")
                .category("[org.aesh.App]").append(" ")
                .threadName("(main)").append(" ")
                .message("Application crashed")
                .toString();

        assertEquals(
                COLOR_START + "38;5;252m2024-01-15 10:30:45" + RESET + " " +
                        COLOR_START + "91mFATAL" + RESET + " " +
                        COLOR_START + "94m[org.aesh.App]" + RESET + " " +
                        COLOR_START + "92m(main)" + RESET + " " +
                        COLOR_START + "37mApplication crashed" + RESET,
                result);
    }

    @Test
    public void testFullLogLineWithCapabilityAndOverrides() {
        // Full log line with dark theme capability and HSL overrides
        TerminalColorCapability cap = new TerminalColorCapability(
                ColorDepth.TRUE_COLOR, TerminalTheme.DARK);

        ANSIBuilder builder = ANSIBuilder.builder(cap)
                .categoryHsl(220f, 90f, 80f)
                .threadNameHsl(120f, 42.9f, 80f);

        String result = builder
                .timestamp("10:30:45").append(" ")
                .error("ERROR").append(" ")
                .category("[org.aesh]").append(" ")
                .threadName("(main)").append(" ")
                .message("Something failed")
                .toString();

        // Verify it produces valid ANSI output (RGB overrides from HSL)
        // We just check the structure is correct - RGB values from HSL conversion
        assertTrue(result.contains("10:30:45"));
        assertTrue(result.contains("ERROR"));
        assertTrue(result.contains("[org.aesh]"));
        assertTrue(result.contains("(main)"));
        assertTrue(result.contains("Something failed"));
        // Verify RGB format is used for category and threadName (HSL override)
        assertTrue(result.contains("38;2;"));
    }
}
