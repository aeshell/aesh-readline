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

/**
 * A fluent builder for constructing ANSI escape sequences for terminal text formatting.
 * Supports colors, text styles (bold, italic, underline, etc.), and various text effects.
 * <p>
 * Basic usage:
 *
 * <pre>
 * String output = ANSIBuilder.builder()
 *         .bold().redText("Error: ").boldOff()
 *         .append("Something went wrong")
 *         .toString();
 * </pre>
 * <p>
 * Theme-aware usage with semantic colors:
 *
 * <pre>
 * TerminalColorCapability cap = TerminalColorCapability.detectFromEnvironment();
 * String output = ANSIBuilder.builder(cap)
 *         .timestamp("2024-01-15 10:30:45").append(" ")
 *         .success("[INFO]").append(" ")
 *         .message("Application started")
 *         .toString();
 * </pre>
 * <p>
 * Extended color support:
 *
 * <pre>
 * ANSIBuilder.builder()
 *         .bright().redText("Bright red") // Bright color variant
 *         .color256(208).append("Orange") // 256-color palette
 *         .rgb(255, 100, 50).append("Custom") // True color RGB
 *         .toString();
 * </pre>
 *
 * @author <a href="mailto:stalep@gmail.com">Ståle Pedersen</a>
 */
public class ANSIBuilder {

    private static final String ANSI_START = "\u001B[";
    private static final String ANSI_RESET = "\u001B[0m";
    private final boolean ansi;

    private StringBuilder b;
    private TextType textType = TextType.DEFAULT;
    private Color bg = Color.DEFAULT;
    private Color text = Color.DEFAULT;
    private boolean havePrintedColor = false;

    // Extended color support
    private TerminalColorCapability capability;
    private boolean bright = false;
    private Integer textCode = null; // Raw ANSI code (30-37, 90-97)
    private Integer bgCode = null; // Raw ANSI code (40-47, 100-107)
    private Integer text256 = null; // 256-color palette index
    private Integer bg256 = null; // 256-color palette index
    private int[] textRgb = null; // True color RGB
    private int[] bgRgb = null; // True color RGB

    private ANSIBuilder(boolean enableAnsi) {
        ansi = enableAnsi;
        b = new StringBuilder();
    }

    private ANSIBuilder(boolean enableAnsi, TerminalColorCapability capability) {
        this(enableAnsi);
        this.capability = capability;
    }

    /**
     * Creates a new ANSIBuilder with ANSI formatting enabled.
     *
     * @return a new ANSIBuilder instance
     */
    public static ANSIBuilder builder() {
        return new ANSIBuilder(true);
    }

    /**
     * Creates a new ANSIBuilder with configurable ANSI formatting.
     *
     * @param enableAnsi true to enable ANSI escape sequences, false to disable
     * @return a new ANSIBuilder instance
     */
    public static ANSIBuilder builder(boolean enableAnsi) {
        return new ANSIBuilder(enableAnsi);
    }

    /**
     * Creates a new theme-aware ANSIBuilder with terminal color capability.
     * <p>
     * When a capability is provided, semantic color methods like {@link #error()},
     * {@link #success()}, {@link #timestamp()}, etc. will automatically choose
     * appropriate color intensities based on the terminal's theme (light or dark).
     *
     * @param capability the detected terminal color capability
     * @return a new ANSIBuilder instance configured for the terminal
     */
    public static ANSIBuilder builder(TerminalColorCapability capability) {
        boolean enableAnsi = capability != null && capability.supportsAnsiColors();
        return new ANSIBuilder(enableAnsi, capability);
    }

    private void checkColor() {
        if (ansi && !havePrintedColor) {
            havePrintedColor = true;
            doAppendColors();
        }
    }

