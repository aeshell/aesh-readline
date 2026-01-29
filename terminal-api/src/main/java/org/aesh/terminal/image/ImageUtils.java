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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * Utility class for image format detection and conversion.
 */
public final class ImageUtils {

    private ImageUtils() {
        // Utility class
    }

    /**
     * Check if the image data is in PNG format.
     *
     * @param data the image data
     * @return true if the data starts with PNG signature
     */
    public static boolean isPng(byte[] data) {
        if (data == null || data.length < 8) {
            return false;
        }
        // PNG signature: 89 50 4E 47 0D 0A 1A 0A
        return data[0] == (byte) 0x89 &&
                data[1] == 0x50 &&
                data[2] == 0x4E &&
                data[3] == 0x47 &&
                data[4] == 0x0D &&
                data[5] == 0x0A &&
                data[6] == 0x1A &&
                data[7] == 0x0A;
    }

    /**
     * Check if the image data is in JPEG format.
     *
     * @param data the image data
     * @return true if the data starts with JPEG signature
     */
    public static boolean isJpeg(byte[] data) {
        if (data == null || data.length < 3) {
            return false;
        }
        // JPEG signature: FF D8 FF
        return data[0] == (byte) 0xFF &&
                data[1] == (byte) 0xD8 &&
                data[2] == (byte) 0xFF;
    }

    /**
     * Check if the image data is in GIF format.
     *
     * @param data the image data
     * @return true if the data starts with GIF signature
     */
    public static boolean isGif(byte[] data) {
        if (data == null || data.length < 6) {
            return false;
        }
        // GIF signature: GIF87a or GIF89a
        return data[0] == 'G' &&
                data[1] == 'I' &&
                data[2] == 'F' &&
                data[3] == '8' &&
                (data[4] == '7' || data[4] == '9') &&
                data[5] == 'a';
    }

    /**
     * Convert image data to PNG format.
     * If the data is already PNG, it is returned as-is.
     * Otherwise, the image is decoded and re-encoded as PNG.
     *
     * @param data the image data (JPEG, PNG, GIF, BMP, etc.)
     * @return PNG encoded image data
     * @throws IOException if the image cannot be read or converted
     */
    public static byte[] toPng(byte[] data) throws IOException {
        if (isPng(data)) {
            return data;
        }

        // Read the image using ImageIO
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
        if (image == null) {
            throw new IOException("Unable to decode image data");
        }

        // Convert to a format that PNG can handle well
        // If the image has transparency, use ARGB; otherwise use RGB
        int imageType = image.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;

        BufferedImage convertedImage = new BufferedImage(
                image.getWidth(), image.getHeight(), imageType);
        Graphics2D g = convertedImage.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        // Write as PNG
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(convertedImage, "PNG", baos)) {
            throw new IOException("Failed to encode image as PNG");
        }

        return baos.toByteArray();
    }

    /**
     * Get the image format name based on magic bytes.
     *
     * @param data the image data
     * @return format name ("PNG", "JPEG", "GIF", or "UNKNOWN")
     */
    public static String getFormat(byte[] data) {
        if (isPng(data)) {
            return "PNG";
        }
        if (isJpeg(data)) {
            return "JPEG";
        }
        if (isGif(data)) {
            return "GIF";
        }
        return "UNKNOWN";
    }
}
