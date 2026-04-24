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
package org.aesh.terminal.formatting;

/**
 * Specify a text style.
 * There are some values that nullify other values.
 * eg:
 * - specifying faint AND bold will nullify bold
 * bold AND italic will nullify bold
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class TerminalTextStyle {
    private boolean bold = false;
    private boolean faint = false;
    private boolean italic = false;
    private boolean underline = false;
    private boolean blink = false;
    private boolean invert = false;
    private boolean crossedOut = false;
    private boolean conceal = false;

    private int length = -1;

    /**
     * Create a default text style with no formatting.
     */
    public TerminalTextStyle() {
    }

    /**
     * Create a text style with the specified character type.
     *
     * @param type the character type to apply
     */
    public TerminalTextStyle(CharacterType type) {
        if (type == CharacterType.BOLD)
            bold = true;
        else if (type == CharacterType.FAINT)
            faint = true;
        else if (type == CharacterType.ITALIC)
            italic = true;
        else if (type == CharacterType.UNDERLINE)
            underline = true;
        else if (type == CharacterType.BLINK)
            blink = true;
        else if (type == CharacterType.INVERT)
            invert = true;
        else if (type == CharacterType.CROSSED_OUT)
            crossedOut = true;
        else if (type == CharacterType.CONCEAL)
            conceal = true;
    }

    /**
     * Create a text style with individual style options.
     *
     * @param bold true for bold text
     * @param faint true for faint text
     * @param italic true for italic text
     * @param underline true for underlined text
     * @param blink true for blinking text
     * @param invert true for inverted colors
     * @param crossedOut true for strikethrough text
     */
    public TerminalTextStyle(boolean bold, boolean faint, boolean italic, boolean underline,
            boolean blink, boolean invert, boolean crossedOut) {
        this.bold = bold;
        this.faint = faint;
        this.italic = italic;
        this.underline = underline;
        this.blink = blink;
        this.invert = invert;
        this.crossedOut = crossedOut;
    }

    /**
     * Check if bold is enabled.
     *
     * @return true if bold
     */
    public boolean isBold() {
        return bold;
    }

    /**
     * Set the bold state.
     *
     * @param bold true to enable bold
     */
    public void setBold(boolean bold) {
        this.bold = bold;
    }

    /**
     * Check if faint is enabled.
     *
     * @return true if faint
     */
    public boolean isFaint() {
        return faint;
    }

    /**
     * Check if italic is enabled.
     *
     * @return true if italic
     */
    public boolean isItalic() {
        return italic;
    }

    /**
     * Set the italic state.
     *
     * @param italic true to enable italic
     */
    public void setItalic(boolean italic) {
        this.italic = italic;
    }

    /**
     * Check if underline is enabled.
     *
     * @return true if underlined
     */
    public boolean isUnderline() {
        return underline;
    }

    /**
     * Set the underline state.
     *
     * @param underline true to enable underline
     */
    public void setUnderline(boolean underline) {
        this.underline = underline;
    }

    /**
     * Check if blink is enabled.
     *
     * @return true if blinking
     */
    public boolean isBlink() {
        return blink;
    }

    /**
     * Set the blink state.
     *
     * @param blink true to enable blinking
     */
    public void setBlink(boolean blink) {
        this.blink = blink;
    }

    /**
     * Check if invert is enabled.
     *
     * @return true if colors are inverted
     */
    public boolean isInvert() {
        return invert;
    }

    /**
     * Set the invert state.
     *
     * @param invert true to invert colors
     */
    public void setInvert(boolean invert) {
        this.invert = invert;
    }

    /**
     * Check if crossed out is enabled.
     *
     * @return true if strikethrough
     */
    public boolean isCrossedOut() {
        return crossedOut;
    }

    /**
     * Set the crossed out state.
     *
     * @param crossedOut true to enable strikethrough
     */
    public void setCrossedOut(boolean crossedOut) {
        this.crossedOut = crossedOut;
    }

    /**
     * Check if conceal is enabled.
     *
     * @return true if concealed
     */
    public boolean isConceal() {
        return conceal;
    }

    /**
     * Set the conceal state.
     *
     * @param conceal true to hide text
     */
    public void setConceal(boolean conceal) {
        this.conceal = conceal;
    }

    /**
     * Check if any formatting is applied.
     *
     * @return true if any style option is enabled
     */
    public boolean isFormatted() {
        return !(!bold && !blink && !faint && !italic && !underline && !invert && !crossedOut);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        if (bold) {
            if (builder.length() > 0)
                builder.append(';');
            builder.append(CharacterType.BOLD.getValue());
        }
        if (faint) {
            if (builder.length() > 0)
                builder.append(';');
            builder.append(CharacterType.FAINT.getValue());
        }
        if (italic) {
            if (builder.length() > 0)
                builder.append(';');
            builder.append(CharacterType.ITALIC.getValue());
        }
        if (underline) {
            if (builder.length() > 0)
                builder.append(';');
            builder.append(CharacterType.UNDERLINE.getValue());
        }
        if (blink) {
            if (builder.length() > 0)
                builder.append(';');
            builder.append(CharacterType.BLINK.getValue());
        }
        if (invert) {
            if (builder.length() > 0)
                builder.append(';');
            builder.append(CharacterType.INVERT.getValue());
        }
        if (crossedOut) {
            if (builder.length() > 0)
                builder.append(';');
            builder.append(CharacterType.CROSSED_OUT.getValue());
        }
        if (conceal) {
            if (builder.length() > 0)
                builder.append(';');
            builder.append(CharacterType.CONCEAL.getValue());
        }

        if (length < 0)
            length = builder.length();

        return builder.toString();
    }

    /**
     * Get the length of the ANSI escape sequence.
     *
     * @return the length of the style code string
     */
    public int getLength() {
        if (length < 0)
            toString();
        return length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TerminalTextStyle))
            return false;

        TerminalTextStyle that = (TerminalTextStyle) o;

        return blink == that.blink &&
                bold == that.bold &&
                crossedOut == that.crossedOut &&
                faint == that.faint &&
                invert == that.invert &&
                italic == that.italic &&
                underline == that.underline &&
                conceal == that.conceal;
    }

    @Override
    public int hashCode() {
        int result = (bold ? 1 : 0);
        result = 31 * result + (faint ? 1 : 0);
        result = 31 * result + (italic ? 1 : 0);
        result = 31 * result + (underline ? 1 : 0);
        result = 31 * result + (blink ? 1 : 0);
        result = 31 * result + (invert ? 1 : 0);
        result = 31 * result + (crossedOut ? 1 : 0);
        result = 31 * result + (conceal ? 1 : 0);
        return result;
    }

    /**
     * Get the ANSI codes for this style relative to a previous style.
     * Only outputs codes for attributes that differ from the previous style.
     *
     * @param prev the previous text style
     * @return the ANSI codes for changed attributes
     */
    public String getValueComparedToPrev(TerminalTextStyle prev) {
        StringBuilder builder = new StringBuilder();
        char SEPARATOR = ';';
        if (!this.equals(prev)) {
            if (prev.isBold() || prev.isFaint()) {
                if (builder.length() > 0)
                    builder.append(SEPARATOR);
                byte BOLD_OFF = 22;
                builder.append(BOLD_OFF);
            }
            if (prev.isUnderline()) {
                if (builder.length() > 0)
                    builder.append(SEPARATOR);
                byte UNDERLINE_OFF = 24;
                builder.append(UNDERLINE_OFF);
            }
            if (prev.isItalic()) {
                if (builder.length() > 0)
                    builder.append(SEPARATOR);
                byte ITALIC_OFF = 23;
                builder.append(ITALIC_OFF);
            }
            if (prev.isBlink()) {
                if (builder.length() > 0)
                    builder.append(SEPARATOR);
                byte BLINK_OFF = 25;
                builder.append(BLINK_OFF);
            }
            if (prev.isInvert()) {
                if (builder.length() > 0)
                    builder.append(SEPARATOR);
                byte INVERT_OFF = 27;
                builder.append(INVERT_OFF);
            }
            if (prev.isCrossedOut()) {
                if (builder.length() > 0)
                    builder.append(SEPARATOR);
                byte CROSSED_OUT_OFF = 29;
                builder.append(CROSSED_OUT_OFF);
            }
            if (prev.isConceal()) {
                if (builder.length() > 0)
                    builder.append(SEPARATOR);
                byte REVEAL = 28;
                builder.append(REVEAL);
            }
        }

        String str = toString();
        if (!str.isEmpty() && builder.length() > 0)
            return builder.append(SEPARATOR).append(str).toString();
        else
            return builder.append(str).toString();
    }
}
