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
package org.jboss.aesh.readline;

import org.jboss.aesh.parser.Parser;
import org.jboss.aesh.readline.completion.CompletionHandler;
import org.jboss.aesh.readline.editing.EditMode;
import org.jboss.aesh.readline.history.History;
import org.jboss.aesh.readline.history.InMemoryHistory;
import org.jboss.aesh.readline.paste.PasteManager;
import org.jboss.aesh.readline.undo.UndoAction;
import org.jboss.aesh.readline.undo.UndoManager;
import org.jboss.aesh.tty.Connection;
import org.jboss.aesh.tty.Size;
import org.jboss.aesh.util.ANSI;
import org.jboss.aesh.util.Config;
import org.jboss.aesh.util.LoggerUtil;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:stale.pedersen@jboss.org">Ståle W. Pedersen</a>
 */
public class AeshConsoleBufferString implements ConsoleBuffer {

    private EditMode editMode;

    private final BufferString buffer;
    private final Connection connection;

    private final UndoManager undoManager;
    private final PasteManager pasteManager;
    private final History history;
    private final CompletionHandler completionHandler;
    private Size size;

    private final boolean ansiMode;

    private final boolean isLogging = true;

    //used to optimize text deletion
    private static final int[] resetLineAndSetCursorToStart =
            Parser.toCodePoints(ANSI.CURSOR_SAVE+ ANSI.START+"0G"+ ANSI.START+"2K");
    private static final int[] MOVE_BACKWARD_AND_CLEAR = Parser.toCodePoints( Parser.SPACE_CHAR + ANSI.START + "1D");

    private static final int[] CLEAR_LINE = Parser.toCodePoints(ANSI.START+"0G"+ ANSI.START+"2K");
    private static final Logger LOGGER = LoggerUtil.getLogger(AeshConsoleBufferString.class.getName());

    public AeshConsoleBufferString(Connection connection, Prompt prompt,
                                   EditMode editMode, History history,
                                   CompletionHandler completionHandler,
                                   Size size,
                                   boolean ansi) {
        this.connection = connection;
        this.ansiMode = ansi;
        this.buffer = new BufferString(ansiMode, prompt);
        pasteManager = new PasteManager();
        undoManager = new UndoManager();
        if(history == null)
            this.history = new InMemoryHistory();
        else {
            this.history = history;
            this.history.enable();
        }

        this.completionHandler = completionHandler;
        this.size = size;

        this.editMode = editMode;

        LOGGER.info("prompt: "+ Parser.fromCodePoints( this.buffer.getPrompt().getPromptAsString()));
    }

    @Override
    public History getHistory() {
        return history;
    }

    @Override
    public CompletionHandler getCompleter() {
        return completionHandler;
    }

    @Override
    public void setSize(Size size) {
        this.size = size;
    }

    @Override
    public Size getSize() {
        return size;
    }

    @Override
    public Buffer getBuffer() {
        return null;
    }

    @Override
    public UndoManager getUndoManager() {
        return undoManager;
    }

    @Override
    public PasteManager getPasteManager() {
        return pasteManager;
    }

    @Override
    public void moveCursor(int where) {
        if(ansiMode) {
            if(editMode.getMode() == EditMode.Mode.VI &&
                    (editMode.getCurrentStatus() == EditMode.Status.COMMAND ||
                            editMode.getCurrentStatus() == EditMode.Status.DELETE)) {
                write(buffer.move(where, size.getWidth(), true));
            }
            else {
                write(buffer.move(where, size.getWidth()));
            }
        }
    }

    @Override
    public void drawLine() {
        drawLine(true);
    }

    @Override
    public void drawLine(boolean keepCursorPosition) {
        drawLine(keepCursorPosition, true);
    }

