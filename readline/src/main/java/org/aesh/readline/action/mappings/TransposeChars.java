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
package org.aesh.readline.action.mappings;

import org.aesh.readline.ConsoleBuffer;
import org.aesh.readline.InputProcessor;
import org.aesh.readline.action.Action;

/**
 * Transpose the character before cursor with the character at cursor,
 * then move cursor forward. At end of line, transpose the two characters
 * before cursor.
 */
public class TransposeChars implements Action {

    /** Constructor. */
    public TransposeChars() {
    }

    @Override
    public String name() {
        return "transpose-chars";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        ConsoleBuffer consoleBuffer = inputProcessor.buffer();
        int cursor = consoleBuffer.buffer().cursor();
        int length = consoleBuffer.buffer().length();

        if (length < 2 || cursor == 0)
            return;

        consoleBuffer.addActionToUndoStack();

        int[] line = consoleBuffer.buffer().multiLine();
        int targetCursor;

        if (cursor == length) {
            // At end of line: swap the two characters before cursor
            int tmp = line[cursor - 1];
            line[cursor - 1] = line[cursor - 2];
            line[cursor - 2] = tmp;
            targetCursor = length;
        } else {
            // In the middle: swap char before cursor with char at cursor, advance
            int tmp = line[cursor];
            line[cursor] = line[cursor - 1];
            line[cursor - 1] = tmp;
            targetCursor = cursor + 1;
        }

        // Replace redraws and puts cursor at end
        consoleBuffer.replace(line);
        consoleBuffer.moveCursor(targetCursor - length);
    }
}
