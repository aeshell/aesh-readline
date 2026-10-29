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
import java.nio.charset.StandardCharsets;

import org.aesh.terminal.detect.BoundedProbeTransport;
import org.aesh.terminal.detect.ProbeDeadline;
import org.aesh.terminal.detect.TerminalProbeSession;

/**
 * JNI-based probe transport for native Windows consoles on runtimes
 * without the Java 22 foreign API (#389).
 * <p>
 * Mirrors the FFM {@code Win32ProbeTransport} session shape over the JNI
 * console bindings: query bytes go out via {@code WriteConsoleW} on the
 * output handle, responses come back as console input records (terminal
 * answers arrive as key-down {@code KEY_EVENT} records with characters).
 * Raw mode is built from scratch ({@code ENABLE_WINDOW_INPUT} plus
 * {@code ENABLE_EXTENDED_FLAGS}): no echo, no line input, no mouse, no
 * quick-edit, and deliberately no virtual-terminal input. The output
 * handle gains virtual-terminal processing for the session so the
 * backend accepts the query sequences.
 * <p>
 * The bridge loads lazily: {@link #isAvailable()} answers from OS facts
 * without touching native code, and opening without a console declines
 * with {@link IOException} instead of leaking a linkage error.
 */
public final class Win32JniProbeTransport implements BoundedProbeTransport {

    /** Inter-byte response timeout in milliseconds (matches VTIME=5). */
    private static final int RESPONSE_TIMEOUT_MS = 500;

    /** Maximum input records drained per wait wakeup. */
    private static final int MAX_DRAIN_EVENTS = 64;

    public Win32JniProbeTransport() {
    }

    @Override
    public boolean isAvailable() {
        if (!WinConsoleNativeJni.isSupported()) {
            return false;
        }
        try {
            long input = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
            if (input == WinConsoleNative.INVALID_HANDLE) {
                return false;
            }
            return WinConsoleNative.getConsoleMode(input) != -1;
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
            return false;
        }
    }

    @Override
    public TerminalProbeSession open() throws IOException {
        try {
            return new Win32JniProbeSession();
        } catch (UnsatisfiedLinkError e) {
            throw new IOException("Win32 console bridge not available: " + e.getMessage());
        }
    }

    @Override
    public TerminalProbeSession open(ProbeDeadline deadline) throws IOException {
        if (deadline == null || deadline.isExpired()) {
            throw new IOException("probe deadline expired");
        }
        try {
            return new Win32JniProbeSession();
        } catch (UnsatisfiedLinkError e) {
            throw new IOException("Win32 console bridge not available: " + e.getMessage());
        }
    }

    /**
     * The raw console input word for probe sessions: window input plus
     * the extended flag that Quick Edit needs to switch off (never set
     * here), and deliberately no virtual-terminal input. Package-visible
     * so the composed word is unit-testable without a console.
     *
     * @return the console input mode word
     */
    static int rawInputMode() {
        return ENABLE_WINDOW_INPUT | ENABLE_EXTENDED_FLAGS;
    }

    /** Matches {@code ENABLE_WINDOW_INPUT} in the Windows console API. */
    private static final int ENABLE_WINDOW_INPUT = 0x0008;
    /** Matches {@code ENABLE_EXTENDED_FLAGS} in the Windows console API. */
    private static final int ENABLE_EXTENDED_FLAGS = 0x0080;

    /**
     * Appends one input record's character (if any) to the sink. Only
     * key-down {@code KEY_EVENT} records with a non-zero character
     * contribute bytes — the same filter terminal responses rely on in
     * the interactive pump. Key-up events, virtual-key-only presses,
     * mouse and window-size events are consumed and ignored.
     * Package-visible so the filter is unit-testable without a console.
     *
     * @param record the decoded record, or null on read failure
     * @param sink collects response characters
     * @return true when a record was consumed, false on read failure
     */
    static boolean appendRecordChars(int[] record, StringBuilder sink) {
        if (record == null || record.length < 6) {
            return false;
        }
        if (record[0] == WinConsoleNative.KEY_EVENT && record[1] != 0) {
            char c = (char) record[4];
            if (c != 0) {
                sink.append(c);
            }
        }
        return true;
    }

    private static final class Win32JniProbeSession implements TerminalProbeSession {
        private final long inputHandle;
        private final long outputHandle;
        private final int savedInputMode;
        private final int savedOutputMode;
        private final boolean outputModeChanged;
        private final Win32JniProbeInput input;
        private boolean closed;

