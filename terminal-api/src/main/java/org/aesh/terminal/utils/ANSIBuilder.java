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

    private ANSIBuilder(boolean enableAnsi) {
        ansi = enableAnsi;
        b = new StringBuilder();
    }

    public static ANSIBuilder builder() {
        return new ANSIBuilder(true);
    }

    public static ANSIBuilder builder(boolean enableAnsi) {
        return new ANSIBuilder(enableAnsi);
    }

    private void checkColor() {
        if(ansi && !havePrintedColor) {
            havePrintedColor = true;
            doAppendColors();
        }
    }

    private void doAppendColors() {
        if(bg == Color.DEFAULT && text == Color.DEFAULT && textType == TextType.DEFAULT)
            return;
        else if (bg == Color.DEFAULT && text == Color.DEFAULT) {
            b.append(ANSI_START)
                    .append(textType.value()).append("m");
        }
        else {
            b.append(ANSI_START)
                    .append(textType.value()).append(';')
                    .append(text.text()).append(';')
                    .append(bg.bg()).append('m');
        }
    }

    public ANSIBuilder resetColors() {
        if(!ansi)
            return this;
        if(textType == TextType.DEFAULT && bg == Color.DEFAULT && text == Color.DEFAULT)
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
    }

    public ANSIBuilder clear() {
        b = new StringBuilder();
        if(ansi)
            doResetColors();
        havePrintedColor = false;
        return this;
    }

    public ANSIBuilder text(Color color) {
        if(color != null && this.text != color) {
            this.text = color;
            havePrintedColor = false;
        }
        return this;
    }

    public ANSIBuilder textType(TextType type) {
        if(type != null && textType != type) {
            textType = type;
            havePrintedColor = false;
        }
        return this;
    }

    public ANSIBuilder bg(Color color) {
        if(color != null && this.bg != color) {
            this.bg = color;
            havePrintedColor = false;
        }
        return this;
    }

    public ANSIBuilder blackText() {
        return text(Color.BLACK);
    }

    public ANSIBuilder redText() {
        return text(Color.RED);
    }

    public ANSIBuilder greenText() {
        return text(Color.GREEN);
    }

    public ANSIBuilder yellowText() {
        return text(Color.YELLOW);
    }

    public ANSIBuilder blueText() {
        return text(Color.BLUE);
    }

    public ANSIBuilder magentaText() {
        return text(Color.MAGENTA);
    }

    public ANSIBuilder cyanText() {
        return text(Color.CYAN);
    }

    public ANSIBuilder whiteText() {
        return text(Color.WHITE);
    }

    public ANSIBuilder defaultText() {
        return text(Color.DEFAULT);
    }

    public ANSIBuilder blackBg() {
        return bg(Color.BLACK);
    }

    public ANSIBuilder redBg() {
        return bg(Color.RED);
    }

    public ANSIBuilder greenBg() {
        return bg(Color.GREEN);
    }

    public ANSIBuilder yellowBg() {
        return bg(Color.YELLOW);
    }

    public ANSIBuilder blueBg() {
        return bg(Color.BLUE);
    }

    public ANSIBuilder magentaBg() {
        return bg(Color.MAGENTA);
    }

    public ANSIBuilder cyanBg() {
        return bg(Color.CYAN);
    }

    public ANSIBuilder whiteBg() {
        return bg(Color.WHITE);
    }

    public ANSIBuilder defaultBg() {
        return bg(Color.DEFAULT);
    }

    public ANSIBuilder blackText(String text) {
        return text(Color.BLACK).append(text).resetColors();
    }

    public ANSIBuilder redText(String text) {
        return text(Color.RED).append(text).resetColors();
    }

    public ANSIBuilder greenText(String text) {
        return text(Color.GREEN).append(text).resetColors();
    }

    public ANSIBuilder yellowText(String text) {
        return text(Color.YELLOW).append(text).resetColors();
    }

    public ANSIBuilder blueText(String text) {
        return text(Color.BLUE).append(text).resetColors();
    }

    public ANSIBuilder magentaText(String text) {
        return text(Color.MAGENTA).append(text).resetColors();
    }

    public ANSIBuilder cyanText(String text) {
        return text(Color.CYAN).append(text).resetColors();
    }

    public ANSIBuilder whiteText(String text) {
        return text(Color.WHITE).append(text).resetColors();
    }

    public ANSIBuilder defaultText(String text) {
        return text(Color.DEFAULT).append(text).resetColors();
    }

    public ANSIBuilder blackBg(String text) {
        return bg(Color.BLACK).append(text).resetColors();
    }

    public ANSIBuilder redBg(String text) {
        return bg(Color.RED).append(text).resetColors();
    }

    public ANSIBuilder greenBg(String text) {
        return bg(Color.GREEN).append(text).resetColors();
    }

    public ANSIBuilder yellowBg(String text) {
        return bg(Color.YELLOW).append(text).resetColors();
    }

    public ANSIBuilder blueBg(String text) {
        return bg(Color.BLUE).append(text).resetColors();
    }

    public ANSIBuilder magentaBg(String text) {
        return bg(Color.MAGENTA).append(text).resetColors();
    }

    public ANSIBuilder cyanBg(String text) {
        return bg(Color.CYAN).append(text).resetColors();
    }

    public ANSIBuilder whiteBg(String text) {
        return bg(Color.WHITE).append(text).resetColors();
    }

    public ANSIBuilder defaultBg(String text) {
        return bg(Color.DEFAULT).append(text).resetColors();
    }

    public ANSIBuilder append(String data) {
        checkColor();
        b.append(data);
        return this;
    }

    public ANSIBuilder append(int data) {
        checkColor();
        b.append(data);
        return this;
    }

    public ANSIBuilder append(char data) {
        checkColor();
        b.append(data);
        return this;
    }

    public ANSIBuilder append(CharSequence data) {
        checkColor();
        b.append(data);
        return this;
    }

    public ANSIBuilder append(char[] data) {
        checkColor();
        b.append(data);
        return this;
    }

    public ANSIBuilder append(Object data) {
        checkColor();
        b.append(data);
        return this;
    }

    public ANSIBuilder append(StringBuilder data) {
        checkColor();
        b.append(data);
        return this;
    }

    public ANSIBuilder append(float data) {
        checkColor();
        b.append(data);
        return this;
    }

    public ANSIBuilder append(double data) {
        checkColor();
        b.append(data);
        return this;
    }

    public ANSIBuilder append(long data) {
        checkColor();
        b.append(data);
        return this;
    }

    public ANSIBuilder bold() {
        return textType(TextType.BOLD);
    }

    public ANSIBuilder boldOff() {
        return textType(TextType.BOLD_OFF);
    }

    public ANSIBuilder faint() {
        return textType(TextType.FAINT);
    }

    public ANSIBuilder faintOff() {
        return textType(TextType.DEFAULT);
    }

    public ANSIBuilder italic() {
        return textType(TextType.ITALIC);
    }

    public ANSIBuilder italicOff() {
        return textType(TextType.ITALIC_OFF);
    }

    public ANSIBuilder underline() {
        return textType(TextType.UNDERLINE);
    }

    public ANSIBuilder underlineOff() {
        return textType(TextType.UNDERLINE_OFF);
    }

    public ANSIBuilder blink() {
        return textType(TextType.BLINK);
    }

    public ANSIBuilder blinkOff() {
        return textType(TextType.BLINK_OFF);
    }

    public ANSIBuilder invert() {
        return textType(TextType.INVERT);
    }

    public ANSIBuilder invertOff() {
        return textType(TextType.INVERT_OFF);
    }

    public ANSIBuilder conceal() {
        return textType(TextType.CONCEAL);
    }

    public ANSIBuilder concealOff() {
        return textType(TextType.CONCEAL_OFF);
    }

    public ANSIBuilder crossedOut() {
        return textType(TextType.CROSSED_OUT);
    }

    public ANSIBuilder crossedOutOff() {
        return textType(TextType.CROSSED_OUT_OFF);
    }

    public ANSIBuilder newline() {
        b.append(Config.getLineSeparator());
        return this;
    }

    public ANSIBuilder bold(String text) {
        return textType(TextType.BOLD).append(text).textType(TextType.BOLD_OFF);
    }

    public ANSIBuilder faint(String text) {
        return textType(TextType.FAINT).append(text).textType(TextType.DEFAULT);
    }

    public ANSIBuilder italic(String text) {
        return textType(TextType.ITALIC).append(text).textType(TextType.ITALIC_OFF);
    }

    public ANSIBuilder underline(String text) {
        return textType(TextType.UNDERLINE).append(text).textType(TextType.UNDERLINE_OFF);
    }

    public ANSIBuilder blink(String text) {
        return textType(TextType.BLINK).append(text).textType(TextType.BLINK_OFF);
    }

    public ANSIBuilder invert(String text) {
        return textType(TextType.INVERT).append(text).textType(TextType.INVERT_OFF);
    }

    public ANSIBuilder conceal(String text) {
        return textType(TextType.CONCEAL).append(text).textType(TextType.CONCEAL_OFF);
    }

    public ANSIBuilder crossedOut(String text) {
        return textType(TextType.CROSSED_OUT).append(text).textType(TextType.CROSSED_OUT_OFF);
    }

    public String toString() {
        resetColors();
        return b.toString();
    }

    public enum Color {
        BLACK(0),
        RED(1),
        GREEN(2),
        YELLOW(3),
        BLUE(4),
        MAGENTA(5),
        CYAN(6),
        WHITE(7),
        DEFAULT(9);

        private final int value;

        private Color(int index) {
            this.value = index;
        }

        public String toString() {
            return this.name();
        }

        public int value() {
            return this.value;
        }

        public int text() {
            return this.value + 30;
        }

        public int bg() {
            return this.value + 40;
        }
    }

    public enum TextType {
        DEFAULT(0),
        BOLD(1),
        FAINT(2),
        ITALIC(3),
        UNDERLINE(4),
        BLINK(5),
        INVERT(7),
        CONCEAL(8),
        CROSSED_OUT(9),
        UNDERLINE_DOUBLE(21),
        BOLD_OFF(22),
        ITALIC_OFF(23),
        UNDERLINE_OFF(24),
        BLINK_OFF(25),
        INVERT_OFF(27),
        CONCEAL_OFF(28),
        CROSSED_OUT_OFF(29);

        private final int value;

        TextType(int c) {
            this.value = c;
        }

        public int value() {
            return value;
        }

    }
}
