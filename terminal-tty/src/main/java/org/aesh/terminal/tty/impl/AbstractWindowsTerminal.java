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

        // Lazy holder — avoids calling WinConsoleNative in static init,
        // which would fail in GraalVM native-image at build time.
        private static final class OutputHandle {
            static final long CONSOLE = WinConsoleNative.getStdHandle(WinConsoleNative.STD_OUTPUT_HANDLE);
        }

        @Override
        public void accept(int[] input) {
            CharBuffer buffer = Encoder.toCharBuffer(input);
            char[] chars = buffer.array();
            if (!WinConsoleNative.writeConsole(OutputHandle.CONSOLE, chars, chars.length)) {
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
    /** Peeked byte for non-blocking peek support. READ_EXPIRED means no peeked byte. */
    private int peekedByte = READ_EXPIRED;

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
        attributes.setLocalFlag(Attributes.LocalFlag.ECHO, (mode & ENABLE_ECHO_INPUT) != 0);
        attributes.setLocalFlag(Attributes.LocalFlag.ICANON, (mode & ENABLE_LINE_INPUT) != 0);
        attributes.setLocalFlag(Attributes.LocalFlag.ISIG, (mode & ENABLE_PROCESSED_INPUT) != 0);
        return new Attributes(attributes);
    }

    /**
     * Set terminal attributes.
     *
     * @param attr the attributes to set
     */
    public void setAttributes(Attributes attr) {
        attributes.copy(attr);
        // Start from current mode and modify only the flags that Attributes
        // maps to, preserving all other Windows-specific flags (ENABLE_MOUSE_INPUT,
        // ENABLE_WINDOW_INPUT, ENABLE_QUICK_EDIT_MODE, ENABLE_EXTENDED_FLAGS, etc.)
        int mode = getConsoleMode();
        if (mode == -1) {
            mode = 0;
        }
        // Clear only the flags we manage — preserve everything else
        // (ENABLE_MOUSE_INPUT, ENABLE_QUICK_EDIT_MODE, ENABLE_EXTENDED_FLAGS, etc.)
        mode &= ~(ENABLE_ECHO_INPUT | ENABLE_LINE_INPUT | ENABLE_PROCESSED_INPUT);
        // Set them based on Attributes
        if (attr.getLocalFlag(Attributes.LocalFlag.ECHO)) {
            mode |= ENABLE_ECHO_INPUT;
        }
        if (attr.getLocalFlag(Attributes.LocalFlag.ICANON)) {
            mode |= ENABLE_LINE_INPUT;
        }
        if (attr.getLocalFlag(Attributes.LocalFlag.ISIG)) {
            mode |= ENABLE_PROCESSED_INPUT;
        }
        // Always enable ENABLE_WINDOW_INPUT for resize events
        mode |= ENABLE_WINDOW_INPUT;
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

    /**
     * Whether this terminal advertises non-blocking read/peek to callers.
     * <p>
     * Returns false because Windows peek goes through a PipedInputStream
     * which has pump thread latency — unreliable for timing-sensitive
     * escape sequence disambiguation. The poll-based pump loop uses
     * {@link #supportsNonBlockingWait()} directly via
     * {@link WinConsoleNative#supportsNonBlockingWait()}.
     */
    @Override
    public boolean supportsNonBlockingRead() {
        return false;
    }

    /**
     * Whether the pump can use WaitForSingleObject with timeout.
     * This is separate from {@link #supportsNonBlockingRead()} which
     * controls whether external callers (Readline) can use peek().
     *
     * @return {@code true} if the pump can use non-blocking wait
     */
    protected boolean supportsNonBlockingWait() {
        return WinConsoleNative.supportsNonBlockingWait();
    }

    @Override
    public int read(long timeoutMs) throws IOException {
        // Return peeked byte if available
        if (peekedByte != READ_EXPIRED) {
            int b = peekedByte;
            peekedByte = READ_EXPIRED;
            return b;
        }
        if (input.available() > 0) {
            return input.read();
        }
        if (timeoutMs == 0) {
            return READ_EXPIRED;
        }
        // Poll the pipe — use increasing sleep intervals to reduce CPU usage
        long deadline = timeoutMs > 0 ? System.currentTimeMillis() + timeoutMs : Long.MAX_VALUE;
        int sleepMs = 1;
        while (!closing) {
            try {
                Thread.sleep(sleepMs);
                // Back off to max 50ms for longer waits
                if (sleepMs < 50)
                    sleepMs = Math.min(sleepMs * 2, 50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return READ_EXPIRED;
            }
            if (input.available() > 0) {
                return input.read();
            }
            if (timeoutMs > 0 && System.currentTimeMillis() >= deadline) {
                return READ_EXPIRED;
            }
        }
        return -1;
    }

    @Override
    public int peek(long timeoutMs) throws IOException {
        if (peekedByte != READ_EXPIRED) {
            return peekedByte;
        }
        peekedByte = read(timeoutMs);
        return peekedByte;
    }

    @Override
    public int read(byte[] b, int off, int len, long timeoutMs) throws IOException {
        if (b == null)
            throw new NullPointerException();
        if (off < 0 || len < 0 || len > b.length - off)
            throw new IndexOutOfBoundsException();
        if (len == 0)
            return 0;

        // First byte: use timeout
        int first = read(timeoutMs);
        if (first < 0)
            return first; // EOF or READ_EXPIRED
        b[off] = (byte) first;

        // Remaining bytes: non-blocking
        int count = 1;
        while (count < len && input.available() > 0) {
            int next = input.read();
            if (next < 0)
                break;
            b[off + count] = (byte) next;
            count++;
        }
        return count;
    }

    public void close() {
        closing = true;
        pump.interrupt();
        // Close the slave input pipe so readers get EOF
        try {
            slaveInputPipe.close();
        } catch (IOException ignored) {
        }
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

    /** Default poll timeout (ms) for the non-blocking pump loop. */
    private static final int PUMP_TIMEOUT_MS = 100;

    /**
     * Pump thread that reads console input and processes it.
     * <p>
     * On Java 22+ (FFM), uses WaitForSingleObject with a timeout for clean
     * shutdown without needing to close the console handle. On Java 8-21 (JNI),
     * blocks on ReadConsoleInputW.
     */
    protected void pump() {
        try {
            if (WinConsoleNative.supportsNonBlockingWait()) {
                pumpWithTimeout();
            } else {
                pumpBlocking();
            }
        } catch (IOException e) {
            if (!closing) {
                LOGGER.log(Level.WARNING, "Error in WindowsStreamPump", e);
            }
        }
    }

    /**
     * Non-blocking pump loop using WaitForSingleObject with timeout (Java 22+ FFM).
     * <p>
     * After the first event arrives, drains all pending events before flushing
     * the pipe. This ensures multi-byte VT sequences (e.g., ESC [ I for focus
     * events) arrive at EventDecoder as complete chunks rather than byte-by-byte.
     */
    private void pumpWithTimeout() throws IOException {
        long inputHandle = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
        while (!closing) {
            int waitResult = WinConsoleNative.waitForSingleObject(inputHandle, PUMP_TIMEOUT_MS);
            if (waitResult == WinConsoleNative.WAIT_TIMEOUT) {
                // Timeout — loop back to check closing flag
                continue;
            }
            if (waitResult == WinConsoleNative.WAIT_FAILED) {
                break;
            }
            // WAIT_OBJECT_0: input available — read first event and drain remaining
            processInputByteNoFlush(readConsoleInput());
            // Drain all remaining pending events without waiting
            int pending = WinConsoleNative.getNumberOfConsoleInputEvents(inputHandle);
            while (pending > 0 && !closing) {
                processInputByteNoFlush(readConsoleInput());
                pending--;
            }
            slaveInputPipe.flush();
        }
    }

    /**
     * Blocking pump loop (Java 8-21 JNI path).
     */
    private void pumpBlocking() throws IOException {
        while (!closing) {
            processInputByte(readConsoleInput());
        }
    }

    private void processInputByte(byte[] buf) throws IOException {
        processInputByteNoFlush(buf);
        slaveInputPipe.flush();
    }

    private void processInputByteNoFlush(byte[] buf) throws IOException {
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
    }
}
