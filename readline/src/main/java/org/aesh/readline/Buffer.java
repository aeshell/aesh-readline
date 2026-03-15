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

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.logging.Logger;

import org.aesh.readline.cursor.CursorLocator;
import org.aesh.terminal.tty.impl.WinSysTerminal;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.Config;
import org.aesh.terminal.utils.IntArrayBuilder;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.Parser;
import org.aesh.terminal.utils.WcWidth;

/**
 * Buffer to keep track of text and cursor position in the console.
 * Is using ANSI-codes to clear text and move cursor in the terminal.
 *
 * @apiNote This class is an internal implementation detail of the readline library
 *          and is not intended for use by external consumers. It may change without notice
 *          in future releases.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public final class Buffer {

    private static final Logger LOGGER = LoggerUtil.getLogger(Buffer.class.getName());

    private int[] line;
    private int cursor;
    private int size;
    private Prompt prompt;
    private int delta; //need to keep track of a delta for ansi terminal
    //if delta happens at the end of the buffer, we can optimize
    //how we update the tty
    private boolean deltaChangedAtEndOfBuffer = true;
    private boolean disablePrompt = false;
    private boolean multiLine = false;
    private int[] multiLineBuffer = new int[0];
    private boolean isPromptDisplayed = false;
    private boolean deletingBackward = true;
    private int mark = -1;
    private boolean overwriteMode = false;

    private final CursorLocator locator;

    Buffer() {
        line = new int[1024];
        prompt = new Prompt("");
        locator = new CursorLocator(this);
    }

    Buffer(Prompt prompt) {
        line = new int[1024];
        if (prompt != null)
            this.prompt = prompt;
        else
            this.prompt = new Prompt("");
        locator = new CursorLocator(this);
    }

    /**
     * Creates a copy of an existing buffer.
     *
     * @param buf the buffer to copy
     */
    public Buffer(Buffer buf) {
        line = buf.line.clone();
        cursor = buf.cursor;
        size = buf.size;
        prompt = buf.prompt.copy();
        locator = new CursorLocator(this);
    }

    /**
     * Returns the cursor locator for this buffer.
     *
     * @return the cursor locator used to track cursor position
     */
    public CursorLocator getCursorLocator() {
        return locator;
    }

    /**
     * Returns the character at the specified position in the buffer.
     *
     * @param pos the position in the buffer
     * @return the character code point at the specified position
     * @throws IndexOutOfBoundsException if the position is out of bounds
     */
    public int get(int pos) {
        if (pos > -1 && pos <= size)
            return line[pos];
        else
            throw new IndexOutOfBoundsException();
    }

    /**
     * Returns the current cursor position in the buffer.
     *
     * @return the current cursor position
     */
    public int cursor() {
        return cursor;
    }

    /**
     * Returns the cursor position including the multi-line buffer offset.
     * If the buffer is in multi-line mode, this returns the cursor position
     * relative to the entire multi-line content.
     *
     * @return the cursor position including multi-line offset
     */
    public int multiCursor() {
        if (multiLine) {
            return multiLineBuffer.length + cursor;
        }
        return cursor;
    }

    /**
     * Checks if the buffer is in masking mode (e.g., for password input).
     *
     * @return true if input is being masked, false otherwise
     */
    public boolean isMasking() {
        return prompt.isMasking();
    }

    /**
     * Checks if the buffer is in multi-line mode.
     *
     * @return true if the buffer is in multi-line mode, false otherwise
     */
    public boolean isMultiLine() {
        return multiLine;
    }

    /**
     * Returns the entire buffer content as a string, including multi-line content.
     *
     * @return the buffer content as a string
     */
    public String asString() {
        return Parser.fromCodePoints(multiLine());
    }

    /**
     * Resets the buffer to its initial empty state.
     * This clears all content, resets the cursor to position 0,
     * and clears any multi-line buffer content.
     */
    public void reset() {
        cursor = 0;
        for (int i = 0; i < size; i++)
            line[i] = 0;
        size = 0;
        isPromptDisplayed = false;
        if (multiLine) {
            multiLineBuffer = new int[0];
            multiLine = false;
        }
        locator.clear();
    }

    /**
     * Sets whether the prompt has been displayed.
     *
     * @param isPromptDisplayed true if the prompt is displayed, false otherwise
     */
    public void setIsPromptDisplayed(boolean isPromptDisplayed) {
        this.isPromptDisplayed = isPromptDisplayed;
    }

    /**
     * Forces the delta changed at end of buffer flag to a specific value.
     * This flag is used to optimize terminal updates.
     *
     * @param delta the value to set for the deltaChangedAtEndOfBuffer flag
     */
    public void forceSetDeltaChangedAtEndOfBuffer(boolean delta) {
        deltaChangedAtEndOfBuffer = delta;
    }

    /**
     * Need to disable prompt in calculations involving search.
     *
     * @param disable prompt or not
     */
    public void disablePrompt(boolean disable) {
        disablePrompt = disable;
    }

    /**
     * Checks if the prompt is disabled for calculations.
     *
     * @return true if the prompt is disabled, false otherwise
     */
    public boolean isPromptDisabled() {
        return disablePrompt;
    }

    void setPrompt(Prompt prompt, Consumer<int[]> out, int width) {
        if (prompt != null) {
            delta = prompt.getLength() - this.prompt.getLength();
            this.prompt = prompt;
            print(out, width);
        }
    }

    /**
     * Returns the current prompt.
     *
     * @return the prompt associated with this buffer
     */
    public Prompt prompt() {
        return prompt;
    }

    /**
     * Returns the length of the buffer content.
     * If masking with a null mask character, returns 1.
     *
     * @return the length of the buffer content
     */
    public int length() {
        if (isMasking() && prompt.getMask() == 0)
            return 1;
        else
            return size;
    }

    private int promptLength() {
        return disablePrompt ? 0 : prompt.getLength();
    }

    /**
     * Sets the multi-line mode for this buffer.
     * Multi-line mode is not enabled if the buffer is in masking mode.
     *
     * @param multi true to enable multi-line mode, false to disable
     */
    public void setMultiLine(boolean multi) {
        if (!isMasking())
            multiLine = multi;
    }

    /**
     * Some completion occured, do not try to compute character index location.
     * This could be revisited to implement a strategy.
     */
    public void invalidateCursorLocation() {
        if (isMultiLine()) {
            locator.invalidateCursorLocation();
        }
    }

    /**
     * Updates the multi-line buffer by appending the current line content.
     * If the line ends with a backslash, the backslash is removed.
     * Otherwise, a newline is appended to the buffer.
     */
    public void updateMultiLineBuffer() {
        int originalSize = multiLineBuffer.length;
        // Store the size of each line.
        int cmdSize;
        if (lineEndsWithBackslash()) {
            cmdSize = size - 1;
            multiLineBuffer = Arrays.copyOf(multiLineBuffer, originalSize + size - 1);
            System.arraycopy(line, 0, multiLineBuffer, originalSize, size - 1);
        }
        //here we have an open quote, so we need to feed a new-line into the buffer
        else {
            cmdSize = size + Config.getLineSeparator().length();
            multiLineBuffer = Arrays.copyOf(multiLineBuffer, originalSize + cmdSize);
            System.arraycopy(line, 0, multiLineBuffer, originalSize, size);
            // add new line
            int[] lineSeparator = Parser.toCodePoints(Config.getLineSeparator());
            System.arraycopy(lineSeparator, 0, multiLineBuffer, originalSize + size, lineSeparator.length);
        }
        locator.addLine(cmdSize, prompt.getLength());
        clear();
        prompt = new Prompt("> ");
        cursor = 0;
        size = 0;
    }

    private boolean lineEndsWithBackslash() {
        return (size > 0 && line[size - 1] == '\\');
    }

    /**
     * Inserts text at the current cursor position.
     *
     * @param out the output consumer for terminal updates
     * @param data the text to insert as an array of code points
     * @param width the terminal width
     */
    public void insert(Consumer<int[]> out, int[] data, int width) {
        doInsert(data);
        printInsertedData(out, width);
    }

    /**
     * Inserts a single character at the current cursor position.
     *
     * @param out the output consumer for terminal updates
     * @param data the character code point to insert
     * @param width the terminal width
     */
    public void insert(Consumer<int[]> out, int data, int width) {
        doInsert(data);
        printInsertedData(out, width);
    }

    private void doInsert(int data) {
        int width = WcWidth.width(data);
        if (width == -1) {
            //todo: handle control chars...
        } else if (width == 1) {
            if (cursor < size)
                System.arraycopy(line, cursor, line, cursor + 1, size - cursor);
            line[cursor++] = data;
            size++;
            delta++;
            if (size == line.length)
                line = Arrays.copyOf(line, line.length + line.length / 2);

            deltaChangedAtEndOfBuffer = (size == cursor);
        }
    }

    private void doInsert(int[] data) {
        boolean gotControlChar = false;
        for (int aData : data) {
            int width = WcWidth.width(aData);
            if (width == -1) {
                gotControlChar = true;
                //todo: handle control chars...
            }
        }
        if (!gotControlChar) {
            doActualInsert(data);
        }
    }

    private void doActualInsert(int[] data) {
        if (data.length > (line.length - size))
            line = Arrays.copyOf(line, line.length + data.length + 1);
        if (cursor < size)
            System.arraycopy(line, cursor, line, cursor + data.length, size - cursor);
        for (int aData : data)
            line[cursor++] = aData;
        size += data.length;
        delta += data.length;

        deltaChangedAtEndOfBuffer = (size == cursor);
    }

    /**
     * Move the cursor left if the param is negative, right if its positive.
     *
     * @param out stream
     * @param move where to move
     * @param termWidth terminal width
     */
    public void move(Consumer<int[]> out, int move, int termWidth) {
        move(out, move, termWidth, false);
    }

    /**
     * Move the cursor left if the param is negative, right if its positive.
     * If viMode is true, the cursor will not move beyond the current buffer size
     *
     * @param out stream
     * @param move where to move
     * @param termWidth terminal width
     * @param viMode edit mode (vi or emacs)
     */
    public void move(Consumer<int[]> out, int move, int termWidth, boolean viMode) {
        move = calculateActualMovement(move, viMode);
        //quick exit
        if (move == 0)
            return;

        // 0 Masking separates the UI cursor position from the 'real' cursor position.
        // Cursor movement still has to occur, via calculateActualMovement and setCursor above,
        // to put new characters in the correct location in the invisible line,
        // but this method should always return an empty character so the UI cursor does not move.
        if (isMasking() && prompt.getMask() == 0) {
            return;
        }

        out.accept(syncCursor(promptLength() + cursor, promptLength() + cursor + move, termWidth));

        cursor = cursor + move;

    }

    private int[] syncCursor(int currentPos, int newPos, int width) {
        return syncCursor(currentPos, newPos, width, false);
    }

    private int[] syncCursor(int currentPos, int newPos, int width, boolean edge) {
        IntArrayBuilder builder = new IntArrayBuilder(16);
        if (newPos < 0)
            newPos = 0;
        if (currentPos / width == newPos / width) {
            if (currentPos > newPos) {
                // Case in which we are suppressing char and command reaches the edge of
                // terminal width. We must on mac offset of + 1.
                int move = Config.isMacOS() && edge ? currentPos - newPos + 1 : currentPos - newPos;
                builder.append(moveNumberOfColumns(move, 'D'));
            } else
                builder.append(moveNumberOfColumns(newPos - currentPos, 'C'));
        }
        //if cursor and end of buffer is on different lines, we need to move the cursor
        else {
            int moveToLine = currentPos / width - newPos / width;
            int moveToColumn = currentPos % width - newPos % width;
            char rowDirection = 'A';
            if (moveToLine < 0) {
                rowDirection = 'B';
                moveToLine = Math.abs(moveToLine);
            }
            builder.append(moveNumberOfColumnsAndRows(moveToLine, rowDirection, moveToColumn));
        }
        return builder.toArray();
    }

    /**
     * This is a special case when we have a insert and the buffer is at the
     * terminal edge.
     * Move cursor to the correct line if its not on the same line.
     * Move cursor to the beginning of the line, then move it to its correct position
     *
     * @param currentPos current position
     * @param newPos end position
     * @param width terminal width
     * @return out buffer
     */
    private int[] syncCursorWhenBufferIsAtTerminalEdge(int currentPos, int newPos, int width) {
        IntArrayBuilder builder = new IntArrayBuilder(16);
        if (Config.isMacOS()) {
            int moveToLine = currentPos / width - newPos / width;
            char rowDirection = 'A';
            if (moveToLine < 0) {
                rowDirection = 'B';
                moveToLine = Math.abs(moveToLine);
            }
            // The cursor is at the 0 of the padded line. Move up and forward (yes - means forward).
            // to newPos.
            builder.append(moveNumberOfColumnsAndRows(moveToLine, rowDirection, -(newPos % width)));
        } else {
            if (currentPos / width == newPos / width) {
                builder.append(moveNumberOfColumns(width, 'D'));
            } else {
                //if cursor and end of buffer is on different lines, we need to move the cursor
                int moveToLine = currentPos / width - newPos / width;
                char rowDirection = 'A';
                if (moveToLine < 0) {
                    rowDirection = 'B';
                    moveToLine = Math.abs(moveToLine);
                }
                builder.append(moveNumberOfColumnsAndRows(moveToLine, rowDirection, width));
            }
            //now the cursor should be on the correct line and at position 0
            // we then need to move it to newPos
            builder.append(moveNumberOfColumns(newPos % width, 'C'));
        }
        return builder.toArray();
    }

    /**
     * Creates an ANSI escape sequence to move the cursor a specified number of columns.
     *
     * @param column the number of columns to move
     * @param direction the direction character ('C' for right, 'D' for left, 'A' for up, 'B' for down)
     * @return an int array containing the ANSI escape sequence
     */
    public static int[] moveNumberOfColumns(int column, char direction) {
        if (column < 10) {
            int[] out = new int[4];
            out[0] = 27; // esc
            out[1] = '['; // [
            out[2] = 48 + column;
            out[3] = direction;
            return out;
        } else {
            int[] asciiColumn = intToAsciiInts(column);
            int[] out = new int[3 + asciiColumn.length];
            out[0] = 27; // esc
            out[1] = '['; // [
            //for(int i=0; i < asciiColumn.length; i++)
            //    out[2+i] = asciiColumn[i];
            System.arraycopy(asciiColumn, 0, out, 2, asciiColumn.length);
            out[out.length - 1] = direction;
            return out;
        }
    }

    private int[] moveNumberOfColumnsAndRows(int row, char rowCommand, int column) {
        char direction = 'D'; //forward
        if (column < 0) {
            column = Math.abs(column);
            direction = 'C';
        }
        if (row < 10 && column < 10) {
            int[] out = new int[8];
            out[0] = 27; //esc, \033
            out[1] = '[';
            out[2] = 48 + row;
            out[3] = rowCommand;
            out[4] = 27;
            out[5] = '[';
            out[6] = 48 + column;
            out[7] = direction;
            return out;
        } else {
            int[] asciiRow = intToAsciiInts(row);
            int[] asciiColumn = intToAsciiInts(column);
            int[] out = new int[6 + asciiColumn.length + asciiRow.length];
            out[0] = 27; //esc, \033
            out[1] = '[';
            //for(int i=0; i < asciiRow.length; i++)
            //    out[2+i] = asciiRow[i];
            System.arraycopy(asciiRow, 0, out, 2, asciiRow.length);
            out[2 + asciiRow.length] = rowCommand;
            out[3 + asciiRow.length] = 27;
            out[4 + asciiRow.length] = '[';
            for (int i = 0; i < asciiColumn.length; i++)
                out[5 + asciiRow.length + i] = asciiColumn[i];
            out[out.length - 1] = direction;
            return out;
        }
    }

    /**
     * Make sure that the cursor do not move ob (out of bounds)
     *
     * @param move left if its negative, right if its positive
     * @param viMode if viMode we need other restrictions compared
     *        to emacs movement
     * @return adjusted movement
     */
    private int calculateActualMovement(final int move, boolean viMode) {
        // cant move to a negative value
        if (cursor() == 0 && move <= 0)
            return 0;
        // cant move longer than the length of the line
        if (viMode) {
            if (cursor() == length() - 1 && (move > 0))
                return 0;
        } else {
            if (cursor() == length() && (move > 0))
                return 0;
        }

        // dont move out of bounds
        if (cursor() + move <= 0)
            return -cursor();

        if (viMode) {
            if (cursor() + move > length() - 1)
                return (length() - 1 - cursor());
        } else {
            if (cursor() + move > length())
                return (length() - cursor());
        }

        return move;
    }

    private int[] getLineFrom(int position) {
        return Arrays.copyOfRange(line, position, size);
    }

    /**
     * Returns the current line content, with masking applied if enabled.
     * If masking is enabled, each character is replaced with the mask character.
     * If the mask character is null (0), an empty array is returned.
     *
     * @return the line content with masking applied, or the original line if not masking
     */
    public int[] getLineMasked() {
        if (!isMasking())
            return Arrays.copyOf(line, size);
        else {
            if (size > 0 && prompt.getMask() != '\u0000') {
                int[] tmpLine = new int[size];
                Arrays.fill(tmpLine, prompt.getMask());
                return tmpLine;
            } else
                return new int[0];
        }
    }

    private int[] getLine() {
        return Arrays.copyOf(line, size);
    }

    /**
     * Clears the buffer content without resetting multi-line state.
     * This resets the cursor position and size to 0 and marks the prompt as not displayed.
     */
    public void clear() {
        Arrays.fill(this.line, 0, size, 0);
        cursor = 0;
        size = 0;
        isPromptDisplayed = false;
    }

    /**
     * If delta > 0 * print from cursor
     * if keepCursor, move cursor back to previous position
     * if delta < 0
     * if deltaChangedAtEndOf buffer {
     * if delta == -1 {
     * clear line from cursor
     * move cursor back
     * }
     * else if cursor + delta > width {
     * check if we need to delete more than the current line
     * }
     * }
     *
     * @param out output
     * @param width terminal size
     */
    void print(Consumer<int[]> out, int width) {
        print(out, width, false);
    }

    private void print(Consumer<int[]> out, int width, boolean viMode) {
        if (delta >= 0)
            printInsertedData(out, width);
        else {
            printDeletedData(out, width, viMode);
        }
        delta = 0;
    }

    private void printInsertedData(Consumer<int[]> out, int width) {
        //print out prompt first if needed
        IntArrayBuilder builder = new IntArrayBuilder(promptLength() + size + 32);
        if (!isPromptDisplayed) {
            //only print the prompt if its longer than 0
            if (promptLength() > 0)
                builder.append(prompt.getANSI());
            isPromptDisplayed = true;
            //need to print the entire buffer
            //force that by setting delta = cursor if delta is 0
            if (delta == 0)
                delta = cursor;
        }
        //quick exit if buffer is empty
        if (size == 0) {
            out.accept(builder.toArray());
            return;
        }

        if (isMasking()) {
            if (prompt.getMask() != 0) {
                int[] mask = new int[delta];
                Arrays.fill(mask, prompt.getMask());
                builder.append(mask);
            }
            //a quick exit if we're masking with a no output mask
            else {
                out.accept(builder.toArray());
                delta = 0;
                deltaChangedAtEndOfBuffer = true;
            }
        } else {
            if (deltaChangedAtEndOfBuffer) {
                if (delta == 1 || delta == 0) {
                    if (cursor > 0)
                        builder.append(new int[] { line[cursor - 1] });
                    else
                        builder.append(new int[] { line[0] });
                } else
                    builder.append(Arrays.copyOfRange(line, cursor - delta, cursor));
            } else {
                builder.append(Arrays.copyOfRange(line, cursor - delta, size));
            }
        }

        //pad if we are at the end of the terminal
        if ((size + promptLength()) % width == 0) {
            builder.append(new int[] { 32, 13 });
        }
        //make sure we sync the cursor back
        if (!deltaChangedAtEndOfBuffer) {
            if ((size + promptLength()) % width == 0 &&
                    (Config.isOSPOSIXCompatible() || (Config.isWindows() && WinSysTerminal.isVTSupported()))) {
                builder.append(syncCursorWhenBufferIsAtTerminalEdge(size + promptLength(), cursor + promptLength(), width));
            } else
                builder.append(syncCursor(size + promptLength(), cursor + promptLength(), width));
        }

        out.accept(builder.toArray());
        delta = 0;
        deltaChangedAtEndOfBuffer = true;
    }

    private void printDeletedData(Consumer<int[]> out, int width, boolean viMode) {
        //if we're masking and the mask is no output we just return
        if (width == 0 || (isMasking() && prompt.getMask() == 0))
            return;
        IntArrayBuilder builder = new IntArrayBuilder(32);
        if (size + promptLength() + Math.abs(delta) >= width) {
            if (deletingBackward) {
                //lets optimize deletes at the end
                if (deltaChangedAtEndOfBuffer &&
                        ((size + promptLength() + 1) % width > Math.abs(delta))) {
                    quickDeleteAtEnd(out, viMode);
                    return;
                } else {
                    clearAllLinesAndReturnToFirstLine(builder,
                            width, cursor + promptLength() + Math.abs(delta),
                            size + promptLength() + Math.abs(delta));
                }
            } else
                clearAllLinesAndReturnToFirstLine(builder,
                        width, cursor + promptLength(),
                        size + promptLength() + Math.abs(delta));
        }

        if ((size + promptLength() + 1) < width && deltaChangedAtEndOfBuffer)
            quickDeleteAtEnd(out, viMode);
        else
            moveCursorToStartAndPrint(out, builder, width, false, viMode);
    }

    private void quickDeleteAtEnd(Consumer<int[]> out, boolean viMode) {
        //move cursor delta then clear the rest of the line
        IntArrayBuilder builder = new IntArrayBuilder(16);
        //only have to move when deleting backwards
        if (deletingBackward)
            builder.append(moveNumberOfColumns(Math.abs(delta), 'D'));
        builder.append(ANSI.ERASE_LINE_FROM_CURSOR);

        if (viMode && cursor == size) {
            builder.append(moveNumberOfColumns(1, 'D'));
            cursor--;
        }

        out.accept(builder.toArray());
    }

    /**
     * Replace the entire current buffer with the given line.
     * The new line will be pushed to the consumer
     * Cursor will be moved to the end of the new buffer line
     *
     * @param out stream
     * @param line new buffer line
     * @param width term width
     */
    public void replace(Consumer<int[]> out, String line, int width) {
        replace(out, Parser.toCodePoints(line), width);
    }

    /**
     * Replaces the entire current buffer with the given line as code points.
     * The new line will be pushed to the consumer and the cursor will be
     * moved to the end of the new buffer line.
     *
     * @param out the output consumer for terminal updates
     * @param line the new buffer content as an array of code points
     * @param width the terminal width
     */
    public void replace(Consumer<int[]> out, int[] line, int width) {
        //quick exit
        if (line == null || size == 0 && line.length == 0)
            return;

        int tmpDelta = line.length - size;
        int oldSize = size + promptLength();
        int oldCursor = cursor + promptLength();
        clear();
        doInsert(line);
        delta = tmpDelta;
        //deltaChangedAtEndOfBuffer = false;
        deltaChangedAtEndOfBuffer = (cursor == size);

        IntArrayBuilder builder = new IntArrayBuilder(32);
        if (oldSize >= width)
            clearAllLinesAndReturnToFirstLine(builder, width, oldCursor, oldSize);

        moveCursorToStartAndPrint(out, builder, width, true, false);

        delta = 0;
        deltaChangedAtEndOfBuffer = true;
    }

    /**
     * All parameter values are included the prompt length
     *
     * @param builder int[] builder
     * @param width terminal size
     * @param oldCursor prev position
     * @param oldSize prev terminal size
     */
    private void clearAllLinesAndReturnToFirstLine(IntArrayBuilder builder, int width,
            int oldCursor, int oldSize) {
        if (oldSize >= width) {
            int cursorRow = oldCursor / width;
            int totalRows = oldSize / width;

            if ((oldSize) % width == 0 && oldSize == oldCursor) {
                cursorRow = (oldCursor - 1) / width;
                builder.append(ANSI.MOVE_LINE_UP);
            }
            //if total row > cursor row it means that the cursor is not at the last line of the row
            //then we need to move down number of rows first
            //TODO: we can optimize here by going the number of rows down in one step
            if (totalRows > cursorRow && delta < 0) {
                for (int i = 0; i < (totalRows - cursorRow); i++) {
                    builder.append(ANSI.MOVE_LINE_DOWN);
                }
                for (int i = 0; i < totalRows; i++) {
                    builder.append(ANSI.ERASE_WHOLE_LINE);
                    builder.append(ANSI.MOVE_LINE_UP);
                }
            } else {
                for (int i = 0; i < cursorRow; i++) {
                    if (delta < 0) {
                        builder.append(ANSI.ERASE_WHOLE_LINE);
                    }
                    builder.append(ANSI.MOVE_LINE_UP);
                }
            }
        }
    }

    private void moveCursorToStartAndPrint(Consumer<int[]> out, IntArrayBuilder builder,
            int width, boolean replace, boolean viMode) {

        if (cursor > 0 || delta < 0) {
            //if we replace we do a quick way of moving to the beginning
            if (replace) {
                builder.append(moveNumberOfColumns(width, 'D'));
            } else {
                int length = promptLength() + cursor;
                if (length > 0 && (length % width == 0))
                    length = width;
                else {
                    length = length % width;
                    //if not deleting backward the cursor should not move
                    if (delta < 0 && deletingBackward)
                        length += Math.abs(delta);
                }
                builder.append(moveNumberOfColumns(length, 'D'));
            }
            //TODO: could optimize this i think if delta > 0 it should not be needed
            builder.append(ANSI.ERASE_LINE_FROM_CURSOR);
        }

        if (promptLength() > 0)
            builder.append(prompt.getANSI());

        //dont print out the line if its empty
        if (size > 0) {
            if (isMasking()) {
                //no output
                if (prompt.getMask() != '\u0000') {
                    //only output the masked char
                    int[] mask = new int[size];
                    Arrays.fill(mask, prompt.getMask());
                    builder.append(mask);
                }
            } else
                builder.append(getLine());
        }

        //pad if we are at the end of the terminal
        if ((size + promptLength()) % width == 0 && cursor == size) {
            builder.append(new int[] { 32, 13 });
        }
        //make sure we sync the cursor back
        if (!deltaChangedAtEndOfBuffer) {
            if ((size + promptLength()) % width == 0 &&
                    (Config.isOSPOSIXCompatible() || (Config.isWindows() && WinSysTerminal.isVTSupported())))
                builder.append(syncCursor(size + promptLength() - 1, cursor + promptLength(), width, true));
            else
                builder.append(syncCursor(size + promptLength(), cursor + promptLength(), width));
        }
        //end of buffer and vi mode
        else if (viMode && cursor == size) {
            builder.append(moveNumberOfColumns(1, 'D'));
            cursor--;
        }

        out.accept(builder.toArray());
        isPromptDisplayed = true;
    }

    /**
     * Returns the complete buffer content including multi-line content.
     * If in multi-line mode, this combines the multi-line buffer with the current line.
     *
     * @return the complete buffer content as an array of code points
     */
    public int[] multiLine() {
        if (multiLine) {
            int[] tmpLine = Arrays.copyOf(multiLineBuffer, multiLineBuffer.length + size);
            System.arraycopy(line, 0, tmpLine, multiLineBuffer.length, size);
            return tmpLine;
        } else {
            return getLine();
        }
    }

    /**
     * Deletes characters from the buffer relative to the cursor position.
     * Deletes backward if delta is negative, forward if delta is positive.
     *
     * @param out the output consumer for terminal updates
     * @param delta the number of characters to delete (negative for backward, positive for forward)
     * @param width the terminal width
     */
    public void delete(Consumer<int[]> out, int delta, int width) {
        delete(out, delta, width, false);
    }

    /**
     * Deletes characters from the buffer relative to the cursor position.
     * Deletes backward if delta is negative, forward if delta is positive.
     *
     * @param out the output consumer for terminal updates
     * @param delta the number of characters to delete (negative for backward, positive for forward)
     * @param width the terminal width
     * @param viMode true if vi editing mode is enabled, false for emacs mode
     */
    public void delete(Consumer<int[]> out, int delta, int width, boolean viMode) {
        if (delta > 0) {
            delta = Math.min(delta, size - cursor);
            if (delta > 0) {
                System.arraycopy(line, cursor + delta, line, cursor, size - cursor + delta);
                size -= delta;
                this.delta = -delta;
                deletingBackward = false;
            }
            //quick return if delta is 0
            else
                return;
        } else if (delta < 0) {
            delta = -Math.min(-delta, cursor);
            System.arraycopy(line, cursor, line, cursor + delta, size - cursor);
            size += delta;
            cursor += delta;
            this.delta = +delta;
            deletingBackward = true;
        }

        //only do any changes if there are any
        if (this.delta < 0) {
            // Erase the remaining.
            Arrays.fill(line, size, line.length, 0);

            deltaChangedAtEndOfBuffer = (cursor == size);

            //finally print our changes
            print(out, width, viMode);
        }
    }

    /**
     * Inserts a string at the current cursor position and updates the cursor accordingly.
     *
     * @param out the output consumer for terminal updates
     * @param str the string to insert
     * @param width the terminal width
     */
    public void insert(Consumer<int[]> out, final String str, int width) {
        insert(out, Parser.toCodePoints(str), width);
    }

    /**
     * Returns the current mark position.
     *
     * @return the mark position, or -1 if no mark is set
     */
    public int getMark() {
        return mark;
    }

    /**
     * Sets the mark at the specified position.
     *
     * @param position the position to set the mark at
     */
    public void setMark(int position) {
        this.mark = position;
    }

    /**
     * Returns whether overwrite mode is enabled.
     *
     * @return true if overwrite mode is active
     */
    public boolean isOverwriteMode() {
        return overwriteMode;
    }

    /**
     * Toggles between insert and overwrite mode.
     */
    public void toggleOverwriteMode() {
        overwriteMode = !overwriteMode;
    }

    /**
     * Replaces the character at the current cursor position without shifting.
     * Used in overwrite mode.
     *
     * @param out the output consumer for terminal updates
     * @param data the character to write
     * @param width the terminal width
     */
    public void overwrite(Consumer<int[]> out, int data, int width) {
        if (cursor < size) {
            line[cursor] = data;
            if (isMasking()) {
                if (prompt.getMask() != 0)
                    out.accept(new int[] { prompt.getMask() });
                // else: no-output mask, emit nothing
            } else {
                out.accept(new int[] { data });
            }
            cursor++;
        } else {
            // At end of buffer, behave like normal insert
            doInsert(data);
            printInsertedData(out, width);
        }
    }

    /**
     * Switch case if the current character is a letter.
     */
    void changeCase(Consumer<int[]> out) {
        if (Character.isLetter(line[cursor])) {
            if (Character.isLowerCase(line[cursor]))
                line[cursor] = Character.toUpperCase(line[cursor]);
            else
                line[cursor] = Character.toLowerCase(line[cursor]);

            out.accept(new int[] { line[cursor] });
            cursor++;
        }
    }

    /**
     * Up case if the current character is a letter
     */
    void upCase(Consumer<int[]> out) {
        if (Character.isLetter(line[cursor])) {
            line[cursor] = Character.toUpperCase(line[cursor]);
            out.accept(new int[] { line[cursor] });
            cursor++;
        }
    }

    /**
     * Lower case if the current character is a letter
     */
    void downCase(Consumer<int[]> out) {
        if (Character.isLetter(line[cursor])) {
            line[cursor] = Character.toLowerCase(line[cursor]);
            out.accept(new int[] { line[cursor] });
            cursor++;
        }
    }

    /**
     * Replaces the character at the current cursor position with the specified character.
     *
     * @param out the output consumer for terminal updates
     * @param rChar the replacement character
     */
    public void replace(Consumer<int[]> out, char rChar) {
        doReplace(out, cursor(), rChar);
    }

    private void doReplace(Consumer<int[]> out, int pos, int rChar) {
        if (pos > -1 && pos <= size) {
            line[pos] = rChar;
            out.accept(new int[] { rChar });
        }
    }

    /**
     * we assume that value is > 0
     *
     * @param value int value (non ascii value)
     * @return ascii represented int value
     */
    private static int[] intToAsciiInts(int value) {
        int length = getAsciiSize(value);
        int[] asciiValue = new int[length];

        if (length == 1) {
            asciiValue[0] = 48 + value;
        } else {
            while (length > 0) {
                length--;
                int num = value % 10;
                asciiValue[length] = 48 + num;
                value = value / 10;
            }
        }
        return asciiValue;
    }

    private static int getAsciiSize(int value) {
        if (value < 10)
            return 1;
        //very simple way of getting the length
        if (value < 99)
            return 2;
        else if (value > 99 && value < 999)
            return 3;
        else if (value > 999 && value < 9999)
            return 4;
        else
            return 5;
    }
}
