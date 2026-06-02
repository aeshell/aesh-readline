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

import java.util.Collections;
import java.util.List;

import org.aesh.readline.InputProcessor;
import org.aesh.readline.action.Action;
import org.aesh.readline.action.ActionEvent;
import org.aesh.readline.fuzzy.FuzzyScheme;
import org.aesh.readline.fuzzy.FuzzyScorer;
import org.aesh.terminal.Key;
import org.aesh.terminal.KeyAction;
import org.aesh.terminal.utils.IntArrayBuilder;
import org.aesh.terminal.utils.Parser;

/**
 * Interactive fuzzy history search (fzf-style).
 * <p>
 * Renders a multi-line list of matching history entries below the prompt,
 * filtered and ranked as the user types. Supports:
 * <ul>
 * <li>Printable chars: update the fuzzy query, re-filter and re-rank results</li>
 * <li>Up/Down: navigate the result list</li>
 * <li>Enter: select the highlighted entry and insert it into the command line</li>
 * <li>Esc: cancel and return to the prompt</li>
 * <li>Backspace: delete last char from query</li>
 * <li>Ctrl+R: toggle sort between relevance and chronological</li>
 * </ul>
 *
 * @see <a href="https://github.com/junegunn/fzf">fzf</a>
 */
public class FuzzySearchHistory implements ActionEvent {

    private enum State {
        NOT_STARTED,
        ACTIVE,
        DONE
    }

    private State state = State.NOT_STARTED;

    // Search state
    private IntArrayBuilder query;
    private FuzzyScorer scorer;
    private List<FuzzyScorer.ScoredEntry> results = Collections.emptyList();
    private List<int[]> allEntries;
    private int selectedIndex;
    private int scrollOffset;
    private int visibleLines;
    private boolean relevanceSort = true; // true = by score, false = chronological

    // What to do on next accept()
    private enum InputAction {
        INIT,
        QUERY_CHANGED,
        MOVE_UP,
        MOVE_DOWN,
        SELECT,
        CANCEL,
        TOGGLE_SORT,
        NOOP
    }

    private InputAction nextAction = InputAction.INIT;

    // Saved state for cleanup
    private int renderedLines = 0; // how many lines we rendered below the prompt
    private int[] savedBuffer; // original buffer content before search started

    @Override
    public String name() {
        return "fuzzy-reverse-search-history";
    }

    @Override
    public void input(Action action, KeyAction key) {
        if (state == State.DONE) {
            return;
        }

        // Ctrl+R while active: toggle sort
        if (action instanceof ReverseSearchHistory || action instanceof FuzzySearchHistory) {
            nextAction = InputAction.TOGGLE_SORT;
            return;
        }

        if (action instanceof Enter) {
            nextAction = InputAction.SELECT;
            return;
        }

        if (key == Key.ESC || action instanceof Interrupt) {
            nextAction = InputAction.CANCEL;
            return;
        }

        if (action instanceof DeletePrevChar) {
            if (query != null && query.size() > 0) {
                query.deleteLastEntry();
                nextAction = InputAction.QUERY_CHANGED;
            } else {
                nextAction = InputAction.NOOP;
            }
            return;
        }

        if (action instanceof PrevHistory || key == Key.UP || key == Key.UP_2) {
            nextAction = InputAction.MOVE_UP;
            return;
        }

        if (action instanceof NextHistory || key == Key.DOWN || key == Key.DOWN_2) {
            nextAction = InputAction.MOVE_DOWN;
            return;
        }

        // Printable character: append to query
        if (action == null && Key.isPrintable(key.buffer())) {
            if (query == null) {
                query = new IntArrayBuilder(1);
            }
            query.append(key.buffer().array()[0]);
            nextAction = InputAction.QUERY_CHANGED;
            return;
        }

        // Also handle printable chars from unknown actions
        if (Key.isPrintable(key.buffer())) {
            if (query == null) {
                query = new IntArrayBuilder(1);
            }
            query.append(key.buffer().array()[0]);
            nextAction = InputAction.QUERY_CHANGED;
            return;
        }

        nextAction = InputAction.NOOP;
    }

