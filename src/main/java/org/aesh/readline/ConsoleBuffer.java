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
import org.aesh.tty.Size;
import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.history.History;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public interface ConsoleBuffer {

    History getHistory();

    CompletionHandler getCompleter();

    void setSize(Size size);

    Size getSize();

    Buffer getBuffer();

    UndoManager getUndoManager();

    void addActionToUndoStack();

    PasteManager getPasteManager();

    void moveCursor(final int where);

    void drawLine();

    void writeChar(char input);

    void writeOut(String out);

    void writeOut(int[] out);

    void writeChars(int[] input);

    void writeString(String input);

    void setPrompt(Prompt prompt);

    void insert(String insert, int position);

    void insert(int[] insert);

    void delete(int delta);

    void upCase();

    void downCase();

    void changeCase();

    void replace(int[] line);

    void replace(String line);

    /**
     * Clear an ansi terminal.
     * Set includeBuffer to true if the current buffer should be
     * printed again after clear.
     *
     * @param includeBuffer if true include the current buffer line
     */
    void clear(boolean includeBuffer);

}
