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

import org.aesh.terminal.Attributes;
import org.aesh.utils.Curses;
import org.aesh.readline.terminal.utils.ShutdownHooks;
import org.aesh.readline.terminal.utils.Signals;
import org.aesh.terminal.tty.Capability;

import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.IOError;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;

abstract class AbstractWindowsTerminal extends AbstractTerminal {

    private static final int PIPE_SIZE = 1024;

    protected static final int ENABLE_PROCESSED_INPUT = 0x0001;
    protected static final int ENABLE_LINE_INPUT      = 0x0002;
    protected static final int ENABLE_ECHO_INPUT      = 0x0004;
    protected static final int ENABLE_WINDOW_INPUT    = 0x0008;
    protected static final int ENABLE_MOUSE_INPUT     = 0x0010;
    protected static final int ENABLE_INSERT_MODE     = 0x0020;
    protected static final int ENABLE_QUICK_EDIT_MODE = 0x0040;

    protected final OutputStream slaveInputPipe;
    protected final InputStream input;
    protected final OutputStream output;
    protected final PrintWriter writer;
    protected final Map<Signal, Object> nativeHandlers = new HashMap<>();
    protected final ShutdownHooks.Task closer;
    protected final Attributes attributes = new Attributes();
    protected final Thread pump;

    private volatile boolean closing;

    public AbstractWindowsTerminal(OutputStream output, String name, boolean nativeSignals, SignalHandler signalHandler) throws IOException {
        super(name, "windows", signalHandler);
        PipedInputStream input = new PipedInputStream(PIPE_SIZE);
        this.slaveInputPipe = new PipedOutputStream(input);
        this.input = new FilterInputStream(input) {};
        this.output = output;
        String encoding = getConsoleEncoding();
        if (encoding == null) {
            encoding = Charset.defaultCharset().name();
        }
        this.writer = new PrintWriter(new OutputStreamWriter(output, encoding));
        // Attributes
        attributes.setLocalFlag(Attributes.LocalFlag.ISIG, true);
        attributes.setControlChar(Attributes.ControlChar.VINTR, ctrl('C'));
        attributes.setControlChar(Attributes.ControlChar.VEOF,  ctrl('D'));
        attributes.setControlChar(Attributes.ControlChar.VSUSP, ctrl('Z'));
        // Handle signals
        if (nativeSignals) {
            for (final Signal signal : Signal.values()) {
                nativeHandlers.put(signal,
                        Signals.register(signal.name(), () -> raise(signal)));
            }
        }
        pump = new Thread(this::pump, "WindowsStreamPump");
        pump.start();
        closer = this::close;
        ShutdownHooks.add(closer);
    }

    @Override
    protected void handleDefaultSignal(Signal signal) {
        Object handler = nativeHandlers.get(signal);
        if (handler != null) {
            Signals.invokeHandler(signal.name(), handler);
        }
    }

    protected String getConsoleEncoding() {
        int codepage = getConsoleOutputCP();
        //http://docs.oracle.com/javase/6/docs/technotes/guides/intl/encoding.doc.html
        String charsetMS = "ms" + codepage;
        if (java.nio.charset.Charset.isSupported(charsetMS)) {
            return charsetMS;
        }
        String charsetCP = "cp" + codepage;
        if (java.nio.charset.Charset.isSupported(charsetCP)) {
            return charsetCP;
        }
        return null;
    }

    protected abstract int getConsoleOutputCP();

    public PrintWriter writer() {
        return writer;
    }

    @Override
    public InputStream input() {
        return input;
    }

    @Override
    public OutputStream output() {
        return output;
    }

    public Attributes getAttributes() {
        int mode = getConsoleMode();
        if ((mode & ENABLE_ECHO_INPUT) != 0) {
            attributes.setLocalFlag(Attributes.LocalFlag.ECHO, true);
        }
        if ((mode & ENABLE_LINE_INPUT) != 0) {
            attributes.setLocalFlag(Attributes.LocalFlag.ICANON, true);
        }
        return new Attributes(attributes);
    }

    public void setAttributes(Attributes attr) {
        attributes.copy(attr);
        int mode = 0;
        if (attr.getLocalFlag(Attributes.LocalFlag.ECHO)) {
            mode |= ENABLE_ECHO_INPUT;
        }
        if (attr.getLocalFlag(Attributes.LocalFlag.ICANON)) {
            mode |= ENABLE_LINE_INPUT;
        }
        setConsoleMode(mode);
    }

    protected int ctrl(char key) {
        return (Character.toUpperCase(key) & 0x1f);
    }

    protected abstract int getConsoleMode();

    protected abstract void setConsoleMode(int mode);

    public void setSize(Size size) {
        throw new UnsupportedOperationException("Can not resize windows terminal");
    }

