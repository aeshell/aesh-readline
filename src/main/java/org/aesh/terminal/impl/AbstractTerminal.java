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
package org.aesh.terminal.impl;

import org.aesh.tty.Signal;
import org.aesh.terminal.Attributes;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.utils.Curses;
import org.aesh.terminal.utils.InfoCmp;
import org.aesh.tty.Capability;
import org.aesh.util.LoggerUtil;

import java.io.IOError;
import java.io.IOException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractTerminal implements Terminal {

    protected final Logger LOGGER = LoggerUtil.getLogger(getClass().getName());

    protected final String name;
    protected final String type;
    protected final Map<Signal, SignalHandler> handlers = new HashMap<>();
    protected final Set<Capability> bools = new HashSet<>();
    protected final Map<Capability, Integer> ints = new HashMap<>();
    protected final Map<Capability, String> strings = new HashMap<>();

    public AbstractTerminal(String name, String type) throws IOException {
        this.name = name;
        this.type = type;
        for (Signal signal : Signal.values()) {
            handlers.put(signal, SignalHandler.SIG_DFL);
        }
    }

    public SignalHandler handle(Signal signal, SignalHandler handler) {
        assert signal != null;
        assert handler != null;
        return handlers.put(signal, handler);
    }

    public void raise(Signal signal) {
        assert signal != null;
        SignalHandler handler = handlers.get(signal);
        if (handler == SignalHandler.SIG_DFL) {
            handleDefaultSignal(signal);
        } else if (handler != SignalHandler.SIG_IGN) {
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
            case TSTP:
                cc = Attributes.ControlChar.VSUSP;
                break;
        }
        if (cc != null) {
            int vcc = getAttributes().getControlChar(cc);
            if (vcc > 0 && vcc < 32) {
                writer().write(new char[]{'^', (char) (vcc + '@')}, 0, 2);
            }
        }
    }

    public Attributes enterRawMode() {
        Attributes prvAttr = getAttributes();
        Attributes newAttr = new Attributes(prvAttr);
        newAttr.setLocalFlags(EnumSet.of(Attributes.LocalFlag.ICANON, Attributes.LocalFlag.ECHO, Attributes.LocalFlag.IEXTEN), false);
        newAttr.setInputFlags(EnumSet.of(Attributes.InputFlag.IXON, Attributes.InputFlag.ICRNL, Attributes.InputFlag.INLCR), false);
        newAttr.setControlChar(Attributes.ControlChar.VMIN, 1);
        newAttr.setControlChar(Attributes.ControlChar.VTIME, 0);
        setAttributes(newAttr);
        return prvAttr;
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

    public void flush() {
        writer().flush();
    }

    public boolean puts(Capability capability, Object... params) {
        String str = getStringCapability(capability);
        if (str == null) {
            return false;
        }
        try {
            Curses.tputs(writer(), str, params);
        } catch (IOException e) {
            throw new IOError(e);
        }
        return true;
    }

    public boolean getBooleanCapability(Capability capability) {
        return bools.contains(capability);
    }

    public Integer getNumericCapability(Capability capability) {
        return ints.get(capability);
    }

    public String getStringCapability(Capability capability) {
        return strings.get(capability);
    }

    protected void parseInfoCmp() {
        String capabilities = null;
        if (type != null) {
            try {
                capabilities = InfoCmp.getInfoCmp(type);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Unable to retrieve infocmp for type " + type, e);
            }
        }
        if (capabilities == null) {
            capabilities = InfoCmp.ANSI_CAPS;
        }
        InfoCmp.parseInfoCmp(capabilities, bools, ints, strings);
    }

}
