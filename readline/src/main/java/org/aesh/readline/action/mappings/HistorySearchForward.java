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

import java.util.List;

import org.aesh.readline.InputProcessor;
import org.aesh.readline.action.Action;
import org.aesh.readline.history.History;
import org.aesh.terminal.utils.Parser;

/**
 * Search forward through the history for the string of characters between
 * the start of the current line and the point (prefix search).
 */
public class HistorySearchForward implements Action {

    private int searchIndex = -1;

    @Override
    public String name() {
        return "history-search-forward";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        History history = inputProcessor.buffer().history();
        if (history.size() == 0)
            return;

        int cursor = inputProcessor.buffer().buffer().cursor();
        String buffer = inputProcessor.buffer().buffer().asString();
        String prefix = buffer.substring(0, cursor);

        if (prefix.isEmpty()) {
            int[] entry = history.getNextFetch();
            if (entry != null) {
                inputProcessor.buffer().replace(entry);
            }
            return;
        }

        int[] prefixCodePoints = Parser.toCodePoints(prefix);
        List<int[]> all = history.getAll();

        // Initialize search index
        if (searchIndex < 0)
            searchIndex = 0;
        else
            searchIndex++;

        // Search forward for a prefix match
        for (int i = searchIndex; i < all.size(); i++) {
            int[] entry = all.get(i);
            if (startsWith(entry, prefixCodePoints)) {
                searchIndex = i;
                inputProcessor.buffer().replace(entry);
                inputProcessor.buffer().moveCursor(cursor - inputProcessor.buffer().buffer().cursor());
                return;
            }
        }
        // Reset if nothing found
        searchIndex = -1;
    }

    private boolean startsWith(int[] source, int[] prefix) {
        if (source.length < prefix.length)
            return false;
        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i])
                return false;
        }
        return true;
    }
}
