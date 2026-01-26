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
 * Tests for ColorDepth enum.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class ColorDepthTest {

    @Test
    public void testColorCounts() {
        assertEquals(0, ColorDepth.NO_COLOR.getColorCount());
        assertEquals(8, ColorDepth.COLORS_8.getColorCount());
        assertEquals(16, ColorDepth.COLORS_16.getColorCount());
        assertEquals(256, ColorDepth.COLORS_256.getColorCount());
        assertEquals(16777216, ColorDepth.TRUE_COLOR.getColorCount());
    }

    @Test
    public void testSupportsColor() {
        assertFalse(ColorDepth.NO_COLOR.supportsColor());
        assertTrue(ColorDepth.COLORS_8.supportsColor());
        assertTrue(ColorDepth.COLORS_16.supportsColor());
        assertTrue(ColorDepth.COLORS_256.supportsColor());
        assertTrue(ColorDepth.TRUE_COLOR.supportsColor());
    }

    @Test
    public void testSupports16Colors() {
        assertFalse(ColorDepth.NO_COLOR.supports16Colors());
        assertFalse(ColorDepth.COLORS_8.supports16Colors());
        assertTrue(ColorDepth.COLORS_16.supports16Colors());
        assertTrue(ColorDepth.COLORS_256.supports16Colors());
        assertTrue(ColorDepth.TRUE_COLOR.supports16Colors());
    }

    @Test
    public void testSupports256Colors() {
        assertFalse(ColorDepth.NO_COLOR.supports256Colors());
        assertFalse(ColorDepth.COLORS_8.supports256Colors());
        assertFalse(ColorDepth.COLORS_16.supports256Colors());
        assertTrue(ColorDepth.COLORS_256.supports256Colors());
        assertTrue(ColorDepth.TRUE_COLOR.supports256Colors());
    }

    @Test
    public void testSupportsTrueColor() {
        assertFalse(ColorDepth.NO_COLOR.supportsTrueColor());
        assertFalse(ColorDepth.COLORS_8.supportsTrueColor());
        assertFalse(ColorDepth.COLORS_16.supportsTrueColor());
        assertFalse(ColorDepth.COLORS_256.supportsTrueColor());
        assertTrue(ColorDepth.TRUE_COLOR.supportsTrueColor());
    }

    @Test
    public void testFromColorCount() {
        assertEquals(ColorDepth.NO_COLOR, ColorDepth.fromColorCount(0));
        assertEquals(ColorDepth.NO_COLOR, ColorDepth.fromColorCount(-1));
        assertEquals(ColorDepth.COLORS_8, ColorDepth.fromColorCount(8));
        assertEquals(ColorDepth.COLORS_8, ColorDepth.fromColorCount(1));
        assertEquals(ColorDepth.COLORS_8, ColorDepth.fromColorCount(15));
        assertEquals(ColorDepth.COLORS_16, ColorDepth.fromColorCount(16));
        assertEquals(ColorDepth.COLORS_16, ColorDepth.fromColorCount(88));
        assertEquals(ColorDepth.COLORS_256, ColorDepth.fromColorCount(256));
        assertEquals(ColorDepth.COLORS_256, ColorDepth.fromColorCount(1000));
        assertEquals(ColorDepth.TRUE_COLOR, ColorDepth.fromColorCount(16777216));
        assertEquals(ColorDepth.TRUE_COLOR, ColorDepth.fromColorCount(Integer.MAX_VALUE));
    }
}
