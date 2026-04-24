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

import java.io.PrintStream;

import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.CodePointUtils;

/**
 * Value object that describe how a string should be displayed
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class TerminalString implements Comparable<TerminalString> {

    private String characters;
    private final TerminalTextStyle style;
    private final TerminalColor color;
    private boolean ignoreRendering;
    private int ansiLength = 0;
    private String hyperlinkUrl;

    /**
     * Create a terminal string with the specified color and style.
     *
     * @param chars the character content
     * @param color the terminal color
     * @param style the text style
     */
    public TerminalString(String chars, TerminalColor color, TerminalTextStyle style) {
        this.characters = chars;
        if (color != null)
            this.color = color;
        else
            this.color = new TerminalColor();
        if (style != null)
            this.style = style;
        else
            this.style = new TerminalTextStyle();
    }

    /**
     * Create a terminal string with the specified color.
     *
     * @param chars the character content
     * @param color the terminal color
     */
    public TerminalString(String chars, TerminalColor color) {
        this(chars, color, new TerminalTextStyle());
    }

    /**
     * Create a terminal string with the specified style.
     *
     * @param chars the character content
     * @param style the text style
     */
    public TerminalString(String chars, TerminalTextStyle style) {
        this(chars, new TerminalColor(), style);
    }

    /**
     * Create a terminal string with default formatting.
     *
     * @param chars the character content
     */
    public TerminalString(String chars) {
        this(chars, new TerminalColor(), new TerminalTextStyle());
    }

    /**
     * Create a terminal string with optional rendering.
     *
     * @param chars the character content
     * @param ignoreRendering if true, no ANSI codes will be applied
     */
    public TerminalString(String chars, boolean ignoreRendering) {
        this(chars, new TerminalColor(), new TerminalTextStyle());
        this.ignoreRendering = ignoreRendering;
    }

    /**
     * Create a terminal string with a hyperlink URL, color and style.
     * When rendered, the text will be wrapped in OSC 8 hyperlink escape sequences.
     *
     * @param chars the character content
     * @param hyperlinkUrl the URL for the hyperlink
     * @param color the terminal color
     * @param style the text style
     */
    public TerminalString(String chars, String hyperlinkUrl, TerminalColor color, TerminalTextStyle style) {
        this(chars, color, style);
        this.hyperlinkUrl = hyperlinkUrl;
    }

    /**
     * Get the character content.
     *
     * @return the characters
     */
    public String getCharacters() {
        return characters;
    }

    /**
     * Set the character content.
     *
     * @param chars the new characters
     */
    public void setCharacters(String chars) {
        this.characters = chars;
    }

    /**
     * Get the hyperlink URL.
     *
     * @return the hyperlink URL, or null if not set
     */
    public String getHyperlinkUrl() {
        return hyperlinkUrl;
    }

    /**
     * Set the hyperlink URL.
     *
     * @param url the hyperlink URL
     */
    public void setHyperlinkUrl(String url) {
        this.hyperlinkUrl = url;
        this.ansiLength = 0; // reset cached length
    }

    /**
     * Check if the string contains spaces.
     *
     * @return true if spaces are present
     */
    public boolean containSpaces() {
        return characters.indexOf(CodePointUtils.SPACE_CHAR) > 0;
    }

    /**
     * Replace spaces with escaped spaces in the character content.
     */
    public void switchSpacesToEscapedSpaces() {
        characters = CodePointUtils.switchSpacesToEscapedSpacesInWord(characters);
    }

    /**
     * Get the text style.
     *
     * @return the text style
     */
    public TerminalTextStyle getStyle() {
        return style;
    }

    /**
     * Get the length of ANSI escape sequences.
     *
     * @return the ANSI code length, or 0 if rendering is ignored
     */
    public int getANSILength() {
        if (ignoreRendering)
            return 0;
        else {
            if (ansiLength == 0) {
                ansiLength = ANSI.START.length() + color.getLength() +
                        style.getLength() + ANSI.RESET.length() + 2; // ; + m
                if (hyperlinkUrl != null) {
                    ansiLength += ANSI.buildHyperlinkStart(hyperlinkUrl, null).length()
                            + ANSI.buildHyperlinkEnd().length();
                }
            }
            return ansiLength;
        }
    }

    /**
     * Create a new terminal string with the same rendering attributes.
     *
     * @param chars the new character content
     * @return a new terminal string with the same style and color
     */
    public TerminalString cloneRenderingAttributes(String chars) {
        if (ignoreRendering)
            return new TerminalString(chars, true);
        else {
            TerminalString ts = new TerminalString(chars, color, style);
            ts.hyperlinkUrl = this.hyperlinkUrl;
            return ts;
        }
    }

    /**
     * Check if this string has any formatting applied.
     *
     * @return true if color or style formatting is applied
     */
    public boolean isFormatted() {
        return !ignoreRendering && (color.isFormatted() || style.isFormatted());
    }

    /**
     * Get the string representation relative to a previous terminal string.
     * Only outputs ANSI codes for attributes that differ.
     *
     * @param prev the previous terminal string
     * @return the formatted string with ANSI codes
     */
    public String toString(TerminalString prev) {
        if (ignoreRendering)
            return characters;
        if (equalsIgnoreCharacter(prev))
            return characters;
        else {
            StringBuilder builder = new StringBuilder();
            if (hyperlinkUrl != null) {
                builder.append(ANSI.buildHyperlinkStart(hyperlinkUrl, null));
            }
            builder.append(ANSI.START)
                    .append(style.getValueComparedToPrev(prev.getStyle()));

            if (!this.color.equals(prev.color)) {
                if (prev.getStyle().isInvert())
                    builder.append(';').append(this.color);
                else
                    builder.append(';').append(this.color.toString(prev.color));
            }

            builder.append('m').append(getCharacters());
            if (hyperlinkUrl != null) {
                builder.append(ANSI.buildHyperlinkEnd());
            }
            return builder.toString();
        }
    }

    @Override
    public String toString() {
        if (ignoreRendering)
            return characters;
        String content = ANSI.START + style.toString() + ';' +
                this.color.toString() +
                'm' + getCharacters() + ANSI.RESET;
        if (hyperlinkUrl != null) {
            return ANSI.buildHyperlinkStart(hyperlinkUrl, null) + content + ANSI.buildHyperlinkEnd();
        }
        return content;
    }

    /**
     * Write this terminal string to a print stream.
     *
     * @param out the output stream to write to
     */
    public void write(PrintStream out) {
        if (ignoreRendering) {
            out.print(characters);
        } else {
            out.print(ANSI.START);
            out.print(style.toString());
            out.print(';');
            this.color.write(out);
            out.print('m');
            out.print(getCharacters());
        }
    }

    /**
     * Check if this terminal string has the same style and color as another,
     * ignoring the actual character content.
     *
     * @param that the terminal string to compare with
     * @return true if style and color are equal
     */
    public boolean equalsIgnoreCharacter(TerminalString that) {
        if (style != that.style)
            return false;
        if (ignoreRendering != that.ignoreRendering)
            return false;
        return color.equals(that.color);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TerminalString))
            return false;

        TerminalString that = (TerminalString) o;

        if (ignoreRendering) {
            return characters.equals(that.characters);
        }

        if (!characters.equals(that.characters))
            return false;
        if (!color.equals(that.color))
            return false;
        return style == that.style;
    }

    @Override
    public int hashCode() {
        int result = characters.hashCode();
        result = 31 * result + color.hashCode();
        result = 31 * result + style.hashCode();
        return result;
    }

    @Override
    public int compareTo(TerminalString terminalString) {
        return this.characters.compareTo(terminalString.getCharacters());
    }
}
