package org.aesh.readline;

import org.aesh.readline.history.InMemoryHistory;
import org.aesh.readline.paste.PasteManager;
import org.aesh.readline.undo.UndoAction;
import org.aesh.readline.undo.UndoManager;
import org.aesh.tty.Size;
import org.aesh.util.ANSI;
import org.aesh.util.Config;
import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.history.History;
import org.aesh.tty.Connection;
import org.aesh.util.LoggerUtil;

import java.util.logging.Logger;

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

    private final boolean isLogging = true;

    private static final Logger LOGGER = LoggerUtil.getLogger(AeshConsoleBuffer.class.getName());

    public AeshConsoleBuffer(Connection connection, Prompt prompt,
                             EditMode editMode, History history,
                             CompletionHandler completionHandler,
                             Size size,
                             boolean ansi) {
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
        this.size = size;

        this.editMode = editMode;
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
        return this.buffer;
    }

    @Override
    public UndoManager getUndoManager() {
        return undoManager;
    }

    @Override
    public void addActionToUndoStack() {
        undoManager.addUndo(new UndoAction(
                getBuffer().getCursor(), getBuffer().getMultiLine()));
    }

    @Override
    public PasteManager getPasteManager() {
        return pasteManager;
    }

    @Override
    public void moveCursor(int where) {
        buffer.move(connection.stdoutHandler(), where,
                getSize().getWidth(), isViMode());
    }

    @Override
    public void drawLine() {
        buffer.print(connection.stdoutHandler(), getSize().getWidth());
    }

    @Override
    public void writeChar(char input) {
        buffer.insert(connection.stdoutHandler(), input, getSize().getWidth());
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
        buffer.insert(connection.stdoutHandler(), input, getSize().getWidth());
    }

    @Override
    public void writeString(String input) {
        if(input != null && input.length() > 0)
            buffer.insert(connection.stdoutHandler(), input, getSize().getWidth());
    }

    @Override
    public void setPrompt(Prompt prompt) {
        buffer.setPrompt(prompt, connection.stdoutHandler(), getSize().getWidth());
    }

    @Override
    public void insert(String insert, int position) {
        buffer.insert(connection.stdoutHandler(), insert, getSize().getWidth());
    }

    @Override
    public void insert(int[] insert) {
        buffer.insert(connection.stdoutHandler(), insert, getSize().getWidth());
    }

    @Override
    public void delete(int delta) {
        buffer.delete(connection.stdoutHandler(), delta, getSize().getWidth(), isViMode());
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
        buffer.replace(connection.stdoutHandler(), line, getSize().getWidth());
    }

    @Override
    public void replace(String line) {
        buffer.replace(connection.stdoutHandler(), line, getSize().getWidth());
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
            buffer.print(connection.stdoutHandler(), getSize().getWidth());
            //connection.write(buffer.getLine());
        }


    }

    private boolean isViMode() {
        return editMode.getMode() == EditMode.Mode.VI &&
                editMode.getStatus() != EditMode.Status.EDIT;
    }


}