    public void close() throws IOException {
        closing = true;
        pump.interrupt();
        ShutdownHooks.remove(closer);
        for (Map.Entry<Signal, Object> entry : nativeHandlers.entrySet()) {
            Signals.unregister(entry.getKey().name(), entry.getValue());
        }
        writer.close();
    }

    protected abstract byte[] readConsoleInput();

    protected String getEscapeSequence(short keyCode) {
        String escapeSequence = null;
        switch (keyCode) {
            case 0x08: // VK_BACK BackSpace
                escapeSequence = getSequence(Capability.key_backspace);
                break;
            case 0x21: // VK_PRIOR PageUp
                escapeSequence = getSequence(Capability.key_ppage);
                break;
            case 0x22: // VK_NEXT PageDown
                escapeSequence = getSequence(Capability.key_npage);
                break;
            case 0x23: // VK_END
                escapeSequence = getSequence(Capability.key_end);
                break;
            case 0x24: // VK_HOME
                escapeSequence = getSequence(Capability.key_home);
                break;
            case 0x25: // VK_LEFT
                escapeSequence = getSequence(Capability.key_left);
                break;
            case 0x26: // VK_UP
                escapeSequence = getSequence(Capability.key_up);
                break;
            case 0x27: // VK_RIGHT
                escapeSequence = getSequence(Capability.key_right);
                break;
            case 0x28: // VK_DOWN
                escapeSequence = getSequence(Capability.key_down);
                break;
            case 0x2D: // VK_INSERT
                escapeSequence = getSequence(Capability.key_ic);
                break;
            case 0x2E: // VK_DELETE
                escapeSequence = getSequence(Capability.key_dc);
                break;
            case 0x70: // VK_F1
                escapeSequence = getSequence(Capability.key_f1);
                break;
            case 0x71: // VK_F2
                escapeSequence = getSequence(Capability.key_f2);
                break;
            case 0x72: // VK_F3
                escapeSequence = getSequence(Capability.key_f3);
                break;
            case 0x73: // VK_F4
                escapeSequence = getSequence(Capability.key_f4);
                break;
            case 0x74: // VK_F5
                escapeSequence = getSequence(Capability.key_f5);
                break;
            case 0x75: // VK_F6
                escapeSequence = getSequence(Capability.key_f6);
                break;
            case 0x76: // VK_F7
                escapeSequence = getSequence(Capability.key_f7);
                break;
            case 0x77: // VK_F8
                escapeSequence = getSequence(Capability.key_f8);
                break;
            case 0x78: // VK_F9
                escapeSequence = getSequence(Capability.key_f9);
                break;
            case 0x79: // VK_F10
                escapeSequence = getSequence(Capability.key_f10);
                break;
            case 0x7A: // VK_F11
                escapeSequence = getSequence(Capability.key_f11);
                break;
            case 0x7B: // VK_F12
                escapeSequence = getSequence(Capability.key_f12);
                break;
            default:
                break;
        }
        return escapeSequence;
    }

    protected String getSequence(Capability cap) {
        String str = device.getStringCapability(cap);
        if (str != null) {
            StringWriter sw = new StringWriter();
            try {
                Curses.tputs(sw, str);
            } catch (IOException e) {
                throw new IOError(e);
            }
            return sw.toString();
        }
        return null;
    }

    protected void pump() {
        try {
            while (!closing) {
                processInputByte(readConsoleInput());
            }
        } catch (IOException e) {
            if (!closing) {
                LOGGER.log(Level.WARNING,"Error in WindowsStreamPump", e);
            }
        }
    }

    private void processInputByte(byte[] buf) throws IOException {
        for(byte b : buf) {
            int c = b;
            if (attributes.getLocalFlag(Attributes.LocalFlag.ISIG)) {
                if (c == attributes.getControlChar(Attributes.ControlChar.VINTR)) {
                    raise(Signal.INT);
                }
                else if (c == attributes.getControlChar(Attributes.ControlChar.VQUIT)) {
                    raise(Signal.QUIT);
                }
                else if (c == attributes.getControlChar(Attributes.ControlChar.VSUSP)) {
                    raise(Signal.SUSP);
                }
                else if (c == attributes.getControlChar(Attributes.ControlChar.VSTATUS)) {
                    raise(Signal.INFO);
                }
            }
            if (c == '\r') {
                if (attributes.getInputFlag(Attributes.InputFlag.ICRNL)) {
                    slaveInputPipe.write('\n');
                }
                else
                    slaveInputPipe.write(c);
            }
            else if (c == '\n' && attributes.getInputFlag(Attributes.InputFlag.INLCR)) {
                slaveInputPipe.write('\r');
            }
            else {
                slaveInputPipe.write(c);
            }
        }
        slaveInputPipe.flush();
    }
}

