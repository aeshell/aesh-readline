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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import org.aesh.terminal.detect.ImageProtocol;

/**
 * Kitty graphics protocol implementation.
 * <p>
 * Protocol format:
 *
 * <pre>
 * ESC _ G [key=value,...] ; [payload] ESC \
 * </pre>
 * <p>
 * The protocol uses APC (Application Program Command) escape sequences.
 * Large images are split into multiple chunks of up to 4096 bytes.
 * <p>
 * Key control parameters:
 * <ul>
 * <li>a=t|T|p|d|f - action (transmit, put, delete, frame)</li>
 * <li>f=24|32|100 - format (RGB, RGBA, PNG)</li>
 * <li>t=d|f|t|s - transmission medium (direct, file, temp, shared memory)</li>
 * <li>s=N - source width in pixels</li>
 * <li>v=N - source height in pixels</li>
 * <li>c=N - display columns</li>
 * <li>r=N - display rows</li>
 * <li>m=0|1 - more data follows (for chunked transfer)</li>
 * <li>q=1|2 - suppress responses</li>
 * </ul>
 * <p>
 * Supported by: Kitty, Konsole (partial)
 *
 * @see <a href="https://sw.kovidgoyal.net/kitty/graphics-protocol/">Kitty Graphics Protocol</a>
 */
public class KittyImage implements TerminalImage {

    private static final String APC = "\u001B_G";
    private static final String ST = "\u001B\\";
    private static final int CHUNK_SIZE = 4096;

    private final byte[] originalData;
    private byte[] pngData;
    private int widthCells = -1;
    private int heightCells = -1;
    private int sourceWidth = -1;
    private int sourceHeight = -1;
    private int zIndex = 0;
    private boolean suppressResponse = true;

    /**
     * Create a Kitty image from image data.
     * <p>
     * Supports PNG, JPEG, GIF, and other formats supported by Java ImageIO.
     * Non-PNG images are automatically converted to PNG format since the
     * Kitty graphics protocol only supports PNG for compressed images.
     *
     * @param imageData the image data (PNG, JPEG, GIF, etc.)
     */
    public KittyImage(byte[] imageData) {
        this.originalData = imageData;
    }

    /**
     * Create a Kitty image from a file.
     * <p>
     * Supports PNG, JPEG, GIF, and other formats supported by Java ImageIO.
     * Non-PNG images are automatically converted to PNG.
     *
     * @param path path to the image file
     * @return the terminal image
     * @throws IOException if the file cannot be read
     */
    public static KittyImage fromFile(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        return new KittyImage(data);
    }

    /**
     * Create a Kitty image from raw bytes.
     * <p>
     * Supports PNG, JPEG, GIF, and other formats supported by Java ImageIO.
     * Non-PNG images are automatically converted to PNG.
     *
     * @param data the image data
     * @return the terminal image
     */
    public static KittyImage fromBytes(byte[] data) {
        return new KittyImage(data);
    }

    /**
     * Set the display width in terminal cells (columns).
     *
     * @param cells number of columns
     * @return this image for chaining
     */
    public KittyImage widthCells(int cells) {
        this.widthCells = cells;
        return this;
    }

    /**
     * Set the display height in terminal cells (rows).
     *
     * @param cells number of rows
     * @return this image for chaining
     */
    public KittyImage heightCells(int cells) {
        this.heightCells = cells;
        return this;
    }

    /**
     * Set the source image dimensions.
     * Required for raw RGB/RGBA data, optional for PNG.
     *
     * @param width source width in pixels
     * @param height source height in pixels
     * @return this image for chaining
     */
    public KittyImage sourceDimensions(int width, int height) {
        this.sourceWidth = width;
        this.sourceHeight = height;
        return this;
    }

    /**
     * Set the z-index for layering multiple images.
     * Higher z-index images appear on top.
     *
     * @param zIndex the z-index value
     * @return this image for chaining
     */
    public KittyImage zIndex(int zIndex) {
        this.zIndex = zIndex;
        return this;
    }

    /**
     * Set whether to suppress terminal responses.
     * Default is true to avoid cluttering input.
     *
     * @param suppress true to suppress responses
     * @return this image for chaining
     */
    public KittyImage suppressResponse(boolean suppress) {
        this.suppressResponse = suppress;
        return this;
    }

    /**
     * Get the image data as PNG, converting if necessary.
     * The result is cached for subsequent calls.
     *
     * @return PNG image data
     * @throws UncheckedIOException if conversion fails
     */
    private byte[] getPngData() {
        if (pngData == null) {
            try {
                pngData = ImageUtils.toPng(originalData);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to convert image to PNG", e);
            }
        }
        return pngData;
    }

    @Override
    public String encode() {
        return buildChunkedPayload("a=T", 0, 0);
    }