    private void doAppendColors() {
        // Check if we have any colors to output
        boolean hasText = text != Color.DEFAULT || textCode != null || text256 != null || textRgb != null;
        boolean hasBg = bg != Color.DEFAULT || bgCode != null || bg256 != null || bgRgb != null;
        boolean hasStyle = textType != TextType.DEFAULT;

        if (!hasText && !hasBg && !hasStyle) {
            return;
        }

        b.append(ANSI_START);
        boolean needsSemicolon = false;

        // Text style
        if (hasStyle) {
            b.append(textType.value());
            needsSemicolon = true;
        }

        // Foreground color
        if (textRgb != null) {
            // True color: ESC[38;2;r;g;bm
            if (needsSemicolon)
                b.append(';');
            b.append("38;2;").append(textRgb[0]).append(';').append(textRgb[1]).append(';').append(textRgb[2]);
            needsSemicolon = true;
        } else if (text256 != null) {
            // 256-color: ESC[38;5;indexm
            if (needsSemicolon)
                b.append(';');
            b.append("38;5;").append(text256);
            needsSemicolon = true;
        } else if (textCode != null) {
            // Raw ANSI code
            if (needsSemicolon)
                b.append(';');
            b.append(textCode);
            needsSemicolon = true;
        } else if (text != Color.DEFAULT) {
            // Basic color with optional bright
            if (needsSemicolon)
                b.append(';');
            b.append(bright ? text.text() + 60 : text.text());
            needsSemicolon = true;
        }

        // Background color
        if (bgRgb != null) {
            // True color: ESC[48;2;r;g;bm
            if (needsSemicolon)
                b.append(';');
            b.append("48;2;").append(bgRgb[0]).append(';').append(bgRgb[1]).append(';').append(bgRgb[2]);
        } else if (bg256 != null) {
            // 256-color: ESC[48;5;indexm
            if (needsSemicolon)
                b.append(';');
            b.append("48;5;").append(bg256);
        } else if (bgCode != null) {
            // Raw ANSI code
            if (needsSemicolon)
                b.append(';');
            b.append(bgCode);
        } else if (bg != Color.DEFAULT) {
            // Basic color with optional bright
            if (needsSemicolon)
                b.append(';');
            b.append(bright ? bg.bg() + 60 : bg.bg());
        }

        b.append('m');
    }

    /**
     * Resets all colors and text styles to default.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder resetColors() {
        if (!ansi)
            return this;
        // Check if any colors or styles are active (basic or extended)
        boolean hasBasicColors = textType != TextType.DEFAULT || bg != Color.DEFAULT || text != Color.DEFAULT;
        boolean hasExtendedColors = textCode != null || bgCode != null ||
                text256 != null || bg256 != null ||
                textRgb != null || bgRgb != null || bright;
        if (!hasBasicColors && !hasExtendedColors)
            return this;
        else {
            doResetColors();
            b.append(ANSI_RESET);
            return this;
        }
    }

    private void doResetColors() {
        textType = TextType.DEFAULT;
        bg = Color.DEFAULT;
        text = Color.DEFAULT;
        bright = false;
        textCode = null;
        bgCode = null;
        text256 = null;
        bg256 = null;
        textRgb = null;
        bgRgb = null;
    }

    /**
     * Clears the builder content and resets all formatting.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder clear() {
        b = new StringBuilder();
        if (ansi)
            doResetColors();
        havePrintedColor = false;
        return this;
    }

    /**
     * Clears the builder content and resets all formatting, identical to clear()
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder reset() {
        return clear();
    }

    /**
     * Sets the foreground text color.
     *
     * @param color the color to use for text
     * @return this builder for method chaining
     */
    public ANSIBuilder text(Color color) {
        if (color != null && this.text != color) {
            this.text = color;
            havePrintedColor = false;
        }
        return this;
    }

    /**
     * Sets the text type/style.
     *
     * @param type the text type to apply
     * @return this builder for method chaining
     */
    public ANSIBuilder textType(TextType type) {
        if (type != null && textType != type) {
            textType = type;
            havePrintedColor = false;
        }
        return this;
    }

