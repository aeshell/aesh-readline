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

import javax.imageio.ImageIO;

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
}
