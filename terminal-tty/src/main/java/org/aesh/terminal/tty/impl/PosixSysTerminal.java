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
package org.aesh.terminal.tty.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.utils.ShutdownHooks;
import org.aesh.terminal.tty.utils.Signals;

/**
 * A POSIX system terminal implementation that provides native signal handling
 * and integrates with the system's PTY.
 */
public class PosixSysTerminal extends AbstractPosixTerminal {

    /** The terminal input stream. */
    protected final InputStream input;

    /** The terminal output stream. */
    protected final OutputStream output;

    /** Map of signals to their native handlers. */
    protected final Map<Signal, Object> nativeHandlers = new HashMap<>();

    /** Shutdown hook task for cleanup. */
    protected final ShutdownHooks.Task closer;

    /**
     * Constructs a PosixSysTerminal with the specified parameters.
     *
     * @param name the terminal name
     * @param type the terminal type
     * @param pty the pseudo-terminal to use
     * @param nativeSignals whether to enable native signal handling
     * @throws IOException if an I/O error occurs during initialization
     */
    public PosixSysTerminal(String name, String type, Pty pty, boolean nativeSignals) throws IOException {
        super(name, type, pty);
        this.input = pty.getSlaveInput();
        this.output = pty.getSlaveOutput();
        if (nativeSignals) {
            for (final Signal signal : Signal.values()) {
                nativeHandlers.put(signal, Signals.register(signal.name(), () -> raise(signal)));
            }
        }
        closer = PosixSysTerminal.this::close;
        ShutdownHooks.add(closer);
    }

    @Override
    public InputStream input() {
        return input;
    }

    @Override
    public OutputStream output() {
        return output;
    }

    @Override
    public void close() throws IOException {
        ShutdownHooks.remove(closer);
        for (Map.Entry<Signal, Object> entry : nativeHandlers.entrySet()) {
            Signals.unregister(entry.getKey().name(), entry.getValue());
        }
        super.close();
    }
}
