/*
 * JBoss, Home of Professional Open Source
 * Copyright 2017 Red Hat Inc. and/or its affiliates and other contributors
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
package org.aesh.readline.cursor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import org.aesh.readline.Buffer;
import org.aesh.readline.terminal.formatting.Color;
import org.aesh.readline.terminal.formatting.TerminalCharacter;
import org.aesh.readline.terminal.formatting.TerminalColor;
import org.aesh.terminal.Connection;
import org.aesh.utils.ANSI;
import org.aesh.util.Parser;

/**
 * A command line. This line abstract commands spread-out on multiple lines.
 *
 * @author jdenise@redhat.com
 */
public class Line {

    private static final Logger LOG = Logger.getLogger(Line.class.getName());

    /**
     * A CursorAction is an action that modifies the cursor.
     */
    public abstract class CursorAction {

        public abstract void apply();
    }

    /**
     * Move the cursor to a given index. It will take into account multilines to
     * retrieve the column/row of a given index.
     */
    public class MoveAction extends CursorAction {

        //private CursorTransaction transaction;
        private final int index;

        MoveAction(int index) {
            this.index = index;
        }

        @Override
        public void apply() {
            CursorLocation loc = buffer.getCursorLocator().locate(index, width);
            if (loc == null) {
                throw new RuntimeException("Null Location for " + index);
            }
            CursorLocation cursorLoc = buffer.getCursorLocator().locate(buffer.multiCursor(), width);
            moveUp(cursorLoc.getRow() - loc.getRow());
            moveBackward(cursorLoc.getColumn());
            moveForward(loc.getColumn());
        }
    }

    /**
     * Move to the index and Colorize the character.
     */
    public class ColorizeAction extends CursorAction {

        private final Color text;
        private final Color background;
        private final int index;
        private final boolean bright;

        ColorizeAction(int index, Color text, Color background, boolean bright) {
            this.index = index;
            this.text = text;
            this.background = background;
            this.bright = bright;
        }

        @Override
        public void apply() {
            MoveAction move = new MoveAction(index);
            move.apply();
            char c = buffer.asString().charAt(index);
            // Usage of Intensity.BRIGHT breaks coloring on Windows.
            // Bold works on both windows and MacOSX, keeping BOLD for all for now.
            //if (OSUtils.IS_WINDOWS) {
            TerminalCharacter characterData = new TerminalCharacter(c, new TerminalColor(text, background));
            if (bright) {
                connection.stdoutHandler().accept(Parser.toCodePoints(ANSI.BOLD));
            } else {
                connection.stdoutHandler().accept(Parser.toCodePoints(ANSI.BOLD_OFF));
            }
            connection.stdoutHandler().accept(Parser.toCodePoints(characterData.toString()));
            //} else {
            //   TerminalColor color = bright ? new TerminalColor(text, background, Color.Intensity.BRIGHT) : new TerminalColor(text, background);
            //   TerminalCharacter characterData = new TerminalCharacter(c, color);
            //  connection.stdoutHandler().accept(Parser.toCodePoints(characterData.toString()));
            //}
            moveBackward(1);
        }
    }

    /**
     * Move cursor backward.
     */
    public class MoveBackwardAction extends CursorAction {

        int move;

        MoveBackwardAction(int move) {
            this.move = move;
        }

        @Override
        public void apply() {
            moveBackward(move);
        }
    }

    /**
     * Move cursor forward.
     */
    public class MoveForwardAction extends CursorAction {

        int move;

        MoveForwardAction(int move) {
            this.move = move;
        }

        @Override
        public void apply() {
            moveForward(move);
        }
    }

    /**
     * Move cursor up.
     */
    public class MoveUpAction extends CursorAction {

        int move;

        MoveUpAction(int move) {
            this.move = move;
        }

        @Override
        public void apply() {
            moveUp(move);
        }
    }

    /**
     * Move cursor down.
     */
    public class MoveDownAction extends CursorAction {

