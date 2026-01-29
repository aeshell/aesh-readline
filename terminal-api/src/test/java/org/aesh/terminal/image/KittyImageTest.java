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

import org.junit.Test;

public class KittyImageTest {

    // Minimal valid 1x1 pixel PNG (red pixel)
    private static final byte[] MINIMAL_PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, // PNG signature
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52, // IHDR length + type
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, // 1x1 pixel
            0x08, 0x02, 0x00, 0x00, 0x00, (byte) 0x90, 0x77, 0x53, // 8-bit RGB + CRC
            (byte) 0xDE,
            0x00, 0x00, 0x00, 0x0C, 0x49, 0x44, 0x41, 0x54, // IDAT length + type
            0x08, (byte) 0xD7, 0x63, (byte) 0xF8, (byte) 0xCF, (byte) 0xC0, 0x00, 0x00,
            0x00, 0x03, 0x00, 0x01, 0x00, 0x05, (byte) 0xFE, (byte) 0xD4,
            0x00, 0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, // IEND
            (byte) 0xAE, 0x42, 0x60, (byte) 0x82
    };

    @Test
    public void testBasicEncode() {
        KittyImage image = new KittyImage(MINIMAL_PNG);

        String encoded = image.encode();

        // Should start with APC (ESC _G)
        assertTrue(encoded.startsWith("\u001B_G"));
        // Should end with ST (ESC \)
        assertTrue(encoded.endsWith("\u001B\\"));
        // Should have action=T (transmit and display)
        assertTrue(encoded.contains("a=T"));
        // Should have format=100 (PNG)
        assertTrue(encoded.contains("f=100"));
        // Should have transmission=d (direct)
        assertTrue(encoded.contains("t=d"));
    }

    @Test
    public void testWidthCells() {
        KittyImage image = new KittyImage(MINIMAL_PNG).widthCells(40);

        String encoded = image.encode();
        assertTrue(encoded.contains("c=40"));
        assertEquals(40, image.getWidthCells());
    }

    @Test
    public void testHeightCells() {
        KittyImage image = new KittyImage(MINIMAL_PNG).heightCells(20);

        String encoded = image.encode();
        assertTrue(encoded.contains("r=20"));
        assertEquals(20, image.getHeightCells());
    }

    @Test
    public void testSourceDimensions() {
        KittyImage image = new KittyImage(MINIMAL_PNG).sourceDimensions(800, 600);

        String encoded = image.encode();
        assertTrue(encoded.contains("s=800"));
        assertTrue(encoded.contains("v=600"));
    }

    @Test
    public void testZIndex() {
        KittyImage image = new KittyImage(MINIMAL_PNG).zIndex(5);

        String encoded = image.encode();
        assertTrue(encoded.contains("z=5"));
    }

    @Test
    public void testSuppressResponse() {
        // Default is suppress=true (q=2)
        KittyImage image1 = new KittyImage(MINIMAL_PNG);
        assertTrue(image1.encode().contains("q=2"));

        // Explicitly disable suppress
        KittyImage image2 = new KittyImage(MINIMAL_PNG).suppressResponse(false);
        assertFalse(image2.encode().contains("q=2"));
    }

    @Test
    public void testChunking() {
        // Create a larger PNG by encoding multiple times
        // We need more than 4096 base64 chars (~3KB of binary data)
        // Create synthetic large PNG-like data that will be passed through
        // Since we use PNG data, we can test with actual PNG encoding

        // For chunking test, use a moderately sized PNG
        // The minimal PNG is small, so create an image using ImageIO
        byte[] largeData = createLargePngData();
        KittyImage image = new KittyImage(largeData);

        String encoded = image.encode();

        // Should have multiple chunks (m=1 for continuation)
        assertTrue("Should have continuation marker", encoded.contains("m=1"));
        // Last chunk should have m=0
        assertTrue("Should have final marker", encoded.contains("m=0"));
        // Should have multiple APC sequences
        int apcCount = countOccurrences(encoded, "\u001B_G");
        assertTrue("Should have multiple APC sequences", apcCount > 1);
    }

    @Test
    public void testGetProtocol() {
        KittyImage image = new KittyImage(MINIMAL_PNG);
        assertEquals(ImageProtocol.KITTY, image.getProtocol());
    }

    @Test
    public void testFromBytes() {
        KittyImage image = KittyImage.fromBytes(MINIMAL_PNG);

        assertNotNull(image);
        assertEquals(ImageProtocol.KITTY, image.getProtocol());
    }

    @Test
    public void testPngPassthrough() {
        // PNG data should pass through without conversion
        assertTrue(ImageUtils.isPng(MINIMAL_PNG));
        KittyImage image = new KittyImage(MINIMAL_PNG);
        // Should not throw - PNG is passed through directly
        assertNotNull(image.encode());
    }

    @Test
    public void testJpegConversion() {
        // Create a JPEG image and verify it gets converted to PNG
        byte[] jpegData = createJpegData();
        assertTrue("Should be detected as JPEG", ImageUtils.isJpeg(jpegData));
        assertFalse("Should not be PNG", ImageUtils.isPng(jpegData));

        KittyImage image = new KittyImage(jpegData);
        // Should not throw - JPEG is converted to PNG automatically
        String encoded = image.encode();
        assertNotNull(encoded);
        assertTrue(encoded.contains("f=100")); // Still uses PNG format
    }

    private byte[] createJpegData() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.BLUE);
            g.fillRect(0, 0, 10, 10);
            g.dispose();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "JPEG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test JPEG", e);
        }
    }

    private byte[] createLargePngData() {
        try {
            // Create a large image to ensure chunking (need > 3KB for >4096 base64 chars)
            // A 200x200 image with varied colors should produce enough data
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    200, 200, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            // Draw a gradient pattern to prevent compression from making it too small
            for (int y = 0; y < 200; y++) {
                for (int x = 0; x < 200; x++) {
                    g.setColor(new java.awt.Color(x % 256, y % 256, (x + y) % 256));
                    g.fillRect(x, y, 1, 1);
                }
            }
            g.dispose();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test image", e);
        }
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
