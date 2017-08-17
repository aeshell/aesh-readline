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

import org.aesh.utils.Config;
import org.aesh.util.IntArrayBuilder;
import org.aesh.utils.ANSI;
import org.aesh.util.LoggerUtil;
import org.aesh.util.Parser;
import org.aesh.util.WcWidth;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.aesh.readline.cursor.CursorLocator;

/**
 * Buffer to keep track of text and cursor position in the console.
 * Is using ANSI-codes to clear text and move cursor in the terminal.
 *
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class Buffer {

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

    private final CursorLocator locator;

    Buffer() {
        line = new int[1024];
        prompt = new Prompt("");
        locator = new CursorLocator(this);
    }

    Buffer(Prompt prompt) {
        line = new int[1024];
        if(prompt != null)
            this.prompt = prompt;
        else
            this.prompt = new Prompt("");
        locator = new CursorLocator(this);
    }

    public Buffer(Buffer buf) {
        line = buf.line.clone();
        cursor = buf.cursor;
        size = buf.size;
        prompt = buf.prompt.copy();
        locator = new CursorLocator(this);
    }

    public CursorLocator getCursorLocator() {
        return locator;
    }

    public int get(int pos) {
        if(pos > -1 && pos <= size)
            return line[pos];
        else
            throw new IndexOutOfBoundsException();
    }

    public int cursor() {
        return cursor;
    }

    public int multiCursor() {
        if (multiLine) {
            return multiLineBuffer.length + cursor;
        }
        return cursor;
    }

    public boolean isMasking() {
        return prompt.isMasking();
    }

    public boolean isMultiLine() {
        return multiLine;
    }

    public String asString() {
        return Parser.fromCodePoints(multiLine());
    }

    public void reset() {
        cursor = 0;
        for(int i=0; i<size; i++)
            line[i] = 0;
        size = 0;
        isPromptDisplayed = false;
        if(multiLine) {
            multiLineBuffer = new int[0];
            multiLine = false;
        }
        locator.clear();
    }

    public void setIsPromptDisplayed(boolean isPromptDisplayed) {
        this.isPromptDisplayed = isPromptDisplayed;
    }

    /**
     * Need to disable prompt in calculations involving search.
     *
     * @param disable prompt or not
     */
    public void disablePrompt(boolean disable) {
        disablePrompt = disable;
    }

    public boolean isPromptDisabled() {
        return disablePrompt;
    }

    void setPrompt(Prompt prompt, Consumer<int[]> out, int width) {
        if(prompt != null) {
            delta =  prompt.getLength() - this.prompt.getLength();
            this.prompt = prompt;
            print(out, width);
        }
    }

    public Prompt prompt() {
        return prompt;
    }

    public int length() {
        if(isMasking() && prompt.getMask() == 0)
            return 1;
        else
            return size;
    }

    private int promptLength() {
        return disablePrompt ? 0 : prompt.getLength();
    }

    public void setMultiLine(boolean multi) {
        if(!isMasking())
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

    public void updateMultiLineBuffer() {
        int originalSize = multiLineBuffer.length;
        // Store the size of each line.
        int cmdSize;
        if (lineEndsWithBackslash()) {
            cmdSize = size - 1;
            multiLineBuffer = Arrays.copyOf(multiLineBuffer, originalSize + size-1);
            System.arraycopy(line, 0, multiLineBuffer, originalSize, size-1);
        }
        else {
            cmdSize = size;
            multiLineBuffer = Arrays.copyOf(multiLineBuffer, originalSize + size);
            System.arraycopy(line, 0, multiLineBuffer, originalSize, size);
        }
        locator.addLine(cmdSize, prompt.getLength());
        clear();
        prompt = new Prompt("> ");
        cursor = 0;
        size = 0;
    }

    private boolean lineEndsWithBackslash() {
        return (size > 0 && line[size-1] == '\\');
    }

    /**
     * Insert text at cursor position
     *
     * @param data text
     */
    public void insert(Consumer<int[]> out, int[] data, int width) {
        doInsert(data);
        printInsertedData(out, width);
    }

    /**
     * Insert at cursor position.
     *
     * @param data char
     */
    public void insert(Consumer<int[]> out, int data, int width) {
        doInsert(data);
        printInsertedData(out, width);
   }

    private void doInsert(int data) {
        int width = WcWidth.width(data);
        if(width == -1) {
            //todo: handle control chars...
        }
        else if(width == 1) {
            if(cursor < size)
                System.arraycopy(line, cursor, line, cursor + 1, size - cursor);
            line[cursor++] = data;
            size++;
            delta++;

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
        if(!gotControlChar) {
            doActualInsert(data);
        }
    }

    private void doActualInsert(int[] data) {
        if(cursor < size)
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
    public void move(Consumer<int[]> out,  int move, int termWidth) {
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
        if(move == 0)
            return;

        // 0 Masking separates the UI cursor position from the 'real' cursor position.
        // Cursor movement still has to occur, via calculateActualMovement and setCursor above,
        // to put new characters in the correct location in the invisible line,
        // but this method should always return an empty character so the UI cursor does not move.
        if(isMasking() && prompt.getMask() == 0){
            return;
        }

        out.accept( syncCursor(promptLength()+cursor, promptLength()+cursor+move, termWidth));

        cursor = cursor + move;


    }

    private int[] syncCursor(int currentPos, int newPos, int width) {
        IntArrayBuilder builder = new IntArrayBuilder();
        if(newPos < 1)
            newPos = 1;
        if(currentPos / width == newPos / width) {
            if(currentPos > newPos)
                builder.append(moveNumberOfColumns(currentPos-newPos, 'D'));
            else
                builder.append(moveNumberOfColumns(newPos-currentPos, 'C'));
        }
        //if cursor and end of buffer is on different lines, we need to move the cursor
        else {
            int moveToLine = currentPos / width - newPos / width;
            int moveToColumn = currentPos % width - newPos % width;
            char rowDirection = 'A';
            if(moveToLine < 0) {
                rowDirection = 'B';
                moveToLine = Math.abs(moveToLine);
            }
            builder.append(  moveNumberOfColumnsAndRows(  moveToLine, rowDirection, moveToColumn));
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
        IntArrayBuilder builder = new IntArrayBuilder();

         if((currentPos-1) / width == newPos / width) {
             builder.append(moveNumberOfColumns(width, 'D'));
        }
        else {
             //if cursor and end of buffer is on different lines, we need to move the cursor
             int moveToLine = (currentPos - 1) / width - newPos / width;
             int moveToColumn = -currentPos;
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
        return builder.toArray();
    }

    public int[] moveNumberOfColumns(int column, char direction) {
        if(column < 10) {
            int[] out = new int[4];
            out[0] = 27; // esc
            out[1] = '['; // [
            out[2] = 48 + column;
            out[3] = direction;
            return out;
        }
        else {
            int[] asciiColumn = intToAsciiInts(column);
            int[] out = new int[3+asciiColumn.length];
            out[0] = 27; // esc
            out[1] = '['; // [
            //for(int i=0; i < asciiColumn.length; i++)
            //    out[2+i] = asciiColumn[i];
            System.arraycopy(asciiColumn, 0, out, 2, asciiColumn.length);
            out[out.length-1] = direction;
            return out;
        }
    }

    private int[] moveNumberOfColumnsAndRows(int row, char rowCommand, int column) {
        char direction = 'D'; //forward
        if(column < 0) {
            column = Math.abs(column);
            direction = 'C';
        }
        if(row < 10 && column < 10) {
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
        }
        else {
            int[] asciiRow = intToAsciiInts(row);
            int[] asciiColumn = intToAsciiInts(column);
            int[] out = new int[6+asciiColumn.length+asciiRow.length];
            out[0] = 27; //esc, \033
            out[1] = '[';
            //for(int i=0; i < asciiRow.length; i++)
            //    out[2+i] = asciiRow[i];
            System.arraycopy(asciiRow, 0, out, 2, asciiRow.length);
            out[2+asciiRow.length] = rowCommand;
            out[3+asciiRow.length] = 27;
            out[4+asciiRow.length] = '[';
            for(int i=0; i < asciiColumn.length; i++)
                out[5+asciiRow.length+i] = asciiColumn[i];
            out[out.length-1] = direction;
            return out;
        }
    }

    /**
     * Make sure that the cursor do not move ob (out of bounds)
     *
     * @param move left if its negative, right if its positive
     * @param viMode if viMode we need other restrictions compared
     * to emacs movement
     * @return adjusted movement
     */
    private int calculateActualMovement(final int move, boolean viMode) {
        // cant move to a negative value
        if(cursor() == 0 && move <=0 )
            return 0;
        // cant move longer than the length of the line
        if(viMode) {
            if(cursor() == length()-1 && (move > 0))
                return 0;
        }
        else {
            if(cursor() == length() && (move > 0))
                return 0;
        }

        // dont move out of bounds
        if(cursor() + move <= 0)
            return -cursor();

        if(viMode) {
            if(cursor() + move > length()-1)
                return (length()-1- cursor());
        }
        else {
            if(cursor() + move > length())
                return (length()- cursor());
        }

        return move;
    }

    private int[] getLineFrom(int position) {
        return Arrays.copyOfRange(line, position, size);
    }

    public int[] getLineMasked() {
        if(!isMasking())
            return Arrays.copyOf(line, size);
        else {
            if(size > 0 && prompt.getMask() != '\u0000') {
                int[] tmpLine = new int[size];
                Arrays.fill(tmpLine, prompt.getMask());
                return tmpLine;
            }
            else
                return new int[0];
        }
    }

    private int[] getLine() {
        return Arrays.copyOf(line, size);
    }

    public void clear() {
        Arrays.fill(this.line, 0, size, 0);
        cursor = 0;
        size = 0;
        isPromptDisplayed = false;
    }

    /**
     * If delta > 0 * print from cursor
     *   if keepCursor, move cursor back to previous position
     * if delta < 0
     *   if deltaChangedAtEndOf buffer {
     *       if delta == -1 {
     *         clear line from cursor
     *         move cursor back
     *       }
     *       else if cursor + delta > width {
     *           check if we need to delete more than the current line
     *       }
     *   }
     * @param out output
     * @param width terminal size
     */
    void print(Consumer<int[]> out, int width) {
        print(out, width, false);
    }

    private void print(Consumer<int[]> out, int width, boolean viMode) {
        if(delta >= 0)
            printInsertedData(out, width);
        else {
            printDeletedData(out, width, viMode);
        }
        delta = 0;
    }

    private void printInsertedData(Consumer<int[]> out, int width) {
        //print out prompt first if needed
        IntArrayBuilder builder = new IntArrayBuilder();
        if(!isPromptDisplayed) {
            //only print the prompt if its longer than 0
            if(promptLength() > 0)
                builder.append(prompt.getANSI());
            isPromptDisplayed = true;
            //need to print the entire buffer
            //force that by setting delta = cursor if delta is 0
            if(delta == 0)
                delta = cursor;
        }
        //quick exit if buffer is empty
        if(size == 0) {
            out.accept(builder.toArray());
            return;
        }

        if(isMasking()) {
            if(prompt.getMask() != 0) {
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
        }
        else {
            if (deltaChangedAtEndOfBuffer) {
                if (delta == 1 || delta == 0)
                    builder.append(new int[]{line[cursor - 1]});
                else
                    builder.append(Arrays.copyOfRange(line, cursor - delta, cursor));
            } else {
                builder.append(Arrays.copyOfRange(line, cursor - delta, size));
            }
        }

        //pad if we are at the end of the terminal
        if((size + promptLength()) % width == 0 && deltaChangedAtEndOfBuffer) {
            builder.append(new int[]{32, 13});
        }
        //make sure we sync the cursor back
        if(!deltaChangedAtEndOfBuffer) {
            if((size + promptLength()) % width == 0 && Config.isOSPOSIXCompatible()) {
                builder.append(syncCursorWhenBufferIsAtTerminalEdge(size + promptLength(), cursor + promptLength(), width));
            }
            else
                builder.append(syncCursor(size+promptLength(), cursor+promptLength(), width));
        }

        out.accept(builder.toArray());
        delta = 0;
        deltaChangedAtEndOfBuffer = true;
    }

    private void printDeletedData(Consumer<int[]> out, int width, boolean viMode) {
        //if we're masking and the mask is no output we just return
        if(isMasking() && prompt.getMask() == 0)
            return;
        IntArrayBuilder builder = new IntArrayBuilder();
         if(size+promptLength()+Math.abs(delta) >= width) {
            if(deletingBackward) {
                //lets optimize deletes at the end
                if(deltaChangedAtEndOfBuffer &&
                        ((size+promptLength()+1) % width > Math.abs(delta))) {
                    quickDeleteAtEnd(out, viMode);
                    return;
                }
                else {
                    clearAllLinesAndReturnToFirstLine(builder,
                            width, cursor + promptLength() + Math.abs(delta),
                            size + promptLength() + Math.abs(delta));
                }
            }
            else
                clearAllLinesAndReturnToFirstLine(builder,
                        width, cursor + promptLength(),
                        size + promptLength() + Math.abs(delta));
        }

        if((size+promptLength()+1) < width && deltaChangedAtEndOfBuffer)
             quickDeleteAtEnd(out, viMode);
        else
            moveCursorToStartAndPrint(out, builder, width, false, viMode);
    }

    private void quickDeleteAtEnd(Consumer<int[]> out, boolean viMode) {
        //move cursor delta then clear the rest of the line
        IntArrayBuilder builder = new IntArrayBuilder();
        //only have to move when deleting backwards
        if(deletingBackward)
            builder.append(moveNumberOfColumns(Math.abs(delta), 'D'));
        builder.append(ANSI.ERASE_LINE_FROM_CURSOR);

        if(viMode && cursor == size) {
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

    public void replace(Consumer<int[]> out, int[] line, int width) {
        //quick exit
        if(line == null || size == 0 && line.length == 0)
            return;

        int tmpDelta = line.length - size;
        int oldSize = size+promptLength();
        int oldCursor = cursor + promptLength();
        clear();
        doInsert(line);
        delta = tmpDelta;
        //deltaChangedAtEndOfBuffer = false;
        deltaChangedAtEndOfBuffer = (cursor == size);

        IntArrayBuilder builder = new IntArrayBuilder();
        if(oldSize >= width)
            clearAllLinesAndReturnToFirstLine(builder, width, oldCursor, oldSize);

        moveCursorToStartAndPrint(out, builder, width, true, false);
        delta = 0;
        deltaChangedAtEndOfBuffer = true;
    }

    /**
     * All parameter values are included the prompt length
     * @param builder int[] builder
     * @param width terminal size
     * @param oldCursor prev position
     * @param oldSize prev terminal size
     */
    private void clearAllLinesAndReturnToFirstLine(IntArrayBuilder builder, int width,
                                                   int oldCursor, int oldSize) {
        if(oldSize >= width) {
            int cursorRow = oldCursor / width;
            int totalRows = oldSize / width;

            if((oldSize) % width == 0 && oldSize == oldCursor) {
                cursorRow = (oldCursor-1) / width;
                builder.append(ANSI.MOVE_LINE_UP);
            }
            //if total row > cursor row it means that the cursor is not at the last line of the row
            //then we need to move down number of rows first
            //TODO: we can optimize here by going the number of rows down in one step
            if(totalRows > cursorRow && delta < 0) {
                for(int i=0; i < (totalRows-cursorRow); i++) {
                    builder.append(ANSI.MOVE_LINE_DOWN);
                }
                for (int i = 0; i < totalRows; i++) {
                    builder.append(ANSI.ERASE_WHOLE_LINE);
                    builder.append(ANSI.MOVE_LINE_UP);
                }
            }
            else {
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

        if((promptLength() > 0 && cursor != 0) || delta < 0) {
            //if we replace we do a quick way of moving to the beginning
            if(replace) {
                builder.append(moveNumberOfColumns(width, 'D'));
            }
            else {
                int length = promptLength() + cursor;
                if(length > 0 && (length % width == 0))
                    length = width;
                else {
                    length = length % width;
                    //if not deleting backward the cursor should not move
                    if(delta < 0 && deletingBackward)
                        length += Math.abs(delta);
                }
                builder.append(moveNumberOfColumns(length, 'D'));
            }
            //TODO: could optimize this i think if delta > 0 it should not be needed
            builder.append(ANSI.ERASE_LINE_FROM_CURSOR);
        }

        if(promptLength() > 0)
            builder.append(prompt.getANSI());

        //dont print out the line if its empty
        if(size > 0) {
            if(isMasking()) {
                //no output
                if(prompt.getMask() != '\u0000') {
                    //only output the masked char
                    int[] mask = new int[size];
                    Arrays.fill(mask, prompt.getMask());
                    builder.append(mask);
                }
            }
            else
                builder.append(getLine());
        }

        //pad if we are at the end of the terminal
        if((size + promptLength()) % width == 0 && cursor == size) {
            builder.append(new int[]{32, 13});
        }
        //make sure we sync the cursor back
        if(!deltaChangedAtEndOfBuffer) {
            if((size + promptLength()) % width == 0 && Config.isOSPOSIXCompatible())
                builder.append(syncCursor(size+promptLength()-1, cursor+promptLength(), width));
            else
                builder.append(syncCursor(size+promptLength(), cursor+promptLength(), width));
         }
        //end of buffer and vi mode
        else if(viMode && cursor == size) {
            builder.append(moveNumberOfColumns(1, 'D'));
            cursor--;
        }

        out.accept(builder.toArray());
        isPromptDisplayed = true;
    }

    public int[] multiLine() {
        if (multiLine) {
            int[] tmpLine = Arrays.copyOf(multiLineBuffer, multiLineBuffer.length + size);
            System.arraycopy(line, 0, tmpLine, multiLineBuffer.length, size);
            return  tmpLine;
        }
        else {
            return getLine();
        }
    }

    /**
     * Delete from cursor position and backwards if delta is < 0
     * Delete from cursor position and forwards if delta is > 0
     * @param delta difference
     */
    public void delete(Consumer<int[]> out, int delta, int width) {
       delete(out, delta, width, false);
    }

    public void delete(Consumer<int[]> out, int delta, int width, boolean viMode) {
        if (delta > 0) {
            delta = Math.min(delta, size - cursor);
            if(delta > 0) {
                System.arraycopy(line, cursor + delta, line, cursor, size - cursor + delta);
                size -= delta;
                this.delta = -delta;
                deletingBackward = false;
            }
            //quick return if delta is 0
            else
                return;
        }
        else if (delta < 0) {
            delta = -Math.min(-delta, cursor);
            System.arraycopy(line, cursor, line, cursor + delta, size - cursor);
            size += delta;
            cursor += delta;
            this.delta =+ delta;
            deletingBackward = true;
        }

        //only do any changes if there are any
        if(this.delta < 0) {
            // Erase the remaining.
            Arrays.fill(line, size, line.length, 0);

            deltaChangedAtEndOfBuffer = (cursor == size);

            //finally print our changes
            print(out, width, viMode);
        }
    }

    /**
     * Write a string to the line and update cursor accordingly
     *
     * @param out consumer
     * @param str string
     */
    public void insert(Consumer<int[]> out, final String str, int width) {
        insert(out, Parser.toCodePoints(str), width);
    }

    /**
     * Switch case if the current character is a letter.
     */
    void changeCase(Consumer<int[]> out) {
        if(Character.isLetter(line[cursor])) {
            if(Character.isLowerCase(line[cursor]))
                line[cursor] = Character.toUpperCase(line[cursor]);
            else
                line[cursor] = Character.toLowerCase(line[cursor]);

            out.accept(new int[]{line[cursor]});
        }
    }

    /**
     * Up case if the current character is a letter
     */
    void upCase(Consumer<int[]> out) {
        if(Character.isLetter(line[cursor])) {
            line[cursor] = Character.toUpperCase(line[cursor]);
            out.accept(new int[]{line[cursor]});
        }
    }

    /**
     * Lower case if the current character is a letter
     */
    void downCase(Consumer<int[]> out) {
        if(Character.isLetter(line[cursor])) {
            line[cursor] = Character.toLowerCase(line[cursor]);
            out.accept(new int[]{line[cursor]});
        }
    }

    /**
     * Replace the current character
     */
    public void replace(Consumer<int[]> out, char rChar) {
        doReplace(out, cursor(), rChar);
    }

    private void doReplace(Consumer<int[]> out, int pos, int rChar) {
        if(pos > -1 && pos <= size) {
            line[pos] = rChar;
            out.accept(new int[]{rChar});
        }
    }

    /**
     * we assume that value is > 0
     *
     * @param value int value (non ascii value)
     * @return ascii represented int value
     */
    private int[] intToAsciiInts(int value) {
        int length = getAsciiSize(value);
        int[] asciiValue = new int[length];

        if(length == 1) {
            asciiValue[0] = 48+value;
        }
        else {
            while(length > 0) {
                length--;
                int num = value % 10;
                asciiValue[length] = 48+num;
                value = value / 10;
            }
        }
        return asciiValue;
    }

    private int getAsciiSize(int value) {
        if(value < 10)
            return 1;
        //very simple way of getting the length
        if(value > 9 && value < 99)
            return 2;
        else if(value > 99 && value < 999)
            return 3;
        else if(value > 999 && value < 9999)
            return 4;
        else
            return 5;
    }
}
