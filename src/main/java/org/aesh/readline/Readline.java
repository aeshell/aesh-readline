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
import org.aesh.terminal.Key;
import org.aesh.tty.Signal;
import org.aesh.tty.Size;
import org.aesh.util.Config;
import org.aesh.util.Parser;
import org.aesh.tty.Connection;
import org.aesh.util.LoggerUtil;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 *
 */
public class Readline {

    private static final Logger LOGGER = LoggerUtil.getLogger(Readline.class.getName());

    private final ActionDecoder decoder;
    private AeshInputProcessor inputProcessor;
    private Size size;

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
                         List<Function<String,Optional<String>>> preProcessors,
                         History history) {
        synchronized(this) {
            if (inputProcessor != null) {
                throw new IllegalStateException("Already reading a line");
            }
            inputProcessor = new AeshInputProcessor(conn, prompt, requestHandler, completions, preProcessors, history);
        }
        inputProcessor.start();
        processInput();
    }

    private void processInput() {
        synchronized (this) {
            if (inputProcessor == null) {
                throw new IllegalStateException("No inputProcessor!");
            }
            if(inputProcessor.connection().suspended())
                inputProcessor.connection().awake();
            if (decoder.hasNext()) {
                readInput();
            }
        }
    }

    private class AeshInputProcessor implements InputProcessor {
        private final Connection conn;
        private Consumer<int[]> prevReadHandler;
        private Consumer<Size> prevSizeHandler;
        private Consumer<Signal> prevEventHandler;
        private final Consumer<String> requestHandler;
        private boolean paused;
        private final ConsoleBuffer consoleBuffer;
        private String returnValue;
        private List<Function<String,Optional<String>>> preProcessors;

        private AeshInputProcessor(
                Connection conn,
                Prompt prompt,
                Consumer<String> requestHandler,
                List<Completion> completions,
                List<Function<String,Optional<String>>> preProcessors,
                History newHistory) {

            completionHandler.clear();
            completionHandler.addCompletions(completions);
            consoleBuffer =
                    new AeshConsoleBuffer(conn, prompt, editMode,
                            //use newHistory if its not null
                            newHistory != null ? newHistory : history,
                            completionHandler, size, true);

            this.conn = conn;
            this.requestHandler = requestHandler;
            this.preProcessors = preProcessors;
        }

        private void finish(String s) {
            conn.setStdinHandler(prevReadHandler);
            conn.setSizeHandler(prevSizeHandler);
            conn.setSignalHandler(prevEventHandler);
            inputProcessor = null;
            requestHandler.accept(s);
        }

        private void parse(KeyAction event) {
            //TODO: the editModes need to parse/handle this, ref ignoreeof
            //ctrl-d
            if (event.length() == 1) {
                if (event.getCodePointAt(0) == 4 && getBuffer().getBuffer().length() == 0) {
                    finish(null);
                    return;
                }
            }

            Action action = editMode.parse(event);
            //LOGGER.info("Found action: "+action);
            if (action != null) {
                paused = true;
                action.accept(this);
                if(this.getReturnValue() != null) {
                    conn.stdoutHandler().accept(Config.CR);
                    finish(this.getReturnValue());
                }
                else {
                    paused = false;
                    processInput();
                }
            }
            else {
                if(Key.isPrintable(event.buffer()) && notInCommandNode())
                    this.getBuffer().writeChar((char) event.buffer().array()[0]);
            }
        }

        private boolean notInCommandNode() {
            return !(editMode.getMode() == EditMode.Mode.VI &&
                    editMode.getStatus() == EditMode.Status.COMMAND);
        }

        private void start() {
            prevReadHandler = conn.getStdinHandler();
            prevSizeHandler = conn.getSizeHandler();
            prevEventHandler = conn.getSignalHandler();
            conn.setStdinHandler(data -> {
                decoder.add(data);
                readInput();
            });
            size = conn.size();
            if(size == null)
                throw new RuntimeException("Terminal size must not be null");
            consoleBuffer.setSize(size);
            conn.setSizeHandler(dim -> {
                if (size != null) {
                    resize(dim);
                }
                size = dim;
            });
            //only set signalHandler if its null
            if(conn.getSignalHandler() == null) {
                conn.setSignalHandler(signal -> {
                    if (signal == Signal.INT) {
                        if (editMode.isInChainedAction()) {
                            parse(Key.CTRL_C);
                        } else {
                            conn.stdoutHandler().accept(new int[]{'^', 'C'});
                            conn.stdoutHandler().accept(Config.CR);
                            this.getBuffer().getBuffer().reset();
                            consoleBuffer.drawLine();
                        }
                    }
                });
            }

            //last, display prompt
            consoleBuffer.drawLine();
        }

        private void resize(Size size) {
            consoleBuffer.setSize(size);
            consoleBuffer.drawLine();
        }

        @Override
        public String getReturnValue() {
            return returnValue;
        }

        @Override
        public ConsoleBuffer getBuffer() {
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
        public EditMode getEditMode() {
            return editMode;
        }

        @Override
        public Connection connection() {
            return conn;
        }
    }

}
