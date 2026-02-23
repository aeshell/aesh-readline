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
package org.aesh.readline;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import org.aesh.readline.action.Action;
import org.aesh.readline.action.ActionDecoder;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.completion.SimpleCompletionHandler;
import org.aesh.readline.cursor.CursorListener;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.history.History;
import org.aesh.readline.history.InMemoryHistory;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Connection;
import org.aesh.terminal.Key;
import org.aesh.terminal.KeyAction;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.Config;
import org.aesh.terminal.utils.LoggerUtil;
import org.aesh.terminal.utils.Parser;

/**
 * Readline is a simple way to read a single input line from the terminal/shell/console.
 * Readline reads/writes from/to a {@link org.aesh.terminal.Connection}.
 *
 * Readline is thread safe and will not accept new {@link org.aesh.readline.Readline#readline} calls
 * while currently reading input.
 */
public class Readline {

    private static final Logger LOGGER = LoggerUtil.getLogger(Readline.class.getName());

    private final ActionDecoder decoder;
    private AeshInputProcessor inputProcessor;

    private CompletionHandler completionHandler;
    private EditMode editMode;
    private History history;

    /**
     * Creates a new Readline instance with default edit mode, in-memory history, and simple completion handler.
     */
    public Readline() {
        this(EditModeBuilder.builder().create());
    }

    /**
     * Creates a new Readline instance with the specified edit mode and in-memory history.
     *
     * @param editMode the edit mode to use (e.g., Emacs or Vi mode)
     */
    public Readline(EditMode editMode) {
        this(editMode, new InMemoryHistory(), null);
        history.enable();
    }

    /**
     * Creates a new Readline instance with the specified edit mode, history, and completion handler.
     *
     * @param editMode the edit mode to use (e.g., Emacs or Vi mode)
     * @param history the history implementation to use for command history
     * @param completionHandler the completion handler for tab completion, or null for default
     */
    public Readline(EditMode editMode, History history, CompletionHandler completionHandler) {
        this.editMode = editMode;
        this.history = history;
        if (completionHandler == null)
            this.completionHandler = new SimpleCompletionHandler();
        else
            this.completionHandler = completionHandler;
        this.decoder = new ActionDecoder(this.editMode);
    }

    /**
     * Returns the current input processor.
     *
     * @return the input processor, or null if not currently reading input
     */
    protected InputProcessor getInputProcessor() {
        return inputProcessor;
    }

    /**
     * Reads and processes input from the decoder until no more actions are available.
     */
    private void readInput() {
        synchronized (this) {
            while (true) {
                if (decoder.hasNext() && inputProcessor != null && !inputProcessor.paused) {
                    inputProcessor.parse(decoder.next());
                } else {
                    return;
                }
            }
        }
    }

    /**
     * Reads a line of input from the connection with a string prompt.
     *
     * @param conn the terminal connection to read from
     * @param prompt the prompt string to display
     * @param requestHandler the callback to receive the completed input line
     */
    public void readline(Connection conn, String prompt, Consumer<String> requestHandler) {
        readline(conn, new Prompt(prompt), requestHandler, null);
    }

    /**
     * Reads a line of input from the connection with a string prompt and completions.
     *
     * @param conn the terminal connection to read from
     * @param prompt the prompt string to display
     * @param requestHandler the callback to receive the completed input line
     * @param completions the list of completions for tab completion
     */
    public void readline(Connection conn, String prompt, Consumer<String> requestHandler,
            List<Completion> completions) {
        readline(conn, new Prompt(prompt), requestHandler, completions);
    }

