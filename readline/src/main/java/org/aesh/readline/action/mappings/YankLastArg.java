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
 * Insert the last argument to the previous command (the last word of
 * the previous history entry).
 */
public class YankLastArg implements Action {

    @Override
    public String name() {
        return "yank-last-arg";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        History history = inputProcessor.buffer().history();
        if (history.size() == 0)
            return;

        // Get the most recent history entry
        int[] lastEntry = history.get(history.size() - 1);
        if (lastEntry == null || lastEntry.length == 0)
            return;

        String lastLine = Parser.fromCodePoints(lastEntry);
        String lastArg = extractLastArg(lastLine);

        if (lastArg != null && !lastArg.isEmpty()) {
            inputProcessor.buffer().addActionToUndoStack();
            inputProcessor.buffer().insert(Parser.toCodePoints(lastArg));
        }
    }

    private String extractLastArg(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty())
            return null;

        int end = trimmed.length();
        int start = end;
        while (start > 0 && !Character.isWhitespace(trimmed.charAt(start - 1)))
            start--;

        return trimmed.substring(start, end);
    }
}
