package org.aesh.readline;

import java.util.logging.Logger;

import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.cursor.CursorListener;
import org.aesh.readline.cursor.Line;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.history.History;
import org.aesh.readline.history.InMemoryHistory;
import org.aesh.readline.paste.PasteManager;
import org.aesh.readline.undo.UndoAction;
import org.aesh.readline.undo.UndoManager;
import org.aesh.terminal.Connection;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.Config;
import org.aesh.terminal.utils.LoggerUtil;

/**
 * Default implementation of the ConsoleBuffer interface for managing console input and output.
 *
 * @apiNote This class is an internal implementation detail of the readline library
 *          and is not intended for use by external consumers. It may change without notice
 *          in future releases.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class AeshConsoleBuffer implements ConsoleBuffer {

    private final EditMode editMode;

    private final Buffer buffer;
    private final Connection connection;

    private final UndoManager undoManager;
    private final PasteManager pasteManager;
    private final History history;
    private final CompletionHandler completionHandler;
    private Size size;

    private String ghostText;

    private static final Logger LOGGER = LoggerUtil.getLogger(AeshConsoleBuffer.class.getName());
    private final CursorListener cursorListener;

    /**
     * Creates a new AeshConsoleBuffer.
     *
     * @param connection the terminal connection
     * @param prompt the initial prompt
     * @param editMode the editing mode (vi or emacs)
     * @param history the command history, or null to use a new in-memory history
     * @param completionHandler the tab completion handler
     * @param ansi whether ANSI mode is enabled
     * @param listener the cursor movement listener, or null
     */
    public AeshConsoleBuffer(Connection connection, Prompt prompt,
            EditMode editMode, History history,
            CompletionHandler completionHandler,
            boolean ansi, CursorListener listener) {
        this.connection = connection;
        this.buffer = new Buffer(prompt);
        pasteManager = new PasteManager();
        undoManager = new UndoManager();
        if (history == null) {
            this.history = new InMemoryHistory();
            this.history.enable();
        } else {
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
    public CompletionHandler completionHandler() {
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
        if (buffer.cursor() < buffer.length())
            buffer.forceSetDeltaChangedAtEndOfBuffer(false);
        buffer.print(connection.stdoutHandler(), size().getWidth());
    }

    @Override
    public void writeChar(char input) {
        clearGhostText();
        if (buffer.isOverwriteMode()) {
            buffer.overwrite(connection.stdoutHandler(), input, size().getWidth());
        } else {
            buffer.insert(connection.stdoutHandler(), input, size().getWidth());
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
    public void writeChars(int[] input) {
        clearGhostText();
        buffer.insert(connection.stdoutHandler(), input, size().getWidth());
    }

    @Override
    public void writeString(String input) {
        if (input != null && !input.isEmpty()) {
            clearGhostText();
            buffer.insert(connection.stdoutHandler(), input, size().getWidth());
        }
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
        clearGhostText();
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
        clearGhostText();
        buffer.replace(connection.stdoutHandler(), line, size().getWidth());
    }

    @Override
    public void replace(String line) {
        clearGhostText();
        buffer.replace(connection.stdoutHandler(), line, size().getWidth());
    }

    @Override
    public void reset() {
        ghostText = null;
        buffer.reset();
    }

    @Override
    public void clear(boolean includeBuffer) {
        ghostText = null;
        //(windows fix)
        if (!Config.isOSPOSIXCompatible())
            connection.stdoutHandler().accept(Config.CR);
        //first clear console
        connection.stdoutHandler().accept(ANSI.CLEAR_SCREEN);
        int cursorPosition = -1;
        if (buffer.cursor() < buffer.length()) {
            cursorPosition = buffer.cursor();
            buffer.move(connection.stdoutHandler(), buffer.length() - cursorPosition, size().getWidth());
        }
        //move cursor to correct position
        connection.stdoutHandler().accept(ANSI.cursorPosition(1, 1));
        //then write prompt
        if (!includeBuffer)
            buffer().reset();

        //redraw
        drawLineForceDisplay();

        if (cursorPosition > -1)
            buffer.move(connection.stdoutHandler(), cursorPosition - buffer.length(), size().getWidth());
    }

    @Override
    public void clearGhostText() {
        if (ghostText != null) {
            // Save cursor, erase from cursor to end of screen (handles wrapped text), restore cursor
            connection.write(ANSI.CURSOR_SAVE);
            connection.stdoutHandler().accept(ANSI.ERASE_SCREEN_FROM_CURSOR);
            connection.write(ANSI.CURSOR_RESTORE);
            ghostText = null;
        }
    }

    @Override
    public void showGhostText(String suggestion) {
        if (suggestion == null || suggestion.isEmpty())
            return;
        clearGhostText();
        // Save cursor, write dim text, erase rest of line, restore cursor
        connection.write(ANSI.CURSOR_SAVE);
        connection.stdoutHandler().accept(ANSI.DIM);
        connection.write(suggestion);
        connection.stdoutHandler().accept(ANSI.DIM_OFF);
        connection.stdoutHandler().accept(ANSI.ERASE_LINE_FROM_CURSOR);
        connection.write(ANSI.CURSOR_RESTORE);
        ghostText = suggestion;
    }

    @Override
    public void acceptGhostText() {
        if (ghostText != null) {
            String text = ghostText;
            ghostText = null;
            // Erase the ghost text display first (screen-level to handle wrapping), then insert into buffer
            connection.write(ANSI.CURSOR_SAVE);
            connection.stdoutHandler().accept(ANSI.ERASE_SCREEN_FROM_CURSOR);
            connection.write(ANSI.CURSOR_RESTORE);
            writeString(text);
        }
    }

    @Override
    public void acceptGhostTextWord() {
        if (ghostText != null) {
            // Find the end of the next word in the ghost text
            int i = 0;
            // Skip leading delimiters/whitespace
            while (i < ghostText.length() && !Character.isLetterOrDigit(ghostText.charAt(i)))
                i++;
            // Skip the word characters
            while (i < ghostText.length() && Character.isLetterOrDigit(ghostText.charAt(i)))
                i++;

            String accepted = ghostText.substring(0, i);
            String remaining = ghostText.substring(i);

            // Erase the ghost text display first (screen-level to handle wrapping)
            connection.write(ANSI.CURSOR_SAVE);
            connection.stdoutHandler().accept(ANSI.ERASE_SCREEN_FROM_CURSOR);
            connection.write(ANSI.CURSOR_RESTORE);

            ghostText = null;
            writeString(accepted);

            // Show remaining ghost text if any
            if (!remaining.isEmpty()) {
                showGhostText(remaining);
            }
        }
    }

    @Override
    public String ghostText() {
        return ghostText;
    }

    private boolean isViMode() {
        return editMode.mode() == EditMode.Mode.VI &&
                editMode.status() != EditMode.Status.EDIT;
    }

}
