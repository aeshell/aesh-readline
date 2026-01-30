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
package org.aesh.readline.cursor;

import java.util.ArrayList;
import java.util.List;

import org.aesh.readline.Buffer;

/**
 * Map a command character index onto a cursor COL/ROW.
 *
 * @author jdenise@redhat.com
 */
public class CursorLocator {

    private final List<Integer> linesSize = new ArrayList<>();
    private boolean invalidatedLines;

    private final Buffer buffer;

    /**
     * Creates a new cursor locator for the specified buffer.
     *
     * @param buffer the buffer to track cursor positions for
     */
    public CursorLocator(Buffer buffer) {
        this.buffer = buffer;
    }

    /**
     * Adds a line with the specified size and prompt size to the locator.
     *
     * @param size the size of the line content
     * @param promptSize the size of the prompt on this line
     */
    public void addLine(int size, int promptSize) {
        linesSize.add(size);
        linesSize.add(promptSize);
    }

    /**
     * Checks if the cursor location tracking has been invalidated.
     *
     * @return true if the location is invalidated, false otherwise
     */
    public boolean isLocationInvalidated() {
        return invalidatedLines;
    }

    /**
     * Marks the cursor location as invalidated. This typically happens
     * when the terminal state changes in a way that makes the stored
     * line information unreliable.
     */
    public void invalidateCursorLocation() {
        invalidatedLines = true;
    }

    /**
     * The core logic of the locator. Map a command index onto an absolute
     * COL/ROW cursor location.
     *
     * @param index The commnd index.
     * @param width The terminal width.
     * @return the cursor location corresponding to the index, or null if the location is invalidated or out of bounds
     */
    public CursorLocation locate(int index, int width) {
        // Upper lines location has been lost.
        if (isLocationInvalidated()) {
            return null;
        }
        int cumulated = 0;

        List<Integer> allLines = new ArrayList<>();
        allLines.addAll(linesSize);
        allLines.add(buffer.length());
        allLines.add(buffer.prompt().getLength());
        int lineIndex = 0;
        for (int i = 0; i < allLines.size(); i++) {
            int cmdSize = allLines.get(i++);
            int promptSize = allLines.get(i);
            lineIndex += 1;
            if (cumulated + cmdSize > index) {
                int part = index - cumulated;
                int col = (part + promptSize) % width;
                // if the part + prompt is longer than width, then
                // the row is in a lower line.
                lineIndex += (promptSize + part) / width;
                return new CursorLocation(lineIndex - 1, col);
            }
            cumulated += cmdSize;
            // Each line could be wrapped if longer than width.
            lineIndex += (cmdSize + promptSize) / width;
        }
        // we are on the last line at the last character.
        if (cumulated == index) {
            int cmdSize = allLines.get(allLines.size() - 2);
            int promptSize = allLines.get(allLines.size() - 1);
            int col = (cmdSize + promptSize) % width;
            return new CursorLocation(lineIndex - 1, col);
        } else {
            return null;
        }
    }

    /**
     * Clears all stored line information from the locator.
     */
    public void clear() {
        linesSize.clear();
    }
}