        Win32JniProbeSession() throws IOException {
            long in;
            try {
                in = WinConsoleNative.getStdHandle(WinConsoleNative.STD_INPUT_HANDLE);
            } catch (UnsatisfiedLinkError e) {
                throw new IOException("Win32 console bridge not available: " + e.getMessage());
            }
            if (in == WinConsoleNative.INVALID_HANDLE) {
                throw new IOException("No console input handle");
            }
            int inMode = WinConsoleNative.getConsoleMode(in);
            if (inMode == -1) {
                throw new IOException("Not a console (GetConsoleMode failed)");
            }
            long out = WinConsoleNative.getStdHandle(WinConsoleNative.STD_OUTPUT_HANDLE);
            if (out == WinConsoleNative.INVALID_HANDLE) {
                throw new IOException("No console output handle");
            }
            int outMode = WinConsoleNative.getConsoleMode(out);
            if (outMode == -1) {
                throw new IOException("Not a console (output GetConsoleMode failed)");
            }
            if (!WinConsoleNative.setConsoleMode(in, rawInputMode())) {
                throw new IOException("Failed to set console raw mode");
            }
            boolean vtChanged = false;
            if ((outMode & WinConsoleNative.ENABLE_VIRTUAL_TERMINAL_PROCESSING) == 0) {
                vtChanged = WinConsoleNative.setConsoleMode(out,
                        outMode | WinConsoleNative.ENABLE_VIRTUAL_TERMINAL_PROCESSING);
            }
            this.inputHandle = in;
            this.outputHandle = out;
            this.savedInputMode = inMode;
            this.savedOutputMode = outMode;
            this.outputModeChanged = vtChanged;
            this.input = new Win32JniProbeInput();
        }

        @Override
        public void write(byte[] data) throws IOException {
            if (closed) {
                throw new IOException("Session is closed");
            }
            if (data == null || data.length == 0) {
                return;
            }
            char[] chars = new String(data, StandardCharsets.UTF_8).toCharArray();
            int offset = 0;
            while (offset < chars.length) {
                int remaining = chars.length - offset;
                char[] window = new char[remaining];
                System.arraycopy(chars, offset, window, 0, remaining);
                if (!WinConsoleNative.writeConsole(outputHandle, window, remaining)) {
                    throw new IOException("WriteConsoleW failed");
                }
                offset += remaining;
            }
        }

        @Override
        public InputStream input() {
            return input;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            WinConsoleNative.setConsoleMode(inputHandle, savedInputMode);
            if (outputModeChanged) {
                WinConsoleNative.setConsoleMode(outputHandle, savedOutputMode);
            }
        }

        /**
         * Response stream over console input records. Waits up to 500ms
         * between byte bursts (the {@code VTIME=5} equivalent): a wait
         * timeout ends the response, matching the POSIX contract where a
         * read returning {@code 0} ends the response.
         */
        private final class Win32JniProbeInput extends InputStream {

            private byte[] stash = new byte[0];
            private int stashOffset;

            Win32JniProbeInput() {
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (b == null) {
                    throw new NullPointerException("buffer is null");
                }
                if (off < 0 || len < 0 || off + len > b.length) {
                    throw new IndexOutOfBoundsException("invalid offset/length");
                }
                if (len == 0) {
                    return 0;
                }
                if (closed) {
                    return -1;
                }
                if (stashOffset < stash.length) {
                    return drainStash(b, off, len);
                }
                StringBuilder sink = new StringBuilder();
                while (sink.length() == 0) {
                    int wait = WinConsoleNative.waitForSingleObject(inputHandle, RESPONSE_TIMEOUT_MS);
                    if (wait != WinConsoleNative.WAIT_OBJECT_0) {
                        return 0;
                    }
                    drainPending(sink);
                }
                stash = sink.toString().getBytes(StandardCharsets.UTF_8);
                stashOffset = 0;
                return drainStash(b, off, len);
            }

            private int drainStash(byte[] b, int off, int len) {
                int n = Math.min(len, stash.length - stashOffset);
                System.arraycopy(stash, stashOffset, b, off, n);
                stashOffset += n;
                return n;
            }

            private void drainPending(StringBuilder sink) {
                int pending = WinConsoleNative.getNumberOfConsoleInputEvents(inputHandle);
                if (pending <= 0) {
                    // Signaled but nothing countable (or error): attempt one
                    // record so a single response is never missed, then stop.
                    appendRecordChars(WinConsoleNative.readConsoleInputEvent(inputHandle), sink);
                    return;
                }
                int budget = Math.min(pending, MAX_DRAIN_EVENTS);
                for (int i = 0; i < budget; i++) {
                    if (!appendRecordChars(WinConsoleNative.readConsoleInputEvent(inputHandle), sink)) {
                        break;
                    }
                }
            }

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                int n = read(one, 0, 1);
                if (n <= 0) {
                    return -1;
                }
                return one[0] & 0xFF;
            }

            @Override
            public void close() {
                // Owned by the session; session close restores console modes.
            }
        }
    }
}
