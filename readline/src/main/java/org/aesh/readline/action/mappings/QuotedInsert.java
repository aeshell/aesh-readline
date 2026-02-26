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
import org.aesh.terminal.KeyAction;

/**
 * Add the next character typed to the line verbatim.
 * This is how to insert key sequences like C-q, for example.
 */
public class QuotedInsert implements ActionEvent {

    private boolean waitingForInput = false;
    private int[] storedKey = null;

    @Override
    public String name() {
        return "quoted-insert";
    }

    @Override
    public void accept(InputProcessor inputProcessor) {
        if (waitingForInput) {
            waitingForInput = false;
            if (storedKey != null) {
                inputProcessor.buffer().addActionToUndoStack();
                inputProcessor.buffer().insert(storedKey);
            }
            storedKey = null;
        } else {
            waitingForInput = true;
        }
    }

    @Override
    public void input(Action action, KeyAction key) {
        if (waitingForInput) {
            storedKey = key.buffer().array();
        }
    }

    @Override
    public boolean keepFocus() {
        return waitingForInput;
    }
}
