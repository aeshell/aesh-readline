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
package org.aesh.readline.action;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

import org.aesh.readline.editing.EditMode;
import org.aesh.readline.terminal.Key;

/**
 * Decodes input key sequences and maps them to corresponding actions.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class ActionDecoder {

    private KeyAction[] mappings;
    private final Queue<KeyAction> actions = new LinkedList<>();
    private int[] buffer = new int[0];

    /**
     * Creates a decoder with key mappings from the specified edit mode.
     *
     * @param editMode the edit mode providing key mappings
     */
    public ActionDecoder(EditMode editMode) {
        this.mappings = editMode.keys();
    }

    /**
     * Creates a decoder with default key mappings.
     */
    public ActionDecoder() {
        this.mappings = Key.values();
    }

    /**
     * Adds input code points to the decoder buffer.
     *
     * @param input the array of code points to add
     */
    public void add(int[] input) {
        buffer = Arrays.copyOf(buffer, buffer.length + input.length);
        System.arraycopy(input, 0, buffer, buffer.length - input.length, input.length);
    }

    /**
     * Adds a single input code point to the decoder buffer.
     *
     * @param input the code point to add
     */
    public void add(int input) {
        buffer = Arrays.copyOf(buffer, buffer.length + 1);
        System.arraycopy(new int[] { input }, 0, buffer, buffer.length - 1, 1);
    }

    /**
     * Returns the next action without removing it from the queue.
     *
     * @return the next key action, or null if none available
     */
    public KeyAction peek() {
        if (actions.isEmpty()) {
            return parse(buffer);
        } else {
            return actions.peek();
        }
    }

    /**
     * Checks if there is another action available.
     *
     * @return true if another action is available
     */
    public boolean hasNext() {
        return peek() != null;
    }

    /**
     * Returns and removes the next action from the queue.
     *
     * @return the next key action
     */
    public KeyAction next() {
        if (actions.isEmpty()) {
            KeyAction next = parse(buffer);
            if (next != null) {
                actions.add(next);
                buffer = Arrays.copyOfRange(buffer, next.length(), buffer.length);
            }
        }
        return actions.remove();
    }

    /**
     * Updates the key mappings from the specified edit mode.
     *
     * @param editMode the edit mode providing new key mappings
     */
    public void setMappings(EditMode editMode) {
        mappings = editMode.keys();
    }

    private KeyAction parse(int[] buffer) {
        if (buffer.length > 0) {
            KeyAction candidate = null;
            int prefixes = 0;
            next: for (KeyAction action : mappings) {
                if (action.length() > 0) {
                    if (action.length() <= buffer.length) {
                        for (int i = 0; i < action.length(); i++) {
                            if (action.getCodePointAt(i) != buffer[i]) {
                                continue next;
                            }
                        }
                        if (candidate != null && candidate.length() > action.length()) {
                            continue;
                        }
                        candidate = action;
                    } else {
                        for (int i = 0; i < buffer.length; i++) {
                            if (action.getCodePointAt(i) != buffer[i]) {
                                continue next;
                            }
                        }
                        prefixes++;
                    }
                }
            }
            if (candidate == null) {
                if (prefixes == 0) {
                    return new DefaultKeyAction(buffer[0]);
                }
            } else {
                return candidate;
            }
        }
        return null;
    }

    private class DefaultKeyAction implements KeyAction {

        private final int code;

        DefaultKeyAction(int i) {
            code = i;
        }

        @Override
        public int getCodePointAt(int index) throws IndexOutOfBoundsException {
            if (index != 0)
                throw new IndexOutOfBoundsException("Index greater than 0");
            return code;
        }

        @Override
        public int length() {
            return 1;
        }

        @Override
        public String name() {
            return "key: " + code;
        }
    }
}
