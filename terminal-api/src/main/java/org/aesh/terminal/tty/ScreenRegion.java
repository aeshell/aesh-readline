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
package org.aesh.terminal.tty;

import java.util.function.Consumer;

/**
 * A region of the terminal screen that can be independently written to.
 * <p>
 * Screen regions are created by splitting the terminal via
 * {@link org.aesh.terminal.Connection#splitScreen(double)}. Each region
 * has its own scrollback buffer and size. Content written to a region
 * scrolls independently within that region's bounds.
 * <p>
 * <b>Experimental API</b> — this feature is under active development
 * and the API may change.
 *
 * @see org.aesh.terminal.Connection#splitScreen(double)
 */
public interface ScreenRegion {

    /**
     * Write text to this region.
     * <p>
     * The text is appended to the region's scrollback buffer and rendered
     * within the region's bounds. If the text exceeds the visible area,
     * older content scrolls up within the region.
     * <p>
     * Thread-safe — can be called from any thread.
     *
     * @param text the text to write, may contain ANSI escape sequences
     */
    void write(String text);

    /**
     * Write a line of text to this region (appends newline).
     *
     * @param text the text to write
     */
    default void writeln(String text) {
        write(text + "\n");
    }

    /**
     * Returns the current size of this region.
     *
     * @return the region size (width x height in characters)
     */
    Size size();

    /**
     * Set a handler to be called when this region is resized.
     *
     * @param handler the resize handler, or null to remove
     */
    void setResizeHandler(Consumer<Size> handler);

    /**
     * Clear the region's visible content and scrollback buffer.
     */
    void clear();

    /**
     * Close this region, merging it back into the parent region.
     * After closing, this region can no longer be written to.
     */
    void close();
}
