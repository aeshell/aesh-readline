package org.aesh.readline;

import org.aesh.readline.cursor.Line;
import org.aesh.readline.cursor.CursorListener;
import org.aesh.readline.history.InMemoryHistory;
import org.aesh.readline.paste.PasteManager;
import org.aesh.readline.undo.UndoAction;
import org.aesh.readline.undo.UndoManager;
import org.aesh.utils.ANSI;
import org.aesh.utils.Config;
import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.history.History;
import org.aesh.terminal.Connection;
import org.aesh.util.LoggerUtil;

import java.util.logging.Logger;
import org.aesh.terminal.tty.Size;

/**
 * @author Ståle W. Pedersen <stale.pedersen@jboss.org>
 */
public class AeshConsoleBuffer implements ConsoleBuffer {

    private EditMode editMode;

    private final Buffer buffer;
    private final Connection connection;

    private final UndoManager undoManager;
    private final PasteManager pasteManager;
    private final History history;
    private final CompletionHandler completionHandler;
    private Size size;

    private final boolean ansiMode;

    private static final Logger LOGGER = LoggerUtil.getLogger(AeshConsoleBuffer.class.getName());
    private final CursorListener cursorListener;

    public AeshConsoleBuffer(Connection connection, Prompt prompt,
                             EditMode editMode, History history,
                             CompletionHandler completionHandler,
                             boolean ansi, CursorListener listener) {
        this.connection = connection;
        this.ansiMode = ansi;
        this.buffer = new Buffer(prompt);
        pasteManager = new PasteManager();
        undoManager = new UndoManager();
        if(history == null) {
            this.history = new InMemoryHistory();
            this.history.enable();
        }
        else {
            //do not enable an history object if its given
            this.history = history;
        }

        this.completionHandler = completionHandler;
        this.size = connection.size();

        this.editMode = editMode;
        this.cursorListener = listener;
    }
      @Override
    public History history() {
        return history;
    }

    @Override
    public CompletionHandler completer() {
        return completionHandler;
    }

    @Override
    public void setSize(Size size) {
        this.size = size;
    }

    @Override
    public Size size() {
        return size;
    }

    @Override
    public Buffer buffer() {
        return this.buffer;
    }

    @Override
    public UndoManager undoManager() {
        return undoManager;
    }

    @Override
    public void addActionToUndoStack() {
        undoManager.addUndo(new UndoAction(
                buffer().cursor(), buffer().multiLine()));
    }

    @Override
    public PasteManager pasteManager() {
        return pasteManager;
    }

    @Override
    public void moveCursor(int where) {
        buffer.move(connection.stdoutHandler(), where,
                size().getWidth(), isViMode());
        if (cursorListener != null) {
            cursorListener.moved(new Line(buffer, connection, size.getWidth()));
        }
    }

    @Override
    public void drawLine() {
        buffer.print(connection.stdoutHandler(), size().getWidth());
    }

    @Override
    public void drawLineForceDisplay() {
        buffer.setIsPromptDisplayed(false);
        buffer.print(connection.stdoutHandler(), size().getWidth());
    }

    @Override
    public void writeChar(char input) {
        buffer.insert(connection.stdoutHandler(), input, size().getWidth());
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
    public void writeChars(int[] input) {
        buffer.insert(connection.stdoutHandler(), input, size().getWidth());
    }

    @Override
    public void writeString(String input) {
        if(input != null && input.length() > 0)
            buffer.insert(connection.stdoutHandler(), input, size().getWidth());
    }

    @Override
    public void setPrompt(Prompt prompt) {
        buffer.setPrompt(prompt, connection.stdoutHandler(), size().getWidth());
    }

    @Override
    public void insert(String insert, int position) {
        buffer.insert(connection.stdoutHandler(), insert, size().getWidth());
    }

    @Override
    public void insert(int[] insert) {
        buffer.insert(connection.stdoutHandler(), insert, size().getWidth());
    }

    @Override
    public void delete(int delta) {
        buffer.delete(connection.stdoutHandler(), delta, size().getWidth(), isViMode());
    }

    @Override
    public void upCase() {
        buffer.upCase(connection.stdoutHandler());
    }

    @Override
    public void downCase() {
        buffer.downCase(connection.stdoutHandler());
    }

    @Override
    public void changeCase() {
        buffer.changeCase(connection.stdoutHandler());
    }

    @Override
    public void replace(int[] line) {
        buffer.replace(connection.stdoutHandler(), line, size().getWidth());
    }

    @Override
    public void replace(String line) {
        buffer.replace(connection.stdoutHandler(), line, size().getWidth());
    }

    @Override
    public void reset() {
        buffer.reset();
    }

    @Override
    public void clear(boolean includeBuffer) {
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
            buffer.print(connection.stdoutHandler(), size().getWidth());
            //connection.write(buffer.getLine());
        }


    }

    private boolean isViMode() {
        return editMode.mode() == EditMode.Mode.VI &&
                editMode.status() != EditMode.Status.EDIT;
    }


}
