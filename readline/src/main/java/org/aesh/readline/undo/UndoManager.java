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
package org.aesh.readline.undo;

import java.util.Stack;

/**
 * Manages a stack of undo actions for line editing operations.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class UndoManager {

    private static final short UNDO_SIZE = 50;

    private final Stack<UndoAction> undoStack;
    private int counter;

    /**
     * Creates a new UndoManager with an empty undo stack.
     */
    public UndoManager() {
        undoStack = new Stack<>();
        undoStack.setSize(UNDO_SIZE);
        counter = 0;
    }

    /**
     * Retrieves and removes the next undo action from the stack.
     *
     * @return the next undo action, or null if the stack is empty
     */
    public UndoAction getNext() {
        if (counter > 0) {
            counter--;
            return undoStack.pop();
        } else
            return null;
    }

    /**
     * Adds an undo action to the stack. If the stack exceeds its maximum size,
     * the oldest action is removed.
     *
     * @param u the undo action to add
     */
    public void addUndo(UndoAction u) {
        if (counter <= UNDO_SIZE) {
            counter++;
            undoStack.push(u);
        } else {
            undoStack.remove(UNDO_SIZE);
            undoStack.push(u);
        }

    }

    /**
     * Clears all undo actions from the stack.
     */
    public void clear() {
        undoStack.clear();
        counter = 0;
    }

    /**
     * Checks if the undo stack is empty.
     *
     * @return true if there are no undo actions, false otherwise
     */
    public boolean isEmpty() {
        return (counter == 0);
    }

    /**
     * Returns the number of undo actions in the stack.
     *
     * @return the number of undo actions
     */
    public int size() {
        return counter;
    }
}
