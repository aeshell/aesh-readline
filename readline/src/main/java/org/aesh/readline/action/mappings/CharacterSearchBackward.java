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
import org.aesh.terminal.Key;
import org.aesh.terminal.KeyAction;

/**
 * Read a character and move point to the previous occurrence of that character.
 */
public class CharacterSearchBackward implements ActionEvent {

    /** Constructor. */
    public CharacterSearchBackward() {
    }

    private boolean waitingForInput = false;
    private int searchChar = -1;

    @Override
    public String name() {
        return "character-search-backward";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        if (waitingForInput) {
            waitingForInput = false;
            if (searchChar >= 0) {
                String buffer = inputProcessor.buffer().buffer().asString();
                int cursor = inputProcessor.buffer().buffer().cursor();
                for (int i = cursor - 1; i >= 0; i--) {
                    if (buffer.charAt(i) == searchChar) {
                        inputProcessor.buffer().moveCursor(i - cursor);
                        break;
                    }
                }
            }
            searchChar = -1;
        } else {
            waitingForInput = true;
        }
    }

    @Override
    public void input(Action action, KeyAction key) {
        if (waitingForInput && Key.isPrintable(key.buffer())) {
            searchChar = key.buffer().array()[0];
        }
    }

    @Override
    public boolean keepFocus() {
        return waitingForInput;
    }
}
