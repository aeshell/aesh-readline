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
package org.aesh.readline.action;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aesh.readline.editing.EditMode;
import org.aesh.terminal.Key;
import org.aesh.terminal.KeyAction;
import org.aesh.terminal.Terminal;
import org.aesh.terminal.parser.VtHandler;
import org.aesh.terminal.parser.VtParser;
import org.aesh.terminal.utils.LoggerUtil;

/**
 * Decodes input key sequences and maps them to corresponding actions.
 * <p>
 * Uses a pre-allocated buffer with offset tracking to avoid per-keystroke
 * array allocations. The buffer grows when needed but is compacted only
 * when the read offset exceeds half the capacity.
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class ActionDecoder {

    private static final Logger LOGGER = LoggerUtil.getLogger(ActionDecoder.class.getName());
    private static final int INITIAL_CAPACITY = 128;

    /** Default timeout (ms) for escape sequence disambiguation. */
    private static final long DEFAULT_ESCAPE_TIMEOUT = 50;

    private KeyAction[] mappings;
    private final KeyMappingTrie trie;
    private final Queue<KeyAction> actions = new LinkedList<>();

    // Pre-allocated buffer with offset tracking — avoids copy on every consume
    private int[] buffer = new int[INITIAL_CAPACITY];
    private int bufferOffset; // read position
    private int bufferLength; // write position (number of valid elements)

    // Reusable VtParser for unknown sequence measurement
    private final VtParser vtParser;
    private final SequenceMeasurer sequenceMeasurer;

    /**
     * Optional function to peek at terminal input without consuming it.
     * When set, enables escape sequence timeout disambiguation:
     * if ESC arrives alone and peek() times out, it's treated as bare ESC.
     * The function takes a timeout in milliseconds and returns the peeked
     * byte (0-255), -1 for EOF, or -2 for timeout.
     */
    private InputPeeker inputPeeker;

    /** The escape sequence disambiguation timeout in milliseconds. */
    private long escapeTimeout = DEFAULT_ESCAPE_TIMEOUT;

    /**
     * Functional interface for peeking at terminal input without consuming it.
     */
    @FunctionalInterface
    public interface InputPeeker {
        /**
         * Peek at the next byte without consuming it.
         *
         * @param timeoutMs timeout in milliseconds
         * @return the byte peeked (0-255), -1 for EOF, or -2 for timeout
         * @throws IOException if an I/O error occurs
         */
        int peek(long timeoutMs) throws IOException;
    }

    /**
     * Creates a decoder with key mappings from the specified edit mode.
     *
     * @param editMode the edit mode providing key mappings
     */
    public ActionDecoder(EditMode editMode) {
        this.mappings = editMode.keys();
        this.trie = new KeyMappingTrie();
        this.trie.build(this.mappings);
        this.sequenceMeasurer = new SequenceMeasurer();
        this.vtParser = new VtParser(this.sequenceMeasurer);
    }

    /**
     * Creates a decoder with default key mappings.
     */
    public ActionDecoder() {
        this.mappings = Key.values();
        this.trie = new KeyMappingTrie();
        this.trie.build(this.mappings);
        this.sequenceMeasurer = new SequenceMeasurer();
        this.vtParser = new VtParser(this.sequenceMeasurer);
    }

    /**
     * Sets the input peeker for escape sequence timeout disambiguation.
     * <p>
     * When set, the decoder will use {@code peek(escapeTimeout)} to check
     * if more input is coming after an ambiguous prefix (e.g., bare ESC).
     * If the peek times out, the prefix is treated as a complete action.
     *
     * @param peeker the input peeker, or null to disable timeout disambiguation
     */
    public void setInputPeeker(InputPeeker peeker) {
        this.inputPeeker = peeker;
    }

    /**
     * Sets the escape sequence disambiguation timeout.
     *
     * @param timeoutMs timeout in milliseconds (default: 50ms)
     */
    public void setEscapeTimeout(long timeoutMs) {
        this.escapeTimeout = timeoutMs;
    }

    /**
     * Adds input code points to the decoder buffer.
     *
     * @param input the array of code points to add
     */
    public void add(int[] input) {
        ensureCapacity(input.length);
        System.arraycopy(input, 0, buffer, bufferLength, input.length);
        bufferLength += input.length;
    }

    /**
     * Adds a single input code point to the decoder buffer.
     *
     * @param input the code point to add
     */
    public void add(int input) {
        ensureCapacity(1);
        buffer[bufferLength++] = input;
    }

    /**
     * Returns the next action without removing it from the queue.
     *
     * @return the next key action, or null if none available
     */
    public KeyAction peek() {
        if (actions.isEmpty()) {
            return parse();
        } else {
            return actions.peek();
        }
    }

    /**
     * Checks if there is another action available.
     *
     * @return true if another action is available
     */
    public boolean hasNext() {
        return peek() != null;
    }

    /**
     * Returns and removes the next action from the queue.
     *
     * @return the next key action
     */
    public KeyAction next() {
        if (actions.isEmpty()) {
            KeyAction next = parse();
            if (next != null) {
                actions.add(next);
                bufferOffset += next.length();
                // Compact when the read offset exceeds half the capacity
                if (bufferOffset > buffer.length / 2) {
                    compact();
                }
            }
        }
        return actions.remove();
    }

    /**
     * Updates the key mappings from the specified edit mode.
     *
     * @param editMode the edit mode providing new key mappings
     */
    public void setMappings(EditMode editMode) {
        mappings = editMode.keys();
        trie.build(mappings);
    }

    private KeyAction parse() {
        int available = bufferLength - bufferOffset;
        if (available == 0) {
            return null;
        }

        // Fast path: single code point
        if (available == 1) {
            int code = buffer[bufferOffset];
            KeyMappingTrie.MatchResult result = trie.matchSingleByte(code);
            if (result.action != null) {
                if (result.hasPrefix && inputPeeker != null) {
                    // Ambiguous: this byte is both a complete binding (e.g., ESC)
                    // AND a prefix of longer sequences (e.g., ESC [ A for UP).
                    // Use peek to check if more input is coming.
                    try {
                        int peeked = inputPeeker.peek(escapeTimeout);
                        if (peeked != Terminal.READ_EXPIRED && peeked >= 0) {
                            // More input is coming — wait for the longer sequence
                            return null;
                        }
                        // Timeout or EOF — return the short binding
                    } catch (IOException e) {
                        LOGGER.log(Level.FINE, "peek() failed during escape disambiguation", e);
                    }
                }
                return result.action;
            }
            if (!result.hasPrefix) {
                return new DefaultKeyAction(code);
            }
            // Has prefix but no action — wait for more input
            return null;
        }

        // Full trie lookup for multi-byte sequences
        KeyMappingTrie.MatchResult result = trie.match(buffer, bufferOffset, available);

        if (result.action != null) {
            return result.action;
        }

        if (result.hasPrefix) {
            // Buffer is prefix of a longer sequence — wait for more input
            return null;
        }

        // No match and not a prefix — use VtParser to classify the sequence.
        // This prevents unknown CSI/OSC/DCS sequences from leaking byte-by-byte.
        int firstByte = buffer[bufferOffset];
        if (firstByte == 27 && available > 1) { // starts with ESC
            int seqLen = measureEscapeSequence(buffer, bufferOffset, available);
            if (seqLen > 1) {
                // Complete unrecognized sequence — consume it as one action
                return new SequenceKeyAction(buffer, bufferOffset, seqLen);
            }
            if (seqLen == -1) {
                // Incomplete sequence — wait for more input
                return null;
            }
        }

        // Not an escape sequence — return single character
        return new DefaultKeyAction(firstByte);
    }

    /**
     * Measures the length of a complete escape sequence using the reusable VtParser.
     */
    private int measureEscapeSequence(int[] buf, int offset, int length) {
        sequenceMeasurer.reset();
        vtParser.reset();

        for (int i = 0; i < length; i++) {
            vtParser.advance(buf[offset + i]);
            if (sequenceMeasurer.complete) {
                return i + 1;
            }
        }

        // Sequence is not complete — need more input
        return -1;
    }

    /**
     * Ensures the buffer has room for additionalCount more elements.
     */
    private void ensureCapacity(int additionalCount) {
        int available = bufferLength - bufferOffset;
        int needed = available + additionalCount;

        if (needed > buffer.length - bufferOffset) {
            if (needed <= buffer.length) {
                // Compact: shift data to front
                compact();
            } else {
                // Grow: allocate bigger buffer
                int newCapacity = Math.max(buffer.length * 2, needed);
                int[] newBuffer = new int[newCapacity];
                System.arraycopy(buffer, bufferOffset, newBuffer, 0, available);
                buffer = newBuffer;
                bufferOffset = 0;
                bufferLength = available;
            }
        }
    }

    /**
     * Compacts the buffer by shifting remaining data to the front.
     */
    private void compact() {
        int available = bufferLength - bufferOffset;
        if (bufferOffset > 0 && available > 0) {
            System.arraycopy(buffer, bufferOffset, buffer, 0, available);
        }
        bufferOffset = 0;
        bufferLength = available;
    }

    /**
     * Reusable VtHandler that tracks whether a complete sequence was found.
     */
    private static class SequenceMeasurer implements VtHandler {
        boolean complete;

        void reset() {
            complete = false;
        }

        @Override
        public void escDispatch(int finalChar, int[] intermediates, int intermediateCount) {
            complete = true;
        }

        @Override
        public void csiDispatch(int finalChar, int[] params, int paramCount,
                int[] intermediates, int intermediateCount,
                boolean hasSubParams) {
            complete = true;
        }

        @Override
        public void oscEnd(String data) {
            complete = true;
        }

        @Override
        public void hook(int finalChar, int[] params, int paramCount,
                int[] intermediates, int intermediateCount) {
            complete = true;
        }
    }

    /**
     * A key action representing a complete but unrecognized escape sequence.
     * This prevents unknown CSI/OSC/DCS sequences from leaking byte-by-byte
     * into the input stream.
     */
    private static class SequenceKeyAction implements KeyAction {

        private final int[] codePoints;

        SequenceKeyAction(int[] buffer, int offset, int length) {
            this.codePoints = new int[length];
            System.arraycopy(buffer, offset, codePoints, 0, length);
        }

        @Override
        public int getCodePointAt(int index) throws IndexOutOfBoundsException {
            return codePoints[index];
        }

        @Override
        public int length() {
            return codePoints.length;
        }

        @Override
        public String name() {
            return "sequence: " + Arrays.toString(codePoints);
        }
    }

    private static class DefaultKeyAction implements KeyAction {

        private final int code;

        DefaultKeyAction(int i) {
            code = i;
        }

        @Override
        public int getCodePointAt(int index) throws IndexOutOfBoundsException {
            if (index != 0)
                throw new IndexOutOfBoundsException("Index greater than 0");
            return code;
        }

        @Override
        public int length() {
            return 1;
        }

        @Override
        public String name() {
            return "key: " + code;
        }
    }
}
