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
package org.aesh.terminal.image;

import static org.junit.Assert.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.aesh.terminal.detect.ImageProtocol;
import org.junit.Test;

public class SixelImageTest {

    @Test
    public void testBasicEncode() {
        byte[] data = createTestPng(10, 10, Color.RED);
        SixelImage image = new SixelImage(data);

        String encoded = image.encode();

        // Should start with DCS (ESC P)
        assertTrue("Should start with DCS", encoded.startsWith("\u001BP"));
        // Should end with ST (ESC \)
        assertTrue("Should end with ST", encoded.endsWith("\u001B\\"));
        // Should contain sixel mode identifier 'q'
        assertTrue("Should contain 'q' identifier", encoded.contains("q"));
        // Should contain color definitions
        assertTrue("Should contain color definition", encoded.contains("#0;2;"));
    }

    @Test
    public void testGetProtocol() {
        SixelImage image = new SixelImage(createTestPng(10, 10, Color.BLUE));
        assertEquals(ImageProtocol.SIXEL, image.getProtocol());
    }

    @Test
    public void testMaxWidth() {
        byte[] data = createTestPng(100, 50, Color.GREEN);
        SixelImage image = new SixelImage(data).maxWidth(50);

        String encoded = image.encode();
        assertNotNull(encoded);
        // Raster attributes should show scaled dimensions
        // Format: "Pan;Pad;Ph;Pv where Ph is width
        assertTrue("Should contain raster attributes", encoded.contains("\"1;1;"));
    }

    @Test
    public void testMaxHeight() {
        byte[] data = createTestPng(50, 100, Color.YELLOW);
        SixelImage image = new SixelImage(data).maxHeight(50);

        String encoded = image.encode();
        assertNotNull(encoded);
    }

    @Test
    public void testMaxColors() {
        byte[] data = createGradientPng(50, 50);
        SixelImage image = new SixelImage(data).maxColors(16);

        String encoded = image.encode();
        assertNotNull(encoded);
        // Should have color definitions but limited to 16
    }

    @Test
    public void testChangingMaxWidthInvalidatesCache() {
        byte[] data = createTestPng(100, 50, Color.GREEN);
        SixelImage image = new SixelImage(data);

        assertTrue(image.encode().contains("\"1;1;100;50"));

        image.maxWidth(50);
        String reencoded = image.encode();
        assertTrue("new width must apply, not the cached raster",
                reencoded.contains("\"1;1;50;25"));
    }

    @Test
    public void testChangingMaxHeightInvalidatesCache() {
        byte[] data = createTestPng(50, 100, Color.GREEN);
        SixelImage image = new SixelImage(data);

        assertTrue(image.encode().contains("\"1;1;50;100"));

        image.maxHeight(50);
        String reencoded = image.encode();
        assertTrue("new height must apply, not the cached raster",
                reencoded.contains("\"1;1;25;50"));
    }

    @Test
    public void testChangingMaxColorsInvalidatesCache() {
        byte[] data = createSixteenColorPng();
        SixelImage image = new SixelImage(data);

        assertEquals(16, paletteDefinitions(image.encode()).size());

        image.maxColors(2);
        assertEquals("new palette limit must apply, not the cached one",
                2, paletteDefinitions(image.encode()).size());
    }

    @Test
    public void testChangingUseRleInvalidatesCache() {
        byte[] data = createSolidColorPng(100, 12, Color.MAGENTA);
        SixelImage image = new SixelImage(data);

        assertTrue(image.encode().contains("!"));

        image.useRle(false);
        assertTrue("disabling RLE must apply, not the cached encoding",
                !image.encode().contains("!"));
    }

    @Test
    public void testUnchangedOptionsRetainCache() {
        byte[] data = createTestPng(10, 10, Color.RED);
        SixelImage image = new SixelImage(data);

        assertSame(image.encode(), image.encode());
    }

    @Test
    public void testSmallPalettesEncode() {
        byte[] data = createSixteenColorPng();
        int[] limits = { 2, 3, 7, 8, 256 };
        int[] expectedCounts = { 2, 3, 7, 8, 16 };
        for (int i = 0; i < limits.length; i++) {
            String encoded = new SixelImage(data).maxColors(limits[i]).encode();
            assertNotNull("limit " + limits[i], encoded);
            assertEquals("limit " + limits[i] + " definition count",
                    expectedCounts[i], paletteDefinitions(encoded).size());
        }
    }

