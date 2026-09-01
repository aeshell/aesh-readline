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

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
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
 * Renders a multi-line list of matching history entries below the prompt
 * in fzf's reverse layout (query at top, results growing downward):
 *
 * <pre>
 *   prompt$ ___                          (original prompt, untouched)
 *   > query_                      3/20   (query line + match count)
 *   > git status                         (selected, highlighted)
 *     git commit -m "initial commit"
 *     mvn clean test
 *     grep -rn "FuzzyMatch" src/
 * </pre>
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
    private List<Long> allTimestamps;
    private int totalUniqueEntries;
    private int selectedIndex;
    private int scrollOffset;
    private int visibleLines;
    private boolean relevanceSort = true;

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

    private int renderedLines = 0;
    private int[] savedBuffer;

    /**
     * Create a new fuzzy history search action.
     */
    public FuzzySearchHistory() {
    }

    @Override
    public String name() {
        return "fuzzy-reverse-search-history";
    }

    @Override
    public void input(Action action, KeyAction key) {
        // Reset for reuse: the action instance is cached by the edit mode,
        // so after a previous DONE cycle, we need to allow re-activation.
        if (state == State.DONE) {
            state = State.NOT_STARTED;
            nextAction = InputAction.INIT;
        }

        // On first activation, don't process the triggering key — let INIT run
        if (state == State.NOT_STARTED) {
            return;
        }

        if (action instanceof ReverseSearchHistory || action instanceof FuzzySearchHistory) {
            nextAction = InputAction.TOGGLE_SORT;
            return;
        }

        if (action instanceof Enter
                || action instanceof Complete
                || action instanceof ForwardChar
                || key == Key.RIGHT || key == Key.RIGHT_2) {
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

        if (key.length() == 1) {
            int codePoint = key.getCodePointAt(0);
            if (codePoint > 31 && codePoint != 127) {
                if (query == null) {
                    query = new IntArrayBuilder(1);
                }
                query.append(codePoint);
                nextAction = InputAction.QUERY_CHANGED;
                return;
            }
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
        // Reset all state for reuse (the action instance is cached by the edit mode)
        state = State.ACTIVE;
        nextAction = InputAction.INIT;
        renderedLines = 0;
        selectedIndex = 0;
        scrollOffset = 0;
        relevanceSort = true;
        totalUniqueEntries = 0;
        scorer = new FuzzyScorer(FuzzyScheme.HISTORY);
        allEntries = inputProcessor.buffer().history().getAll();
        allTimestamps = inputProcessor.buffer().history().getTimestamps();

        savedBuffer = inputProcessor.buffer().buffer().multiLine();

        if (savedBuffer != null && savedBuffer.length > 0) {
            query = new IntArrayBuilder(savedBuffer);
        } else {
            query = new IntArrayBuilder(1);
        }

        int termHeight = inputProcessor.buffer().size().getHeight();
        visibleLines = Math.min(Math.max(termHeight - 3, 1), 15);

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
        results = scorer.scoreAll(allEntries, allTimestamps, pattern, false);
        // Track total unique entries for the info line (only on first call)
        if (totalUniqueEntries == 0 && !results.isEmpty()) {
            // scoreAll with empty pattern returns all unique entries
            totalUniqueEntries = scorer.scoreAll(allEntries, allTimestamps, new int[0], false).size();
        } else if (totalUniqueEntries == 0) {
            totalUniqueEntries = FuzzyScorer.deduplicate(allEntries).size();
        }

        if (!relevanceSort && pattern.length > 0) {
            results.sort((a, b) -> Integer.compare(a.index, b.index));
        }

        selectedIndex = 0;
        scrollOffset = 0;
    }

    // Date formatter for timestamps (MM-dd HH:mm)
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM-dd HH:mm");

    /**
     * Render the fuzzy search UI below the prompt.
     * Layout matches fzf.fish's history search:
     *
     * <pre>
     *   prompt$ ___
     *   > query_
     *     3/20
     *   > 06-02 12:30 │ git status           (selected, highlighted)
     *     06-02 12:28 │ git commit -m "fix"
     *     06-02 12:25 │ mvn clean test
     * </pre>
     */
    private void render(InputProcessor inputProcessor) {
        if (allEntries == null) {
            return;
        }

        clearRenderedArea(inputProcessor);

        String queryStr = query != null && query.size() > 0 ? Parser.fromCodePoints(query.toArray()) : "";
        String sortIndicator = relevanceSort ? "" : " [chrono]";
        int termWidth = inputProcessor.buffer().size().getWidth();

        int end = Math.min(scrollOffset + visibleLines, results.size());
        int resultCount = end - scrollOffset;
        int totalLines = 2 + resultCount; // query line + info line + result lines

        // Line 1: Query input
        out(inputProcessor, "\r\n\033[2K");
        out(inputProcessor, "\033[36m> \033[0m" + queryStr);

        // Line 2: Info line (match count / total)
        out(inputProcessor, "\r\n\033[2K");
        out(inputProcessor, "  \033[90m" + results.size() + "/" + totalUniqueEntries + sortIndicator + "\033[0m");

        // Lines 3+: Results with timestamps
        for (int i = scrollOffset; i < end; i++) {
            out(inputProcessor, "\r\n\033[2K");

            String entryText = Parser.fromCodePoints(results.get(i).text);

            // Format timestamp if available
            String timeStr = "";
            if (results.get(i).timestamp > 0) {
                timeStr = DATE_FORMAT.format(new Date(results.get(i).timestamp));
            }

            if (i == selectedIndex) {
                out(inputProcessor, "\033[36m> \033[0m");
                if (!timeStr.isEmpty()) {
                    out(inputProcessor, "\033[7m" + timeStr + " \033[0m\033[90m\u2502\033[0m ");
                }
                int prefixLen = 2 + (timeStr.isEmpty() ? 0 : timeStr.length() + 3);
                int maxLen = termWidth - prefixLen - 1;
                if (maxLen > 0 && entryText.length() > maxLen) {
                    entryText = entryText.substring(0, maxLen) + "~";
                }
                out(inputProcessor, "\033[7m" + entryText + "\033[0m");
            } else {
                out(inputProcessor, "  ");
                if (!timeStr.isEmpty()) {
                    out(inputProcessor, "\033[90m" + timeStr + " \u2502\033[0m ");
                }
                int prefixLen = 2 + (timeStr.isEmpty() ? 0 : timeStr.length() + 3);
                int maxLen = termWidth - prefixLen - 1;
                if (maxLen > 0 && entryText.length() > maxLen) {
                    entryText = entryText.substring(0, maxLen) + "\033[90m~\033[0m";
                }
                out(inputProcessor, entryText);
            }
        }

        renderedLines = totalLines;

        // Move cursor back to the query line (first rendered line)
        if (totalLines > 1) {
            out(inputProcessor, "\033[" + (totalLines - 1) + "A");
        }
        // Position cursor on the query line after "> " + query text
        out(inputProcessor, "\r");
        int cursorCol = 2 + queryStr.length();
        out(inputProcessor, "\033[" + (cursorCol + 1) + "G");
    }

    private void clearRenderedArea(InputProcessor inputProcessor) {
        if (renderedLines > 0) {
            // Cursor is on the query line (first rendered line, one below prompt).
            // Use CSI J (erase from cursor to end of screen) — simpler and more
            // robust than line-by-line clearing, especially with Unicode content
            // that may have different display widths.
            out(inputProcessor, "\r"); // column 1
            out(inputProcessor, "\033[J"); // erase from cursor to end of screen
            // Move up to the original prompt line
            out(inputProcessor, "\033[A");
            renderedLines = 0;
        }
    }

    private void selectEntry(InputProcessor inputProcessor) {
        int[] selected;
        if (!results.isEmpty() && selectedIndex < results.size()) {
            selected = results.get(selectedIndex).text;
        } else {
            selected = savedBuffer != null && savedBuffer.length > 0 ? savedBuffer : new int[0];
        }

        cleanupAndRestore(inputProcessor);

        // Insert the selected entry into the command line for editing
        // (do NOT call setReturnValue — that would execute it immediately)
        if (selected.length == 0) {
            // Buffer.replace() short-circuits for empty-to-empty, skipping
            // the prompt redraw. Force a redraw after cleanup erased the UI.
            inputProcessor.buffer().drawLineForceDisplay();
        } else {
            inputProcessor.buffer().replace(selected);
        }

        state = State.DONE;
    }

    private void cancel(InputProcessor inputProcessor) {
        cleanupAndRestore(inputProcessor);
        int[] restored = savedBuffer != null ? savedBuffer : new int[0];
        if (restored.length == 0) {
            // Buffer.replace() short-circuits for empty-to-empty, skipping
            // the prompt redraw. Force a redraw after cleanup erased the UI.
            inputProcessor.buffer().drawLineForceDisplay();
        } else {
            inputProcessor.buffer().replace(restored);
        }
        state = State.DONE;
    }

    private void cleanupAndRestore(InputProcessor inputProcessor) {
        clearRenderedArea(inputProcessor);

        out(inputProcessor, "\033[2K");
        out(inputProcessor, "\r");

        query = null;
        results = Collections.emptyList();
        allEntries = null;
        allTimestamps = null;
        totalUniqueEntries = 0;
        scorer = null;
    }

    /** Write a string to the terminal output. */
    private void out(InputProcessor inputProcessor, String s) {
        inputProcessor.buffer().writeOut(s);
    }

}
