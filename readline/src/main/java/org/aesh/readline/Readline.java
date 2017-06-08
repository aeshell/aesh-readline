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

import org.aesh.readline.cursor.CursorListener;
import org.aesh.readline.action.Action;
import org.aesh.readline.action.ActionDecoder;
import org.aesh.readline.action.KeyAction;
import org.aesh.readline.completion.Completion;
import org.aesh.readline.completion.CompletionHandler;
import org.aesh.readline.completion.SimpleCompletionHandler;
import org.aesh.readline.editing.EditMode;
import org.aesh.readline.editing.EditModeBuilder;
import org.aesh.readline.history.History;
import org.aesh.readline.history.InMemoryHistory;
import org.aesh.readline.terminal.Key;
import org.aesh.terminal.Attributes;
import org.aesh.utils.Config;
import org.aesh.util.Parser;
import org.aesh.terminal.Connection;
import org.aesh.util.LoggerUtil;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;

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

    public Readline() {
        this(EditModeBuilder.builder().create());
    }

    public Readline(EditMode editMode) {
        this(editMode, new InMemoryHistory(), null);
        history.enable();
    }

    public Readline(EditMode editMode, History history, CompletionHandler completionHandler) {
        this.editMode = editMode;
        this.history = history;
        if(completionHandler == null)
            this.completionHandler = new SimpleCompletionHandler();
        else
            this.completionHandler = completionHandler;
        this.decoder = new ActionDecoder(this.editMode);
    }

    protected InputProcessor getInputProcessor() {
        return inputProcessor;
    }

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

    public void readline(Connection conn, String prompt, Consumer<String> requestHandler) {
        readline(conn, new Prompt(prompt), requestHandler, null);
    }

    public void readline(Connection conn, String prompt, Consumer<String> requestHandler,
                         List<Completion> completions) {
        readline(conn, new Prompt(prompt), requestHandler, completions);
    }

    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler) {
        readline(conn, prompt, requestHandler, null);
    }

    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler,
                         List<Completion> completions) {
        readline(conn, prompt, requestHandler, completions, null);
    }

    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler,
                         List<Completion> completions,
                         List<Function<String,Optional<String>>> preProcessors ) {
        readline(conn, prompt, requestHandler, completions, preProcessors, null);
    }

    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler,
                         List<Completion> completions,
                         List<Function<String,Optional<String>>> preProcessors, History history) {
        readline(conn, prompt, requestHandler, completions, preProcessors, history, null);
    }

    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler,
                         List<Completion> completions,
                         List<Function<String,Optional<String>>> preProcessors,
                         History history, CursorListener listener) {
         readline(conn, prompt, requestHandler, completions, preProcessors, history, listener,
                new EnumMap<>(ReadlineFlag.class));
    }

    public void readline(Connection conn, Prompt prompt, Consumer<String> requestHandler,
                         List<Completion> completions,
                         List<Function<String,Optional<String>>> preProcessors,
                         History history, CursorListener listener, EnumMap<ReadlineFlag, Integer> flags) {
        synchronized(this) {
            if (inputProcessor != null) {
                throw new IllegalStateException("Already reading a line");
            }
            inputProcessor = new AeshInputProcessor(conn, prompt, requestHandler,
                    completions, preProcessors, history, listener, flags);
            inputProcessor.start();
            //inputProcessor can be set to null from the start() method
            if(inputProcessor != null)
                processInput();
        }
    }

    private void processInput() {
        synchronized (this) {
            if (inputProcessor == null) {
                LOGGER.warning("No inputprocessor in Readline.processInput");
            }
            else if (decoder.hasNext()) {
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
        private List<Function<String,Optional<String>>> preProcessors;
        private Attributes attributes;
        private final EnumMap<ReadlineFlag, Integer> flags;

        private AeshInputProcessor(
                Connection conn,
                Prompt prompt,
                Consumer<String> requestHandler,
                List<Completion> completions,
                List<Function<String,Optional<String>>> preProcessors,
                History newHistory, CursorListener listener, EnumMap<ReadlineFlag, Integer> flags) {

            completionHandler.clear();
            completionHandler.addCompletions(completions);
            consoleBuffer =
                    new AeshConsoleBuffer(conn, prompt, editMode,
                            //use newHistory if its not null
                            newHistory != null ? newHistory : history,
                            completionHandler, true, listener);

            this.conn = conn;
            this.requestHandler = requestHandler;
            this.preProcessors = preProcessors;
            attributes = conn.getAttributes();
            this.flags = flags;
        }

        private void finish(String s) {
            conn.setStdinHandler(prevReadHandler);
            conn.setSizeHandler(prevSizeHandler);
            conn.setSignalHandler(prevSignalHandler);
            synchronized (Readline.this) {
                inputProcessor = null;
            }
            //revert back to the old attributes
            conn.setAttributes(attributes);

            //call requestHandler with the output
            requestHandler.accept(s);
        }

        /**
         * Parse the event given
         * @param event event
         */
        private void parse(KeyAction event) {
            //TODO: the editModes need to parse/handle this, ref ignoreeof
            //ctrl-d
            /*
            if (event.length() == 1) {
                if (event.getCodePointAt(0) == 4 && buffer().buffer().length() == 0) {
                    finish(null);
                    return;
                }
            }
            */

            Action action = editMode.parse(event);
            if (action != null) {
                synchronized (Readline.this) {
                    paused = true;
                }
                action.accept(this);
                editMode.setPrevKey(event);
                if(this.returnValue() != null) {
                    conn.stdoutHandler().accept(Config.CR);
                    finish(this.returnValue());
                }
                else {
                    synchronized (Readline.this) {
                        paused = false;
                    }
                    //some actions might call finish
                    if(inputProcessor != null)
                        processInput();
                }
            }
            else {
                if(Key.isPrintable(event.buffer()) && notInCommandNode())
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
                            } else {
                                if (attributes.getLocalFlag(Attributes.LocalFlag.ECHOCTL)) {
                                    conn.stdoutHandler().accept(new int[]{'^', 'C'});
                                }
                                if (!flags.containsKey(ReadlineFlag.NO_PROMPT_REDRAW_ON_INTR)) {
                                    conn.stdoutHandler().accept(Config.CR);
                                    this.buffer().buffer().reset();
                                    consoleBuffer.drawLine();
                                }
                            }
                            if (prevSignalHandler != null) {
                                prevSignalHandler.accept(signal);
                            }
                            break;
                        case CONT:
                            conn.enterRawMode();
                            //just call resize since it will redraw the buffer and set size
                            resize(conn.size());
                            break;
                        case EOF:
                            parse(Key.CTRL_D);
                            //if inputHandler is null we send a signal to the previous handler)
                            /*
                            if (prevSignalHandler != null) {
                                prevSignalHandler.accept(signal);
                            }
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

            //last, display prompt
            consoleBuffer.drawLine();
            //last process input, the readInput() can read/finish in one go
            //since EventDecoder might have queued up data
            conn.setStdinHandler(data -> {
                synchronized(Readline.this) {
                    decoder.add(data);
                }
                readInput();
            });
        }

        private void resize(Size size) {
            //redraw the buffer when we resize
            if(inputProcessor.consoleBuffer.buffer().length() > 0) {
                int[] buffer = inputProcessor.buffer().buffer().multiLine();
                inputProcessor.consoleBuffer.setSize(size);
                inputProcessor.consoleBuffer.replace(buffer);
            }
            else
                inputProcessor.consoleBuffer.setSize(size);
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
            if(preProcessors != null && preProcessors.size() > 0) {
                preProcessors.forEach(pre -> pre.apply(input).ifPresent(v -> returnValue = v));
            }
            if(returnValue == null)
                returnValue = input;
        }

        @Override
        public EditMode editMode() {
            return editMode;
        }

        @Override
        public void setEditMode(EditMode edit) {
            if(edit != null) {
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
