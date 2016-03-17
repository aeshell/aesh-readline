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

import org.jboss.aesh.readline.completion.Completion;
import org.jboss.aesh.readline.completion.CompletionHandler;
import org.jboss.aesh.readline.completion.SimpleCompletionHandler;
import org.jboss.aesh.readline.editing.EditMode;
import org.jboss.aesh.readline.editing.EditModeBuilder;
import org.jboss.aesh.readline.history.History;
import org.jboss.aesh.readline.history.InMemoryHistory;
import org.jboss.aesh.terminal.Key;
import org.jboss.aesh.tty.Signal;
import org.jboss.aesh.tty.Size;
import org.jboss.aesh.tty.Connection;
import org.jboss.aesh.util.Config;
import org.jboss.aesh.util.LoggerUtil;

import java.util.List;
import java.util.function.Consumer;
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
    }

    public Readline(EditMode editMode, History history, CompletionHandler completionHandler) {
        this.editMode = editMode;
        this.history = history;
        this.completionHandler = completionHandler;
        this.decoder = new ActionDecoder(this.editMode);
    }

    protected InputProcessor getInputProcessor() {
        return inputProcessor;
    }

    private void readInput() {
        while (true) {
            if (decoder.hasNext() && inputProcessor != null && !inputProcessor.paused) {
                inputProcessor.parse(decoder.next());
            }
            else {
                return;
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
        if (inputProcessor != null) {
            throw new IllegalStateException("Already reading a line");
        }
        inputProcessor = new AeshInputProcessor(conn, prompt, requestHandler, completions);
        inputProcessor.start();
        processInput();
    }

    public void processInput() {
        synchronized (this) {
            if (inputProcessor == null) {
                throw new IllegalStateException("No inputProcessor!");
            }
            if (decoder.hasNext()) {
                readInput();
            }
            else {
                return;
            }
        }
    }

    public class AeshInputProcessor implements InputProcessor {
        private final Connection conn;
        private Consumer<int[]> prevReadHandler;
        private Consumer<Size> prevSizeHandler;
        private Consumer<Signal> prevEventHandler;
        private final Consumer<String> requestHandler;
        private boolean paused;
        private final ConsoleBuffer consoleBuffer;
        private String returnValue;

        private AeshInputProcessor(
                Connection conn,
                Prompt prompt,
                Consumer<String> requestHandler,
                List<Completion> completions) {

            completionHandler = new SimpleCompletionHandler();
            completionHandler.addCompletions(completions);
            consoleBuffer =
                    new AeshConsoleBuffer(conn, prompt, editMode, history, completionHandler, size, true);

            this.conn = conn;
            this.requestHandler = requestHandler;
        }

        private void finish(String s) {
            conn.setStdinHandler(prevReadHandler);
            conn.setSizeHandler(prevSizeHandler);
            conn.setSignalHandler(prevEventHandler);
            inputProcessor = null;
            requestHandler.accept(s);
        }

        private void parse(KeyAction event) {
            //LOGGER.info("got event: "+event);
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
                action.apply(this);
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
                if(Key.isPrintable(event.buffer().array()))
                    this.getBuffer().writeChar((char) event.buffer().array()[0]);
            }
        }

        public final Size size() {
            return size;
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
            conn.setSignalHandler( signal -> {
                if(signal == Signal.INT) {
                    if(editMode.isInChainedAction()) {
                        parse(Key.CTRL_C);
                    }
                    else {
                        conn.stdoutHandler().accept(new int[]{'^', 'C', '\n'});
                        conn.stdoutHandler().accept(this.getBuffer().getBuffer().getPrompt().getANSI());
                        this.getBuffer().getBuffer().reset();
                    }
                }
            });

            //last, display prompt
            consoleBuffer.displayPrompt();
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
        public void setReturnValue(String value) {
            returnValue = value;
        }
    }

}
