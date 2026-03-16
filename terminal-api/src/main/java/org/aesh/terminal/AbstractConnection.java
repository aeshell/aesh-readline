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

import java.util.function.Consumer;

import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.utils.TerminalTheme;

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

    protected EventDecoder eventDecoder;
    protected Consumer<int[]> stdout;
    protected Consumer<Size> sizeHandler;
    protected Consumer<Void> closeHandler;
    protected Attributes attributes;
    protected volatile boolean reading;
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
