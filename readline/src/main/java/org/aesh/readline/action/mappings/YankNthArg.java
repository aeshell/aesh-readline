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

import org.aesh.readline.InputProcessor;
import org.aesh.readline.action.Action;
import org.aesh.readline.history.History;
import org.aesh.terminal.utils.Parser;

/**
 * Insert the first argument to the previous command (usually the second
 * word on the previous line) at point. Without a numeric argument, this
 * inserts the first argument of the previous command.
 */
public class YankNthArg implements Action {

    @Override
    public String name() {
        return "yank-nth-arg";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        History history = inputProcessor.buffer().history();
        if (history.size() == 0)
            return;

        int[] lastEntry = history.get(history.size() - 1);
        if (lastEntry == null || lastEntry.length == 0)
            return;

        String lastLine = Parser.fromCodePoints(lastEntry);
        String firstArg = extractFirstArg(lastLine);

        if (firstArg != null && !firstArg.isEmpty()) {
            inputProcessor.buffer().addActionToUndoStack();
            inputProcessor.buffer().insert(Parser.toCodePoints(firstArg));
        }
    }

    private String extractFirstArg(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty())
            return null;

        // Skip the command (first word)
        int i = 0;
        while (i < trimmed.length() && !Character.isWhitespace(trimmed.charAt(i)))
            i++;
        // Skip whitespace between command and first arg
        while (i < trimmed.length() && Character.isWhitespace(trimmed.charAt(i)))
            i++;
        if (i >= trimmed.length())
            return null;

        // Extract the first argument
        int start = i;
        while (i < trimmed.length() && !Character.isWhitespace(trimmed.charAt(i)))
            i++;
        return trimmed.substring(start, i);
    }
}
