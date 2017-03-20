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

import org.aesh.readline.paste.PasteManager;
import org.aesh.readline.undo.UndoManager;
import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.history.History;
import org.aesh.terminal.tty.Size;

/**
 * Internal class for actions to get access to the buffer, history, connection, ++
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public interface ConsoleBuffer {

    /**
     * @return history
     */
    History history();

    /**
     * @return CompletionHandler
     */
    CompletionHandler completer();

    /**
     * @param size specify new size
     */
    void setSize(Size size);

    /**
     * @return size
     */
    Size size();

    /**
     * @return buffer
     */
    Buffer buffer();

    /**
     * @return UndoManager
     */
    UndoManager undoManager();

    /**
     * Will add the current action to the undo stack
     */
    void addActionToUndoStack();

    /**
     * @return PasteManager
     */
    PasteManager pasteManager();

    /**
     * Move the cursor either back or forth.
     * Boundary checks will be done to make sure that the cursor do not go OB.
     *
     * @param where &lt; 0 move back, where &gt; 0 move forward
     */
    void moveCursor(final int where);

    /**
     * Print the contents of the current buffer to the console
     */
    void drawLine();

    /**
     * Force a rewrite of the prompt even though it might not be needed
     */
    void drawLineForceDisplay();

    /**
     * @param input char to write to the Buffer
     */
    void writeChar(char input);

    /**
     * @param out write directly to the Connection output stream
     */
    void writeOut(String out);

    /**
     * @param out write directly to the Connection output stream
     */
    void writeOut(int[] out);

    /**
     * @param input write to the Buffer
     */
    void writeChars(int[] input);

    /**
     * @param input write to the Buffer
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

}
