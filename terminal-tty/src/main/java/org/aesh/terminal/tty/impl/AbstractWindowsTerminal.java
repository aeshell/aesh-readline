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

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.terminal.Attributes;
import org.aesh.terminal.io.Encoder;
import org.aesh.terminal.tty.Capability;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.tty.Size;
import org.aesh.terminal.tty.utils.ShutdownHooks;
import org.aesh.terminal.tty.utils.Signals;
import org.aesh.terminal.utils.Curses;
import org.aesh.terminal.utils.LoggerUtil;

abstract class AbstractWindowsTerminal extends AbstractTerminal {

    private static class ConsoleOutput implements Consumer<int[]> {

        private static final Logger LOGGER = LoggerUtil.getLogger(AbstractWindowsTerminal.class.getName());
        private static final long console = WinConsoleNative.getStdHandle(WinConsoleNative.STD_OUTPUT_HANDLE);

        @Override
        public void accept(int[] input) {
            CharBuffer buffer = Encoder.toCharBuffer(input);
            char[] chars = buffer.array();
            if (!WinConsoleNative.writeConsole(console, chars, chars.length)) {
                LOGGER.log(Level.WARNING, "Failed to write out.");
            }
        }

    }

    private static final int PIPE_SIZE = 1024;

    /** Enable processed input mode. */
    protected static final int ENABLE_PROCESSED_INPUT = 0x0001;
    /** Enable line input mode. */
    protected static final int ENABLE_LINE_INPUT = 0x0002;
    /** Enable echo input mode. */
    protected static final int ENABLE_ECHO_INPUT = 0x0004;
    /** Enable window input mode. */
    protected static final int ENABLE_WINDOW_INPUT = 0x0008;
    /** Enable mouse input mode. */
    protected static final int ENABLE_MOUSE_INPUT = 0x0010;
    /** Enable insert mode. */
    protected static final int ENABLE_INSERT_MODE = 0x0020;
    /** Enable quick edit mode. */
    protected static final int ENABLE_QUICK_EDIT_MODE = 0x0040;
    /** Enable virtual terminal input (VT sequences for special keys and mouse). */
    protected static final int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;

    /** Slave input pipe. */
    protected final OutputStream slaveInputPipe;
    /** Terminal input stream. */
    protected final InputStream input;
    /** Terminal output stream. */
    protected final OutputStream output;
    /** Print writer for output. */
    protected final PrintWriter writer;
    /** Map of native signal handlers. */
    protected final Map<Signal, Object> nativeHandlers = new HashMap<>();
    /** Shutdown hook task for cleanup. */
    protected final ShutdownHooks.Task closer;
    /** Terminal attributes. */
    protected final Attributes attributes = new Attributes();
    /** Input pump thread. */
    protected final Thread pump;

    private volatile boolean closing;
    private final ConsoleOutput cpConsumer;
    /** Whether VT input mode was successfully enabled on the input handle. */
    protected boolean vtInputEnabled;
    /** Original console input mode, saved for restoration on close. */
    protected int originalInputMode = -1;

    AbstractWindowsTerminal(boolean consumeCP, OutputStream output, String name, boolean nativeSignals,
            SignalHandler signalHandler) throws IOException {
        super(name, "windows", signalHandler);
        PipedInputStream input = new PipedInputStream(PIPE_SIZE);
        this.slaveInputPipe = new PipedOutputStream(input);
        this.input = new FilterInputStream(input) {
        };
        this.cpConsumer = consumeCP ? new ConsoleOutput() : null;
        this.output = output;
        String encoding = getConsoleEncoding();
        if (encoding == null) {
            encoding = Charset.defaultCharset().name();
        }
        this.writer = new PrintWriter(new OutputStreamWriter(this.output, encoding));
        // Attributes
        attributes.setLocalFlag(Attributes.LocalFlag.ISIG, true);
        attributes.setControlChar(Attributes.ControlChar.VINTR, ctrl('C'));
        attributes.setControlChar(Attributes.ControlChar.VEOF, ctrl('D'));
        attributes.setControlChar(Attributes.ControlChar.VSUSP, ctrl('Z'));
        // Handle signals
        if (nativeSignals) {
            for (final Signal signal : Signal.values()) {
                nativeHandlers.put(signal,
                        Signals.register(signal.name(), () -> raise(signal)));
            }
        }
        pump = new Thread(this::pump, "WindowsStreamPump");
        pump.setDaemon(true);
        pump.start();
        closer = this::close;
        ShutdownHooks.add(closer);
    }

    @Override
    public Consumer<int[]> getCodePointConsumer() {
        return cpConsumer;
    }

