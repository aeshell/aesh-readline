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

package org.aesh.readline.action.mappings;

/**
 * Enumeration of all available readline action names.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public enum Actions {
    /** Abort the current operation. */
    ABORT("abort"),
    /** Accept and execute the current line. */
    ACCEPT_LINE("accept-line"),
    /** Move cursor backward one character. */
    BACKWARD_CHAR("backward-char"),
    /** Delete the character before the cursor. */
    BACKWARD_DELETE_CHAR("backward-delete-char"),
    /** Kill text from cursor to the beginning of the line. */
    BACKWARD_KILL_LINE("backward-kill-line"),
    /** Kill the word before the cursor. */
    BACKWARD_KILL_WORD("backward-kill-word"),
    /** Move cursor backward one word. */
    BACKWARD_WORD("backward-word"),
    /** Move to the first entry in history. */
    BEGINNING_OF_HISTORY("beginning-of-history"),
    /** Move cursor to the beginning of the line. */
    BEGINNING_OF_LINE("beginning-of-line"),
    /** Execute the last keyboard macro. */
    CALL_LAST_KBD_MACRO("call-last-kbd-macro"),
    /** Capitalize the current or following word. */
    CAPITALIZE_WORD("capitalize-word"),
    /** Search forward for a character. */
    CHARACTER_SEARCH("character-search"),
    /** Search backward for a character. */
    CHARACTER_SEARCH_BACKWARD("character-search-backward"),
    /** Clear the terminal screen. */
    CLEAR_SCREEN("clear-screen"),
    /** Trigger command completion. */
    COMPLETE("complete"),
    /** Copy the word before the cursor to the kill ring. */
    COPY_BACKWARD_WORD("copy-backward-word"),
    /** Copy the word after the cursor to the kill ring. */
    COPY_FORWARD_WORD("copy-forward-word"),
    /** Copy the entire line to the kill ring. */
    COPY_LINE("copy-line"),
    /** Delete the character at the cursor. */
    DELETE_CHAR("delete-char"),
    /** Delete character at cursor or list completions if line is empty. */
    DELETE_CHAR_OR_LIST("delete-char-or-list"),
    /** Delete all horizontal whitespace around the cursor. */
    DELETE_HORIZONTAL_SPACE("delete-horizontal-space"),
    /** Start or accumulate a numeric argument for subsequent commands. */
    DIGIT_ARGUMENT("digit_argument"),
    /** Execute the uppercase equivalent of a meta command. */
    DO_UPPERCASE_VERSION("do-uppercase-version"),
    /** Convert the current or following word to lowercase. */
    DOWNCASE_WORD("downcase-word"),
    /** Dump all readline functions to output. */
    DUMP_FUNCTIONS("dump-functions"),
    /** Dump all readline macros to output. */
    DUMP_MACROS("dump-macros"),
    /** Dump all readline variables to output. */
    DUMP_VARIABLES("dump-variables"),
    /** Switch to emacs editing mode. */
    EMACS_EDITING_MODE("emacs-editing-mode"),
    /** Stop recording a keyboard macro. */
    END_KBD_MACRO("end-kbd-macro"),
    /** Move to the last entry in history. */
    END_OF_HISTORY("end-of-history"),
    /** Move cursor to the end of the line. */
    END_OF_LINE("end-of-line"),
    /** Swap the cursor position with the mark. */
    EXCHANGE_POINT_AND_MARK("exchange-point-and-mark"),
    /** Delete character at cursor or before cursor depending on position. */
    FORWARD_BACKWARD_DELETE_CHAR("forward-backward-delete-char"),
    /** Move cursor forward one character. */
    FORWARD_CHAR("forward-char"),
    /** Search forward through history. */
    FORWARD_SEARCH_HISTORY("forward-search-history"),
    /** Move cursor forward one word. */
    FORWARD_WORD("forward-word"),
    /** Search backward starting from forward position in history. */
    FORWARD_SEARCH_BACKWARD("forward-search-backward"),
    /** Search forward through history for lines starting with current input. */
    HISTORY_SEARCH_FORWARD("history-search-forward"),
    /** Insert a comment character at the beginning of the line. */
    INSERT_COMMENT("insert-comment"),
    /** Insert all completions of the text before cursor. */
    INSERT_COMPLETIONS("insert-completions"),
    /** Kill text from cursor to the end of the line. */
    KILL_LINE("kill-line"),
    /** Kill the text between cursor and mark. */
    KILL_REGION("kill-region"),
    /** Kill the entire current line. */
    KILL_WHOLE_LINE("kill-whole-line"),
    /** Kill the word after the cursor. */
    KILL_WORD("kill-word"),
    /** Complete using menu of possible completions. */
    MENU_COMPLETE("menu-complete"),
    /** Complete backward using menu of possible completions. */
    MENU_COMPLETE_BACKWARD("menu-complete-backward"),
    /** Move to the next entry in history. */
    NEXT_HISTORY("next-history"),
    /** Search forward through history non-incrementally. */
    NON_INCREMENTAL_FORWARD_SEARCH_HISTORY("non-incremental-forward-search-history"),
    /** Search backward through history non-incrementally. */
    NON_INCREMENTAL_REVERSE_SEARCH_HISTORY("non-incremental-reverse-search-history"),
    /** Toggle overwrite mode for character insertion. */
    OVERWRITE_MODE("overwrite-mode"),
    /** List possible completions for the current input. */
    POSSIBLE_COMPLETIONS("possible-completions"),
    /** Make the next character typed be meta-prefixed. */
    PREFIX_META("prefix-meta"),
    /** Move to the previous entry in history. */
    PREVIOUS_HISTORY("previous-history"),
    /** Insert the next character literally without interpretation. */
    QUOTED_INSERT("quoted-insert"),
    /** Re-read the readline initialization file. */
    RE_READ_INIT_FILE("re-read-init-file"),
    /** Redraw the current line. */
    REDRAW_CURRENT_LINE("redraw-current-line"),
    /** Search backward through history incrementally. */
    REVERSE_SEARCH_HISTORY("reverse-search-history"),
    /** Revert the line to its original state from history. */
    REVERT_LINE("revert-line"),
    /** Insert the typed character at the cursor position. */
    SELF_INSERT("self-insert"),
    /** Set the mark at the current cursor position. */
    SET_MARK("set-mark"),
    /** Skip a CSI escape sequence. */
    SKIP_CSI_SEQUENCE("skip-csi-sequence"),
    /** Start recording a keyboard macro. */
    START_KBD_MACRO("start-kbd-macro"),
    /** Insert a tab character. */
    TAB_INSERT("tab-insert"),
    /** Expand tilde to home directory path. */
    TILDE_EXPAND("tilde-expand"),
    /** Swap the character at cursor with the previous character. */
    TRANSPOSE_CHARS("transpose-chars"),
    /** Swap the word at cursor with the previous word. */
    TRANSPOSE_WORDS("transpose-words"),
    /** Undo the last editing change. */
    UNDO("undo"),
    /** Multiply the argument count by four. */
    UNIVERSAL_ARGUMENT("universal-argument"),
    /** Kill backward to the previous slash or whitespace. */
    UNIX_FILENAME_RUBOUT("unix-filename-rubout"),
    /** Convert the character at cursor to uppercase. */
    UPCASE_CHAR("upcase-char"),
    /** Convert the current or following word to uppercase. */
    UPCASE_WORD("upcase-word"),
    /** Switch to vi editing mode. */
    VI_EDITING_MODE("vi-editing-mode"),
    /** Yank (paste) the most recently killed text. */
    YANK("yank"),
    /** Yank the last argument from the previous command. */
    YANK_LAST_ARG("yank-last-arg"),
    /** Yank the nth argument from the previous command. */
    YANK_NTH_ARG("yank-nth-arg"),
    /** Cycle through the kill ring after a yank. */
    YANK_POP("yank-pop"),
    /** Yank text after the cursor position (vi mode). */
    YANK_AFTER("yank-after");

    Actions(String name) {
    }
}
