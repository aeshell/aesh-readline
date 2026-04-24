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

/**
 * Enumeration of readline configuration variables corresponding to inputrc settings.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public enum Variable {

    /**
     * Controls how readline indicates a terminal bell. Values: none, visible, audible.
     */
    BELL_STYLE("bell-style"),

    /**
     * If set to on, readline attempts to bind the control characters treated specially
     * by the kernel's terminal driver to their readline equivalents.
     */
    BIND_TTY_SPECIAL_CHARS("bind-tty-special-chars"),

    /**
     * The string to insert at the beginning of the line when the insert-comment command is executed.
     */
    COMMENT_BEGIN("comment-begin"),

    /**
     * The number of screen columns used to display possible matches when performing completion.
     */
    COMPLETION_DISPLAY_WIDTH("completion-display-width"),

    /**
     * If set to on, readline performs filename matching and completion in a case-insensitive fashion.
     */
    COMPLETION_IGNORE_CASE("completion-ignore-case"),

    /**
     * If set to on, and completion-ignore-case is enabled, readline treats hyphens and underscores as equivalent.
     */
    COMPLETION_MAP_CASE("completion-map-case"),

    /**
     * The length in characters of the common prefix of a list of possible completions
     * that is displayed without modification.
     */
    COMPLETION_PREFIX_DISPLAY_LENGTH("completion-prefix-display-length"),

    /**
     * The number of possible completions that determines when the user is asked whether
     * the list of possibilities should be displayed.
     */
    COMPLETION_QUERY_ITEMS("completion-query-items"),

    /**
     * If set to on, readline converts characters with the eighth bit set to an ASCII key sequence.
     */
    CONVERT_META("convert-meta"),

    /**
     * If set to on, readline inhibits word completion.
     */
    DISABLE_COMPLETION("disable-completion"),

    /**
     * Controls whether readline starts in emacs or vi editing mode.
     */
    EDITING_MODE("editing-mode"),

    /**
     * If set to on, readline displays control characters in a visible fashion when echoing.
     */
    ECHO_CONTROL_CHARACTERS("echo-control-characters"),

    /**
     * If set to on, readline enables the application keypad when called.
     */
    ENABLE_KEYPAD("enable-keypad"),

    /**
     * If set to on, tilde expansion is performed when readline attempts word completion.
     */
    EXPAND_TILDE("expand-tilde"),

    /**
     * If set to on, the history code attempts to place the point at the same location
     * on each history line retrieved with previous-history or next-history.
     */
    HISTORY_PRESERVE_POINT("history-preserve-point"),

    /**
     * Sets the maximum number of history entries saved in the history list.
     */
    HISTORY_SIZE("history-size"),

    /**
     * Controls the scroll mode behavior when navigating through history.
     */
    HISTORY_SCROLL_MODE("history-scroll-mode"),

    /**
     * If set to on, readline enables eight-bit input regardless of terminal claims.
     */
    INPUT_META("input-meta"),

    /**
     * The string of characters that should terminate an incremental search.
     */
    ISEARCH_TERMINATORS("isearch-terminators"),

    /**
     * Sets readline's keymap. Valid keymap names are emacs, emacs-standard, vi, vi-move, vi-command, vi-insert.
     */
    KEYMAP("keymap"),

    /**
     * If set to on, completed directory names have a slash appended.
     */
    MARK_DIRECTORIES("mark-directories"),

    /**
     * If set to on, history lines that have been modified are displayed with a preceding asterisk.
     */
    MARK_MODIFIED_LINES("mark-modified-lines"),

    /**
     * If set to on, completed names which are symbolic links to directories have a slash appended.
     */
    MARK_SYMLINKED_DIRECTORIES("mark-symlinked-directories"),

    /**
     * If set to on, readline matches files whose names begin with a dot when performing filename completion.
     */
    MATCH_HIDDEN_FILES("match-hidden-files"),

    /**
     * If set to on, menu completion displays the common prefix of the list of possible completions.
     */
    MENU_COMPLETE_DISPLAY_PREFIX("menu-complete-display-prefix"),

    /**
     * If set to on, readline displays characters with the eighth bit set directly rather than as a meta-prefixed escape
     * sequence.
     */
    OUTPUT_META("output-meta"),

    /**
     * If set to on, readline uses an internal more-like pager to display a screenful of possible completions at a time.
     */
    PAGE_COMPLETIONS("page-completions"),

    /**
     * If set to on, readline displays completions with matches sorted horizontally in alphabetical order.
     */
    PRINT_COMPLETIONS_HORIZONTALLY("print-completions-horizontally"),

    /**
     * If set to on, readline undoes all changes to history lines before returning when accept-line is executed.
     */
    REVERT_ALL_AT_NEWLINE("revert-all-at-newline"),

    /**
     * If set to on, words which have more than one possible completion cause the matches to be listed immediately.
     */
    SHOW_ALL_IF_AMBIGUOUS("show-all-if-ambiguous"),

    /**
     * If set to on, words which have more than one possible completion without any possible partial completion
     * cause the matches to be listed immediately.
     */
    SHOW_ALL_IF_UNMODIFIED("show-all-if-unmodified"),

    /**
     * If set to on, this alters the default completion behavior when inserting a single match into the line.
     */
    SKIP_COMPLETED_TEXT("skip-completed-text"),

    /**
     * If set to on, a character denoting a file's type is appended to the filename when listing possible completions.
     */
    VISIBLE_STATS("visible-stats");

    /**
     * The inputrc string representation of this variable.
     */
    private final String value;

    /**
     * Constructs a Variable with the specified inputrc string value.
     *
     * @param value the inputrc string representation of this variable
     */
    Variable(String value) {
        this.value = value;
    }

    /**
     * Returns the inputrc string representation of this variable.
     *
     * @return the inputrc string value
     */
    public String getValue() {
        return value;
    }

    /**
     * Finds a Variable by its inputrc string value.
     *
     * @param value the inputrc string value to search for
     * @return the matching Variable, or {@code null} if no match is found or if the value is {@code null}
     */
    public static Variable findVariable(String value) {
        if (value == null)
            return null;
        for (Variable variable : values()) {
            if (variable.getValue().equals(value))
                return variable;
        }
        return null;
    }

}