    /**
     * Reads a line of input from the connection with a Prompt object.
     *
     * @param conn the terminal connection to read from
     * @param prompt the prompt to display
     * @param requestHandler the callback to receive the completed input line
     */
    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler) {
        readline(conn, prompt, requestHandler, null);
    }

    /**
     * Reads a line of input from the connection with a Prompt object and completions.
     *
     * @param conn the terminal connection to read from
     * @param prompt the prompt to display
     * @param requestHandler the callback to receive the completed input line
     * @param completions the list of completions for tab completion
     */
    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler,
            List<Completion> completions) {
        readline(conn, prompt, requestHandler, completions, null);
    }

    /**
     * Reads a line of input from the connection with completions and pre-processors.
     *
     * @param conn the terminal connection to read from
     * @param prompt the prompt to display
     * @param requestHandler the callback to receive the completed input line
     * @param completions the list of completions for tab completion
     * @param preProcessors the list of input pre-processors
     */
    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler,
            List<Completion> completions,
            List<Function<String, Optional<String>>> preProcessors) {
        readline(conn, prompt, requestHandler, completions, preProcessors, null);
    }

    /**
     * Reads a line of input from the connection with completions, pre-processors, and custom history.
     *
     * @param conn the terminal connection to read from
     * @param prompt the prompt to display
     * @param requestHandler the callback to receive the completed input line
     * @param completions the list of completions for tab completion
     * @param preProcessors the list of input pre-processors
     * @param history the history instance to use for this readline operation
     */
    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler,
            List<Completion> completions,
            List<Function<String, Optional<String>>> preProcessors, History history) {
        readline(conn, prompt, requestHandler, completions, preProcessors, history, null);
    }

    /**
     * Reads a line of input from the connection with completions, pre-processors, custom history, and cursor listener.
     *
     * @param conn the terminal connection to read from
     * @param prompt the prompt to display
     * @param requestHandler the callback to receive the completed input line
     * @param completions the list of completions for tab completion
     * @param preProcessors the list of input pre-processors
     * @param history the history instance to use for this readline operation
     * @param listener the cursor listener to receive cursor movement events
     */
    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler,
            List<Completion> completions,
            List<Function<String, Optional<String>>> preProcessors,
            History history, CursorListener listener) {
        readline(conn, prompt, requestHandler, completions, preProcessors, history, listener,
                new EnumMap<>(ReadlineFlag.class));
    }

    /**
     * Reads a line of input from the connection with all configuration options.
     *
     * @param conn the terminal connection to read from
     * @param prompt the prompt to display
     * @param requestHandler the callback to receive the completed input line
     * @param completions the list of completions for tab completion
     * @param preProcessors the list of input pre-processors
     * @param history the history instance to use for this readline operation
     * @param listener the cursor listener to receive cursor movement events
     * @param flags the readline flags controlling behavior
     * @throws IllegalStateException if already reading a line
     */
    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler,
            List<Completion> completions,
            List<Function<String, Optional<String>>> preProcessors,
            History history, CursorListener listener, EnumMap<ReadlineFlag, Integer> flags) {
        synchronized (this) {
            if (inputProcessor != null) {
                throw new IllegalStateException("Already reading a line");
            }
            inputProcessor = new AeshInputProcessor(conn, prompt, requestHandler,
                    completions, preProcessors, history, listener, flags);
            inputProcessor.start();
            //inputProcessor can be set to null from the start() method
            if (inputProcessor != null)
                processInput();
        }
    }

    /**
     * Processes pending input from the decoder if an input processor is available.
     */
    private void processInput() {
        synchronized (this) {
            if (inputProcessor == null) {
                LOGGER.warning("No inputprocessor in Readline.processInput");
            } else if (decoder.hasNext()) {
                readInput();
            }
        }
    }

    /**
     * AeshInputProcessor, default InputProcessor impl.
     * Used to parse the incoming input from the Connection until a value is returned.
     * A new instance of AeshInputProcessor is created for each readline.
     */
    private class AeshInputProcessor implements InputProcessor {
        private final Connection conn;
        private Consumer<int[]> prevReadHandler;
        private Consumer<Size> prevSizeHandler;
        private Consumer<Signal> prevSignalHandler;
        private final Consumer<String> requestHandler;
        private boolean paused;
        private final ConsoleBuffer consoleBuffer;
        private String returnValue;
        private List<Function<String, Optional<String>>> preProcessors;
        private Attributes attributes;
        private final EnumMap<ReadlineFlag, Integer> flags;
        private boolean graphemeClusterModeActive;
        private boolean synchronizedOutputSupported;

        private AeshInputProcessor(
                Connection conn,
                Prompt prompt,
                Consumer<String> requestHandler,
                List<Completion> completions,
                List<Function<String, Optional<String>>> preProcessors,
                History newHistory, CursorListener listener, EnumMap<ReadlineFlag, Integer> flags) {

            completionHandler.clear();
            completionHandler.addCompletions(completions);
            consoleBuffer = new AeshConsoleBuffer(conn, prompt, editMode,
                    //use newHistory if its not null
                    newHistory != null ? newHistory : history,
                    completionHandler, true, listener);

            this.conn = conn;
            this.requestHandler = requestHandler;
            this.preProcessors = preProcessors;
            attributes = conn.getAttributes();
            this.flags = flags;
        }

        @Override
        public void finish(String s) {
            conn.setStdinHandler(prevReadHandler);
            conn.setSizeHandler(prevSizeHandler);
            conn.setSignalHandler(prevSignalHandler);
            synchronized (Readline.this) {
                if (graphemeClusterModeActive) {
                    conn.disableGraphemeClusterMode();
                    graphemeClusterModeActive = false;
                }
                //revert back to the old attributes
                conn.setAttributes(attributes);
                inputProcessor = null;
            }

            //call requestHandler with the output
            requestHandler.accept(s);
        }

        /**
         * Parse the event given
         *
         * @param event event
         */
        private void parse(KeyAction event) {
            Action action = editMode.parse(event);
            if (action != null) {
                synchronized (Readline.this) {
                    paused = true;
                }
                if (synchronizedOutputSupported)
                    conn.enableSynchronizedOutput();
                action.accept(this);
                if (synchronizedOutputSupported)
                    conn.disableSynchronizedOutput();
                editMode.setPrevKey(event);
                if (this.returnValue() != null) {
                    conn.stdoutHandler().accept(Config.CR);
                    finish(this.returnValue());
                } else {
                    synchronized (Readline.this) {
                        paused = false;
                    }
                    //some actions might call finish
                    if (inputProcessor != null)
                        processInput();
                }
            } else {
                if (Key.isPrintable(event.buffer()) && notInCommandNode())
                    this.buffer().writeChar((char) event.buffer().array()[0]);
            }
        }

        private boolean notInCommandNode() {
            return !(editMode.mode() == EditMode.Mode.VI &&
                    editMode.status() == EditMode.Status.COMMAND);
        }

        /**
         * Make a copy of Connection's current handlers and then use our own.
         */
        private void start() {
            prevReadHandler = conn.getStdinHandler();
            prevSizeHandler = conn.getSizeHandler();
            prevSignalHandler = conn.getSignalHandler();

            //we've made a backup of the current signal handler
            conn.setSignalHandler(signal -> {
                if (signal != null) {
                    switch (signal) {
                        case INT:
                            if (editMode.isInChainedAction()) {
                                parse(Key.CTRL_C);
                                break;
                            } else {
                                if (attributes.getLocalFlag(Attributes.LocalFlag.ECHOCTL)) {
                                    conn.stdoutHandler().accept(new int[] { '^', 'C' });
                                }
                                if (!flags.containsKey(ReadlineFlag.NO_PROMPT_REDRAW_ON_INTR)) {
                                    conn.stdoutHandler().accept(Config.CR);
                                }
                            }
                            if (prevSignalHandler != null) {
                                prevSignalHandler.accept(signal);
                            }
                            finish("");
                            break;
                        case CONT:
                            conn.enterRawMode();
                            // Re-enable Mode 2027 since terminal modes are lost during suspension
                            if (graphemeClusterModeActive) {
                                conn.enableGraphemeClusterMode();
                            }
                            //just call resize since it will redraw the buffer and set size
                            resize(conn.size());
                            break;
                        case EOF:
                            parse(Key.CTRL_D);
                            //if inputHandler is null we send a signal to the previous handler)
                            /*
                             * if (prevSignalHandler != null) {
                             * prevSignalHandler.accept(signal);
                             * }
                             */
                            break;
                        default:
                            break;
                    }
                }
            });
            //make sure we refresh if we get a resize
            conn.setSizeHandler(this::resize);

            //setting attributes to previous values
            attributes = conn.enterRawMode();

            // Enable Mode 2027 for terminals that support grapheme cluster segmentation
            if (!flags.containsKey(ReadlineFlag.NO_GRAPHEME_CLUSTER_MODE)
                    && conn.supportsGraphemeClusterMode()) {
                conn.enableGraphemeClusterMode();
                graphemeClusterModeActive = true;
            }

            // Detect Mode 2026 (synchronized output) support
            if (!flags.containsKey(ReadlineFlag.NO_SYNCHRONIZED_OUTPUT)
                    && conn.supportsSynchronizedOutput()) {
                synchronizedOutputSupported = true;
            }

            //last, display prompt
            if (synchronizedOutputSupported)
                conn.enableSynchronizedOutput();
            consoleBuffer.drawLine();
            if (synchronizedOutputSupported)
                conn.disableSynchronizedOutput();
            //last process input, the readInput() can read/finish in one go
            //since EventDecoder might have queued up data
            conn.setStdinHandler(data -> {
                synchronized (Readline.this) {
                    decoder.add(data);
                }
                readInput();
            });
        }

        private void resize(Size size) {
            //redraw the buffer when we resize
            if (synchronizedOutputSupported)
                conn.enableSynchronizedOutput();
            if (inputProcessor.consoleBuffer.buffer().length() > 0) {
                int[] buffer = inputProcessor.buffer().buffer().multiLine();
                inputProcessor.consoleBuffer.setSize(size);
                inputProcessor.consoleBuffer.replace(buffer);
            } else
                inputProcessor.consoleBuffer.setSize(size);
            if (synchronizedOutputSupported)
                conn.disableSynchronizedOutput();
        }

        @Override
        public String returnValue() {
            return returnValue;
        }

        @Override
        public ConsoleBuffer buffer() {
            return consoleBuffer;
        }

        @Override
        public void setReturnValue(int[] in) {
            String input = Parser.fromCodePoints(in);
            if (preProcessors != null && preProcessors.size() > 0) {
                preProcessors.forEach(pre -> pre.apply(input).ifPresent(v -> returnValue = v));
            }
            if (returnValue == null)
                returnValue = input;
        }

        @Override
        public EditMode editMode() {
            return editMode;
        }

        @Override
        public void setEditMode(EditMode edit) {
            if (edit != null) {
                editMode = edit;
                decoder.setMappings(editMode);
            }
        }

        @Override
        public Connection connection() {
            return conn;
        }

        @Override
        public EnumMap<ReadlineFlag, Integer> flags() {
            return flags;
        }
    }

}
