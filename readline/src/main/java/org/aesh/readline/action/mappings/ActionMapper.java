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

import org.aesh.readline.InputProcessor;
import org.aesh.readline.action.Action;

/**
 * Maps readline function names to their corresponding action implementations.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class ActionMapper {

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private ActionMapper() {
    }

    /**
     * Maps a readline function name to its corresponding action.
     *
     * @param function the readline function name (e.g., "backward-char", "delete-char")
     * @return the action corresponding to the function name, or a no-op action if not found
     */
    public static Action mapToAction(String function) {

        switch (function) {
            case "abort":
                return new NullAction();
            case "accept-line":
                return new Enter();
            case "backward-char":
                return new BackwardChar();
            case "backward-delete-char":
                return new DeletePrevChar();
            case "backward-kill-line":
                return new DeleteStartOfLine();
            case "backward-kill-word":
                return new DeleteBackwardWord();
            case "backward-word":
                return new MoveBackwardWord();
            case "beginning-of-history":
                return new BeginningOfHistory();
            case "beginning-of-line":
                return new BeginningOfLine();
            case "call-last-kbd-macro":
                return new NullAction(); //TODO: need to add a proper Operation
            case "capitalize-word":
                return new CapitalizeForwardWord();
            case "character-search":
                return new CharacterSearch();
            case "character-search-backward":
                return new CharacterSearchBackward();
            case "clear-screen":
                return new Clear();
            case "complete":
                return new Complete();
            case "copy-backward-word":
                return new CopyBackwardWord();
            case "copy-backward-big-word":
                return new CopyBackwardBigWord();
            case "copy-forward-word":
                return new CopyForwardWord();
            case "copy-forward-big-word":
                return new CopyForwardBigWord();
            case "copy-line":
                return new CopyLine();
            case "delete-char":
                return new DeleteChar();
            case "delete-char-or-list":
                return new DeleteCharOrList();
            case "delete-horizontal-space":
                return new DeleteHorizontalSpace();
            case "digit-argument":
                return new NullAction(); //TODO: need to add a proper Operation
            case "do-uppercase-version":
                return new NullAction(); //TODO: need to add a proper Operation
            case "downcase-word":
                return new DownCaseForwardWord();
            case "dump-functions":
                return new NullAction(); //TODO: need to add a proper Operation
            case "dump-macros":
                return new NullAction(); //TODO: need to add a proper Operation
            case "dump-variables":
                return new NullAction(); //TODO: need to add a proper Operation
            case "emacs-editing-mode":
                return new EmacsEditingMode();
            case "end-kbd-macro":
                return new NullAction(); // TODO: need to add a proper Operation
            case "end-of-history":
                return new EndOfHistory();
            case "end-of-line":
                return new EndOfLine();
            case "exchange-point-and-mark":
                return new ExchangePointAndMark();
            case "forward-backward-delete-char":
                return new DeleteChar(); //TODO: need a proper impl
            case "forward-char":
                return new ForwardChar();
            case "forward-search-history":
                return new ForwardSearchHistory();
            case "forward-word":
                return new MoveForwardWord();
            case "history-search-backward":
                return new HistorySearchBackward();
            case "history-search-forward":
                return new HistorySearchForward();
            case "insert-comment":
                return new InsertComment();
            case "insert-completions":
                return new NullAction(); // TODO: need to add a proper Operation
            case "kill-line":
                return new DeleteEndOfLine();
            case "kill-region":
                return new KillRegion();
            case "kill-whole-line":
                return new DeleteLine();
            case "kill-word":
                return new DeleteForwardWord();
            case "menu-complete":
                return new NullAction(); // TODO: need to add a proper Operation
            case "menu-complete-backward":
                return new NullAction(); // TODO: need to add a proper Operation
            case "next-history":
                return new NextHistory();
            case "non-incremental-forward-search-history":
                return new NonIncrementalForwardSearchHistory();
            case "non-incremental-reverse-search-history":
                return new NonIncrementalReverseSearchHistory();
            case "overwrite-mode":
                return new OverwriteMode();
            case "possible-completions":
                return new PossibleCompletions();
            case "prefix-meta":
                return new NullAction(); // TODO: need to add a proper Operation
            case "previous-history":
                return new PrevHistory();
            case "quoted-insert":
                return new QuotedInsert();
            case "re-read-init-file":
                return new NullAction(); // TODO: need to add a proper Operation
            case "redraw-current-line":
                return new RedrawCurrentLine();
            case "reverse-search-history":
                return new ReverseSearchHistory();
            case "revert-line":
                return new RevertLine();
            case "self-insert":
                return new NullAction(); // TODO: need to add a proper Operation
            case "set-mark":
                return new SetMark();
            case "skip-csi-sequence":
                return new NullAction(); // TODO: need to add a proper Operation
            case "start-kbd-macro":
                return new NullAction(); // TODO: need to add a proper Operation
            case "tilde-expand":
                return new TildeExpand();
            case "transpose-chars":
                return new TransposeChars();
            case "transpose-words":
                return new TransposeWords();
            case "undo":
                return new Undo();
            case "universal-argument":
                return new NullAction(); // TODO: need to add a proper Operation
            case "unix-filename-rubout":
                return new UnixFilenameRubout();
            case "unix-line-discard":
                return new DeleteStartOfLine();
            case "unix-word-rubout":
                return new DeleteBackwardBigWord();
            case "upcase-word":
                return new UpCaseForwardWord();
            case "upcase-char":
                return new UpCaseChar();
            case "vi-editing-mode":
                return new ViEditingMode();
            case "yank":
                return new Yank();
            case "yank-last-arg":
                return new YankLastArg();
            case "yank-nth-arg":
                return new YankNthArg();
            case "yank-pop":
                return new YankPop();
            case "yank-after":
                return new YankAfter(); // TODO: need to add a proper Operation
            case "eof":
                return new EndOfFile();
        }

        return new NullAction();
    }

    private static class NullAction implements Action {

        @Override
        public String name() {
            return "no-action";
        }

        @Override
        public void accept(InputProcessor inputProcessor) {
        }
    }
}
