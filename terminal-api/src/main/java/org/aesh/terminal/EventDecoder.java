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
package org.aesh.terminal;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.function.Consumer;

import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.utils.ANSI;
import org.aesh.terminal.utils.TerminalTheme;

/**
 * Decodes terminal input events, separating signals (INT, SUSP, EOF) and
 * unsolicited terminal responses from regular input.
 * <p>
 * When a {@link #setThemeChangeHandler(Consumer) themeChangeHandler} is
 * registered, this decoder also intercepts {@code CSI ? 997 ; Ps n} theme
 * change DSR notifications and routes them to the handler instead of passing
 * them through as input. This prevents theme change notifications from
 * corrupting the readline buffer.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class EventDecoder implements Consumer<int[]> {

    private final int intr;
    private final int susp;
    private final int eof;

    private Consumer<Signal> signalHandler;
    private Consumer<int[]> inputHandler;
    private Consumer<TerminalTheme> themeChangeHandler;

    private Queue<int[]> inputQueue = new ArrayDeque<>(10);

    // ---- Theme DSR state machine ----
    // The prefix we're matching: ESC [ ? 9 9 7 ;
    private static final int[] DSR_PREFIX = { 27, 91, 63, 57, 57, 55, 59 };

    // State machine states
    private static final int DSR_IDLE = 0;
    // States 1..7 = matching positions 0..6 of DSR_PREFIX
    private static final int DSR_COLLECTING_PARAM = 8; // collecting digit(s) after the semicolon

    private int dsrState = DSR_IDLE;
    private int dsrParamValue = 0;
    // Buffer for code points consumed by the state machine (may need to be flushed on mismatch)
    private int[] dsrPending = new int[16];
    private int dsrPendingLen = 0;

    /**
     * Create a new EventDecoder with default control character values.
     * Default values: INTR=3 (Ctrl+C), EOF=4 (Ctrl+D), SUSP=26 (Ctrl+Z).
     */
    public EventDecoder() {
        intr = 3;
        eof = 4;
        susp = 26;
    }

    /**
     * Create a new EventDecoder with custom control character values.
     *
     * @param intr the interrupt character code (typically Ctrl+C = 3)
     * @param eof the end-of-file character code (typically Ctrl+D = 4)
     * @param susp the suspend character code (typically Ctrl+Z = 26)
     */
    public EventDecoder(int intr, int eof, int susp) {
        this.intr = intr;
        this.eof = eof;
        this.susp = susp;
    }

    /**
     * Create a new EventDecoder using control characters from terminal attributes.
     * Falls back to default values if the attributes do not specify valid control characters.
     *
     * @param attributes the terminal attributes to extract control characters from
     */
    public EventDecoder(Attributes attributes) {
        this.intr = attributes.getControlChar(Attributes.ControlChar.VINTR) > 0
                ? attributes.getControlChar(Attributes.ControlChar.VINTR)
                : 3;
        this.eof = attributes.getControlChar(Attributes.ControlChar.VEOF) > 0
                ? attributes.getControlChar(Attributes.ControlChar.VEOF)
                : 4;
        this.susp = attributes.getControlChar(Attributes.ControlChar.VSUSP) > 0
                ? attributes.getControlChar(Attributes.ControlChar.VSUSP)
                : 26;
    }

    /**
     * Get the current signal handler.
     *
     * @return the signal handler, or null if not set
     */
    public Consumer<Signal> getSignalHandler() {
        return signalHandler;
    }

    /**
     * Set the signal handler that will be called when signals are detected in input.
     *
     * @param signalHandler the handler to process signals
     */
    public void setSignalHandler(Consumer<Signal> signalHandler) {
        this.signalHandler = signalHandler;
    }

    /**
     * Get the current input handler.
     *
     * @return the input handler, or null if not set
     */
    public Consumer<int[]> getInputHandler() {
        return inputHandler;
    }

    /**
     * Set the input handler that will receive non-signal input.
     * Any queued input will be delivered to the handler immediately.
     *
     * @param inputHandler the handler to process input as code point arrays
     */
    public void setInputHandler(Consumer<int[]> inputHandler) {
        this.inputHandler = inputHandler;
        checkQueue();
    }

    /**
     * Get the current theme change handler.
     *
     * @return the theme change handler, or null if not set
     */
    public Consumer<TerminalTheme> getThemeChangeHandler() {
        return themeChangeHandler;
    }

    /**
     * Set the handler for theme change DSR notifications.
     * <p>
     * When set, the decoder will intercept {@code CSI ? 997 ; Ps n} sequences
     * from the input stream and invoke this handler instead of passing them
     * through as regular input.
     *
     * @param themeChangeHandler the handler, or null to disable interception
     */
    public void setThemeChangeHandler(Consumer<TerminalTheme> themeChangeHandler) {
        this.themeChangeHandler = themeChangeHandler;
        if (themeChangeHandler == null) {
            // Reset state machine when handler is removed
            dsrState = DSR_IDLE;
            dsrPendingLen = 0;
            dsrParamValue = 0;
        }
    }

    private void checkQueue() {
        while (inputHandler != null && !inputQueue.isEmpty())
            inputHandler.accept(inputQueue.poll());
    }

    /**
     * Process input, separating signals and theme DSR notifications from regular input.
     * <p>
     * Signal characters are extracted and sent to the signal handler.
     * When a theme change handler is registered, {@code CSI ? 997 ; Ps n}
     * sequences are intercepted and routed to the theme change handler.
     * Remaining input is sent to the input handler.
     *
     * @param input the input code points to process
     */
    @Override
    public void accept(int[] input) {
        if (signalHandler != null) {
            int index = 0;
            while (index < input.length) {
                int val = input[index];
                Signal event = null;
                if (val == intr) {
                    event = Signal.INT;
                } else if (val == susp) {
                    event = Signal.SUSP;
                } else if (val == eof) {
                    event = Signal.EOF;
                }
                if (event != null) {
                    if (signalHandler != null) {
                        if (inputHandler != null) {
                            int[] a = new int[index];
                            if (index > 0) {
                                System.arraycopy(input, 0, a, 0, index);
                                inputHandler.accept(a);
                            }
                        }
                        signalHandler.accept(event);
                        int[] a = new int[input.length - index - 1];
                        System.arraycopy(input, index + 1, a, 0, a.length);
                        input = a;
                        index = 0;
                        continue;
                    }
                }
                index++;
            }
        }
        // Filter theme DSR sequences if a handler is registered
        if (input.length > 0 && themeChangeHandler != null) {
            input = filterThemeDsr(input);
        }
        if (input.length > 0) {
            if (inputHandler != null)
                inputHandler.accept(input);
            else
                inputQueue.add(input);
        }
    }

    /**
     * Filter theme DSR sequences ({@code CSI ? 997 ; Ps n}) from the input.
     * <p>
     * Uses a state machine to recognize the sequence, handling both complete
     * sequences within a single chunk and sequences split across multiple chunks.
     * Recognized sequences are dispatched to the {@link #themeChangeHandler}.
     * Non-matching bytes are passed through unchanged.
     *
     * @param input the input code points to filter
     * @return the filtered input with DSR sequences removed
     */
    private int[] filterThemeDsr(int[] input) {
        // Fast path: if not mid-sequence and input contains no ESC, pass through unchanged.
        // This avoids allocation for the vast majority of input (normal keystrokes).
        if (dsrState == DSR_IDLE) {
            boolean hasEsc = false;
            for (int c : input) {
                if (c == 27) {
                    hasEsc = true;
                    break;
                }
            }
            if (!hasEsc) {
                return input;
            }
        }

        // Output buffer for non-DSR code points
        int[] output = new int[input.length + dsrPendingLen];
        int outLen = 0;

        for (int i = 0; i < input.length; i++) {
            int cp = input[i];

            if (dsrState == DSR_IDLE) {
                // Looking for ESC to start a potential DSR sequence
                if (cp == DSR_PREFIX[0]) { // ESC
                    dsrState = 1;
                    dsrPendingLen = 0;
                    dsrParamValue = 0;
                    appendDsrPending(cp);
                } else {
                    output[outLen++] = cp;
                }
            } else if (dsrState >= 1 && dsrState < DSR_PREFIX.length) {
                // Matching the fixed prefix: ESC [ ? 9 9 7 ;
                if (cp == DSR_PREFIX[dsrState]) {
                    dsrState++;
                    appendDsrPending(cp);
                    // If we've matched the entire prefix, move to parameter collection
                    if (dsrState == DSR_PREFIX.length) {
                        dsrState = DSR_COLLECTING_PARAM;
                    }
                } else {
                    // Mismatch — flush buffered prefix as normal input
                    outLen = flushDsrPending(output, outLen);
                    // Re-process the current code point from IDLE state
                    i--;
                }
            } else if (dsrState == DSR_COLLECTING_PARAM) {
                if (cp >= '0' && cp <= '9') {
                    // Accumulate digit
                    dsrParamValue = dsrParamValue * 10 + (cp - '0');
                    appendDsrPending(cp);
                } else if (cp == 'n' && dsrPendingLen > DSR_PREFIX.length) {
                    // Terminating 'n' with at least one digit collected — complete DSR
                    TerminalTheme theme = null;
                    if (dsrParamValue == ANSI.THEME_DSR_DARK) {
                        theme = TerminalTheme.DARK;
                    } else if (dsrParamValue == ANSI.THEME_DSR_LIGHT) {
                        theme = TerminalTheme.LIGHT;
                    }
                    // Reset state machine
                    dsrState = DSR_IDLE;
                    dsrPendingLen = 0;
                    dsrParamValue = 0;
                    // Dispatch to handler (even for unknown mode values, we consume the sequence)
                    if (theme != null && themeChangeHandler != null) {
                        themeChangeHandler.accept(theme);
                    }
                } else {
                    // Unexpected character — flush prefix + collected digits + this char
                    appendDsrPending(cp);
                    outLen = flushDsrPending(output, outLen);
                }
            }
        }

        // If we're mid-sequence at the end of the chunk, the pending buffer
        // stays for the next accept() call. Don't flush it — the sequence
        // may continue in the next chunk.

        if (outLen == 0) {
            return new int[0];
        }
        // If nothing was filtered out and no pending bytes, return original array
        if (outLen == input.length && dsrPendingLen == 0) {
            return input;
        }
        return Arrays.copyOf(output, outLen);
    }

    /**
     * Append a code point to the DSR pending buffer, growing it if needed.
     */
    private void appendDsrPending(int cp) {
        if (dsrPendingLen >= dsrPending.length) {
            dsrPending = Arrays.copyOf(dsrPending, dsrPending.length * 2);
        }
        dsrPending[dsrPendingLen++] = cp;
    }

    /**
     * Flush the DSR pending buffer into the output array and reset state.
     * <p>
     * The output array is guaranteed to have enough room because it was
     * allocated as {@code input.length + dsrPendingLen}.
     *
     * @param output the output array to write to
     * @param outLen the current write position in output
     * @return the updated write position
     */
    private int flushDsrPending(int[] output, int outLen) {
        System.arraycopy(dsrPending, 0, output, outLen, dsrPendingLen);
        outLen += dsrPendingLen;
        dsrPendingLen = 0;
        dsrState = DSR_IDLE;
        dsrParamValue = 0;
        return outLen;
    }
}
