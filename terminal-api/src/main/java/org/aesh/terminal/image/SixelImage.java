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
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

/**
 * Sixel graphics protocol implementation.
 * <p>
 * Sixel is a bitmap graphics format using DCS escape sequences.
 * Each "sixel" represents 6 vertical pixels encoded as a single character.
 * <p>
 * Protocol format:
 *
 * <pre>
 * DCS P1;P2;P3 q [sixel data] ST
 * </pre>
 *
 * Where:
 * <ul>
 * <li>DCS = ESC P (or 0x90)</li>
 * <li>P1 = pixel aspect ratio (0=2:1, 1=5:1, 2=3:1, etc.)</li>
 * <li>P2 = background mode (0=device default, 1=no change, 2=set to 0)</li>
 * <li>P3 = horizontal grid size (ignored by most terminals)</li>
 * <li>q = sixel mode identifier</li>
 * <li>ST = ESC \ (string terminator)</li>
 * </ul>
 * <p>
 * Sixel data includes:
 * <ul>
 * <li>Color definitions: #Pc;Pu;Px;Py;Pz (register, mode, R/H, G/L, B/S)</li>
 * <li>Color selection: #Pc</li>
 * <li>Sixel characters: ? (0x3F) to ~ (0x7E), value = char - 63</li>
 * <li>RLE compression: !Pn[char] (repeat char Pn times)</li>
 * <li>Carriage return: $</li>
 * <li>New line: - (move to next sixel band)</li>
 * </ul>
 * <p>
 * Supported by: xterm, mlterm, foot, Windows Terminal, mintty, contour, etc.
 *
 * @see <a href="https://vt100.net/docs/vt3xx-gp/chapter14.html">VT300 Sixel Graphics</a>
 * @see <a href="https://en.wikipedia.org/wiki/Sixel">Wikipedia: Sixel</a>
 */
public class SixelImage implements TerminalImage {

    private static final String DCS = "\u001BP";
    private static final String ST = "\u001B\\";
    private static final int MAX_COLORS = 256;

    private final byte[] imageData;
    private int maxWidth = -1;
    private int maxHeight = -1;
    private int maxColors = MAX_COLORS;
    private boolean useRle = true;
    private String encodedData;

    /**
     * Create a Sixel image from raw image data.
     *
     * @param imageData the image data (PNG, JPEG, GIF, etc.)
     */
    public SixelImage(byte[] imageData) {
        this.imageData = imageData;
    }

    /**
     * Create a Sixel image from a file.
     *
     * @param path path to the image file
     * @return the terminal image
     * @throws IOException if the file cannot be read
     */
    public static SixelImage fromFile(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        return new SixelImage(data);
    }

    /**
     * Create a Sixel image from raw bytes.
     *
     * @param data the image data
     * @return the terminal image
     */
    public static SixelImage fromBytes(byte[] data) {
        return new SixelImage(data);
    }

    /**
     * Set maximum width in pixels. Image will be scaled if larger.
     *
     * @param pixels maximum width
     * @return this image for chaining
     */
    public SixelImage maxWidth(int pixels) {
        this.maxWidth = pixels;
        return this;
    }

    /**
     * Set maximum height in pixels. Image will be scaled if larger.
     *
     * @param pixels maximum height
     * @return this image for chaining
     */
    public SixelImage maxHeight(int pixels) {
        this.maxHeight = pixels;
        return this;
    }

    /**
     * Set maximum number of colors in palette (2-256).
     * Lower values produce smaller output but less accurate colors.
     *
     * @param colors maximum colors (default 256)
     * @return this image for chaining
     */
    public SixelImage maxColors(int colors) {
        this.maxColors = Math.max(2, Math.min(MAX_COLORS, colors));
        return this;
    }

    /**
     * Enable or disable run-length encoding compression.
     *
     * @param useRle true to use RLE (default true)
     * @return this image for chaining
     */
    public SixelImage useRle(boolean useRle) {
        this.useRle = useRle;
        return this;
    }

    @Override
    public String encode() {
        if (encodedData == null) {
            try {
                encodedData = encodeSixel();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to encode image as Sixel", e);
            }
        }
        return encodedData;
    }