    @Override
    public boolean supportsPlacement() {
        return true;
    }

    @Override
    public String transmit(int imageId) {
        return buildChunkedPayload("a=t", imageId, 0);
    }

    @Override
    public String transmitAndDisplay(int imageId) {
        return buildChunkedPayload("a=T", imageId, 0);
    }

    @Override
    public String place(int imageId) {
        return place(imageId, 0);
    }

    @Override
    public String place(int imageId, int placementId) {
        StringBuilder result = new StringBuilder();
        result.append(APC);
        result.append("a=p,i=").append(imageId);
        if (placementId > 0) {
            result.append(",p=").append(placementId);
        }
        appendDisplayParams(result);
        if (suppressResponse) {
            result.append(",q=2");
        }
        result.append(ST);
        return result.toString();
    }

    /**
     * Delete a previously transmitted image from the terminal's memory.
     * This removes the image data and all its placements.
     *
     * @param imageId the image ID to delete
     * @return the escape sequence for deletion
     */
    public static String delete(int imageId) {
        return APC + "a=d,d=I,i=" + imageId + ",q=2" + ST;
    }

    /**
     * Delete all placements of an image without removing the image data
     * from terminal memory. The image can be re-placed afterwards.
     * <p>
     * Uses lowercase {@code d=i} which deletes placements only.
     * Use {@link #delete(int)} (uppercase {@code d=I}) to also free
     * the image data.
     *
     * @param imageId the image ID whose placements to remove
     * @return the escape sequence for placement deletion
     */
    public static String deletePlacement(int imageId) {
        return APC + "a=d,d=i,i=" + imageId + ",q=2" + ST;
    }

    // =========================================================================
    // Internal methods
    // =========================================================================

    /**
     * Build the complete chunked APC payload for transmitting image data.
     * Shared by encode(), transmit(), and transmitAndDisplay().
     *
     * @param action the action string (e.g., "a=T" or "a=t")
     * @param imageId the image ID (0 = none)
     * @param placementId the placement ID (0 = none)
     * @return the complete escape sequence string
     */
    private String buildChunkedPayload(String action, int imageId, int placementId) {
        byte[] data = getPngData();
        String base64Data = Base64.getEncoder().encodeToString(data);
        StringBuilder result = new StringBuilder();

        int offset = 0;
        boolean isFirst = true;

        while (offset < base64Data.length()) {
            int end = Math.min(offset + CHUNK_SIZE, base64Data.length());
            String chunk = base64Data.substring(offset, end);
            boolean hasMore = end < base64Data.length();

            result.append(APC);

            if (isFirst) {
                result.append(buildFirstChunkParams(action, imageId, placementId, hasMore));
                isFirst = false;
            } else {
                result.append("m=").append(hasMore ? "1" : "0");
            }

            result.append(';');
            result.append(chunk);
            result.append(ST);

            offset = end;
        }

        return result.toString();
    }

    /**
     * Build control parameters for the first chunk of a transmission.
     */
    private String buildFirstChunkParams(String action, int imageId,
            int placementId, boolean hasMore) {
        StringBuilder params = new StringBuilder();

        params.append(action);

        // Format: PNG (100)
        params.append(",f=100");

        // Transmission: direct (base64 in escape sequence)
        params.append(",t=d");

        // Image ID
        if (imageId > 0) {
            params.append(",i=").append(imageId);
        }

        // Placement ID
        if (placementId > 0) {
            params.append(",p=").append(placementId);
        }

        // Display params only for transmit-and-display (a=T), not transmit-only (a=t)
        if ("a=T".equals(action)) {
            appendDisplayParams(params);
        }

        // Source dimensions (optional for PNG)
        if (sourceWidth > 0) {
            params.append(",s=").append(sourceWidth);
        }
        if (sourceHeight > 0) {
            params.append(",v=").append(sourceHeight);
        }

        // Z-index
        if (zIndex != 0) {
            params.append(",z=").append(zIndex);
        }

        // Suppress response
        if (suppressResponse) {
            params.append(",q=2");
        }

        // More data follows
        params.append(",m=").append(hasMore ? "1" : "0");

        return params.toString();
    }

    /**
     * Append display layout parameters (columns, rows) to the param builder.
     * Shared by transmission and placement methods.
     */
    private void appendDisplayParams(StringBuilder params) {
        if (widthCells > 0) {
            params.append(",c=").append(widthCells);
        }
        if (heightCells > 0) {
            params.append(",r=").append(heightCells);
        }
    }

    @Override
    public ImageProtocol getProtocol() {
        return ImageProtocol.KITTY;
    }

    @Override
    public int getWidthCells() {
        return widthCells;
    }

    @Override
    public int getHeightCells() {
        return heightCells;
    }
}
