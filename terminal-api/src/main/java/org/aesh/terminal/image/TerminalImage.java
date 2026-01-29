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
}