    @Test
    public void testTwoColorPaletteSemantics() {
        // Classic cube corners in enumeration order: black, blue, ...
        byte[] data = createSixteenColorPng();
        Map<Integer, String> defs = paletteDefinitions(
                new SixelImage(data).maxColors(2).encode());
        assertEquals("0;0;0", defs.get(0));
        assertEquals("0;0;100", defs.get(1));
    }

    @Test
    public void testSevenColorPaletteSemantics() {
        byte[] data = createSixteenColorPng();
        Map<Integer, String> defs = paletteDefinitions(
                new SixelImage(data).maxColors(7).encode());
        assertEquals(7, defs.size());
        assertEquals("100;100;0", defs.get(6));
        assertTrue("white must be capped away", !defs.containsKey(7));
    }

    @Test
    public void testEightColorPaletteIsFullCube() {
        byte[] data = createSixteenColorPng();
        Map<Integer, String> defs = paletteDefinitions(
                new SixelImage(data).maxColors(8).encode());
        assertEquals(8, defs.size());
        assertEquals("100;100;100", defs.get(7));
    }

    @Test
    public void testClosestMappingOnTinyPalette() {
        // 30x6: vertical red/green/blue thirds, exactly one sixel band.
        // At maxColors(2) the palette is black+blue: red and green thirds
        // map to index 0, the blue third to index 1.
        byte[] data = createThreeColorPng();
        String encoded = new SixelImage(data).maxColors(2).useRle(false).encode();

        assertEquals("~~~~~~~~~~~~~~~~~~~~??????????", dataRow(encoded, 0));
        assertEquals("????????????????????~~~~~~~~~~", dataRow(encoded, 1));
    }

    @Test
    public void testTallNarrowImageKeepsPositiveWidth() {
        byte[] data = createTestPng(1, 100, Color.RED);
        String encoded = new SixelImage(data).maxHeight(10).encode();

        assertNotNull(encoded);
        assertTrue("raster must stay 1x10, never 0-wide",
                encoded.contains("\"1;1;1;10"));
    }

    @Test
    public void testWideShortImageKeepsPositiveHeight() {
        byte[] data = createTestPng(100, 1, Color.RED);
        String encoded = new SixelImage(data).maxWidth(10).encode();

        assertNotNull(encoded);
        assertTrue("raster must stay 10x1, never 0-high",
                encoded.contains("\"1;1;10;1"));
    }

    @Test
    public void testBothLimitsTogether() {
        byte[] data = createTestPng(100, 100, Color.RED);
        String encoded = new SixelImage(data).maxWidth(10).maxHeight(5).encode();

        assertNotNull(encoded);
        assertTrue(encoded.contains("\"1;1;5;5"));
    }

    @Test
    public void testNoResizeKeepsDimensions() {
        byte[] data = createTestPng(10, 10, Color.RED);
        String encoded = new SixelImage(data).maxWidth(100).maxHeight(100).encode();

        assertNotNull(encoded);
        assertTrue(encoded.contains("\"1;1;10;10"));
    }

    @Test
    public void testScaledDimensions() {
        assertArrayEquals(new int[] { 10, 5 },
                SixelImage.scaledDimensions(100, 50, 10, -1));
        assertArrayEquals(new int[] { 5, 5 },
                SixelImage.scaledDimensions(100, 100, 10, 5));
        assertArrayEquals(new int[] { 10, 10 },
                SixelImage.scaledDimensions(10, 10, 100, 100));
        assertArrayEquals(new int[] { 1, 10 },
                SixelImage.scaledDimensions(1, 100, -1, 10));
        assertArrayEquals(new int[] { 10, 1 },
                SixelImage.scaledDimensions(100, 1, 10, -1));
    }

    @Test
    public void testScaledDimensionsOverflow() {
        // Intermediates reach 4e9 and 8e9: int math overflows, long holds.
        assertArrayEquals(new int[] { 40000, 40000 },
                SixelImage.scaledDimensions(100000, 100000, 40000, -1));
        assertArrayEquals(new int[] { 80000, 40000 },
                SixelImage.scaledDimensions(200000, 100000, -1, 40000));
    }

