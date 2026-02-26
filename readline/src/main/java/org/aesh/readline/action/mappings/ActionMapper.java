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

        if (function.equals("abort"))
            return new NullAction();
        else if (function.equals("accept-line"))
            return new Enter();
        else if (function.equals("backward-char"))
            return new BackwardChar();
        else if (function.equals("backward-delete-char"))
            return new DeletePrevChar();
        else if (function.equals("backward-kill-line"))
            return new DeleteStartOfLine();
        else if (function.equals("backward-kill-word"))
            return new DeleteBackwardWord();
        else if (function.equals("backward-word"))
            return new MoveBackwardWord();
        else if (function.equals("beginning-of-history"))
            return new BeginningOfHistory();
        else if (function.equals("beginning-of-line"))
            return new BeginningOfLine();
        else if (function.equals("call-last-kbd-macro"))
            return new NullAction(); //TODO: need to add a proper Operation
        else if (function.equals("capitalize-word"))
            return new CapitalizeForwardWord();
        else if (function.equals("character-search"))
            return new CharacterSearch();
        else if (function.equals("character-search-backward"))
            return new CharacterSearchBackward();
        else if (function.equals("clear-screen"))
            return new Clear();
        else if (function.equals("complete"))
            return new Complete();
        else if (function.equals("copy-backward-word"))
            return new CopyBackwardWord();
        else if (function.equals("copy-backward-big-word"))
            return new CopyBackwardBigWord();
        else if (function.equals("copy-forward-word"))
            return new CopyForwardWord();
        else if (function.equals("copy-forward-big-word"))
            return new CopyForwardBigWord();
        else if (function.equals("copy-line"))
            return new CopyLine();
        else if (function.equals("delete-char"))
            return new DeleteChar();
        else if (function.equals("delete-char-or-list"))
            return new DeleteCharOrList();
        else if (function.equals("delete-horizontal-space"))
            return new DeleteHorizontalSpace();
        else if (function.equals("digit-argument"))
            return new NullAction(); //TODO: need to add a proper Operation
        else if (function.equals("do-uppercase-version"))
            return new NullAction(); //TODO: need to add a proper Operation
        else if (function.equals("downcase-word"))
            return new DownCaseForwardWord();
        else if (function.equals("dump-functions"))
            return new NullAction(); //TODO: need to add a proper Operation
        else if (function.equals("dump-macros"))
            return new NullAction(); //TODO: need to add a proper Operation
        else if (function.equals("dump-variables"))
            return new NullAction(); //TODO: need to add a proper Operation
        else if (function.equals("emacs-editing-mode"))
            return new EmacsEditingMode();
        else if (function.equals("end-kbd-macro"))
            return new NullAction(); // TODO: need to add a proper Operation
        else if (function.equals("end-of-history"))
            return new EndOfHistory();
        else if (function.equals("end-of-line"))
            return new EndOfLine();
        else if (function.equals("exchange-point-and-mark"))
            return new ExchangePointAndMark();
        else if (function.equals("forward-backward-delete-char"))
            return new DeleteChar(); //TODO: need a proper impl
        else if (function.equals("forward-char"))
            return new ForwardChar();
        else if (function.equals("forward-search-history"))
            return new ForwardSearchHistory();
        else if (function.equals("forward-word"))
            return new MoveForwardWord();
        else if (function.equals("history-search-backward"))
            return new HistorySearchBackward();
        else if (function.equals("history-search-forward"))
            return new HistorySearchForward();
        else if (function.equals("insert-comment"))
            return new InsertComment();
        else if (function.equals("insert-completions"))
            return new NullAction(); // TODO: need to add a proper Operation
        else if (function.equals("kill-line"))
            return new DeleteEndOfLine();
        else if (function.equals("kill-region"))
            return new KillRegion();
        else if (function.equals("kill-whole-line"))
            return new DeleteLine();
        else if (function.equals("kill-word"))
            return new DeleteForwardWord();
        else if (function.equals("menu-complete"))
            return new NullAction(); // TODO: need to add a proper Operation
        else if (function.equals("menu-complete-backward"))
            return new NullAction(); // TODO: need to add a proper Operation
        else if (function.equals("next-history"))
            return new NextHistory();
        else if (function.equals("non-incremental-forward-search-history"))
            return new NonIncrementalForwardSearchHistory();
        else if (function.equals("non-incremental-reverse-search-history"))
            return new NonIncrementalReverseSearchHistory();
        else if (function.equals("overwrite-mode"))
            return new OverwriteMode();
        else if (function.equals("possible-completions"))
            return new PossibleCompletions();
        else if (function.equals("prefix-meta"))
            return new NullAction(); // TODO: need to add a proper Operation
        else if (function.equals("previous-history"))
            return new PrevHistory();
        else if (function.equals("quoted-insert"))
            return new QuotedInsert();
        else if (function.equals("re-read-init-file"))
            return new NullAction(); // TODO: need to add a proper Operation
        else if (function.equals("redraw-current-line"))
            return new RedrawCurrentLine();
        else if (function.equals("reverse-search-history"))
            return new ReverseSearchHistory();
        else if (function.equals("revert-line"))
            return new RevertLine();
        else if (function.equals("self-insert"))
            return new NullAction(); // TODO: need to add a proper Operation
        else if (function.equals("set-mark"))
            return new SetMark();
        else if (function.equals("skip-csi-sequence"))
            return new NullAction(); // TODO: need to add a proper Operation
        else if (function.equals("start-kbd-macro"))
            return new NullAction(); // TODO: need to add a proper Operation
        else if (function.equals("tilde-expand"))
            return new TildeExpand();
        else if (function.equals("transpose-chars"))
            return new TransposeChars();
        else if (function.equals("transpose-words"))
            return new TransposeWords();
        else if (function.equals("undo"))
            return new Undo();
        else if (function.equals("universal-argument"))
            return new NullAction(); // TODO: need to add a proper Operation
        else if (function.equals("unix-filename-rubout"))
            return new UnixFilenameRubout();
        else if (function.equals("unix-line-discard"))
            return new DeleteStartOfLine();
        else if (function.equals("unix-word-rubout"))
            return new DeleteBackwardBigWord();
        else if (function.equals("upcase-word"))
            return new UpCaseForwardWord();
        else if (function.equals("upcase-char"))
            return new UpCaseChar();
        else if (function.equals("vi-editing-mode"))
            return new ViEditingMode();
        else if (function.equals("yank"))
            return new Yank();
        else if (function.equals("yank-last-arg"))
            return new YankLastArg();
        else if (function.equals("yank-nth-arg"))
            return new YankNthArg();
        else if (function.equals("yank-pop"))
            return new YankPop();
        else if (function.equals("yank-after"))
            return new YankAfter(); // TODO: need to add a proper Operation
        else if (function.equals("eof"))
            return new EndOfFile();

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