        int move;

        MoveDownAction(int move) {
            this.move = move;
        }

        @Override
        public void apply() {
            moveDown(move);
        }
    }

    /**
     * A cursor transaction runs action. NB: At the end of a transaction, the
     * cursor is back to where it was before the transaction was run. A
     * transaction doesn't change the Aesh internal cursor/content states.
     */
    public class CursorTransaction {

        private final List<CursorAction> actions = new ArrayList<>();

        CursorTransaction(List<CursorAction> actions) {
            this.actions.addAll(actions);
        }

        public void run() {
            saveCursor();
            try {
                for (CursorAction a : actions) {
                    try {
                        a.apply();
                    } catch (Exception ex) {
                        // Something went wrong, don't go any further
                        LOG.fine("Exception in Cursor transaction: "
                                + ex.getLocalizedMessage());
                        break;
                    }
                }
            } finally {
                restoreCursor();
            }
        }
    }

    /**
     * Builder for CursorTransaction.
     */
    public class CursorTransactionBuilder {

        private final List<CursorAction> actions = new ArrayList<>();

        public CursorTransactionBuilder move(int value) {
            actions.add(new MoveAction(value));
            return this;
        }

        public CursorTransactionBuilder colorize(int index, Color text, Color bg, boolean bright) {
            actions.add(new ColorizeAction(index, text, bg, bright));
            return this;
        }

        public CursorTransactionBuilder moveBackward(int value) {
            actions.add(new MoveBackwardAction(value));
            return this;
        }

        public CursorTransactionBuilder moveForward(int value) {
            actions.add(new MoveForwardAction(value));
            return this;
        }

        public CursorTransactionBuilder moveUp(int value) {
            actions.add(new MoveUpAction(value));
            return this;
        }

        public CursorTransactionBuilder moveDown(int value) {
            actions.add(new MoveDownAction(value));
            return this;
        }

        public CursorTransaction build() {
            if (buffer.getCursorLocator().isLocationInvalidated()) {
                return new CursorTransaction(Collections.emptyList());
            } else {
                return new CursorTransaction(actions);
            }
        }
    }

    private final Buffer buffer;
    private final Connection connection;
    private final int width;

    public Line(Buffer buffer, Connection connection, int width) {
        this.buffer = buffer;
        this.connection = connection;
        this.width = width;
    }

    /**
     * Build a new builder.
     *
     * @return The builder.
     */
    public CursorTransactionBuilder newCursorTransactionBuilder() {
        return new CursorTransactionBuilder();
    }

    public CursorLocator getCursorLocator() {
        return buffer.getCursorLocator();
    }

    /**
     * Gets the index of the character where the cursor is located.
     *
     * @return The selected character index.
     */
    public int getCurrentCharacterIndex() {
        return buffer.multiCursor();
    }

    /**
     * Returns a string from the beginning of the line to the cursor. Takes into
     * account multiple lines.
     *
     * @return
     */
    public String getLineToCursor() {
        return buffer.asString().substring(0, buffer.multiCursor());
    }

    /**
     * Returns the character located at the cursor.
     *
     * @return The character.
     */
    public int getCharacterAtCursor() {
        return buffer.get(buffer.cursor());
    }

    private void moveUp(int delta) {
        move(delta, 'A');
    }

    private void moveDown(int delta) {
        move(delta, 'B');
    }

    private void moveForward(int delta) {
        move(delta, 'C');
    }

    private void moveBackward(int delta) {
        move(delta, 'D');
    }

    private void move(int delta, char action) {
        if (delta > 0) {
            connection.stdoutHandler().accept(buffer.moveNumberOfColumns(delta, action));
        }
    }

    private void saveCursor() {
        connection.stdoutHandler().accept(Parser.toCodePoints(ANSI.CURSOR_SAVE));
    }

    private void restoreCursor() {
        connection.stdoutHandler().accept(Parser.toCodePoints(ANSI.CURSOR_RESTORE));
    }

}