    /**
     * Sets the background color.
     *
     * @param color the color to use for background
     * @return this builder for method chaining
     */
    public ANSIBuilder bg(Color color) {
        if (color != null && this.bg != color) {
            this.bg = color;
            havePrintedColor = false;
        }
        return this;
    }

    /**
     * Sets foreground text color to black.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder blackText() {
        return text(Color.BLACK);
    }

    /**
     * Sets foreground text color to red.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder redText() {
        return text(Color.RED);
    }

    /**
     * Sets foreground text color to green.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder greenText() {
        return text(Color.GREEN);
    }

    /**
     * Sets foreground text color to yellow.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder yellowText() {
        return text(Color.YELLOW);
    }

    /**
     * Sets foreground text color to blue.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder blueText() {
        return text(Color.BLUE);
    }

    /**
     * Sets foreground text color to magenta.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder magentaText() {
        return text(Color.MAGENTA);
    }

    /**
     * Sets foreground text color to cyan.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder cyanText() {
        return text(Color.CYAN);
    }

    /**
     * Sets foreground text color to white.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder whiteText() {
        return text(Color.WHITE);
    }

    /**
     * Sets foreground text color to default.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder defaultText() {
        return text(Color.DEFAULT);
    }

    /**
     * Sets background color to black.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder blackBg() {
        return bg(Color.BLACK);
    }

    /**
     * Sets background color to red.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder redBg() {
        return bg(Color.RED);
    }

    /**
     * Sets background color to green.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder greenBg() {
        return bg(Color.GREEN);
    }

    /**
     * Sets background color to yellow.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder yellowBg() {
        return bg(Color.YELLOW);
    }

    /**
     * Sets background color to blue.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder blueBg() {
        return bg(Color.BLUE);
    }

    /**
     * Sets background color to magenta.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder magentaBg() {
        return bg(Color.MAGENTA);
    }

    /**
     * Sets background color to cyan.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder cyanBg() {
        return bg(Color.CYAN);
    }

    /**
     * Sets background color to white.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder whiteBg() {
        return bg(Color.WHITE);
    }

    /**
     * Sets background color to default.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder defaultBg() {
        return bg(Color.DEFAULT);
    }

    /**
     * Appends text with black foreground color and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder blackText(String text) {
        return text(Color.BLACK).append(text).resetColors();
    }

    /**
     * Appends text with red foreground color and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder redText(String text) {
        return text(Color.RED).append(text).resetColors();
    }

    /**
     * Appends text with green foreground color and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder greenText(String text) {
        return text(Color.GREEN).append(text).resetColors();
    }

    /**
     * Appends text with yellow foreground color and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder yellowText(String text) {
        return text(Color.YELLOW).append(text).resetColors();
    }

    /**
     * Appends text with blue foreground color and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder blueText(String text) {
        return text(Color.BLUE).append(text).resetColors();
    }

    /**
     * Appends text with magenta foreground color and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder magentaText(String text) {
        return text(Color.MAGENTA).append(text).resetColors();
    }

    /**
     * Appends text with cyan foreground color and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder cyanText(String text) {
        return text(Color.CYAN).append(text).resetColors();
    }

    /**
     * Appends text with white foreground color and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder whiteText(String text) {
        return text(Color.WHITE).append(text).resetColors();
    }

    /**
     * Appends text with default foreground color and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder defaultText(String text) {
        return text(Color.DEFAULT).append(text).resetColors();
    }

    /**
     * Appends text with black background and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder blackBg(String text) {
        return bg(Color.BLACK).append(text).resetColors();
    }

    /**
     * Appends text with red background and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder redBg(String text) {
        return bg(Color.RED).append(text).resetColors();
    }

    /**
     * Appends text with green background and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder greenBg(String text) {
        return bg(Color.GREEN).append(text).resetColors();
    }

    /**
     * Appends text with yellow background and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder yellowBg(String text) {
        return bg(Color.YELLOW).append(text).resetColors();
    }

    /**
     * Appends text with blue background and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder blueBg(String text) {
        return bg(Color.BLUE).append(text).resetColors();
    }

    /**
     * Appends text with magenta background and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder magentaBg(String text) {
        return bg(Color.MAGENTA).append(text).resetColors();
    }

    /**
     * Appends text with cyan background and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder cyanBg(String text) {
        return bg(Color.CYAN).append(text).resetColors();
    }

    /**
     * Appends text with white background and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder whiteBg(String text) {
        return bg(Color.WHITE).append(text).resetColors();
    }

    /**
     * Appends text with default background and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder defaultBg(String text) {
        return bg(Color.DEFAULT).append(text).resetColors();
    }

    /**
     * Appends a string to the builder.
     *
     * @param data the string to append
     * @return this builder for method chaining
     */
    public ANSIBuilder append(String data) {
        checkColor();
        b.append(data);
        return this;
    }

