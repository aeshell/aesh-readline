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

import java.util.Arrays;

import org.aesh.readline.ConsoleBuffer;
import org.aesh.readline.InputProcessor;
import org.aesh.readline.action.Action;

/**
 * Kill the word behind point, using whitespace and the slash character
 * as word boundaries. The killed text is saved on the kill ring.
 */
public class UnixFilenameRubout implements Action {

    @Override
    public String name() {
        return "unix-filename-rubout";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        ConsoleBuffer consoleBuffer = inputProcessor.buffer();
        int cursor = consoleBuffer.buffer().cursor();

        if (cursor == 0)
            return;

        String buffer = consoleBuffer.buffer().asString();
        int newCursor = cursor;

        // Skip trailing delimiters (whitespace and slashes)
        while (newCursor > 0 && isFilenameDelimiter(buffer.charAt(newCursor - 1)))
            newCursor--;
        // Skip word characters (anything that isn't whitespace or slash)
        while (newCursor > 0 && !isFilenameDelimiter(buffer.charAt(newCursor - 1)))
            newCursor--;

        if (newCursor == cursor)
            return;

        consoleBuffer.addActionToUndoStack();
        consoleBuffer.pasteManager().addText(
                Arrays.copyOfRange(consoleBuffer.buffer().multiLine(), newCursor, cursor));
        consoleBuffer.delete(newCursor - cursor);
    }

    private boolean isFilenameDelimiter(char c) {
        return Character.isWhitespace(c) || c == '/';
    }
}
