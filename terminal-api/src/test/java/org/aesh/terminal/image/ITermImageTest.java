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

import java.util.Base64;

import org.junit.Test;

public class ITermImageTest {

    @Test
    public void testBasicEncode() {
        byte[] data = "test".getBytes();
        ITermImage image = new ITermImage(data, "test.png");

        String encoded = image.encode();

        // Should start with OSC 1337
        assertTrue(encoded.startsWith("\u001B]1337;File="));
        // Should end with BEL by default
        assertTrue(encoded.endsWith("\u0007"));
        // Should contain base64 encoded data
        String expectedData = Base64.getEncoder().encodeToString(data);
        assertTrue(encoded.contains(":" + expectedData));
        // Should have inline=1
        assertTrue(encoded.contains("inline=1"));
        // Should have size
        assertTrue(encoded.contains("size=" + data.length));
    }

    @Test
    public void testFilename() {
        byte[] data = "test".getBytes();
        ITermImage image = new ITermImage(data, "myimage.png");

        String encoded = image.encode();

        // Filename should be base64 encoded
        String expectedName = Base64.getEncoder().encodeToString("myimage.png".getBytes());
        assertTrue(encoded.contains("name=" + expectedName));
    }

    @Test
    public void testNoFilename() {
        byte[] data = "test".getBytes();
        ITermImage image = new ITermImage(data, null);

        String encoded = image.encode();

        // Should not contain name parameter when null
        assertFalse(encoded.contains("name="));
    }

    @Test
    public void testWidthCells() {
        byte[] data = "test".getBytes();
        ITermImage image = new ITermImage(data, null).widthCells(40);

        String encoded = image.encode();
        assertTrue(encoded.contains("width=40"));
        assertFalse(encoded.contains("width=40px"));
        assertFalse(encoded.contains("width=40%"));
        assertEquals(40, image.getWidthCells());
    }

    @Test
    public void testWidthPixels() {
        byte[] data = "test".getBytes();
        ITermImage image = new ITermImage(data, null).widthPixels(800);

        String encoded = image.encode();
        assertTrue(encoded.contains("width=800px"));
        assertEquals(-1, image.getWidthCells()); // Cells cleared
    }

    @Test
    public void testWidthPercent() {
        byte[] data = "test".getBytes();
        ITermImage image = new ITermImage(data, null).widthPercent(50);

        String encoded = image.encode();
        assertTrue(encoded.contains("width=50%"));
    }

    @Test
    public void testHeightCells() {
        byte[] data = "test".getBytes();
        ITermImage image = new ITermImage(data, null).heightCells(20);

        String encoded = image.encode();
        assertTrue(encoded.contains("height=20"));
        assertFalse(encoded.contains("height=20px"));
        assertEquals(20, image.getHeightCells());
    }

    @Test
    public void testHeightPixels() {
        byte[] data = "test".getBytes();
        ITermImage image = new ITermImage(data, null).heightPixels(600);

        String encoded = image.encode();
        assertTrue(encoded.contains("height=600px"));
    }

    @Test
    public void testHeightPercent() {
        byte[] data = "test".getBytes();
        ITermImage image = new ITermImage(data, null).heightPercent(75);

        String encoded = image.encode();
        assertTrue(encoded.contains("height=75%"));
    }

    @Test
    public void testPreserveAspectRatio() {
        byte[] data = "test".getBytes();

        // Default is true
        ITermImage image1 = new ITermImage(data, null);
        assertTrue(image1.encode().contains("preserveAspectRatio=1"));

        // Explicitly set to false
        ITermImage image2 = new ITermImage(data, null).preserveAspectRatio(false);
        assertTrue(image2.encode().contains("preserveAspectRatio=0"));
    }

    @Test
    public void testStTerminator() {
        byte[] data = "test".getBytes();

        // Default uses BEL
        ITermImage image1 = new ITermImage(data, null);
        assertTrue(image1.encode().endsWith("\u0007"));

        // Use ST terminator
        ITermImage image2 = new ITermImage(data, null).useStTerminator(true);
        assertTrue(image2.encode().endsWith("\u001B\\"));
    }

    @Test
    public void testGetProtocol() {
        ITermImage image = new ITermImage("test".getBytes(), null);
        assertEquals(ImageProtocol.ITERM2, image.getProtocol());
    }

    @Test
    public void testFromBytes() {
        byte[] data = "test".getBytes();
        ITermImage image = ITermImage.fromBytes(data);

        assertNotNull(image);
        assertEquals(ImageProtocol.ITERM2, image.getProtocol());
    }

    @Test
    public void testFluentApi() {
        byte[] data = "test".getBytes();
        ITermImage image = new ITermImage(data, "test.png")
                .widthCells(40)
                .heightCells(20)
                .preserveAspectRatio(false)
                .useStTerminator(true);

        String encoded = image.encode();
        assertTrue(encoded.contains("width=40"));
        assertTrue(encoded.contains("height=20"));
        assertTrue(encoded.contains("preserveAspectRatio=0"));
        assertTrue(encoded.endsWith("\u001B\\"));
    }
}
