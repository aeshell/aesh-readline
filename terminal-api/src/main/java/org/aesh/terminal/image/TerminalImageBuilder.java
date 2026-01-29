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
import java.nio.file.Files;
import java.nio.file.Path;

import org.aesh.terminal.Device;

/**
 * Builder for creating terminal images with automatic protocol detection.
 * <p>
 * Example usage:
 *
 * <pre>
 * TerminalImage image = TerminalImageBuilder.builder(connection.device())
 *         .file(Paths.get("image.png"))
 *         .widthCells(40)
 *         .build();
 * connection.write(image.encode());
 * </pre>
 */
public class TerminalImageBuilder {

    private final ImageProtocol protocol;
    private byte[] imageData;
    private String filename;
    private int widthCells = -1;
    private int heightCells = -1;
    private int widthPixels = -1;
    private int heightPixels = -1;
    private boolean preserveAspectRatio = true;

    private TerminalImageBuilder(ImageProtocol protocol) {
        this.protocol = protocol;
    }

    /**
     * Create a builder that auto-detects the best image protocol for the device.
     *
     * @param device the terminal device
     * @return a new builder
     */
    public static TerminalImageBuilder builder(Device device) {
        return new TerminalImageBuilder(detectProtocol(device));
    }

    /**
     * Create a builder using a specific image protocol.
     *
     * @param protocol the protocol to use
     * @return a new builder
     */
    public static TerminalImageBuilder builder(ImageProtocol protocol) {
        return new TerminalImageBuilder(protocol);
    }

    /**
     * Detect the best image protocol for the given device.
     *
     * @param device the terminal device
     * @return the detected protocol, or NONE if no image support
     */
    public static ImageProtocol detectProtocol(Device device) {
        if (device == null) {
            return ImageProtocol.NONE;
        }
        return device.getImageProtocol();
    }

    /**
     * Set the image data from a file.
     *
     * @param path path to the image file
     * @return this builder
     * @throws IOException if the file cannot be read
     */
    public TerminalImageBuilder file(Path path) throws IOException {
        this.imageData = Files.readAllBytes(path);
        this.filename = path.getFileName().toString();
        return this;
    }

    /**
     * Set the image data from raw bytes.
     *
     * @param data the image data
     * @return this builder
     */
    public TerminalImageBuilder data(byte[] data) {
        this.imageData = data;
        return this;
    }

    /**
     * Set the filename (used by iTerm2 protocol).
     *
     * @param filename the filename
     * @return this builder
     */
    public TerminalImageBuilder filename(String filename) {
        this.filename = filename;
        return this;
    }

    /**
     * Set the display width in terminal cells.
     *
     * @param cells number of cells
     * @return this builder
     */
    public TerminalImageBuilder widthCells(int cells) {
        this.widthCells = cells;
        this.widthPixels = -1;
        return this;
    }

    /**
     * Set the display height in terminal cells.
     *
     * @param cells number of cells
     * @return this builder
     */
    public TerminalImageBuilder heightCells(int cells) {
        this.heightCells = cells;
        return this;
    }

    /**
     * Set the display width in pixels (iTerm2 and Sixel).
     *
     * @param pixels width in pixels
     * @return this builder
     */
    public TerminalImageBuilder widthPixels(int pixels) {
        this.widthPixels = pixels;
        this.widthCells = -1;
        return this;
    }

    /**
     * Set the display height in pixels (iTerm2 and Sixel).
     *
     * @param pixels height in pixels
     * @return this builder
     */
    public TerminalImageBuilder heightPixels(int pixels) {
        this.heightPixels = pixels;
        return this;
    }

    /**
     * Set whether to preserve aspect ratio (iTerm2 only).
     *
     * @param preserve true to preserve aspect ratio
     * @return this builder
     */
    public TerminalImageBuilder preserveAspectRatio(boolean preserve) {
        this.preserveAspectRatio = preserve;
        return this;
    }

    /**
     * Get the protocol that will be used.
     *
     * @return the image protocol
     */
    public ImageProtocol getProtocol() {
        return protocol;
    }

    /**
     * Check if image display is supported.
     *
     * @return true if images can be displayed
     */
    public boolean isSupported() {
        return protocol != ImageProtocol.NONE;
    }

    /**
     * Build the terminal image.
     *
     * @return the terminal image, or null if protocol is NONE
     * @throws IllegalStateException if no image data has been set
     */
    public TerminalImage build() {
        if (imageData == null) {
            throw new IllegalStateException("No image data set");
        }

        switch (protocol) {
            case ITERM2:
                ITermImage iterm = new ITermImage(imageData, filename);
                if (widthCells > 0)
                    iterm.widthCells(widthCells);
                if (heightCells > 0)
                    iterm.heightCells(heightCells);
                if (widthPixels > 0)
                    iterm.widthPixels(widthPixels);
                if (heightPixels > 0)
                    iterm.heightPixels(heightPixels);
                iterm.preserveAspectRatio(preserveAspectRatio);
                return iterm;

            case KITTY:
                KittyImage kitty = new KittyImage(imageData);
                if (widthCells > 0)
                    kitty.widthCells(widthCells);
                if (heightCells > 0)
                    kitty.heightCells(heightCells);
                return kitty;

            case SIXEL:
                SixelImage sixel = new SixelImage(imageData);
                // For Sixel, convert cell dimensions to approximate pixels
                // Assume typical cell is 8x16 pixels
                if (widthCells > 0)
                    sixel.maxWidth(widthCells * 8);
                if (heightCells > 0)
                    sixel.maxHeight(heightCells * 16);
                if (widthPixels > 0)
                    sixel.maxWidth(widthPixels);
                if (heightPixels > 0)
                    sixel.maxHeight(heightPixels);
                return sixel;

            case NONE:
            default:
                return null;
        }
    }
}