    @Override
    public boolean keepFocus() {
        return state == State.ACTIVE || state == State.NOT_STARTED;
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        switch (nextAction) {
            case INIT:
                initialize(inputProcessor);
                break;
            case QUERY_CHANGED:
                updateResults();
                render(inputProcessor);
                break;
            case MOVE_UP:
                if (selectedIndex > 0) {
                    selectedIndex--;
                    if (selectedIndex < scrollOffset) {
                        scrollOffset = selectedIndex;
                    }
                }
                render(inputProcessor);
                break;
            case MOVE_DOWN:
                if (selectedIndex < results.size() - 1) {
                    selectedIndex++;
                    if (selectedIndex >= scrollOffset + visibleLines) {
                        scrollOffset = selectedIndex - visibleLines + 1;
                    }
                }
                render(inputProcessor);
                break;
            case SELECT:
                selectEntry(inputProcessor);
                break;
            case CANCEL:
                cancel(inputProcessor);
                break;
            case TOGGLE_SORT:
                relevanceSort = !relevanceSort;
                updateResults();
                render(inputProcessor);
                break;
            case NOOP:
                break;
        }
    }

    private void initialize(InputProcessor inputProcessor) {
        state = State.ACTIVE;
        scorer = new FuzzyScorer(FuzzyScheme.HISTORY);
        allEntries = inputProcessor.buffer().history().getAll();

        // Save current buffer contents
        savedBuffer = inputProcessor.buffer().buffer().multiLine();

        // Pre-populate query from current command line (matching fzf.fish behavior)
        if (savedBuffer != null && savedBuffer.length > 0) {
            query = new IntArrayBuilder(savedBuffer);
        } else {
            query = new IntArrayBuilder(1);
        }

        // Calculate visible lines based on terminal height
        int termHeight = inputProcessor.buffer().size().getHeight();
        visibleLines = Math.min(Math.max(termHeight - 3, 1), 15); // Leave room for prompt + query + status

        selectedIndex = 0;
        scrollOffset = 0;

        updateResults();
        render(inputProcessor);
    }

    private void updateResults() {
        if (scorer == null || allEntries == null) {
            return;
        }
        int[] pattern = query != null ? query.toArray() : new int[0];
        results = scorer.scoreAll(allEntries, pattern, false);

        if (!relevanceSort && pattern.length > 0) {
            // Chronological sort: by index (most recent first = lowest index)
            results.sort((a, b) -> Integer.compare(a.index, b.index));
        }

        // Reset selection to top
        selectedIndex = 0;
        scrollOffset = 0;
    }