    @Override
    protected void handleDefaultSignal(Signal signal) {
        Object handler = nativeHandlers.get(signal);
        if (handler != null) {
            Signals.invokeHandler(signal.name(), handler);
        }
    }

    /**
     * Get the console encoding based on the console output code page.
     *
     * @return the charset name, or null if not supported
     */
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

    /**
     * Get the console output code page.
     *
     * @return the code page number
     */
    protected abstract int getConsoleOutputCP();

    /**
     * Get the print writer for output.
     *
     * @return the print writer
     */
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

    /**
     * Set terminal attributes.
     *
     * @param attr the attributes to set
     */
    public void setAttributes(Attributes attr) {
        attributes.copy(attr);
        int mode = 0;
        if (attr.getLocalFlag(Attributes.LocalFlag.ECHO)) {
            mode |= ENABLE_ECHO_INPUT;
        }
        if (attr.getLocalFlag(Attributes.LocalFlag.ICANON)) {
            mode |= ENABLE_LINE_INPUT;
        }
        if (vtInputEnabled) {
            mode |= ENABLE_VIRTUAL_TERMINAL_INPUT;
        }
        setConsoleMode(mode);
    }

    /**
     * Convert a character to its control code equivalent.
     *
     * @param key the character
     * @return the control code
     */
    protected int ctrl(char key) {
        return (Character.toUpperCase(key) & 0x1f);
    }

    /**
     * Get the current console mode.
     *
     * @return the console mode flags
     */
    protected abstract int getConsoleMode();

    /**
     * Set the console mode.
     *
     * @param mode the mode flags to set
     */
    protected abstract void setConsoleMode(int mode);

    /**
     * Set the terminal size. Not supported on Windows.
     *
     * @param size the size to set
     * @throws UnsupportedOperationException always
     */
    public void setSize(Size size) {
        throw new UnsupportedOperationException("Can not resize windows terminal");
    }

    public void close() {
        closing = true;
        pump.interrupt();
        // Restore original console input mode before closing
        if (originalInputMode != -1) {
            setConsoleMode(originalInputMode);
        }
        ShutdownHooks.remove(closer);
        for (Map.Entry<Signal, Object> entry : nativeHandlers.entrySet()) {
            Signals.unregister(entry.getKey().name(), entry.getValue());
        }
        // Flush but do not close — the output stream (typically System.out)
        // is not owned by this terminal and may still be used after close.
        writer.flush();
    }

    /**
     * Read console input from the Windows console.
     *
     * @return the input bytes
     */
    protected abstract byte[] readConsoleInput();

    /**
     * Get the escape sequence for a Windows virtual key code.
     *
     * @param keyCode the Windows virtual key code
     * @return the escape sequence, or null if not mapped
     */
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

    /**
     * Get the terminal sequence for a capability.
     *
     * @param cap the capability
     * @return the sequence string, or null if not available
     */
    protected String getSequence(Capability cap) {
        String str = device.getStringCapability(cap);
        if (str != null) {
            StringWriter sw = new StringWriter();
            Curses.tputs(sw, str);
            return sw.toString();
        }
        return null;
    }

    /**
     * Pump thread that reads console input and processes it.
     */
    protected void pump() {
        try {
            while (!closing) {
                processInputByte(readConsoleInput());
            }
        } catch (IOException e) {
            if (!closing) {
                LOGGER.log(Level.WARNING, "Error in WindowsStreamPump", e);
            }
        }
    }

    private void processInputByte(byte[] buf) throws IOException {
        for (byte b : buf) {
            if (attributes.getLocalFlag(Attributes.LocalFlag.ISIG)) {
                if ((int) b == attributes.getControlChar(Attributes.ControlChar.VINTR)) {
                    raise(Signal.INT);
                } else if ((int) b == attributes.getControlChar(Attributes.ControlChar.VQUIT)) {
                    raise(Signal.QUIT);
                } else if ((int) b == attributes.getControlChar(Attributes.ControlChar.VSUSP)) {
                    raise(Signal.SUSP);
                } else if ((int) b == attributes.getControlChar(Attributes.ControlChar.VSTATUS)) {
                    raise(Signal.INFO);
                }
            }
            if ((int) b == '\r') {
                if (attributes.getInputFlag(Attributes.InputFlag.ICRNL)) {
                    slaveInputPipe.write('\n');
                } else
                    slaveInputPipe.write((int) b);
            } else if ((int) b == '\n' && attributes.getInputFlag(Attributes.InputFlag.INLCR)) {
                slaveInputPipe.write('\r');
            } else {
                slaveInputPipe.write((int) b);
            }
        }
        slaveInputPipe.flush();
    }
}
