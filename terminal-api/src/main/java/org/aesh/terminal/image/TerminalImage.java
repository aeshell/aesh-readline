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

import org.aesh.terminal.detect.ImageProtocol;

/**
 * Represents an image that can be displayed in a terminal.
 * Different implementations handle different terminal image protocols.
 */
public interface TerminalImage {

    /**
     * Encode the image as an escape sequence string ready to be written to the terminal.
     *
     * @return the escape sequence that will display the image
     */
    String encode();

    /**
     * Get the protocol used by this image.
     *
     * @return the image protocol
     */
    ImageProtocol getProtocol();

    /**
     * Get the display width in terminal cells.
     * Returns -1 if width is auto-detected or not specified.
     *
     * @return width in cells, or -1 for auto
     */
    int getWidthCells();

    /**
     * Get the display height in terminal cells.
     * Returns -1 if height is auto-detected or not specified.
     *
     * @return height in cells, or -1 for auto
     */
    int getHeightCells();

    /**
     * Whether this image supports the transmit-once / place-many pattern.
     * When true, callers can use {@link #transmit(int)} to send the image
     * data once, then {@link #place(int)} to display it at different
     * positions without re-sending the data.
     *
     * @return true if placement is supported (currently only Kitty protocol)
     */
    default boolean supportsPlacement() {
        return false;
    }

    /**
     * Transmit image data to the terminal with an ID but do not display it.
     * The image is stored in the terminal's memory and can be displayed
     * later using {@link #place(int)}.
     *
     * @param imageId a positive integer (1-4294967295) to identify the image
     * @return the escape sequence for transmission
     * @throws UnsupportedOperationException if the protocol does not support placement
     */
    default String transmit(int imageId) {
        throw new UnsupportedOperationException(
                getProtocol() + " does not support separate transmit/place");
    }

    /**
     * Transmit image data AND display it at the current cursor position,
     * assigning an ID for later re-placement via {@link #place(int)}.
     * <p>
     * This is the common use case for images that are shown once and may
     * need to be repositioned later.
     *
     * @param imageId a positive integer (1-4294967295) to identify the image
     * @return the escape sequence for transmission and display
     */
    default String transmitAndDisplay(int imageId) {
        return encode();
    }

    /**
     * Place a previously transmitted image at the current cursor position.
     * This is a lightweight operation (~30 bytes) that references the image
     * by ID without re-sending any image data.
     *
     * @param imageId the image ID from a previous {@link #transmit(int)} call
     * @return the escape sequence for placement
     * @throws UnsupportedOperationException if the protocol does not support placement
     */
    default String place(int imageId) {
        throw new UnsupportedOperationException(
                getProtocol() + " does not support separate transmit/place");
    }

    /**
     * Place a previously transmitted image with a specific placement ID.
     * Sending the same image ID and placement ID replaces the previous
     * placement without flicker — useful for animation.
     *
     * @param imageId the image ID from a previous {@link #transmit(int)} call
     * @param placementId a positive integer (1-4294967295) identifying this placement
     * @return the escape sequence for placement
     * @throws UnsupportedOperationException if the protocol does not support placement
     */
    default String place(int imageId, int placementId) {
        return place(imageId);
    }
}