    private String encodeSixel() throws IOException {
        // Load and prepare image
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
        if (image == null) {
            throw new IOException("Unable to decode image data");
        }

        // Scale if needed
        image = scaleImage(image);

        // Quantize colors
        ColorPalette palette = quantizeColors(image);

        // Build sixel output
        StringBuilder sb = new StringBuilder();

        // DCS introducer with parameters
        // P1=0 (2:1 aspect), P2=1 (no background change), P3=0 (default grid)
        sb.append(DCS).append("0;1;0q");

        // Define raster attributes: "Pan;Pad;Ph;Pv
        // Pan/Pad = pixel aspect ratio numerator/denominator
        // Ph/Pv = horizontal/vertical extent in pixels
        sb.append("\"1;1;").append(image.getWidth()).append(";").append(image.getHeight());

        // Define color palette
        for (int i = 0; i < palette.size(); i++) {
            int[] rgb = palette.getColor(i);
            // Convert 0-255 to 0-100 for sixel
            int r = (rgb[0] * 100) / 255;
            int g = (rgb[1] * 100) / 255;
            int b = (rgb[2] * 100) / 255;
            sb.append("#").append(i).append(";2;").append(r).append(";").append(g).append(";").append(b);
        }

        // Encode image data as sixels
        int height = image.getHeight();
        int width = image.getWidth();
        int[][] colorIndices = new int[height][width];

        // Map pixels to palette colors
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                colorIndices[y][x] = palette.findClosest(rgb);
            }
        }

        // Process image in bands of 6 rows (one sixel band)
        for (int bandY = 0; bandY < height; bandY += 6) {
            // For each color in the palette
            for (int color = 0; color < palette.size(); color++) {
                StringBuilder colorRow = new StringBuilder();

                // Build sixel row for this color
                for (int x = 0; x < width; x++) {
                    int sixelValue = 0;
                    for (int bit = 0; bit < 6; bit++) {
                        int y = bandY + bit;
                        if (y < height && colorIndices[y][x] == color) {
                            sixelValue |= (1 << bit);
                        }
                    }
                    colorRow.append((char) (sixelValue + 63));
                }

                // Only output if there's actual content for this color
                String rowStr = colorRow.toString();
                if (!isEmptyRow(rowStr)) {
                    sb.append("#").append(color);
                    if (useRle) {
                        sb.append(rleEncode(rowStr));
                    } else {
                        sb.append(rowStr);
                    }
                    sb.append("$"); // Carriage return
                }
            }
            sb.append("-"); // New sixel line
        }

        // String terminator
        sb.append(ST);

        return sb.toString();
    }

    private BufferedImage scaleImage(BufferedImage image) {
        int origWidth = image.getWidth();
        int origHeight = image.getHeight();
        int newWidth = origWidth;
        int newHeight = origHeight;

        // Scale down if larger than max dimensions
        if (maxWidth > 0 && origWidth > maxWidth) {
            newWidth = maxWidth;
            newHeight = (origHeight * maxWidth) / origWidth;
        }
        if (maxHeight > 0 && newHeight > maxHeight) {
            newHeight = maxHeight;
            newWidth = (origWidth * maxHeight) / origHeight;
        }

        if (newWidth == origWidth && newHeight == origHeight) {
            return image;
        }

        Image scaled = image.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        g.drawImage(scaled, 0, 0, null);
        g.dispose();
        return result;
    }

    private boolean isEmptyRow(String row) {
        for (int i = 0; i < row.length(); i++) {
            if (row.charAt(i) != '?') { // '?' = 0 = no pixels set
                return false;
            }
        }
        return true;
    }

    private String rleEncode(String input) {
        if (input.isEmpty()) {
            return input;
        }

        StringBuilder result = new StringBuilder();
        int i = 0;

        while (i < input.length()) {
            char c = input.charAt(i);
            int count = 1;

            while (i + count < input.length() && input.charAt(i + count) == c) {
                count++;
            }

            if (count >= 4) {
                // Use RLE for runs of 4 or more
                result.append("!").append(count).append(c);
            } else {
                // Output characters directly
                for (int j = 0; j < count; j++) {
                    result.append(c);
                }
            }

            i += count;
        }

        return result.toString();
    }

    /**
     * Simple color quantization using median cut algorithm variant.
     */
    private ColorPalette quantizeColors(BufferedImage image) {
        // Collect unique colors (limit sample size for large images)
        Map<Integer, Integer> colorCounts = new HashMap<>();
        int width = image.getWidth();
        int height = image.getHeight();
        int step = Math.max(1, (width * height) / 10000); // Sample up to 10k pixels

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int rgb = image.getRGB(x, y) & 0xFFFFFF; // Remove alpha
                colorCounts.merge(rgb, 1, Integer::sum);
            }
        }

        // If few enough colors, use them all
        if (colorCounts.size() <= maxColors) {
            List<int[]> colors = new ArrayList<>();
            for (int rgb : colorCounts.keySet()) {
                colors.add(new int[] { (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF });
            }
            return new ColorPalette(colors);
        }

        // Simple quantization: divide color space into buckets
        List<int[]> palette = new ArrayList<>();
        int buckets = (int) Math.cbrt(maxColors);

        for (int r = 0; r < buckets; r++) {
            for (int g = 0; g < buckets; g++) {
                for (int b = 0; b < buckets; b++) {
                    if (palette.size() < maxColors) {
                        int red = (r * 255) / (buckets - 1);
                        int green = (g * 255) / (buckets - 1);
                        int blue = (b * 255) / (buckets - 1);
                        palette.add(new int[] { red, green, blue });
                    }
                }
            }
        }

        return new ColorPalette(palette);
    }

    @Override
    public ImageProtocol getProtocol() {
        return ImageProtocol.SIXEL;
    }

    @Override
    public int getWidthCells() {
        return -1; // Sixel uses pixel dimensions
    }

    @Override
    public int getHeightCells() {
        return -1; // Sixel uses pixel dimensions
    }

    /**
     * Simple color palette holder.
     */
    private static class ColorPalette {
        private final List<int[]> colors;

        ColorPalette(List<int[]> colors) {
            this.colors = colors;
        }

        int size() {
            return colors.size();
        }

        int[] getColor(int index) {
            return colors.get(index);
        }

        int findClosest(int rgb) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            int bestIndex = 0;
            int bestDist = Integer.MAX_VALUE;

            for (int i = 0; i < colors.size(); i++) {
                int[] c = colors.get(i);
                int dr = r - c[0];
                int dg = g - c[1];
                int db = b - c[2];
                int dist = dr * dr + dg * dg + db * db;
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIndex = i;
                }
            }

            return bestIndex;
        }
    }
}