    @Override
    public void drawLine(boolean keepCursorPosition, boolean optimize) {
        if(isLogging)
            LOGGER.info("drawing: "+ Parser.fromCodePoints( buffer.getPrompt().getPromptAsString()) + buffer.getLine());
        //need to clear more than one line
        //if we're not in ansi mode, just write the string again:
        if(!ansiMode) {
            connection.stdoutHandler().accept(Config.CR);
            connection.stdoutHandler().accept(buffer.getPrompt().getPromptAsString());
            connection.write(buffer.getLine());
            return;
        }

        if(buffer.totalLength() > size.getWidth() ||
                (buffer.getDelta() < 0 && buffer.totalLength()+ Math.abs(buffer.getDelta()) > size.getWidth())) {
            if(buffer.getDelta() == -1 && buffer.getCursor() >= buffer.length() && Config.isOSPOSIXCompatible())
                connection.stdoutHandler().accept(MOVE_BACKWARD_AND_CLEAR);
            else
                redrawMultipleLines(keepCursorPosition);
        }
        // only clear the current line
        else {
            //most deletions are backspace from the end of the line so we've
            //optimize that like this.
            //NOTE: this doesnt work with history, need to find a better solution
            if(buffer.getDelta() == -1 && buffer.getCursor() >= buffer.length() && optimize) {
                connection.stdoutHandler().accept(MOVE_BACKWARD_AND_CLEAR);
            }
            else {
                //save cursor, move the cursor to the beginning, reset line
                if(keepCursorPosition)
                    connection.stdoutHandler().accept(resetLineAndSetCursorToStart);
                if(!buffer.isPromptDisabled()) {
                    writeOut(CLEAR_LINE);
                    displayPrompt();
                }
                //write line and restore cursor
                if(keepCursorPosition)
                    connection.write(buffer.getLine() + ANSI.CURSOR_RESTORE);
                else {
                    connection.write(buffer.getLine());
                    buffer.setCursor(buffer.getLine().length());
                }
            }
        }
    }

    private void redrawMultipleLines(boolean keepCursorPosition) {
        //if its the same line, just return
        if(buffer.getDelta() == 0)
            return;
        int currentRow = 0;
        if(buffer.getCursorWithPrompt() > 0)
            currentRow = buffer.getCursorWithPrompt() / size.getWidth();
        if(currentRow > 0 && buffer.getCursorWithPrompt() % size.getWidth() == 0)
            currentRow--;

        if(isLogging) {
            LOGGER.info("actual position: " + buffer.getCursor());
            LOGGER.info("currentRow:" + currentRow + ", cursorWithPrompt:" + buffer.getCursorWithPrompt()
                    + ", width:" + size.getWidth() + ", height:" + size.getHeight()
                    + ", delta:" + buffer.getDelta() + ", buffer:" + buffer.getLine());
        }

        StringBuilder builder = new StringBuilder();

        if(keepCursorPosition)
            builder.append(ANSI.CURSOR_SAVE); //save cursor

        if(buffer.getDelta() > 0) {
            currentRow = (buffer.getCursorWithPrompt()-buffer.getDelta()) / size.getWidth();
            if(currentRow > 0 && (buffer.getCursorWithPrompt()-buffer.getDelta()) % size.getWidth() == 0)
                currentRow--;
            /*
            if(buffer.length()-buffer.getDelta() < size.getWidth()) {
                //no need to move..
            }
            else */
            if (currentRow > 0)
                for (int i = 0; i < currentRow; i++)
                    builder.append(BufferString.printAnsi('A')); //move to top
        }
        else
            clearDelta(currentRow, builder);

        builder.append(BufferString.printAnsi('0','G')); //clear

        if(!buffer.isPromptDisabled()) {
            if(buffer.getPrompt().hasANSI())
                builder.append( Parser.fromCodePoints(   buffer.getPrompt().getANSI()));
            else
                builder.append(Parser.fromCodePoints(  buffer.getPrompt().getPromptAsString()));
        }
        builder.append(buffer.getLine());
        if(buffer.getDelta() < 0)
            builder.append(BufferString.printAnsi('K'));

        // move cursor to saved pos
        if(keepCursorPosition) {
            builder.append(ANSI.CURSOR_RESTORE);
            connection.write(builder.toString());
        }
        else {
            connection.write(builder.toString());
            buffer.setCursor(buffer.getLine().length());
        }
    }

