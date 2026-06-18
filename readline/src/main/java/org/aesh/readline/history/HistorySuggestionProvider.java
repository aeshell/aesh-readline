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
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.aesh.readline.history;

import java.util.List;

import org.aesh.readline.suggestion.SuggestionProvider;
import org.aesh.terminal.utils.Parser;

/**
 * Provides inline ghost text suggestions from command history.
 * Searches history for the most recent entry that starts with the
 * current buffer content and returns the suffix as a suggestion.
 * <p>
 * Similar to fish shell's auto-suggestions feature.
 *
 * @author Aesh team
 */
public class HistorySuggestionProvider implements SuggestionProvider {

    private final History history;

    /**
     * Create a new suggestion provider backed by the given history.
     *
     * @param history the history to search for suggestions
     */
    public HistorySuggestionProvider(History history) {
        this.history = history;
    }

    @Override
    public String suggest(String buffer) {
        if (buffer == null || buffer.isEmpty() || history == null)
            return null;

        int[] bufferCodePoints = Parser.toCodePoints(buffer);
        List<int[]> entries = history.getAll();

        // Search from most recent to oldest
        for (int i = entries.size() - 1; i >= 0; i--) {
            int[] entry = entries.get(i);
            if (entry.length > bufferCodePoints.length && startsWith(entry, bufferCodePoints)) {
                // Return only the suffix (the part the user hasn't typed yet)
                int[] suffix = new int[entry.length - bufferCodePoints.length];
                System.arraycopy(entry, bufferCodePoints.length, suffix, 0, suffix.length);
                return Parser.fromCodePoints(suffix);
            }
        }
        return null;
    }

    private static boolean startsWith(int[] entry, int[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (entry[i] != prefix[i])
                return false;
        }
        return true;
    }
}
