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
 * Represents the detected terminal background theme (light or dark).
 * <p>
 * This is useful for selecting appropriate foreground colors that will
 * be readable against the terminal's background color.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public enum TerminalTheme {

    /**
     * Dark background theme (e.g., black, dark gray, dark blue background).
     * Light foreground colors work best with this theme.
     */
    DARK,

    /**
     * Light background theme (e.g., white, light gray background).
     * Dark foreground colors work best with this theme.
     */
    LIGHT,

    /**
     * Theme could not be determined.
     * Applications should fall back to a safe default (typically assuming dark theme).
     */
    UNKNOWN;

    /**
     * Check if this is a dark theme.
     *
     * @return true if the theme is dark or unknown (safe default)
     */
    public boolean isDark() {
        return this == DARK || this == UNKNOWN;
    }

    /**
     * Check if this is a light theme.
     *
     * @return true if the theme is explicitly light
     */
    public boolean isLight() {
        return this == LIGHT;
    }

    /**
     * Determine theme from RGB background color values.
     * Uses the perceived luminance formula to determine if the background
     * is light or dark.
     *
     * @param red red component (0-255)
     * @param green green component (0-255)
     * @param blue blue component (0-255)
     * @return LIGHT if luminance > 128, DARK otherwise
     */
    public static TerminalTheme fromRGB(int red, int green, int blue) {
        // Use perceived luminance formula: 0.299*R + 0.587*G + 0.114*B
        double luminance = 0.299 * red + 0.587 * green + 0.114 * blue;
        return luminance > 128 ? LIGHT : DARK;
    }

    /**
     * Determine theme from a grayscale value.
     *
     * @param gray grayscale value (0-255, where 0 is black and 255 is white)
     * @return LIGHT if gray > 128, DARK otherwise
     */
    public static TerminalTheme fromGrayscale(int gray) {
        return gray > 128 ? LIGHT : DARK;
    }
}