    private void clearDelta(int currentRow, StringBuilder builder) {
        if(buffer.getDelta() < 0) {
            //int currentLength = buffer.getLineWithPrompt().length();
            int currentLength = buffer.totalLength();
            //buffer.getTotalLength add one extra char, need to check
            if(currentLength > 1)
                currentLength--;
            int numberOfCurrentRows =  currentLength /  size.getWidth();
            int numberOfPrevRows =
                    (currentLength + (buffer.getDelta()*-1)) / size.getWidth();
            int numberOfRowsToRemove = numberOfPrevRows - numberOfCurrentRows;
            int numberofRows = ((buffer.getDelta()*-1)+ buffer.totalLength()) /
                    size.getWidth();

            if (numberOfRowsToRemove == 0)
                numberOfRowsToRemove++;

            int tmpCurrentRow = currentRow;
            while(tmpCurrentRow < numberofRows) {
                builder.append(ANSI.printAnsi('B')); //move to the last row
                tmpCurrentRow++;
            }
            while(tmpCurrentRow > 0) {
                if(numberOfRowsToRemove > 0) {
                    builder.append(BufferString.printAnsi('2','K'));
                    numberOfRowsToRemove--;
                }
                builder.append(BufferString.printAnsi('A'));
                tmpCurrentRow--;
            }
        }
    }

    @Override
    public void writeOut(String out) {
        connection.write(out);
    }

    @Override
    public void writeOut(int[] out) {
        connection.stdoutHandler().accept(out);
    }

    @Override
    public void writeChars(int[] chars) {
        for(int c : chars)
            writeChar((char) c);
    }

    @Override
    public void writeChars(char[] chars) {
        for(char c : chars)
            writeChar(c);
    }

    @Override
    public void writeString(String input) {
        for(char c : input.toCharArray())
            writeChar(c);
    }

    @Override
    public void writeChar(char c) {

        buffer.write(c);
        //if mask is set and not set to 0 (nullvalue) we write out
        //the masked char. if masked is set to 0 we write nothing
        if(buffer.getPrompt().isMasking()) {
            if(buffer.getPrompt().getMask() != 0)
                write(buffer.getPrompt().getMask());
            else
                return;
        }
        else {
            write(c);
        }

        // add a 'fake' new line when inserting at the edge of terminal
        if(buffer.getCursorWithPrompt() > size.getWidth() &&
                buffer.getCursorWithPrompt() % size.getWidth() == 1) {
            connection.stdoutHandler().accept(new int[] { 32, 13});
        }

        // if we're not in ansi, just flush and return
        if(!ansiMode) {
            return;
        }

        // if we insert somewhere other than the end of the line we need to redraw from cursor
        if(buffer.getCursor() < buffer.length()) {
            //check if we just started a new line, if we did we need to make sure that we add one
            if(buffer.totalLength() > size.getWidth() &&
                    (buffer.totalLength()-1) % size.getWidth() == 1) {
                //int ansiCurrentRow = shell.getCursor().getRow();
                int currentRow = (buffer.getCursorWithPrompt() / size.getWidth());
                if(currentRow > 0 && buffer.getCursorWithPrompt() % size.getWidth() == 0)
                    currentRow--;

                int totalRows = buffer.totalLength() / size.getWidth();
                if(totalRows > 0 && buffer.totalLength() % size.getWidth() == 0)
                    totalRows--;

                /* todo: we might need this..
                if(ansiCurrentRow+(totalRows-currentRow) > size.getHeight() && ansiMode) {
                    connection.write(Buffer.printAnsi("1S")); //adding a line
                    connection.write(Buffer.printAnsi("1A")); // moving up a line
                }
                */
            }
            drawLine();
        }
    }

    @Override
    public void displayPrompt() {
        displayPrompt(buffer.getPrompt());
    }

    private void write(char c) {
        connection.stdoutHandler().accept(new int[] { c});
    }
    private void write(char[] chars) {
        LOGGER.info("writing to stdout: "+ Arrays.toString(chars));
        int[] out = new int[chars.length];
        for(int i=0; i<chars.length;i++)
            out[i] = chars[i];

        connection.stdoutHandler().accept(out);
    }

    @Override
    public void setPrompt(Prompt prompt) {
        if(!buffer.getPrompt().equals(prompt)) {
            buffer.updatePrompt(prompt);
            //only update the prompt if Console is running
            //set cursor position line.length
            if(ansiMode) {
                connection.stdoutHandler().accept(CLEAR_LINE);
                displayPrompt(prompt);
                if(buffer.getLine().length() > 0) {
                    connection.write(buffer.getLine());
                    buffer.setCursor(buffer.getLine().length());
                }
            }
        }
    }

