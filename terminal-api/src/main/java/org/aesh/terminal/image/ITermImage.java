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
import java.util.Base64;

/**
 * iTerm2 inline images protocol implementation.
 * <p>
 * Protocol format:
 *
 * <pre>
 * ESC ] 1337 ; File = [arguments] : base64_data BEL
 * </pre>
 * <p>
 * Supported arguments:
 * <ul>
 * <li>name=base64_filename - base64 encoded filename</li>
 * <li>size=N - file size in bytes</li>
 * <li>width=N or width=Npx or width=N% - display width</li>
 * <li>height=N or height=Npx or height=N% - display height</li>
 * <li>preserveAspectRatio=0|1 - whether to preserve aspect ratio</li>
 * <li>inline=1 - display inline (required for display)</li>
 * </ul>
 * <p>
 * Supported by: iTerm2, WezTerm, Mintty, VSCode terminal, Tabby, Hyper
 *
 * @see <a href="https://iterm2.com/documentation-images.html">iTerm2 Inline Images</a>
 */
public class ITermImage implements TerminalImage {

    private static final String OSC = "\u001B]";
    private static final String BEL = "\u0007";
    private static final String ST = "\u001B\\";

    private final byte[] imageData;
    private final String filename;
    private int widthCells = -1;
    private int heightCells = -1;
    private int widthPixels = -1;
    private int heightPixels = -1;
    private int widthPercent = -1;
    private int heightPercent = -1;
    private boolean preserveAspectRatio = true;
    private boolean useStTerminator = false;

    /**
     * Create an iTerm2 image from raw image data.
     *
     * @param imageData the image data (PNG, JPEG, GIF, etc.)
     * @param filename optional filename for the image
     */
    public ITermImage(byte[] imageData, String filename) {
        this.imageData = imageData;
        this.filename = filename;
    }

    /**
     * Create an iTerm2 image from a file.
     *
     * @param path path to the image file
     * @return the terminal image
     * @throws IOException if the file cannot be read
     */
    public static ITermImage fromFile(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        return new ITermImage(data, path.getFileName().toString());
    }

    /**
     * Create an iTerm2 image from raw bytes.
     *
     * @param data the image data
     * @return the terminal image
     */
    public static ITermImage fromBytes(byte[] data) {
        return new ITermImage(data, null);
    }

    /**
     * Set the display width in terminal cells.
     *
     * @param cells number of cells
     * @return this image for chaining
     */
    public ITermImage widthCells(int cells) {
        this.widthCells = cells;
        this.widthPixels = -1;
        this.widthPercent = -1;
        return this;
    }

    /**
     * Set the display height in terminal cells.
     *
     * @param cells number of cells
     * @return this image for chaining
     */
    public ITermImage heightCells(int cells) {
        this.heightCells = cells;
        this.heightPixels = -1;
        this.heightPercent = -1;
        return this;
    }

    /**
     * Set the display width in pixels.
     *
     * @param pixels width in pixels
     * @return this image for chaining
     */
    public ITermImage widthPixels(int pixels) {
        this.widthPixels = pixels;
        this.widthCells = -1;
        this.widthPercent = -1;
        return this;
    }

    /**
     * Set the display height in pixels.
     *
     * @param pixels height in pixels
     * @return this image for chaining
     */
    public ITermImage heightPixels(int pixels) {
        this.heightPixels = pixels;
        this.heightCells = -1;
        this.heightPercent = -1;
        return this;
    }

    /**
     * Set the display width as a percentage of the terminal width.
     *
     * @param percent percentage (1-100)
     * @return this image for chaining
     */
    public ITermImage widthPercent(int percent) {
        this.widthPercent = percent;
        this.widthCells = -1;
        this.widthPixels = -1;
        return this;
    }

    /**
     * Set the display height as a percentage of the terminal height.
     *
     * @param percent percentage (1-100)
     * @return this image for chaining
     */
    public ITermImage heightPercent(int percent) {
        this.heightPercent = percent;
        this.heightCells = -1;
        this.heightPixels = -1;
        return this;
    }

    /**
     * Set whether to preserve the aspect ratio when resizing.
     *
     * @param preserve true to preserve aspect ratio
     * @return this image for chaining
     */
    public ITermImage preserveAspectRatio(boolean preserve) {
        this.preserveAspectRatio = preserve;
        return this;
    }

    /**
     * Use ST (ESC \) terminator instead of BEL.
     * Some terminals prefer ST over BEL.
     *
     * @param useSt true to use ST terminator
     * @return this image for chaining
     */
    public ITermImage useStTerminator(boolean useSt) {
        this.useStTerminator = useSt;
        return this;
    }

    @Override
    public String encode() {
        StringBuilder sb = new StringBuilder();

        // OSC 1337 ; File =
        sb.append(OSC).append("1337;File=");

        // Arguments
        StringBuilder args = new StringBuilder();

        // Filename (base64 encoded)
        if (filename != null && !filename.isEmpty()) {
            args.append("name=").append(Base64.getEncoder().encodeToString(filename.getBytes()));
        }

        // Size
        if (args.length() > 0)
            args.append(';');
        args.append("size=").append(imageData.length);

        // Width
        if (widthCells > 0) {
            args.append(";width=").append(widthCells);
        } else if (widthPixels > 0) {
            args.append(";width=").append(widthPixels).append("px");
        } else if (widthPercent > 0) {
            args.append(";width=").append(widthPercent).append("%");
        }

        // Height
        if (heightCells > 0) {
            args.append(";height=").append(heightCells);
        } else if (heightPixels > 0) {
            args.append(";height=").append(heightPixels).append("px");
        } else if (heightPercent > 0) {
            args.append(";height=").append(heightPercent).append("%");
        }

        // Preserve aspect ratio
        args.append(";preserveAspectRatio=").append(preserveAspectRatio ? "1" : "0");

        // Inline (required for display)
        args.append(";inline=1");

        sb.append(args);

        // Separator and base64 data
        sb.append(':');
        sb.append(Base64.getEncoder().encodeToString(imageData));

        // Terminator
        sb.append(useStTerminator ? ST : BEL);

        return sb.toString();
    }

    @Override
    public ImageProtocol getProtocol() {
        return ImageProtocol.ITERM2;
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