    /**
     * Appends an integer to the builder.
     *
     * @param data the integer to append
     * @return this builder for method chaining
     */
    public ANSIBuilder append(int data) {
        checkColor();
        b.append(data);
        return this;
    }

    /**
     * Appends a character to the builder.
     *
     * @param data the character to append
     * @return this builder for method chaining
     */
    public ANSIBuilder append(char data) {
        checkColor();
        b.append(data);
        return this;
    }

    /**
     * Appends a CharSequence to the builder.
     *
     * @param data the CharSequence to append
     * @return this builder for method chaining
     */
    public ANSIBuilder append(CharSequence data) {
        checkColor();
        b.append(data);
        return this;
    }

    /**
     * Appends a character array to the builder.
     *
     * @param data the character array to append
     * @return this builder for method chaining
     */
    public ANSIBuilder append(char[] data) {
        checkColor();
        b.append(data);
        return this;
    }

    /**
     * Appends an Object to the builder.
     *
     * @param data the Object to append
     * @return this builder for method chaining
     */
    public ANSIBuilder append(Object data) {
        checkColor();
        b.append(data);
        return this;
    }

    /**
     * Appends a StringBuilder to the builder.
     *
     * @param data the StringBuilder to append
     * @return this builder for method chaining
     */
    public ANSIBuilder append(StringBuilder data) {
        checkColor();
        b.append(data);
        return this;
    }

    /**
     * Appends a float to the builder.
     *
     * @param data the float to append
     * @return this builder for method chaining
     */
    public ANSIBuilder append(float data) {
        checkColor();
        b.append(data);
        return this;
    }

    /**
     * Appends a double to the builder.
     *
     * @param data the double to append
     * @return this builder for method chaining
     */
    public ANSIBuilder append(double data) {
        checkColor();
        b.append(data);
        return this;
    }

    /**
     * Appends a long to the builder.
     *
     * @param data the long to append
     * @return this builder for method chaining
     */
    public ANSIBuilder append(long data) {
        checkColor();
        b.append(data);
        return this;
    }

    /**
     * Enables bold text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder bold() {
        return textType(TextType.BOLD);
    }

    /**
     * Disables bold text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder boldOff() {
        return textType(TextType.BOLD_OFF);
    }

    /**
     * Enables faint (dim) text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder faint() {
        return textType(TextType.FAINT);
    }

    /**
     * Disables faint text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder faintOff() {
        return textType(TextType.DEFAULT);
    }

    /**
     * Enables italic text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder italic() {
        return textType(TextType.ITALIC);
    }

    /**
     * Disables italic text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder italicOff() {
        return textType(TextType.ITALIC_OFF);
    }

    /**
     * Enables underline text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder underline() {
        return textType(TextType.UNDERLINE);
    }

    /**
     * Disables underline text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder underlineOff() {
        return textType(TextType.UNDERLINE_OFF);
    }

    /**
     * Enables blinking text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder blink() {
        return textType(TextType.BLINK);
    }

    /**
     * Disables blinking text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder blinkOff() {
        return textType(TextType.BLINK_OFF);
    }

    /**
     * Enables inverted (reverse video) text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder invert() {
        return textType(TextType.INVERT);
    }

    /**
     * Disables inverted text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder invertOff() {
        return textType(TextType.INVERT_OFF);
    }

    /**
     * Enables concealed (hidden) text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder conceal() {
        return textType(TextType.CONCEAL);
    }

    /**
     * Disables concealed text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder concealOff() {
        return textType(TextType.CONCEAL_OFF);
    }

    /**
     * Enables crossed-out (strikethrough) text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder crossedOut() {
        return textType(TextType.CROSSED_OUT);
    }

    /**
     * Disables crossed-out text style.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder crossedOutOff() {
        return textType(TextType.CROSSED_OUT_OFF);
    }

    /**
     * Appends a newline to the builder.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder newline() {
        b.append(Config.getLineSeparator());
        return this;
    }

    /**
     * Appends text with bold style and disables bold after.
     *
     * @param text the text to append in bold
     * @return this builder for method chaining
     */
    public ANSIBuilder bold(String text) {
        return textType(TextType.BOLD).append(text).textType(TextType.BOLD_OFF);
    }

