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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Defines the allowed values for each readline configuration variable.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public enum VariableValues {

    /** Bell style values: none, visible, audible. */
    BELL_STYLE(Variable.BELL_STYLE, Arrays.asList("none", "visible", "audible")),
    /** Bind TTY special chars: on/off. */
    BIND_TTY_SPECIAL_CHARS(Variable.BIND_TTY_SPECIAL_CHARS, Arrays.asList("on", "off")),
    /** Comment begin string (free-form). */
    COMMENT_BEGIN(Variable.COMMENT_BEGIN, new ArrayList<String>()),
    /** Completion display width (numeric). */
    COMPLETION_DISPLAY_WIDTH(Variable.COMPLETION_DISPLAY_WIDTH, new ArrayList<String>()),
    /** Completion ignore case: on/off. */
    COMPLETION_IGNORE_CASE(Variable.COMPLETION_IGNORE_CASE, Arrays.asList("on", "off")),
    /** Completion map case: on/off. */
    COMPLETION_MAP_CASE(Variable.COMPLETION_MAP_CASE, Arrays.asList("on", "off")),
    /** Completion prefix display length (numeric). */
    COMPLETION_PREFIX_DISPLAY_LENGTH(Variable.COMPLETION_PREFIX_DISPLAY_LENGTH, new ArrayList<String>()),
    /** Completion query items threshold (numeric). */
    COMPLETION_QUERY_ITEMS(Variable.COMPLETION_QUERY_ITEMS, new ArrayList<String>()),
    /** Convert meta characters: on/off. */
    CONVERT_META(Variable.CONVERT_META, Arrays.asList("on", "off")),
    /** Disable completion: on/off. */
    DISABLE_COMPLETION(Variable.DISABLE_COMPLETION, Arrays.asList("on", "off")),
    /** Editing mode: vi or emacs. */
    EDITING_MODE(Variable.EDITING_MODE, Arrays.asList("vi", "emacs")),
    /** Echo control characters: on/off. */
    ECHO_CONTROL_CHARACTERS(Variable.ECHO_CONTROL_CHARACTERS, Arrays.asList("on", "off")),
    /** Enable keypad: on/off. */
    ENABLE_KEYPAD(Variable.ENABLE_KEYPAD, Arrays.asList("on", "off")),
    /** Expand tilde: on/off. */
    EXPAND_TILDE(Variable.EXPAND_TILDE, Arrays.asList("on", "off")),
    /** History preserve point: on/off. */
    HISTORY_PRESERVE_POINT(Variable.HISTORY_PRESERVE_POINT, Arrays.asList("on", "off")),
    /** History size (numeric). */
    HISTORY_SIZE(Variable.HISTORY_SIZE, new ArrayList<String>()),
    /** History scroll mode: on/off. */
    HISTORY_SCROLL_MODE(Variable.HISTORY_SCROLL_MODE, Arrays.asList("on", "off")),
    /** Input meta: on/off. */
    INPUT_META(Variable.INPUT_META, Arrays.asList("on", "off")),
    /** Isearch terminators (free-form). */
    ISEARCH_TERMINATORS(Variable.ISEARCH_TERMINATORS, new ArrayList<String>()),
    /** Keymap names for different editing contexts. */
    KEYMAP(Variable.KEYMAP,
            Arrays.asList("emacs", "vi", "emacs-standard", "emacs-meta", "emacs-ctlx", "vi-move", "vi-command", "vi-insert")),
    /** Mark directories: on/off. */
    MARK_DIRECTORIES(Variable.MARK_DIRECTORIES, Arrays.asList("on", "off")),
    /** Mark modified lines: on/off. */
    MARK_MODIFIED_LINES(Variable.MARK_MODIFIED_LINES, Arrays.asList("on", "off")),
    /** Mark symlinked directories: on/off. */
    MARK_SYMLINKED_DIRECTORIES(Variable.MARK_SYMLINKED_DIRECTORIES, Arrays.asList("on", "off")),
    /** Match hidden files: on/off. */
    MATCH_HIDDEN_FILES(Variable.MATCH_HIDDEN_FILES, Arrays.asList("on", "off")),
    /** Menu complete display prefix: on/off. */
    MENU_COMPLETE_DISPLAY_PREFIX(Variable.MENU_COMPLETE_DISPLAY_PREFIX, Arrays.asList("on", "off")),
    /** Output meta: on/off. */
    OUTPUT_META(Variable.OUTPUT_META, Arrays.asList("on", "off")),
    /** Page completions: on/off. */
    PAGE_COMPLETIONS(Variable.PAGE_COMPLETIONS, Arrays.asList("on", "off")),
    /** Print completions horizontally: on/off. */
    PRINT_COMPLETIONS_HORIZONTALLY(Variable.PRINT_COMPLETIONS_HORIZONTALLY, Arrays.asList("on", "off")),
    /** Revert all at newline: on/off. */
    REVERT_ALL_AT_NEWLINE(Variable.REVERT_ALL_AT_NEWLINE, Arrays.asList("on", "off")),
    /** Show all if ambiguous: on/off. */
    SHOW_ALL_IF_AMBIGUOUS(Variable.SHOW_ALL_IF_AMBIGUOUS, Arrays.asList("on", "off")),
    /** Show all if unmodified: on/off. */
    SHOW_ALL_IF_UNMODIFIED(Variable.SHOW_ALL_IF_UNMODIFIED, Arrays.asList("on", "off")),
    /** Skip completed text: on/off. */
    SKIP_COMPLETED_TEXT(Variable.SKIP_COMPLETED_TEXT, Arrays.asList("on", "off")),
    /** Visible stats: on/off. */
    VISIBLE_STATS(Variable.VISIBLE_STATS, Arrays.asList("on", "off"));

    private Variable variable;
    private List<String> values;

    VariableValues(Variable variable, List<String> values) {
        this.variable = variable;
        this.values = values;
    }

    /**
     * Get the allowed values for a given variable.
     *
     * @param variable the variable to look up
     * @return the list of allowed values, or an empty list if not found
     */
    public static List<String> getValuesByVariable(Variable variable) {
        for (VariableValues value : values()) {
            if (value.variable == variable)
                return value.values;
        }
        return new ArrayList<>();
    }

    /**
     * Check if this variable has defined values.
     *
     * @return true if there are allowed values defined
     */
    public boolean hasValue() {
        return values.size() > 0;
    }
}
