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

import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import org.aesh.terminal.detect.TerminalTheme;
import org.aesh.terminal.parser.VtHandler;
import org.aesh.terminal.parser.VtParser;
import org.aesh.terminal.tty.MouseEvent;
import org.aesh.terminal.tty.Signal;
import org.aesh.terminal.utils.ANSI;

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
    private final int quit;
    private final int susp;
    private final int eof;

    private Consumer<Signal> signalHandler;
    private volatile Consumer<int[]> inputHandler;
    private Consumer<TerminalTheme> themeChangeHandler;
    private Consumer<MouseEvent> mouseHandler;
    private Consumer<Boolean> focusHandler;

    private final Queue<int[]> inputQueue = new ConcurrentLinkedQueue<>();

    // ---- VtParser-based sequence filtering ----
    // A single VtParser instance replaces the three hand-rolled state machines
    // (DSR theme, mouse SGR, focus events). The parser classifies sequences
    // via table-driven state transitions, and the FilterHandler dispatches
    // recognized sequences to the appropriate handler.
    private final FilterHandler filterHandler = new FilterHandler();
    private final VtParser filterParser = new VtParser(filterHandler);

    /** Pre-allocated output buffer for filtered input. Reused across calls. */
    private int[] filterOutput = new int[256];
    private int filterOutputLen;

    /**
     * Tracks raw sequence bytes as they enter VtParser. When a non-filtered
     * sequence completes (e.g., arrow key ESC [ A), these bytes are re-emitted
     * to the output buffer.
     */
    private int[] sequenceBytes = new int[32];
    private int sequenceBytesLen;

    /**
     * Create a new EventDecoder with default control character values.
     * Default values: INTR=3 (Ctrl+C), EOF=4 (Ctrl+D), SUSP=26 (Ctrl+Z).
     */
    public EventDecoder() {
        intr = 3;
        quit = 28;
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
        this.quit = 28;
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
        this.quit = attributes.getControlChar(Attributes.ControlChar.VQUIT) > 0
                ? attributes.getControlChar(Attributes.ControlChar.VQUIT)
                : 28;
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
     * Get the current mouse event handler.
     *
     * @return the mouse handler, or null if not set
     */
    public Consumer<MouseEvent> getMouseHandler() {
        return mouseHandler;
    }

    /**
     * Set the handler for mouse events.
     * <p>
     * When set, the decoder will intercept SGR mouse sequences
     * ({@code CSI < Pb ; Px ; Py M/m}) from the input stream and
     * invoke this handler instead of passing them through as input.
     *
     * @param mouseHandler the handler, or null to disable interception
     */
    public void setMouseHandler(Consumer<MouseEvent> mouseHandler) {
        this.mouseHandler = mouseHandler;
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
    }

    /**
     * Get the current focus event handler.
     *
     * @return the focus handler, or null if not set
     */
    public Consumer<Boolean> getFocusHandler() {
        return focusHandler;
    }

    /**
     * Set the handler for terminal focus events.
     * <p>
     * When set, the decoder will intercept focus in ({@code ESC [ I}) and
     * focus out ({@code ESC [ O}) sequences from the input stream and
     * invoke this handler instead of passing them through as input.
     * The handler receives {@code true} for focus gained and {@code false}
     * for focus lost.
     *
     * @param focusHandler the handler, or null to disable interception
     */
    public void setFocusHandler(Consumer<Boolean> focusHandler) {
        this.focusHandler = focusHandler;
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
        if (signalHandler != null && input.length > 0) {
            // Single-pass signal extraction: scan once, dispatch signals and
            // input segments without re-scanning the remainder.
            int segmentStart = 0;
            for (int i = 0; i < input.length; i++) {
                int val = input[i];
                Signal event = null;
                if (val == intr) {
                    event = Signal.INT;
                } else if (val == quit) {
                    event = Signal.QUIT;
                } else if (val == susp) {
                    event = Signal.SUSP;
                } else if (val == eof) {
                    event = Signal.EOF;
                }
                if (event != null) {
                    // Send any input before this signal to the input handler
                    if (i > segmentStart && inputHandler != null) {
                        int[] segment = new int[i - segmentStart];
                        System.arraycopy(input, segmentStart, segment, 0, segment.length);
                        inputHandler.accept(segment);
                    }
                    signalHandler.accept(event);
                    segmentStart = i + 1;
                }
            }
            // Remaining input after the last signal (or all input if no signals)
            if (segmentStart >= input.length) {
                return; // all consumed by signals
            }
            if (segmentStart > 0) {
                // Create trimmed array for the remainder
                int[] remainder = new int[input.length - segmentStart];
                System.arraycopy(input, segmentStart, remainder, 0, remainder.length);
                input = remainder;
            }
        }
        // Filter sequences (DSR theme, mouse SGR, focus) using VtParser
        if (input.length > 0) {
            input = filterSequences(input);
        }
        if (input.length > 0) {
            if (inputHandler != null)
                inputHandler.accept(input);
            else
                inputQueue.add(input);
        }
    }

    // =========================================================================
    // VtParser-based sequence filtering
    // =========================================================================

    /**
     * Checks if any sequence filtering is active (at least one handler is set).
     */
    private boolean hasAnyFilter() {
        return themeChangeHandler != null || mouseHandler != null || focusHandler != null;
    }

    /**
     * Filter terminal sequences (DSR theme, mouse SGR, focus) from input
     * using a single VtParser pass.
     * <p>
     * Fast path: if the parser is in GROUND state (no partial sequence from a
     * previous chunk) and no filter handlers are set, or no ESC byte is present,
     * the input is returned unchanged with zero allocation.
     * <p>
     * When ESC is present or the parser has pending state, each code point is fed
     * through VtParser. The {@link FilterHandler} classifies dispatched sequences
     * and either consumes them (DSR, mouse, focus) or re-emits them to the output.
     *
     * @param input the input code points to filter
     * @return the filtered input with recognized sequences removed
     */
    private int[] filterSequences(int[] input) {
        // Fast path: no filter handlers set → pass through
        if (!hasAnyFilter()) {
            return input;
        }

        // Fast path: parser in GROUND state and no ESC in input → pass through
        if (filterParser.isGroundState()) {
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

        // Ensure output buffer is large enough
        // Worst case: input passes through entirely + pending sequence bytes
        int maxOutput = input.length + sequenceBytesLen;
        if (filterOutput.length < maxOutput) {
            filterOutput = new int[maxOutput];
        }
        filterOutputLen = 0;

        // Hybrid loop: bulk-copy text runs in GROUND state (fast, no per-byte
        // VtParser overhead), only feed ESC sequences through VtParser.
        int i = 0;
        while (i < input.length) {
            if (filterParser.isGroundState()) {
                // In GROUND state — scan ahead for ESC to find text run length
                int textStart = i;
                while (i < input.length && input[i] != 27) {
                    i++;
                }
                // Bulk-copy text run [textStart, i) directly to output
                int textLen = i - textStart;
                if (textLen > 0) {
                    ensureFilterOutputCapacity(filterOutputLen + textLen);
                    System.arraycopy(input, textStart, filterOutput, filterOutputLen, textLen);
                    filterOutputLen += textLen;
                }
                // If we hit ESC or end of input, continue (ESC will be fed through VtParser below)
                sequenceBytesLen = 0;
            } else {
                // Mid-sequence from a previous chunk — feed through VtParser
                appendSequenceByte(input[i]);
                filterParser.advance(input[i]);
                i++;
                // If parser returned to GROUND, the dispatch callback already handled it
                continue;
            }

            // If we stopped at ESC, feed the escape sequence through VtParser
            if (i < input.length && input[i] == 27) {
                sequenceBytesLen = 0;
                // Feed bytes through VtParser until we return to GROUND or exhaust input
                while (i < input.length) {
                    appendSequenceByte(input[i]);
                    filterParser.advance(input[i]);
                    i++;
                    if (filterParser.isGroundState()) {
                        // Sequence complete — dispatch callback already fired
                        sequenceBytesLen = 0;
                        break;
                    }
                }
            }
        }

        // If nothing was filtered and no pending sequence, return original
        if (filterOutputLen == input.length && filterParser.isGroundState()) {
            return input;
        }
        if (filterOutputLen == 0) {
            return new int[0];
        }
        return Arrays.copyOf(filterOutput, filterOutputLen);
    }

    private void ensureFilterOutputCapacity(int needed) {
        if (filterOutput.length < needed) {
            filterOutput = Arrays.copyOf(filterOutput, Math.max(needed, filterOutput.length * 2));
        }
    }

    private void appendSequenceByte(int cp) {
        if (sequenceBytesLen >= sequenceBytes.length) {
            sequenceBytes = Arrays.copyOf(sequenceBytes, sequenceBytes.length * 2);
        }
        sequenceBytes[sequenceBytesLen++] = cp;
    }

    /**
     * Flush accumulated sequence bytes to the output buffer (for non-filtered sequences).
     */
    private void flushSequenceBytes() {
        if (sequenceBytesLen > 0) {
            ensureFilterOutputCapacity(filterOutputLen + sequenceBytesLen);
            System.arraycopy(sequenceBytes, 0, filterOutput, filterOutputLen, sequenceBytesLen);
            filterOutputLen += sequenceBytesLen;
            sequenceBytesLen = 0;
        }
    }

    /**
     * VtHandler implementation that classifies parsed sequences and either
     * consumes them (DSR theme, mouse SGR, focus) or re-emits them to the
     * output buffer.
     */
    private class FilterHandler implements VtHandler {

        @Override
        public void print(int codePoint) {
            // Only reached for code points > 255 (Unicode) in GROUND state.
            // ASCII printable bytes are bulk-copied in the hybrid loop and
            // never reach VtParser when in GROUND state.
            ensureFilterOutputCapacity(filterOutputLen + 1);
            filterOutput[filterOutputLen++] = codePoint;
        }

        @Override
        public void execute(int controlChar) {
            // C0 control — either in GROUND state or mid-CSI (VT spec allows
            // C0 controls within CSI sequences without aborting them).
            // Signals are already extracted in the pre-pass.
            ensureFilterOutputCapacity(filterOutputLen + 1);
            filterOutput[filterOutputLen++] = controlChar;
        }

        @Override
        public void csiDispatch(int finalChar, int[] params, int paramCount,
                int[] intermediates, int intermediateCount, boolean hasSubParams) {
            // Classify the CSI sequence
            if (isDsrThemeResponse(finalChar, params, paramCount,
                    intermediates, intermediateCount)) {
                handleDsrTheme(params, paramCount);
            } else if (isMouseSgrEvent(finalChar, intermediates, intermediateCount)) {
                handleMouseSgr(finalChar, params, paramCount);
            } else if (isFocusEvent(finalChar, params, paramCount,
                    intermediates, intermediateCount)) {
                handleFocus(finalChar);
            } else {
                // Not a filtered sequence — re-emit the raw sequence bytes
                flushSequenceBytes();
            }
            sequenceBytesLen = 0;
        }

        @Override
        public void escDispatch(int finalChar, int[] intermediates,
                int intermediateCount) {
            // Unfiltered ESC sequence — re-emit
            flushSequenceBytes();
            sequenceBytesLen = 0;
        }

        @Override
        public void oscEnd(String data) {
            // Unfiltered OSC — re-emit
            flushSequenceBytes();
            sequenceBytesLen = 0;
        }

        @Override
        public void hook(int finalChar, int[] params, int paramCount,
                int[] intermediates, int intermediateCount) {
            // DCS start — re-emit
            flushSequenceBytes();
            sequenceBytesLen = 0;
        }

        @Override
        public void put(int b) {
            // DCS data — re-emit
            if (filterOutputLen >= filterOutput.length) {
                filterOutput = Arrays.copyOf(filterOutput, filterOutput.length * 2);
            }
            filterOutput[filterOutputLen++] = b;
        }

        @Override
        public void unhook() {
            // DCS end — nothing to flush (sequence bytes already flushed at hook)
        }
    }

    // =========================================================================
    // Sequence classification and dispatch
    // =========================================================================

    /**
     * Checks if a CSI sequence is a theme DSR response: {@code CSI ? 997 ; Ps n}
     * <p>
     * In VtParser terms: finalChar='n', intermediates=['?'], params=[997, Ps]
     */
    private static boolean isDsrThemeResponse(int finalChar, int[] params, int paramCount,
            int[] intermediates, int intermediateCount) {
        return finalChar == 'n'
                && intermediateCount == 1 && intermediates[0] == '?'
                && paramCount == 2 && params[0] == 997;
    }

    /**
     * Checks if a CSI sequence is a mouse SGR event: {@code CSI < Pb ; Px ; Py M/m}
     * <p>
     * In VtParser terms: finalChar='M'|'m', intermediates=['<']
     */
    private static boolean isMouseSgrEvent(int finalChar,
            int[] intermediates, int intermediateCount) {
        return (finalChar == 'M' || finalChar == 'm')
                && intermediateCount == 1 && intermediates[0] == '<';
    }

    /**
     * Checks if a CSI sequence is a focus event: {@code CSI I} or {@code CSI O}
     * <p>
     * In VtParser terms: finalChar='I'|'O', no intermediates, no real params
     * (VtParser produces paramCount=1 with params[0]=-1 for empty CSI)
     */
    private static boolean isFocusEvent(int finalChar, int[] params, int paramCount,
            int[] intermediates, int intermediateCount) {
        return (finalChar == 'I' || finalChar == 'O')
                && intermediateCount == 0
                && paramCount <= 1
                && (paramCount == 0 || params[0] == -1);
    }

    private void handleDsrTheme(int[] params, int paramCount) {
        if (paramCount >= 2 && themeChangeHandler != null) {
            int value = params[1];
            TerminalTheme theme = null;
            if (value == ANSI.THEME_DSR_DARK) {
                theme = TerminalTheme.DARK;
            } else if (value == ANSI.THEME_DSR_LIGHT) {
                theme = TerminalTheme.LIGHT;
            }
            if (theme != null) {
                themeChangeHandler.accept(theme);
            }
        }
    }

    private void handleMouseSgr(int finalChar, int[] params, int paramCount) {
        if (paramCount >= 3 && mouseHandler != null) {
            MouseEvent event = MouseEvent.parseSgr(finalChar,
                    new int[] { params[0], params[1], params[2] }, 3);
            if (event != null) {
                mouseHandler.accept(event);
            }
        }
    }

    private void handleFocus(int finalChar) {
        if (focusHandler != null) {
            focusHandler.accept(finalChar == 'I');
        }
    }
}
