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
package org.aesh.readline;

import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.history.History;
import org.aesh.readline.paste.PasteManager;
import org.aesh.readline.undo.UndoManager;
import org.aesh.terminal.tty.Size;

/**
 * Internal class for actions to get access to the buffer, history, connection, ++
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public interface ConsoleBuffer {

    /**
     * Get the command history manager.
     *
     * @return the history manager
     */
    History history();

    /**
     * Get the completion handler for tab completion.
     *
     * @return the completion handler
     */
    CompletionHandler completer();

    /**
     * Set the terminal size.
     *
     * @param size the new terminal size
     */
    void setSize(Size size);

    /**
     * Get the current terminal size.
     *
     * @return the terminal size
     */
    Size size();

    /**
     * Get the input buffer containing the current line being edited.
     *
     * @return the input buffer
     */
    Buffer buffer();

    /**
     * Get the undo manager for undo/redo operations.
     *
     * @return the undo manager
     */
    UndoManager undoManager();

    /**
     * Will add the current action to the undo stack
     */
    void addActionToUndoStack();

    /**
     * Get the paste manager for copy/paste operations.
     *
     * @return the paste manager
     */
    PasteManager pasteManager();

    /**
     * Move the cursor either back or forth.
     * Boundary checks will be done to make sure that the cursor do not go OB.
     *
     * @param where &lt; 0 move back, where &gt; 0 move forward
     */
    void moveCursor(int where);

    /**
     * Print the contents of the current buffer to the console
     */
    void drawLine();

    /**
     * Force a rewrite of the prompt even though it might not be needed
     */
    void drawLineForceDisplay();

    /**
     * Write a single character to the buffer.
     *
     * @param input the character to write
     */
    void writeChar(char input);

    /**
     * Write a string directly to the connection output stream.
     *
     * @param out the string to write
     */
    void writeOut(String out);

    /**
     * Write code points directly to the connection output stream.
     *
     * @param out the code points to write
     */
    void writeOut(int[] out);

    /**
     * Write code points to the buffer.
     *
     * @param input the code points to write
     */
    void writeChars(int[] input);

    /**
     * Write a string to the buffer.
     *
     * @param input the string to write
     */
    void writeString(String input);

    /**
     * Specify the prompt
     *
     * @param prompt new prompt
     */
    void setPrompt(Prompt prompt);

    /**
     * Insert a String into the buffer at a specific point
     *
     * @param insert input
     * @param position point in the buffer
     */
    void insert(String insert, int position);

    /**
     * Insert at the end of the Buffer
     *
     * @param insert data
     */
    void insert(int[] insert);

    /**
     * Delete from cursor position back or forth depending on the value of delta.
     * Delta &lt; 0 delete backwards, delta &gt; 0 delete forwards.
     *
     * @param delta specify which direction and how far to delete
     */
    void delete(int delta);

    /**
     * Up case the char located at the cursor position
     */
    void upCase();

    /**
     * Down case the char located at the cursor position
     */
    void downCase();

    /**
     * Change case on the char located at the cursor position
     */
    void changeCase();

    /**
     * Replace the entire current buffer with the given line
     * The new line will be pushed to the Connection
     * Cursor will be moved to the end of the new buffer line
     *
     * @param line input
     */
    void replace(int[] line);

    /**
     * Replace the entire current buffer with the given line
     * The new line will be pushed to the Connection
     * Cursor will be moved to the end of the new buffer line
     *
     * @param line input
     */
    void replace(String line);

    /**
     * Clear all content in the Buffer and reset all data
     */
    void reset();

    /**
     * Clear an ansi terminal.
     * Set includeBuffer to true if the current buffer should be
     * printed again after clear.
     *
     * @param includeBuffer if true include the current buffer line
     */
    void clear(boolean includeBuffer);

    /**
     * Clear any currently displayed ghost text from the terminal.
     */
    default void clearGhostText() {
    }

    /**
     * Display ghost text (dimmed inline suggestion) after the cursor.
     *
     * @param suggestion the suggestion suffix to display
     */
    default void showGhostText(String suggestion) {
    }

    /**
     * Accept the currently displayed ghost text into the buffer.
     */
    default void acceptGhostText() {
    }

    /**
     * Accept the next word from the currently displayed ghost text into the buffer.
     * The remaining ghost text continues to be displayed.
     */
    default void acceptGhostTextWord() {
    }

    /**
     * Get the currently displayed ghost text.
     *
     * @return the ghost text, or null if none
     */
    default String getGhostText() {
        return null;
    }

}
