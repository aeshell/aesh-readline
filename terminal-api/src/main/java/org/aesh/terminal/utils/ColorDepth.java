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
 * Represents the color depth capability of a terminal.
 * <p>
 * Terminals can support different levels of color:
 * <ul>
 * <li>{@link #NO_COLOR} - No color support (monochrome)</li>
 * <li>{@link #COLORS_8} - 8 basic ANSI colors (SGR 30-37, 40-47)</li>
 * <li>{@link #COLORS_16} - 16 colors (8 colors + bright variants)</li>
 * <li>{@link #COLORS_256} - 256 color palette (SGR 38;5;n)</li>
 * <li>{@link #TRUE_COLOR} - 24-bit true color support (SGR 38;2;r;g;b)</li>
 * </ul>
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public enum ColorDepth {

    /**
     * No color support - monochrome terminal
     */
    NO_COLOR(0),

    /**
     * 8 basic ANSI colors (black, red, green, yellow, blue, magenta, cyan, white)
     */
    COLORS_8(8),

    /**
     * 16 colors - 8 basic colors plus their bright/bold variants
     */
    COLORS_16(16),

    /**
     * 256 color palette support (xterm-256color)
     */
    COLORS_256(256),

    /**
     * 24-bit true color support (16 million colors)
     */
    TRUE_COLOR(16777216);

    private final int colorCount;

    ColorDepth(int colorCount) {
        this.colorCount = colorCount;
    }

    /**
     * Get the maximum number of colors supported at this depth.
     *
     * @return the maximum number of colors supported at this depth
     */
    public int getColorCount() {
        return colorCount;
    }

    /**
     * Check if this color depth supports at least 256 colors
     *
     * @return true if 256 colors or more are supported
     */
    public boolean supports256Colors() {
        return colorCount >= 256;
    }

    /**
     * Check if this color depth supports true color (24-bit)
     *
     * @return true if true color is supported
     */
    public boolean supportsTrueColor() {
        return this == TRUE_COLOR;
    }

    /**
     * Check if this color depth supports at least 16 colors
     *
     * @return true if 16 colors or more are supported
     */
    public boolean supports16Colors() {
        return colorCount >= 16;
    }

    /**
     * Check if this color depth supports any color
     *
     * @return true if any color is supported
     */
    public boolean supportsColor() {
        return colorCount > 0;
    }

    /**
     * Determine the color depth from the number of supported colors.
     *
     * @param colors the number of colors reported by the terminal
     * @return the corresponding ColorDepth
     */
    public static ColorDepth fromColorCount(int colors) {
        if (colors <= 0) {
            return NO_COLOR;
        } else if (colors < 16) {
            return COLORS_8;
        } else if (colors < 256) {
            return COLORS_16;
        } else if (colors < 16777216) {
            return COLORS_256;
        } else {
            return TRUE_COLOR;
        }
    }
}