    @Test
    public void testRleEncoding() {
        // Create an image with runs of same color (good for RLE)
        byte[] data = createSolidColorPng(100, 12, Color.MAGENTA);
        SixelImage image = new SixelImage(data).useRle(true);

        String encoded = image.encode();
        // RLE uses ! followed by count
        assertTrue("Should use RLE encoding", encoded.contains("!"));
    }

    @Test
    public void testNoRle() {
        byte[] data = createSolidColorPng(20, 12, Color.CYAN);
        SixelImage imageWithRle = new SixelImage(data).useRle(true);
        SixelImage imageNoRle = new SixelImage(data).useRle(false);

        String encodedWithRle = imageWithRle.encode();
        String encodedNoRle = imageNoRle.encode();

        // Without RLE, output should be longer (no compression)
        assertTrue("RLE should produce shorter output",
                encodedWithRle.length() <= encodedNoRle.length());
    }

    @Test
    public void testFromBytes() {
        byte[] data = createTestPng(10, 10, Color.ORANGE);
        SixelImage image = SixelImage.fromBytes(data);

        assertNotNull(image);
        assertEquals(ImageProtocol.SIXEL, image.getProtocol());
    }

    @Test
    public void testSixelBands() {
        // Create image with height > 6 to test multiple sixel bands
        byte[] data = createTestPng(10, 18, Color.PINK);
        SixelImage image = new SixelImage(data);

        String encoded = image.encode();
        // Should contain newline markers (-) for sixel bands
        assertTrue("Should have sixel band separators", encoded.contains("-"));
    }

    @Test
    public void testJpegInput() {
        byte[] data = createTestJpeg(20, 20, Color.DARK_GRAY);
        SixelImage image = new SixelImage(data);

        // Should handle JPEG input
        String encoded = image.encode();
        assertNotNull(encoded);
        assertTrue(encoded.startsWith("\u001BP"));
    }

    private byte[] createTestPng(int width, int height, Color color) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(color);
            g.fillRect(0, 0, width, height);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test PNG", e);
        }
    }

    private byte[] createSolidColorPng(int width, int height, Color color) {
        return createTestPng(width, height, color);
    }

    private byte[] createGradientPng(int width, int height) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    g.setColor(new Color(
                            (x * 255) / width,
                            (y * 255) / height,
                            ((x + y) * 128) / (width + height)));
                    g.fillRect(x, y, 1, 1);
                }
            }
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create gradient PNG", e);
        }
    }

    private byte[] createTestJpeg(int width, int height, Color color) {
        try {
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(color);
            g.fillRect(0, 0, width, height);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "JPEG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test JPEG", e);
        }
    }

    private byte[] createThreeColorPng() {
        try {
            BufferedImage img = new BufferedImage(30, 6, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setColor(Color.RED);
            g.fillRect(0, 0, 10, 6);
            g.setColor(Color.GREEN);
            g.fillRect(10, 0, 10, 6);
            g.setColor(Color.BLUE);
            g.fillRect(20, 0, 10, 6);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create three-color PNG", e);
        }
    }

    private byte[] createSixteenColorPng() {
        Color[] colors = { Color.BLACK, Color.WHITE, Color.RED, Color.GREEN,
                Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW,
                Color.GRAY, Color.ORANGE, Color.PINK, new Color(1, 2, 3),
                new Color(200, 100, 50), new Color(10, 200, 30),
                new Color(90, 90, 200), new Color(30, 30, 30) };
        try {
            BufferedImage img = new BufferedImage(16, 1, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            for (int x = 0; x < colors.length; x++) {
                g.setColor(colors[x]);
                g.fillRect(x, 0, 1, 1);
            }
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create sixteen-color PNG", e);
        }
    }

    private static Map<Integer, String> paletteDefinitions(String encoded) {
        Map<Integer, String> defs = new HashMap<>();
        Matcher m = Pattern.compile("#(\\d+);2;(\\d+);(\\d+);(\\d+)").matcher(encoded);
        while (m.find()) {
            defs.put(Integer.parseInt(m.group(1)),
                    m.group(2) + ";" + m.group(3) + ";" + m.group(4));
        }
        return defs;
    }

    private static String dataRow(String encoded, int color) {
        Matcher m = Pattern.compile("#" + color + "([?-~]+)").matcher(encoded);
        if (m.find()) {
            return m.group(1);
        }
        return "";
    }
}
