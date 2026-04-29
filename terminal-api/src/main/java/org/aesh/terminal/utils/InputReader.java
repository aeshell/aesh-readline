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
package org.aesh.terminal.utils;

import java.io.IOException;
import java.io.Reader;
import java.util.OptionalInt;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.aesh.terminal.Connection;

/**
 * A {@link Reader} adapter that bridges a terminal's push-style input
 * to Java's pull-style {@link Reader}.
 *
 * <p>
 * Incoming code points are converted to {@code char}s via the {@code push} methods,
 * with supplementary code points (above U+FFFF) split into surrogate pairs.
 *
 * <p>
 * Usage example:
 *
 * <pre>{@code
 * InputReader reader = InputReader.asReader(connection);
 * // now use reader.read(...) or reader.readCodePoint(...)
 * }</pre>
 *
 * @author <a href="mailto:spederse@redhat.com">Ståle W. Pedersen</a>
 */
public class InputReader extends Reader {

    private static final int DEFAULT_CAPACITY = 4096;

    /** Field. */
    public static final int EOF = -1;
    /** Field. */
    public static final int TIMEOUT = -2;

    private final LinkedBlockingQueue<Character> queue;
    private volatile boolean closed = false;

    /**
     * Create an InputReader with the default queue capacity (4096).
     */
    public InputReader() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Create an InputReader with a custom queue capacity.
     *
     * @param capacity the maximum number of chars to buffer
     */
    public InputReader(int capacity) {
        this.queue = new LinkedBlockingQueue<>(capacity);
    }

    /**
     * Create an InputReader and wire it to the given connection's stdin handler.
     *
     * @param connection the connection to read from
     * @return a new InputReader already receiving input from the connection
     */
    public static InputReader asReader(Connection connection) {
        return asReader(connection, DEFAULT_CAPACITY);
    }

    /**
     * Create an InputReader with a custom queue capacity and wire it to the
     * given connection's stdin handler.
     *
     * @param connection the connection to read from
     * @param capacity the maximum number of chars to buffer
     * @return a new InputReader already receiving input from the connection
     */
    public static InputReader asReader(Connection connection, int capacity) {
        InputReader reader = new InputReader(capacity);
        connection.setStdinHandler(cps -> {
            for (int cp : cps) {
                reader.push(cp);
            }
        });
        return reader;
    }

    /**
     * Push a single char into the reader.
     *
     * @param ch the character to push
     */
    public void push(char ch) {
        if (!closed) {
            queue.offer(ch);
        }
    }

    /**
     * Push a code point into the reader. Supplementary code points
     * (above U+FFFF) are split into surrogate pairs.
     *
     * @param codePoint the Unicode code point to push
     */
    public void push(int codePoint) {
        if (closed) {
            return;
        }
        if (Character.isBmpCodePoint(codePoint)) {
            push((char) codePoint);
        } else {
            char[] chars = Character.toChars(codePoint);
            for (char c : chars) {
                push(c);
            }
        }
    }

    /**
     * Push a character sequence into the reader.
     *
     * @param csq the character sequence to push
     */
    public void push(CharSequence csq) {
        for (int i = 0; i < csq.length(); i++) {
            push(csq.charAt(i));
        }
    }

    /**
     * Read a single character with a timeout.
     *
     * @param timeout the timeout in milliseconds
     * @return the character as an int, or {@code -2} if no input within timeout
     * @throws IOException if the reader has been closed
     */
    public int read(int timeout) throws IOException {
        ensureOpen();
        try {
            Character event = queue.poll(timeout, TimeUnit.MILLISECONDS);
            if (event == null) {
                return TIMEOUT;
            }
            if (closed) {
                return EOF;
            }
            return event;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return EOF;
        }
    }

    /**
     * Read a single code point with a timeout. If the first char is a high
     * surrogate, the next char is also consumed to form the complete code point.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return the code point, or {@link OptionalInt#empty()} on timeout
     * @throws IOException if the reader has been closed
     */
    public OptionalInt readCodePoint(long timeout, TimeUnit unit) throws IOException {
        ensureOpen();
        try {
            Character ch = queue.poll(timeout, unit);
            if (ch == null) {
                return OptionalInt.empty();
            }
            if (closed) {
                return OptionalInt.empty();
            }
            if (Character.isHighSurrogate(ch)) {
                Character low = queue.poll(timeout, unit);
                if (low != null && Character.isLowSurrogate(low)) {
                    return OptionalInt.of(Character.toCodePoint(ch, low));
                }
                // Unpaired high surrogate — return as-is
                return OptionalInt.of((int) ch);
            }
            return OptionalInt.of((int) ch);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading", e);
        }
    }

    @Override
    /** Method. */
    public boolean ready() throws IOException {
        ensureOpen();
        return !queue.isEmpty();
    }

    /**
     * Reads chars into a portion of a char array. Blocks on the first
     * char, then drains remaining available chars without blocking.
     */
    @Override
    /** Method. */
    public int read(char[] cbuf, int off, int len) throws IOException {
        ensureOpen();
        if (len == 0) {
            return 0;
        }

        try {
            int count = 0;
            while (count < len) {
                Character event = (count == 0) ? queue.take() : queue.poll();
                if (event == null) {
                    break;
                }
                if (closed) {
                    return count > 0 ? count : -1;
                }
                cbuf[off + count] = event;
                count++;
            }
            return count > 0 ? count : -1;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading", e);
        }
    }

    @Override
    /** Method. */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        queue.clear();
        // Offer a dummy char to unblock any thread waiting on take()
        queue.offer('\0');
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("InputReader is closed");
        }
    }
}
