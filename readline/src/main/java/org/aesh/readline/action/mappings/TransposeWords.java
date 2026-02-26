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
 * Transpose the word before cursor with the word after (or at) cursor.
 * At end of line, transpose the last two words. Cursor ends up after
 * the second transposed word.
 */
public class TransposeWords implements Action {

    @Override
    public String name() {
        return "transpose-words";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        ConsoleBuffer consoleBuffer = inputProcessor.buffer();
        String content = consoleBuffer.buffer().asString();
        int cursor = consoleBuffer.buffer().cursor();
        int length = content.length();

        if (length == 0)
            return;

        int w2Start, w2End;

        // Try to find word2 at or after cursor
        w2Start = cursor;
        while (w2Start < length && !Character.isLetterOrDigit(content.charAt(w2Start)))
            w2Start++;

        if (w2Start < length) {
            // Found a word after cursor
            w2End = w2Start;
            while (w2End < length && Character.isLetterOrDigit(content.charAt(w2End)))
                w2End++;
        } else {
            // No word after cursor — find the last word by scanning backward
            w2End = length;
            while (w2End > 0 && !Character.isLetterOrDigit(content.charAt(w2End - 1)))
                w2End--;
            if (w2End == 0)
                return;
            w2Start = w2End;
            while (w2Start > 0 && Character.isLetterOrDigit(content.charAt(w2Start - 1)))
                w2Start--;
        }

        // Find word1 (the word before word2)
        int w1End = w2Start;
        while (w1End > 0 && !Character.isLetterOrDigit(content.charAt(w1End - 1)))
            w1End--;
        if (w1End == 0)
            return;
        int w1Start = w1End;
        while (w1Start > 0 && Character.isLetterOrDigit(content.charAt(w1Start - 1)))
            w1Start--;

        consoleBuffer.addActionToUndoStack();

        // Build new string: [before w1] + word2 + [between] + word1 + [after w2]
        String word1 = content.substring(w1Start, w1End);
        String word2 = content.substring(w2Start, w2End);
        String between = content.substring(w1End, w2Start);

        StringBuilder sb = new StringBuilder();
        sb.append(content, 0, w1Start);
        sb.append(word2);
        sb.append(between);
        sb.append(word1);
        sb.append(content, w2End, length);

        String newContent = sb.toString();
        // Cursor ends after the transposed region
        int targetCursor = w1Start + word2.length() + between.length() + word1.length();

        consoleBuffer.replace(newContent);
        consoleBuffer.moveCursor(targetCursor - newContent.length());
    }
}
