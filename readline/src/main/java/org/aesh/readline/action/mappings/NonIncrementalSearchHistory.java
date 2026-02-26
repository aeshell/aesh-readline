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
import org.aesh.readline.action.ActionEvent;
import org.aesh.readline.history.SearchDirection;
import org.aesh.terminal.Key;
import org.aesh.terminal.KeyAction;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.IntArrayBuilder;

/**
 * Base class for non-incremental history search. Prompts for a search
 * string, then searches history once when Enter is pressed.
 */
abstract class NonIncrementalSearchHistory implements ActionEvent {

    private enum Status {
        NOT_STARTED,
        COLLECTING_INPUT,
        DONE
    }

    private Status status = Status.NOT_STARTED;
    private IntArrayBuilder searchArgument;
    private final SearchDirection direction;
    private final int[] promptText;

    NonIncrementalSearchHistory(SearchDirection direction, int[] promptText) {
        this.direction = direction;
        this.promptText = promptText;
    }

    @Override
    public void input(Action action, KeyAction key) {
        if (status != Status.COLLECTING_INPUT)
            return;

        if (action instanceof Enter) {
            status = Status.DONE;
        } else if (action instanceof Interrupt) {
            searchArgument = null;
            status = Status.DONE;
        } else if (action instanceof DeletePrevChar) {
            if (searchArgument != null && searchArgument.size() > 0)
                searchArgument.deleteLastEntry();
        } else if (key == Key.ESC) {
            searchArgument = null;
            status = Status.DONE;
        } else if (Key.isPrintable(key.buffer())) {
            if (searchArgument == null)
                searchArgument = new IntArrayBuilder(1);
            searchArgument.append(key.buffer().array()[0]);
        }
    }

    @Override
    public boolean keepFocus() {
        return status == Status.COLLECTING_INPUT;
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        if (status == Status.NOT_STARTED) {
            // Show the search prompt
            status = Status.COLLECTING_INPUT;
            searchArgument = new IntArrayBuilder(1);
            printPrompt(inputProcessor);
        } else if (status == Status.COLLECTING_INPUT) {
            // Update the prompt display with current search term
            printPrompt(inputProcessor);
        } else if (status == Status.DONE) {
            // Perform the search and restore the buffer
            inputProcessor.buffer().buffer().disablePrompt(false);

            if (searchArgument != null && searchArgument.size() > 0) {
                inputProcessor.buffer().history().setSearchDirection(direction);
                int[] result = inputProcessor.buffer().history().search(searchArgument.toArray());

                if (result != null) {
                    inputProcessor.buffer().replace(result);
                } else {
                    // No match — restore empty or original line
                    inputProcessor.buffer().replace(new int[] {});
                }
            } else {
                // Empty search or cancelled — restore original line
                inputProcessor.buffer().replace(new int[] {});
            }

            // Reset state for next invocation
            searchArgument = null;
            status = Status.NOT_STARTED;
        }
    }

    private void printPrompt(InputProcessor inputProcessor) {
        IntArrayBuilder builder = new IntArrayBuilder(promptText);
        if (searchArgument != null && searchArgument.size() > 0) {
            builder.append(searchArgument.toArray());
        }

        inputProcessor.buffer().moveCursor(-inputProcessor.buffer().buffer().cursor());
        inputProcessor.buffer().buffer().disablePrompt(true);
        inputProcessor.buffer().writeOut(ANSI.CURSOR_START);
        inputProcessor.buffer().writeOut(ANSI.ERASE_WHOLE_LINE);
        inputProcessor.buffer().replace(builder.toArray());
    }
}
