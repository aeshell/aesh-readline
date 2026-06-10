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
package org.aesh.terminal;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.aesh.terminal.detect.TerminalTheme;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.StatusLine;

/**
 * Abstract base class for {@link Connection} implementations that use an
 * {@link EventDecoder} for signal and input handling.
 * <p>
 * Provides the common handler plumbing (signal, stdin, stdout, size, close,
 * attributes, reading state) so that subclasses only need to implement
 * device/transport-specific methods.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public abstract class AbstractConnection implements Connection {

    /** Field. */
    protected EventDecoder eventDecoder;
    /** Field. */
    protected Consumer<int[]> stdout;
    /** Field. */
    protected Consumer<Size> sizeHandler;
    /** Field. */
    protected Consumer<Void> closeHandler;
    /** Field. */
    protected Attributes attributes;
    /** Field. */
    protected volatile boolean reading;

    /** Constructor. */
    protected AbstractConnection() {
    }

    private TerminalFeatures terminalFeatures;

    @Override
    public TerminalFeatures terminal() {
        if (terminalFeatures == null) {
            terminalFeatures = new TerminalFeatures(this);
        }
        return terminalFeatures;
    }

    @Override
    public Consumer<Signal> signalHandler() {
        return eventDecoder.getSignalHandler();
    }

    @Override
    public void setSignalHandler(Consumer<Signal> handler) {
        eventDecoder.setSignalHandler(handler);
    }

    @Override
    public Consumer<int[]> stdinHandler() {
        return eventDecoder.getInputHandler();
    }

    @Override
    public void setStdinHandler(Consumer<int[]> handler) {
        eventDecoder.setInputHandler(handler);
    }

    @Override
    public Consumer<int[]> stdoutHandler() {
        return stdout;
    }

    @Override
    public Consumer<Size> sizeHandler() {
        return sizeHandler;
    }

    @Override
    public void setSizeHandler(Consumer<Size> handler) {
        this.sizeHandler = handler;
    }

    @Override
    public void setCloseHandler(Consumer<Void> closeHandler) {
        this.closeHandler = closeHandler;
    }

    @Override
    public Consumer<Void> closeHandler() {
        return closeHandler;
    }

    @Override
    public void setThemeChangeHandler(Consumer<TerminalTheme> handler) {
        eventDecoder.setThemeChangeHandler(handler);
    }

    @Override
    public Consumer<TerminalTheme> themeChangeHandler() {
        return eventDecoder.getThemeChangeHandler();
    }

    @Override
    public void setMouseHandler(java.util.function.Consumer<org.aesh.terminal.tty.MouseEvent> handler) {
        eventDecoder.setMouseHandler(handler);
    }

    @Override
    public java.util.function.Consumer<org.aesh.terminal.tty.MouseEvent> mouseHandler() {
        return eventDecoder.getMouseHandler();
    }

    @Override
    public void setFocusHandler(java.util.function.Consumer<Boolean> handler) {
        eventDecoder.setFocusHandler(handler);
    }

    @Override
    public java.util.function.Consumer<Boolean> focusHandler() {
        return eventDecoder.getFocusHandler();
    }

    /** Handler for printAbove requests. */
    private volatile Consumer<String> printAboveHandler;

    /** Registered status lines, sorted by priority. */
    private final CopyOnWriteArrayList<StatusLineImpl> statusLines = new CopyOnWriteArrayList<>();

    @Override
    public StatusLine registerStatusLine(int priority) {
        StatusLineImpl sl = new StatusLineImpl(priority, this);
        statusLines.add(sl);
        // Sort by priority (lowest first = top, highest last = bottom near prompt)
        statusLines.sort((a, b) -> Integer.compare(a.priority, b.priority));
        // Trigger a redraw if a printAbove handler is active
        Consumer<String> handler = printAboveHandler;
        if (handler != null) {
            handler.accept("");
        }
        return sl;
    }

    /**
     * Returns the current status line messages in display order
     * (lowest priority first).
     *
     * @return unmodifiable list of active status line messages
     */
    public List<String> getStatusMessages() {
        if (statusLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> messages = new java.util.ArrayList<>();
        for (StatusLineImpl sl : statusLines) {
            String msg = sl.message;
            if (msg != null && !msg.isEmpty()) {
                messages.add(msg);
            }
        }
        return Collections.unmodifiableList(messages);
    }

    void removeStatusLine(StatusLineImpl sl) {
        statusLines.remove(sl);
        // Trigger a redraw
        Consumer<String> handler = printAboveHandler;
        if (handler != null) {
            handler.accept("");
        }
    }

    void statusLineUpdated() {
        // Trigger a redraw when a status line message changes
        Consumer<String> handler = printAboveHandler;
        if (handler != null) {
            handler.accept("");
        }
    }

    private static class StatusLineImpl implements StatusLine {
        final int priority;
        volatile String message;
        volatile boolean closed;
        private final AbstractConnection connection;

        StatusLineImpl(int priority, AbstractConnection connection) {
            this.priority = priority;
            this.connection = connection;
        }

        @Override
        public void setMessage(String message) {
            if (!closed) {
                this.message = message;
                connection.statusLineUpdated();
            }
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                connection.removeStatusLine(this);
            }
        }
    }

    @Override
    public void setPrintAboveHandler(Consumer<String> handler) {
        this.printAboveHandler = handler;
    }

    @Override
    public Consumer<String> printAboveHandler() {
        return printAboveHandler;
    }

    @Override
    public boolean reading() {
        return reading;
    }

    @Override
    public Attributes attributes() {
        return attributes;
    }

    @Override
    public void setAttributes(Attributes attr) {
        this.attributes = attr;
    }
}
