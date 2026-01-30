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

/**
 * Represents a single undo operation containing cursor position and buffer state.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class UndoAction {

    private int cursorPosition;
    private int[] buffer;

    /**
     * Creates a new undo action with the specified cursor position and buffer state.
     *
     * @param cursorPosition the cursor position at the time of the action
     * @param buffer the buffer content at the time of the action
     */
    public UndoAction(int cursorPosition, int[] buffer) {
        setCursorPosition(cursorPosition);
        setBuffer(buffer);
    }

    /**
     * Returns the cursor position stored in this undo action.
     *
     * @return the cursor position
     */
    public int getCursorPosition() {
        return cursorPosition;
    }

    /**
     * Sets the cursor position for this undo action.
     *
     * @param cursorPosition the cursor position to set
     */
    private void setCursorPosition(int cursorPosition) {
        this.cursorPosition = cursorPosition;
    }

    /**
     * Returns the buffer content stored in this undo action.
     *
     * @return the buffer content as code points
     */
    public int[] getBuffer() {
        return buffer;
    }

    /**
     * Sets the buffer content for this undo action.
     *
     * @param buffer the buffer content to set
     */
    private void setBuffer(int[] buffer) {
        this.buffer = buffer;
    }
}