    /**
     * Appends text with faint style and resets after.
     *
     * @param text the text to append in faint style
     * @return this builder for method chaining
     */
    public ANSIBuilder faint(String text) {
        return textType(TextType.FAINT).append(text).textType(TextType.DEFAULT);
    }

    /**
     * Appends text with italic style and disables italic after.
     *
     * @param text the text to append in italic
     * @return this builder for method chaining
     */
    public ANSIBuilder italic(String text) {
        return textType(TextType.ITALIC).append(text).textType(TextType.ITALIC_OFF);
    }

    /**
     * Appends text with underline style and disables underline after.
     *
     * @param text the text to append with underline
     * @return this builder for method chaining
     */
    public ANSIBuilder underline(String text) {
        return textType(TextType.UNDERLINE).append(text).textType(TextType.UNDERLINE_OFF);
    }

    /**
     * Appends text with blink style and disables blink after.
     *
     * @param text the text to append with blink
     * @return this builder for method chaining
     */
    public ANSIBuilder blink(String text) {
        return textType(TextType.BLINK).append(text).textType(TextType.BLINK_OFF);
    }

    /**
     * Appends text with inverted style and disables invert after.
     *
     * @param text the text to append with invert
     * @return this builder for method chaining
     */
    public ANSIBuilder invert(String text) {
        return textType(TextType.INVERT).append(text).textType(TextType.INVERT_OFF);
    }

    /**
     * Appends text with concealed style and disables conceal after.
     *
     * @param text the text to append concealed
     * @return this builder for method chaining
     */
    public ANSIBuilder conceal(String text) {
        return textType(TextType.CONCEAL).append(text).textType(TextType.CONCEAL_OFF);
    }

    /**
     * Appends text with crossed-out style and disables it after.
     *
     * @param text the text to append crossed out
     * @return this builder for method chaining
     */
    public ANSIBuilder crossedOut(String text) {
        return textType(TextType.CROSSED_OUT).append(text).textType(TextType.CROSSED_OUT_OFF);
    }

    // ==================== Brightness Modifier ====================

