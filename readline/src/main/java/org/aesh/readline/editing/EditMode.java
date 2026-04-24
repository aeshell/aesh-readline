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
package org.aesh.readline.editing;

import java.util.Arrays;
import java.util.Optional;

import org.aesh.readline.action.Action;
import org.aesh.terminal.Device;
import org.aesh.terminal.Key;
import org.aesh.terminal.KeyAction;

/**
 * Defines the interface for line editing modes such as Emacs and Vi.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public interface EditMode {

    /**
     * Get the editing mode type.
     *
     * @return the mode (EMACS or VI)
     */
    Mode mode();

    /**
     * Get the key actions for this mode.
     *
     * @return the key actions array
     */
    KeyAction[] keys();

    /**
     * Get the current edit status.
     *
     * @return the current status
     */
    Status status();

    /**
     * Set the edit status.
     *
     * @param status the new status
     */
    void setStatus(Status status);

    /**
     * Parse a key event and return the corresponding action.
     *
     * @param event the key event to parse
     * @return the action for this key event, or null if none
     */
    Action parse(KeyAction event);

    /**
     * Check if currently in a chained action sequence.
     *
     * @return true if in a chained action
     */
    boolean isInChainedAction();

    /**
     * Update the ignore-EOF counter.
     *
     * @param eof the EOF value to update
     */
    void updateIgnoreEOF(int eof);

    /**
     * Add a variable with its value.
     *
     * @param variable the variable to set
     * @param value the value to assign
     */
    void addVariable(Variable variable, String value);

    /**
     * Get the value of a variable, wrapped in an Optional.
     *
     * @param variable the variable to look up
     * @return an Optional containing the variable value, or empty if not set
     */
    Optional<String> variable(Variable variable);

    /**
     * Add an action mapping for a key.
     *
     * @param key the key to map
     * @param action the action to perform
     * @return this edit mode for chaining
     */
    EditMode addAction(Key key, Action action);

    /**
     * Add an action mapping by key input and action name.
     *
     * @param input the key input code points
     * @param action the action name
     */
    void addAction(int[] input, String action);

    /**
     * Remap keys based on device capabilities.
     *
     * @param device the terminal device
     */
    void remapKeysFromDevice(Device device);

    /**
     * Get the previous key action.
     *
     * @return the previous key action
     */
    KeyAction prevKey();

    /**
     * Create a key event from input code points.
     *
     * @param input the input code points
     * @return a KeyAction representing the input
     */
    default KeyAction createKeyEvent(int[] input) {
        Key key = Key.getKey(input);
        if (key != null)
            return key;
        else {
            return new KeyAction() {
                private final int[] key = input;

                @Override
                public int getCodePointAt(int index) throws IndexOutOfBoundsException {
                    return key[index];
                }

                @Override
                public int length() {
                    return key.length;
                }

                @Override
                public String name() {
                    return Arrays.toString(key);
                }
            };
        }
    }

    /**
     * Set the previous key action.
     *
     * @param event the key action to set as previous
     */
    void setPrevKey(KeyAction event);

    /**
     * Editing status values.
     */
    enum Status {
        /** Delete text status. */
        DELETE,
        /** Cursor movement status. */
        MOVE,
        /** Yank (copy) text status. */
        YANK,
        /** Change text status. */
        CHANGE,
        /** Edit mode status. */
        EDIT,
        /** Command mode status (vi). */
        COMMAND,
        /** History navigation status. */
        HISTORY,
        /** Search status. */
        SEARCH,
        /** Repeat last action status. */
        REPEAT,
        /** Newline entered status. */
        NEWLINE,
        /** Paste from kill ring status. */
        PASTE,
        /** Paste from system clipboard status. */
        PASTE_FROM_CLIPBOARD,
        /** Tab completion status. */
        COMPLETE,
        /** Undo status. */
        UNDO,
        /** Case change status. */
        CASE,
        /** Exit status. */
        EXIT,
        /** Clear screen status. */
        CLEAR,
        /** Abort current operation status. */
        ABORT,
        /** Change edit mode status. */
        CHANGE_EDITMODE,
        /** No action status. */
        NO_ACTION,
        /** Replace character status. */
        REPLACE,
        /** Interrupt signal status. */
        INTERRUPT,
        /** Ignore EOF status. */
        IGNORE_EOF,
        /** End of file status. */
        EOF,
        /** Upper case status. */
        UP_CASE,
        /** Lower case status. */
        DOWN_CASE,
        /** Capitalize word status. */
        CAPITALIZE,
    }

    /**
     * Editing mode types.
     */
    enum Mode {
        /** Emacs-style editing mode. */
        EMACS,
        /** Vi-style editing mode. */
        VI
    }
}