    private void render(InputProcessor inputProcessor) {
        // First clear any previously rendered lines
        clearRenderedArea(inputProcessor);

        // Build the query line
        String queryStr = query != null && query.size() > 0 ? Parser.fromCodePoints(query.toArray()) : "";
        String sortIndicator = relevanceSort ? "" : " [chrono]";
        String headerLine = "> " + queryStr;
        String countLine = "  " + results.size() + "/" + allEntries.size() + sortIndicator;

        // Move to the line below the prompt
        inputProcessor.buffer().writeOut("\n");

        // Write header (query input)
        inputProcessor.buffer().writeOut("\033[2K"); // erase line
        inputProcessor.buffer().writeOut(headerLine);
        inputProcessor.buffer().writeOut("\n");

        // Write count
        inputProcessor.buffer().writeOut("\033[2K");
        inputProcessor.buffer().writeOut(countLine);

        int linesWritten = 2;

        // Write visible results
        int end = Math.min(scrollOffset + visibleLines, results.size());
        int termWidth = inputProcessor.buffer().size().getWidth();

        for (int i = scrollOffset; i < end; i++) {
            inputProcessor.buffer().writeOut("\n");
            inputProcessor.buffer().writeOut("\033[2K"); // erase line

            String prefix;
            if (i == selectedIndex) {
                prefix = "\033[7m> "; // reverse video for selected
            } else {
                prefix = "  ";
            }

            String entryText = Parser.fromCodePoints(results.get(i).text);
            // Truncate to terminal width (accounting for prefix)
            int maxLen = termWidth - 3;
            if (maxLen > 0 && entryText.length() > maxLen) {
                entryText = entryText.substring(0, maxLen) + "~";
            }

            inputProcessor.buffer().writeOut(prefix + entryText);

            if (i == selectedIndex) {
                inputProcessor.buffer().writeOut("\033[27m"); // end reverse video
            }
            linesWritten++;
        }

        renderedLines = linesWritten;

        // Move cursor back up to the query line, positioned after the query text
        // We need to go up renderedLines lines to get back to the prompt line,
        // then down 1 to the query line
        inputProcessor.buffer().writeOut("\033[" + renderedLines + "A"); // up to prompt
        inputProcessor.buffer().writeOut("\n"); // down to query line
        inputProcessor.buffer().writeOut("\033[G"); // column 1
        inputProcessor.buffer().writeOut("\033[" + (queryStr.length() + 3) + "C"); // position after "> query"
    }

    private void clearRenderedArea(InputProcessor inputProcessor) {
        if (renderedLines > 0) {
            // We're on the query line (1 below prompt). Move to start and clear downward.
            inputProcessor.buffer().writeOut("\033[G"); // column 1
            for (int i = 0; i < renderedLines; i++) {
                inputProcessor.buffer().writeOut("\033[2K"); // erase line
                if (i < renderedLines - 1) {
                    inputProcessor.buffer().writeOut("\n"); // move down
                }
            }
            // Move back up
            if (renderedLines > 1) {
                inputProcessor.buffer().writeOut("\033[" + (renderedLines - 1) + "A");
            }
            renderedLines = 0;
        }
    }

    private void selectEntry(InputProcessor inputProcessor) {
        // Clear the rendered search UI
        cleanupAndRestore(inputProcessor);

        if (!results.isEmpty() && selectedIndex < results.size()) {
            int[] selected = results.get(selectedIndex).text;
            inputProcessor.buffer().replace(selected);
        } else {
            // No selection — restore original buffer
            inputProcessor.buffer().replace(savedBuffer != null ? savedBuffer : new int[0]);
        }

        state = State.DONE;
    }

    private void cancel(InputProcessor inputProcessor) {
        // Clear the rendered search UI and restore original buffer
        cleanupAndRestore(inputProcessor);
        inputProcessor.buffer().replace(savedBuffer != null ? savedBuffer : new int[0]);
        state = State.DONE;
    }

    private void cleanupAndRestore(InputProcessor inputProcessor) {
        // Clear all rendered lines below the prompt
        if (renderedLines > 0) {
            // Move to column 1 on current line (query line)
            inputProcessor.buffer().writeOut("\033[G");
            // Erase from cursor to end of screen
            for (int i = 0; i < renderedLines; i++) {
                inputProcessor.buffer().writeOut("\033[2K");
                if (i < renderedLines - 1) {
                    inputProcessor.buffer().writeOut("\n");
                }
            }
            // Move back up to prompt
            if (renderedLines > 1) {
                inputProcessor.buffer().writeOut("\033[" + (renderedLines - 1) + "A");
            }
            // Move up one more to the original prompt line
            inputProcessor.buffer().writeOut("\033[A");
            inputProcessor.buffer().writeOut("\033[G");
            renderedLines = 0;
        }

        // Erase the prompt line and redraw
        inputProcessor.buffer().writeOut("\033[2K");
        inputProcessor.buffer().writeOut("\033[G");

        // Reset search state
        query = null;
        results = Collections.emptyList();
        allEntries = null;
        scorer = null;
    }
}