    /**
     * Enables bright (high intensity) mode for subsequent colors.
     * <p>
     * When bright mode is enabled, basic colors (red, green, etc.) will use
     * their bright variants (codes 90-97 instead of 30-37).
     * <p>
     * Example:
     *
     * <pre>
     * ANSIBuilder.builder().bright().redText("Bright red").toString();
     * </pre>
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder bright() {
        this.bright = true;
        havePrintedColor = false;
        return this;
    }

    /**
     * Disables bright mode, returning to normal intensity colors.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder brightOff() {
        this.bright = false;
        havePrintedColor = false;
        return this;
    }

    // ==================== 256-Color Support ====================

    /**
     * Sets the foreground color using the 256-color palette.
     * <p>
     * The 256-color palette is organized as:
     * <ul>
     * <li>0-7: Standard colors (same as basic ANSI)</li>
     * <li>8-15: High intensity colors</li>
     * <li>16-231: 6x6x6 color cube</li>
     * <li>232-255: Grayscale from dark to light</li>
     * </ul>
     *
     * @param index the color index (0-255)
     * @return this builder for method chaining
     * @throws IllegalArgumentException if index is out of range
     */
    public ANSIBuilder color256(int index) {
        if (index < 0 || index > 255) {
            throw new IllegalArgumentException("Color index must be 0-255, got: " + index);
        }
        this.text256 = index;
        this.text = Color.DEFAULT;
        this.textCode = null;
        this.textRgb = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Sets the background color using the 256-color palette.
     *
     * @param index the color index (0-255)
     * @return this builder for method chaining
     * @throws IllegalArgumentException if index is out of range
     */
    public ANSIBuilder bg256(int index) {
        if (index < 0 || index > 255) {
            throw new IllegalArgumentException("Color index must be 0-255, got: " + index);
        }
        this.bg256 = index;
        this.bg = Color.DEFAULT;
        this.bgCode = null;
        this.bgRgb = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Appends text with the specified 256-color foreground and resets.
     *
     * @param index the color index (0-255)
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder color256(int index, String text) {
        return color256(index).append(text).resetColors();
    }

    /**
     * Appends text with the specified 256-color background and resets.
     *
     * @param index the color index (0-255)
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder bg256(int index, String text) {
        return bg256(index).append(text).resetColors();
    }

    // ==================== True Color (24-bit RGB) Support ====================

    /**
     * Sets the foreground color using RGB values (true color).
     * <p>
     * True color support requires a terminal that supports 24-bit colors.
     * Use {@link TerminalColorCapability#getColorDepth()} to check if
     * the terminal supports true color.
     *
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @return this builder for method chaining
     * @throws IllegalArgumentException if any component is out of range
     */
    public ANSIBuilder rgb(int r, int g, int b) {
        validateRgb(r, g, b);
        this.textRgb = new int[] { r, g, b };
        this.text = Color.DEFAULT;
        this.textCode = null;
        this.text256 = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Sets the background color using RGB values (true color).
     *
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @return this builder for method chaining
     * @throws IllegalArgumentException if any component is out of range
     */
    public ANSIBuilder bgRgb(int r, int g, int b) {
        validateRgb(r, g, b);
        this.bgRgb = new int[] { r, g, b };
        this.bg = Color.DEFAULT;
        this.bgCode = null;
        this.bg256 = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Sets the foreground color using a hex color value.
     *
     * @param hex the color in hex format (e.g., "#FF5733" or "FF5733")
     * @return this builder for method chaining
     * @throws IllegalArgumentException if the hex string is invalid
     */
    public ANSIBuilder hex(String hex) {
        int[] rgb = TerminalColorCapability.hexToRgb(hex);
        if (rgb == null) {
            throw new IllegalArgumentException("Invalid hex color: " + hex);
        }
        return rgb(rgb[0], rgb[1], rgb[2]);
    }

    /**
     * Sets the background color using a hex color value.
     *
     * @param hex the color in hex format (e.g., "#FF5733" or "FF5733")
     * @return this builder for method chaining
     * @throws IllegalArgumentException if the hex string is invalid
     */
    public ANSIBuilder bgHex(String hex) {
        int[] rgb = TerminalColorCapability.hexToRgb(hex);
        if (rgb == null) {
            throw new IllegalArgumentException("Invalid hex color: " + hex);
        }
        return bgRgb(rgb[0], rgb[1], rgb[2]);
    }

    /**
     * Appends text with the specified RGB foreground color and resets.
     *
     * @param r red component (0-255)
     * @param g green component (0-255)
     * @param b blue component (0-255)
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder rgb(int r, int g, int b, String text) {
        return rgb(r, g, b).append(text).resetColors();
    }

    /**
     * Appends text with the specified hex foreground color and resets.
     *
     * @param hex the color in hex format
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder hex(String hex, String text) {
        return hex(hex).append(text).resetColors();
    }

    private void validateRgb(int r, int g, int b) {
        if (r < 0 || r > 255 || g < 0 || g > 255 || b < 0 || b > 255) {
            throw new IllegalArgumentException(
                    "RGB values must be 0-255, got: (" + r + ", " + g + ", " + b + ")");
        }
    }

    // ==================== Semantic Colors (Theme-Aware) ====================

    /**
     * Sets the foreground color to the theme-appropriate error color (red).
     * <p>
     * If a {@link TerminalColorCapability} was provided to the builder,
     * this will use bright red for dark themes and dark red for light themes.
     * Without capability info, defaults to bright red.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder error() {
        if (capability != null) {
            this.textCode = capability.getSuggestedErrorCode();
        } else {
            this.textCode = 91; // bright red default
        }
        this.text = Color.DEFAULT;
        this.text256 = null;
        this.textRgb = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Appends text with error styling (red) and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder error(String text) {
        return error().append(text).resetColors();
    }

    /**
     * Sets the foreground color to the theme-appropriate success color (green).
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder success() {
        if (capability != null) {
            this.textCode = capability.getSuggestedSuccessCode();
        } else {
            this.textCode = 92; // bright green default
        }
        this.text = Color.DEFAULT;
        this.text256 = null;
        this.textRgb = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Appends text with success styling (green) and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder success(String text) {
        return success().append(text).resetColors();
    }

    /**
     * Sets the foreground color to the theme-appropriate warning color (yellow).
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder warning() {
        if (capability != null) {
            this.textCode = capability.getSuggestedWarningCode();
        } else {
            this.textCode = 93; // bright yellow default
        }
        this.text = Color.DEFAULT;
        this.text256 = null;
        this.textRgb = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Appends text with warning styling (yellow) and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder warning(String text) {
        return warning().append(text).resetColors();
    }

    /**
     * Sets the foreground color to the theme-appropriate info color (blue/cyan).
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder info() {
        if (capability != null) {
            this.textCode = capability.getSuggestedInfoCode();
        } else {
            this.textCode = 94; // bright blue default
        }
        this.text = Color.DEFAULT;
        this.text256 = null;
        this.textRgb = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Appends text with info styling (blue/cyan) and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder info(String text) {
        return info().append(text).resetColors();
    }

    /**
     * Sets the foreground color to the theme-appropriate timestamp color (cyan).
     * <p>
     * Timestamps use a subdued cyan color that is visible but doesn't
     * distract from the main message content.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder timestamp() {
        if (capability != null) {
            this.textCode = capability.getSuggestedTimestampCode();
        } else {
            this.textCode = 96; // bright cyan default
        }
        this.text = Color.DEFAULT;
        this.text256 = null;
        this.textRgb = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Appends text with timestamp styling (cyan) and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder timestamp(String text) {
        return timestamp().append(text).resetColors();
    }

    /**
     * Sets the foreground color to the theme-appropriate message color (magenta).
     * <p>
     * Message color is used for highlighted or emphasized message content
     * that should stand out from regular text.
     *
     * @return this builder for method chaining
     */
    public ANSIBuilder message() {
        if (capability != null) {
            this.textCode = capability.getSuggestedMessageCode();
        } else {
            this.textCode = 95; // bright magenta default
        }
        this.text = Color.DEFAULT;
        this.text256 = null;
        this.textRgb = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Appends text with message styling (magenta) and resets.
     *
     * @param text the text to append
     * @return this builder for method chaining
     */
    public ANSIBuilder message(String text) {
        return message().append(text).resetColors();
    }

    /**
     * Sets the foreground color using a raw ANSI color code.
     * <p>
     * This allows direct control over the ANSI color code used:
     * <ul>
     * <li>30-37: Standard foreground colors</li>
     * <li>90-97: Bright foreground colors</li>
     * </ul>
     *
     * @param code the ANSI color code
     * @return this builder for method chaining
     */
    public ANSIBuilder textCode(int code) {
        this.textCode = code;
        this.text = Color.DEFAULT;
        this.text256 = null;
        this.textRgb = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Sets the background color using a raw ANSI color code.
     * <p>
     * This allows direct control over the ANSI color code used:
     * <ul>
     * <li>40-47: Standard background colors</li>
     * <li>100-107: Bright background colors</li>
     * </ul>
     *
     * @param code the ANSI color code
     * @return this builder for method chaining
     */
    public ANSIBuilder bgCode(int code) {
        this.bgCode = code;
        this.bg = Color.DEFAULT;
        this.bg256 = null;
        this.bgRgb = null;
        havePrintedColor = false;
        return this;
    }

    /**
     * Returns the built string with ANSI formatting.
     *
     * @return the formatted string
     */
    public String toString() {
        resetColors();
        return b.toString();
    }

    /**
     * Returns the built string with ANSI formatting followed by a newline.
     * <p>
     * This is a convenience method equivalent to {@code toString() + "\n"}.
     *
     * @return the formatted string with a trailing newline
     */
    public String toLine() {
        resetColors();
        return b.toString() + Config.getLineSeparator();
    }

    /**
     * Appends text followed by a newline.
     * <p>
     * This is a convenience method equivalent to {@code append(text).newline()}.
     *
     * @param text the text to append before the newline
     * @return this builder for method chaining
     */
    public ANSIBuilder appendLine(String text) {
        return append(text).newline();
    }

    /**
     * Enumeration of ANSI color codes for text and background colors.
     */
    public enum Color {
        /** Black color. */
        BLACK(0),
        /** Red color. */
        RED(1),
        /** Green color. */
        GREEN(2),
        /** Yellow color. */
        YELLOW(3),
        /** Blue color. */
        BLUE(4),
        /** Magenta color. */
        MAGENTA(5),
        /** Cyan color. */
        CYAN(6),
        /** White color. */
        WHITE(7),
        /** Default terminal color. */
        DEFAULT(9);

        private final int value;

        Color(int index) {
            this.value = index;
        }

        public String toString() {
            return this.name();
        }

        /**
         * Returns the raw color value.
         *
         * @return the color index value
         */
        public int value() {
            return this.value;
        }

        /**
         * Returns the ANSI code for foreground text color.
         *
         * @return the text color ANSI code
         */
        public int text() {
            return this.value + 30;
        }

        /**
         * Returns the ANSI code for background color.
         *
         * @return the background color ANSI code
         */
        public int bg() {
            return this.value + 40;
        }
    }

    /**
     * Enumeration of ANSI text style/type codes.
     */
    public enum TextType {
        /** Default text style. */
        DEFAULT(0),
        /** Bold text style. */
        BOLD(1),
        /** Faint (dim) text style. */
        FAINT(2),
        /** Italic text style. */
        ITALIC(3),
        /** Underlined text style. */
        UNDERLINE(4),
        /** Blinking text style. */
        BLINK(5),
        /** Inverted (reverse video) text style. */
        INVERT(7),
        /** Concealed (hidden) text style. */
        CONCEAL(8),
        /** Crossed-out (strikethrough) text style. */
        CROSSED_OUT(9),
        /** Double underline text style. */
        UNDERLINE_DOUBLE(21),
        /** Turn off bold text style. */
        BOLD_OFF(22),
        /** Turn off italic text style. */
        ITALIC_OFF(23),
        /** Turn off underline text style. */
        UNDERLINE_OFF(24),
        /** Turn off blink text style. */
        BLINK_OFF(25),
        /** Turn off invert text style. */
        INVERT_OFF(27),
        /** Turn off conceal text style. */
        CONCEAL_OFF(28),
        /** Turn off crossed-out text style. */
        CROSSED_OUT_OFF(29);

        private final int value;

        TextType(int c) {
            this.value = c;
        }

        /**
         * Returns the ANSI code value for this text type.
         *
         * @return the text type ANSI code
         */
        public int value() {
            return value;
        }

    }
}
