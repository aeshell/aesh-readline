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
package org.aesh.readline.paste;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Keep track of edits for paste
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class PasteManager {

    private static final int PASTE_SIZE = 10;
    private final List<int[]> pasteStack;
    private Consumer<int[]> clipboardWriter;

    /**
     * Creates a new PasteManager with an empty paste stack.
     */
    public PasteManager() {
        pasteStack = new ArrayList<>(PASTE_SIZE);
    }

    /**
     * Adds text to the paste stack. If the stack exceeds its maximum size,
     * the oldest entry is removed.
     *
     * @param buffer the text buffer (as code points) to add to the paste stack
     */
    /**
     * Sets a callback that is invoked whenever text is added to the paste stack.
     * Used to write killed/copied text to the system clipboard via OSC 52.
     *
     * @param clipboardWriter the callback to invoke with the added text, or null to disable
     */
    public void setClipboardWriter(Consumer<int[]> clipboardWriter) {
        this.clipboardWriter = clipboardWriter;
    }

    /**
     * Add text to the paste stack.
     *
     * @param buffer the text to add
     */
    public void addText(int[] buffer) {
        checkSize();
        pasteStack.add(buffer);
        if (clipboardWriter != null) {
            clipboardWriter.accept(buffer);
        }
    }

    /**
     * Ensures the paste stack does not exceed the maximum size by removing
     * the oldest entry if necessary.
     */
    private void checkSize() {
        if (pasteStack.size() >= PASTE_SIZE) {
            pasteStack.remove(0);
        }
    }

    /**
     * Retrieves text from the paste stack at the specified index.
     * Index 0 returns the most recently added text.
     *
     * @param index the index in the paste stack (0 = most recent)
     * @return the text buffer at the specified index, or the oldest entry if index is out of bounds
     */
    public int[] get(int index) {
        if (index < pasteStack.size())
            return pasteStack.get((pasteStack.size() - index - 1));
        else
            return pasteStack.get(0);
    }
}
