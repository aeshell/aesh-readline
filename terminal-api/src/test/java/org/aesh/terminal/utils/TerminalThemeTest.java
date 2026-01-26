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
 * Tests for TerminalTheme enum.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class TerminalThemeTest {

    @Test
    public void testIsDark() {
        assertTrue(TerminalTheme.DARK.isDark());
        assertFalse(TerminalTheme.LIGHT.isDark());
        assertTrue(TerminalTheme.UNKNOWN.isDark()); // UNKNOWN defaults to dark for safety
    }

    @Test
    public void testIsLight() {
        assertFalse(TerminalTheme.DARK.isLight());
        assertTrue(TerminalTheme.LIGHT.isLight());
        assertFalse(TerminalTheme.UNKNOWN.isLight());
    }

    @Test
    public void testFromRGB_DarkBackground() {
        // Black background
        assertEquals(TerminalTheme.DARK, TerminalTheme.fromRGB(0, 0, 0));
        // Dark gray
        assertEquals(TerminalTheme.DARK, TerminalTheme.fromRGB(50, 50, 50));
        // Dark blue
        assertEquals(TerminalTheme.DARK, TerminalTheme.fromRGB(0, 0, 128));
        // Dark red
        assertEquals(TerminalTheme.DARK, TerminalTheme.fromRGB(128, 0, 0));
    }

    @Test
    public void testFromRGB_LightBackground() {
        // White background
        assertEquals(TerminalTheme.LIGHT, TerminalTheme.fromRGB(255, 255, 255));
        // Light gray
        assertEquals(TerminalTheme.LIGHT, TerminalTheme.fromRGB(200, 200, 200));
        // Light yellow
        assertEquals(TerminalTheme.LIGHT, TerminalTheme.fromRGB(255, 255, 200));
    }

    @Test
    public void testFromRGB_MidTones() {
        // Luminance = 0.299 * R + 0.587 * G + 0.114 * B
        // Threshold is 128

        // Pure green at 128 -> luminance = 0.587 * 128 = 75.1 -> DARK
        assertEquals(TerminalTheme.DARK, TerminalTheme.fromRGB(0, 128, 0));

        // Pure green at 218 -> luminance = 0.587 * 218 = 127.97 -> DARK (just below)
        assertEquals(TerminalTheme.DARK, TerminalTheme.fromRGB(0, 218, 0));

        // Pure green at 219 -> luminance = 0.587 * 219 = 128.55 -> LIGHT (just above)
        assertEquals(TerminalTheme.LIGHT, TerminalTheme.fromRGB(0, 219, 0));
    }

    @Test
    public void testFromGrayscale() {
        assertEquals(TerminalTheme.DARK, TerminalTheme.fromGrayscale(0));
        assertEquals(TerminalTheme.DARK, TerminalTheme.fromGrayscale(64));
        assertEquals(TerminalTheme.DARK, TerminalTheme.fromGrayscale(128));
        assertEquals(TerminalTheme.LIGHT, TerminalTheme.fromGrayscale(129));
        assertEquals(TerminalTheme.LIGHT, TerminalTheme.fromGrayscale(200));
        assertEquals(TerminalTheme.LIGHT, TerminalTheme.fromGrayscale(255));
    }
}
