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
package org.aesh.terminal.detect;

/**
 * Terminal dimensions from a point-in-time query.
 * <p>
 * Unlike {@code org.aesh.terminal.tty.Size}, this type carries no
 * fallback defaults: a query that cannot measure the terminal
 * returns {@code Optional.empty()} instead of a fabricated size.
 * Dimensions are always positive; the constructor rejects
 * non-positive values.
 * <p>
 * Java 8, zero dependencies.
 *
 * @since 3.18.8
 */
public final class TerminalSize {

    private final int width;
    private final int height;

    /**
     * Create terminal dimensions.
     *
     * @param width the terminal width in columns, must be positive
     * @param height the terminal height in rows, must be positive
     * @throws IllegalArgumentException when either dimension is not positive
     */
    public TerminalSize(int width, int height) {
        if (width <= 0) {
            throw new IllegalArgumentException(
                    "width must be positive, got " + width);
        }
        if (height <= 0) {
            throw new IllegalArgumentException(
                    "height must be positive, got " + height);
        }
        this.width = width;
        this.height = height;
    }

    /**
     * Get the terminal width in columns.
     *
     * @return the width, always positive
     */
    public int width() {
        return width;
    }

    /**
     * Get the terminal height in rows.
     *
     * @return the height, always positive
     */
    public int height() {
        return height;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TerminalSize that = (TerminalSize) o;
        return width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
        return 31 * width + height;
    }

    @Override
    public String toString() {
        return "TerminalSize[width=" + width + ", height=" + height + ']';
    }
}