    @Override
    public void setBufferLine(String newLine) {
        //must make sure that there are enough space for the
        // line thats about to be injected
        if((newLine.length()+buffer.getPrompt().getLength()) >= size.getWidth() &&
                newLine.length() >= buffer.getLine().length()) {
            //int currentRow = shell.getCursor().getRow();
            int currentRow = buffer.getCursor() / size.getWidth();
            if(currentRow > -1) {
                int cursorRow = buffer.getCursorWithPrompt() / size.getWidth();
                if(currentRow + (newLine.length() / size.getWidth()) - cursorRow >= size.getHeight()) {
                    int numNewRows = currentRow +
                            ((newLine.length()+buffer.getPrompt().getLength()) / size.getWidth()) -
                            cursorRow - size.getHeight();
                    //if the line is exactly equal to termWidth we need to add another row
                    if((newLine.length()+buffer.getPrompt().getLength()) % size.getWidth() == 0)
                        numNewRows++;
                    if(numNewRows > 0) {
                        if(isLogging) {
                            int totalRows = (newLine.length()+buffer.getPrompt().getLength()) / size.getWidth() +1;
                            LOGGER.info("ADDING "+numNewRows+", totalRows:"+totalRows+
                                    ", currentRow:"+currentRow+", cursorRow:"+cursorRow);
                        }
                        write(BufferString.printAnsi((char)numNewRows,'S'));
                        write(BufferString.printAnsi((char)numNewRows, 'A'));
                    }
                }
            }
        }
        buffer.setLine(newLine);
    }

    @Override
    public void insertBufferLine(String insert, int position) {
        if((insert.length()+buffer.totalLength()) >= size.getWidth()) { //&&
            //(insert.length()+buffer.totalLength()) > buffer.getLine().length()) {
            //int currentRow = shell.getCursor().getRow();
            int currentRow = buffer.getCursor() / size.getWidth();
            if(currentRow > -1) {
                int newLine = insert.length()+buffer.totalLength();
                int cursorRow = buffer.getCursorWithPrompt() / size.getWidth();
                if(currentRow + (newLine / size.getWidth()) - cursorRow >= size.getHeight()) {
                    int numNewRows = currentRow + (newLine / size.getWidth()) - cursorRow - size.getHeight();
                    //if the line is exactly equal to termWidth we need to add another row
                    if((insert.length()+buffer.totalLength()) % size.getWidth() == 0)
                        numNewRows++;
                    if(numNewRows > 0) {
                        write(BufferString.printAnsi((char) numNewRows, 'S'));
                        write(BufferString.printAnsi((char) numNewRows, 'A'));
                    }
                }
            }
        }
        buffer.insert(position, insert);
    }

    @Override
    public void delete(int delta) {

    }

    @Override
    public void upCase() {

    }

    @Override
    public void downCase() {

    }

    @Override
    public void changeCase() {

    }

    @Override
    public void replace(int[] line) {

    }

    @Override
    public void replace(String line) {

    }

    private void displayPrompt(Prompt prompt) {
        if(prompt.hasANSI() && ansiMode) {
            connection.stdoutHandler().accept(prompt.getANSI());
        }
        else
            connection.stdoutHandler().accept(prompt.getPromptAsString());
    }

    /**
     * Add current text and cursor position to the undo stack
     */
    @Override
    public void addActionToUndoStack() {
        UndoAction ua = new UndoAction(buffer.getCursor(), buffer.getLine());
        undoManager.addUndo(ua);
    }

    private void addToPaste(String buffer) {
        pasteManager.addText(new StringBuilder(buffer));
    }

    /**
     * Clear an ansi terminal.
     * Set includeBuffer to true if the current buffer should be
     * printed again after clear.
     *
     * @param includeBuffer if true include the current buffer line
     */
    @Override
    public void clear(boolean includeBuffer) {
        if(ansiMode) {
            //(windows fix)
            if(!Config.isOSPOSIXCompatible())
                connection.stdoutHandler().accept(Config.CR);
            //first clear console
            connection.stdoutHandler().accept(ANSI.CLEAR_SCREEN);
            //move cursor to correct position
           // connection.stdoutHandler().accept(Buffer.printAnsi("1;1H"));
            connection.stdoutHandler().accept(new int[] {27, '[', '1', ';', '1', 'H'});
            //then write prompt
            if(includeBuffer) {
                displayPrompt();
                connection.write(buffer.getLine());
            }
        }
    }

}
