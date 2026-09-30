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
package org.aesh.terminal.detect;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * FFM-based probe transport for native Windows consoles (Java 22+).
 * <p>
 * Uses the Win32 Console API directly: query bytes go out via
 * {@code WriteConsoleW} on the output handle, responses come back as
 * console input records (terminal answers arrive as key-down
 * {@code KEY_EVENT} records with characters — the same path interactive
 * terminal queries already use). Raw mode is built from scratch
 * ({@code ENABLE_WINDOW_INPUT} plus {@code ENABLE_EXTENDED_FLAGS}):
 * no echo, no line input, no mouse, no quick-edit (which would block
 * reads during text selection), and
 * deliberately no {@code ENABLE_VIRTUAL_TERMINAL_INPUT} (which duplicates
 * key events). The output handle gains
 * {@code ENABLE_VIRTUAL_TERMINAL_PROCESSING} for the session so the
 * backend accepts the query sequences.
 * <p>
 * Loaded reflectively by {@link TerminalColorQuery} so non-Windows and
 * pre-22 runtimes never touch it. Under Cygwin/MSYS2 (mintty) there is no
 * Windows console on stdin, so this transport reports unavailable and the
 * POSIX {@code stty} path applies instead.
 */
final class Win32ProbeTransport implements TerminalProbeTransport {

    /** Inter-byte response timeout in milliseconds (matches VTIME=5). */
    private static final int RESPONSE_TIMEOUT_MS = 500;

    Win32ProbeTransport() {
    }

    @Override
    public boolean isAvailable() {
        if (!Win32Probe.IS_WINDOWS) {
            return false;
        }
        if (!Win32Probe.isNativeAccessEnabled()) {
            return false;
        }
        long input = Win32Probe.getStdHandle(Win32Probe.STD_INPUT_HANDLE);
        if (input == Win32Probe.INVALID_HANDLE) {
            return false;
        }
        return Win32Probe.getConsoleMode(input) != -1;
    }

    @Override
    public TerminalProbeSession open() throws IOException {
        return new Win32ProbeSession();
    }

    /**
     * Check native access without loading any FFM API classes.
     * Called reflectively before instantiation so callers can skip the
     * transport cheaply.
     *
     * @return true if native access is enabled for this module
     */
    static boolean isNativeAccessEnabled() {
        return Win32ProbeTransport.class.getModule().isNativeAccessEnabled();
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
        return Win32Probe.ENABLE_WINDOW_INPUT | Win32Probe.ENABLE_EXTENDED_FLAGS;
    }

    private static final class Win32ProbeSession implements TerminalProbeSession {        private final long inputHandle;
        private final long outputHandle;
        private final int savedInputMode;
        private final int savedOutputMode;
        private final boolean outputModeChanged;
        private final Win32ProbeInput input;
        private boolean closed;

        Win32ProbeSession() throws IOException {
            long in = Win32Probe.getStdHandle(Win32Probe.STD_INPUT_HANDLE);
            if (in == Win32Probe.INVALID_HANDLE) {
                throw new IOException("No console input handle");
            }
            int inMode = Win32Probe.getConsoleMode(in);
            if (inMode == -1) {
                throw new IOException("Not a console (GetConsoleMode failed)");
            }
            long out = Win32Probe.getStdHandle(Win32Probe.STD_OUTPUT_HANDLE);
            if (out == Win32Probe.INVALID_HANDLE) {
                throw new IOException("No console output handle");
            }
            int outMode = Win32Probe.getConsoleMode(out);
            if (outMode == -1) {
                throw new IOException("Not a console (output GetConsoleMode failed)");
            }
            if (!Win32Probe.setConsoleMode(in, rawInputMode())) {
                throw new IOException("Failed to set console raw mode");
            }
            boolean vtChanged = false;
            if ((outMode & Win32Probe.ENABLE_VIRTUAL_TERMINAL_PROCESSING) == 0) {
                vtChanged = Win32Probe.setConsoleMode(out,
                        outMode | Win32Probe.ENABLE_VIRTUAL_TERMINAL_PROCESSING);
            }
            this.inputHandle = in;
            this.outputHandle = out;
            this.savedInputMode = inMode;
            this.savedOutputMode = outMode;
            this.outputModeChanged = vtChanged;
            this.input = new Win32ProbeInput();
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
                char[] window;
                if (offset == 0) {
                    window = chars;
                } else {
                    int remaining = chars.length - offset;
                    window = new char[remaining];
                    System.arraycopy(chars, offset, window, 0, remaining);
                }
                int written = Win32Probe.writeConsole(outputHandle, window);
                if (written <= 0) {
                    throw new IOException("WriteConsoleW failed");
                }
                offset += written;
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
            Win32Probe.setConsoleMode(inputHandle, savedInputMode);
            if (outputModeChanged) {
                Win32Probe.setConsoleMode(outputHandle, savedOutputMode);
            }
        }

        /**
         * Response stream over console input records. Waits up to 500ms
         * between byte bursts (the {@code VTIME=5} equivalent): a wait
         * timeout ends the response, matching the POSIX contract where a
         * read returning {@code 0} ends the response.
         */
        private final class Win32ProbeInput extends InputStream {

            private byte[] stash = new byte[0];
            private int stashOffset;

            Win32ProbeInput() {
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
                    int wait = Win32Probe.waitForInput(inputHandle, RESPONSE_TIMEOUT_MS);
                    if (wait != Win32Probe.WAIT_OBJECT_0) {
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
                int pending = Win32Probe.pendingInputEvents(inputHandle);
                if (pending <= 0) {
                    // Signaled but nothing countable (or error): attempt one
                    // record so a single response is never missed, then stop.
                    Win32Probe.readRecordChars(inputHandle, sink);
                    return;
                }
                int budget = Math.min(pending, Win32Probe.MAX_DRAIN_EVENTS);
                for (int i = 0; i < budget; i++) {
                    if (!Win32Probe.readRecordChars(inputHandle, sink)) {
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
