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

public class TerminalImageBuilderTest {

    @Test
    public void testBuilderWithProtocol() {
        TerminalImageBuilder builder = TerminalImageBuilder.builder(ImageProtocol.KITTY);
        assertEquals(ImageProtocol.KITTY, builder.getProtocol());
        assertTrue(builder.isSupported());
    }

    @Test
    public void testBuilderWithNoneProtocol() {
        TerminalImageBuilder builder = TerminalImageBuilder.builder(ImageProtocol.NONE);
        assertEquals(ImageProtocol.NONE, builder.getProtocol());
        assertFalse(builder.isSupported());
    }

    @Test
    public void testBuildKittyImage() {
        byte[] data = "test".getBytes();
        TerminalImage image = TerminalImageBuilder.builder(ImageProtocol.KITTY)
                .data(data)
                .widthCells(40)
                .build();

        assertNotNull(image);
        assertTrue(image instanceof KittyImage);
        assertEquals(ImageProtocol.KITTY, image.getProtocol());
        assertEquals(40, image.getWidthCells());
    }

    @Test
    public void testBuildITermImage() {
        byte[] data = "test".getBytes();
        TerminalImage image = TerminalImageBuilder.builder(ImageProtocol.ITERM2)
                .data(data)
                .filename("test.png")
                .widthCells(40)
                .heightCells(20)
                .build();

        assertNotNull(image);
        assertTrue(image instanceof ITermImage);
        assertEquals(ImageProtocol.ITERM2, image.getProtocol());
        assertEquals(40, image.getWidthCells());
        assertEquals(20, image.getHeightCells());
    }

    @Test
    public void testBuildWithPixels() {
        byte[] data = "test".getBytes();
        TerminalImage image = TerminalImageBuilder.builder(ImageProtocol.ITERM2)
                .data(data)
                .widthPixels(800)
                .heightPixels(600)
                .build();

        assertNotNull(image);
        assertTrue(image instanceof ITermImage);
        // widthCells should be -1 since we used pixels
        assertEquals(-1, image.getWidthCells());
    }

    @Test
    public void testBuildWithPreserveAspectRatio() {
        byte[] data = "test".getBytes();
        ITermImage image = (ITermImage) TerminalImageBuilder.builder(ImageProtocol.ITERM2)
                .data(data)
                .preserveAspectRatio(false)
                .build();

        assertNotNull(image);
        String encoded = image.encode();
        assertTrue(encoded.contains("preserveAspectRatio=0"));
    }

    @Test
    public void testBuildNoneReturnsNull() {
        byte[] data = "test".getBytes();
        TerminalImage image = TerminalImageBuilder.builder(ImageProtocol.NONE)
                .data(data)
                .build();

        assertNull(image);
    }

    @Test(expected = IllegalStateException.class)
    public void testBuildWithoutDataThrows() {
        TerminalImageBuilder.builder(ImageProtocol.KITTY).build();
    }

    @Test
    public void testBuildSixelImage() {
        byte[] data = createTestPng();
        TerminalImage image = TerminalImageBuilder.builder(ImageProtocol.SIXEL)
                .data(data)
                .widthPixels(100)
                .build();

        assertNotNull(image);
        assertTrue(image instanceof SixelImage);
        assertEquals(ImageProtocol.SIXEL, image.getProtocol());
    }

    private byte[] createTestPng() {
        try {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    10, 10, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = img.createGraphics();
            g.setColor(java.awt.Color.RED);
            g.fillRect(0, 0, 10, 10);
            g.dispose();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "PNG", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test PNG", e);
        }
    }

    @Test
    public void testDetectProtocolWithNullDevice() {
        ImageProtocol protocol = TerminalImageBuilder.detectProtocol(null);
        assertEquals(ImageProtocol.NONE, protocol);
    }

    @Test
    public void testWidthCellsClearsPixels() {
        byte[] data = "test".getBytes();
        TerminalImage image = TerminalImageBuilder.builder(ImageProtocol.ITERM2)
                .data(data)
                .widthPixels(800) // Set pixels first
                .widthCells(40) // Then set cells (should clear pixels)
                .build();

        assertNotNull(image);
        String encoded = image.encode();
        // Should have cells, not pixels
        assertTrue(encoded.contains("width=40"));
        assertFalse(encoded.contains("width=800px"));
    }
}
