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

    public CursorLocator(Buffer buffer) {
        this.buffer = buffer;
    }

    public void addLine(int size, int promptSize) {
        linesSize.add(size);
        linesSize.add(promptSize);
    }

    public boolean isLocationInvalidated() {
        return invalidatedLines;
    }

    public void invalidateCursorLocation() {
        invalidatedLines = true;
    }

    /**
     * The core logic of the locator. Map a command index onto an absolute
     * COL/ROW cursor location.
     *
     * @param index The commnd index.
     * @param width The terminal width.
     * @return
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

    public void clear() {
        linesSize.clear();
    }
}
