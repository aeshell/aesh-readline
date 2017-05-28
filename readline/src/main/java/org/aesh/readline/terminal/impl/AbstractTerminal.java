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
package org.aesh.readline.terminal.impl;

import org.aesh.readline.terminal.DeviceBuilder;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Device;
import org.aesh.terminal.Terminal;
import org.aesh.util.LoggerUtil;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.aesh.terminal.tty.Signal;

public abstract class AbstractTerminal implements Terminal {

    protected final Logger LOGGER = LoggerUtil.getLogger(getClass().getName());

    protected final String name;
    protected final String type;
    protected final Map<Signal, SignalHandler> handlers = new HashMap<>();
    protected final Device device;

    public AbstractTerminal(String name, String type) throws IOException {
        this(name, type, SignalHandlers.SIG_DFL);
    }

    public AbstractTerminal(String name, String type, SignalHandler signalHandler) throws IOException {
        this.name = name;
        this.type = type;
        for (Signal signal : Signal.values()) {
            handlers.put(signal, signalHandler);
        }
        device = DeviceBuilder.builder().name(type).build();
    }

    public SignalHandler handle(Signal signal, SignalHandler handler) {
        assert signal != null;
        assert handler != null;
        return handlers.put(signal, handler);
    }

    public void raise(Signal signal) {
        assert signal != null;
        SignalHandler handler = handlers.get(signal);
        if (handler == SignalHandlers.SIG_DFL) {
            handleDefaultSignal(signal);
        }
        else if (handler != SignalHandlers.SIG_IGN) {
            handler.handle(signal);
        }
    }

    protected void handleDefaultSignal(Signal signal) {
    }

    protected void echoSignal(Signal signal) {
        Attributes.ControlChar cc = null;
        switch (signal) {
            case INT:
                cc = Attributes.ControlChar.VINTR;
                break;
            case QUIT:
                cc = Attributes.ControlChar.VQUIT;
                break;
            case SUSP:
                cc = Attributes.ControlChar.VSUSP;
                break;
        }
        if (cc != null) {
            int vcc = getAttributes().getControlChar(cc);
            if (vcc > 0 && vcc < 32) {
                try {
                    output().write(new String(new char[]{'^', (char) (vcc + '@')}).getBytes());
                }
                catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to write out ^(C) - or other signals", e);
                }
            }
        }
    }

    public boolean echo() {
        return getAttributes().getLocalFlag(Attributes.LocalFlag.ECHO);
    }

    public boolean echo(boolean echo) {
        Attributes attr = getAttributes();
        boolean prev = attr.getLocalFlag(Attributes.LocalFlag.ECHO);
        if (prev != echo) {
            attr.setLocalFlag(Attributes.LocalFlag.ECHO, echo);
            setAttributes(attr);
        }
        return prev;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public Device device() {
        return device;
    }
}

