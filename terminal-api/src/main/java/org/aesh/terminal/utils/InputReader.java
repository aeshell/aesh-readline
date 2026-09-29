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
import java.util.function.Consumer;

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
 * <p>
 * Overflow policy: the queue is bounded and pushing never blocks, so a
 * producer (typically the single input event thread) can never deadlock
 * against a slow consumer. Chars beyond capacity are dropped newest-first
 * and counted — never silently: {@link #droppedCount()} reports the
 * total, {@link #clearDroppedCount()} resets it. Each {@code push} call
 * is atomic per code point (a supplementary pair is stored whole or not
 * at all), and calls are mutually exclusive, so concurrent producers
 * cannot interleave half pairs either. Pushing after {@link #close()}
 * is ignored.
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
    private long dropped;
    /**
     * Retained lookahead: a char consumed past a high surrogate that did
     * not complete a pair. Served before the queue by every read method,
     * so at most one is ever held.
     */
    private Character pushback;

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
        connection.setStdinHandler(new Consumer<int[]>() {
            @Override
            public void accept(int[] cps) {
                for (int cp : cps) {
                    reader.push(cp);
                }
            }
        });
        return reader;
    }

    /**
     * Push a single char into the reader. Never blocks: when the queue
     * is full the char is dropped and counted (see the class policy).
     *
     * @param ch the character to push
     */
    public synchronized void push(char ch) {
        if (closed) {
            return;
        }
        if (!queue.offer(ch)) {
            dropped++;
        }
    }

    /**
     * Push a code point into the reader. Supplementary code points
     * (above U+FFFF) are split into surrogate pairs. The pair is
     * stored whole or not at all: when fewer than two slots remain
     * the code point is dropped and counted, never stranded half
     * queued. Never blocks.
     *
     * @param codePoint the Unicode code point to push
     */
    public synchronized void push(int codePoint) {
        if (closed) {
            return;
        }
        if (Character.isBmpCodePoint(codePoint)) {
            push((char) codePoint);
        } else {
            if (queue.remainingCapacity() < 2) {
                dropped += 2;
                return;
            }
            char[] chars = Character.toChars(codePoint);
            for (char c : chars) {
                push(c);
            }
        }
    }

    /**
     * Push a character sequence into the reader, code point by code
     * point so supplementary pairs keep the per-call atomicity above.
     * Never blocks; excess is dropped and counted.
     *
     * @param csq the character sequence to push
     */
    public synchronized void push(CharSequence csq) {
        for (int i = 0; i < csq.length();) {
            int cp = Character.codePointAt(csq, i);
            push(cp);
            i += Character.charCount(cp);
        }
    }

    /**
     * Report how many pushed chars were dropped for lack of capacity.
     * A supplementary code point dropped whole counts its two chars.
     * Monotonic until {@link #clearDroppedCount()}.
     *
     * @return the total dropped char count
     */
    public synchronized long droppedCount() {
        return dropped;
    }

    /**
     * Reset the dropped-char count, e.g. for per-paste accounting.
     */
    public synchronized void clearDroppedCount() {
        dropped = 0;
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
        Character stashed = takePushback();
        if (stashed != null) {
            if (closed) {
                return EOF;
            }
            return stashed;
        }
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
     * <p>
     * Both polls share one deadline: the lookahead poll receives only the
     * remaining budget, so a dribbled-in high surrogate cannot cost nearly
     * twice the timeout. A lookahead char that does not complete a pair is
     * retained for the next read instead of discarded. A close racing the
     * lookahead resolves like a timeout: the already-consumed high
     * surrogate is returned as-is.
     *
     * @param timeout the maximum time to wait
     * @param unit the time unit
     * @return the code point, or {@link OptionalInt#empty()} on timeout
     * @throws IOException if the reader has been closed
     */
    public OptionalInt readCodePoint(long timeout, TimeUnit unit) throws IOException {
        ensureOpen();
        long deadline = System.nanoTime() + saturatedToNanos(timeout, unit);
        try {
            Character ch = takePushback();
            if (ch == null) {
                ch = queue.poll(timeout, unit);
            }
            if (ch == null) {
                return OptionalInt.empty();
            }
            if (closed) {
                return OptionalInt.empty();
            }
            if (Character.isHighSurrogate(ch)) {
                long remaining = deadline - System.nanoTime();
                Character low = queue.poll(Math.max(remaining, 0), TimeUnit.NANOSECONDS);
                if (low != null && Character.isLowSurrogate(low)) {
                    return OptionalInt.of(Character.toCodePoint(ch, low));
                }
                if (low != null) {
                    // Not a pair: keep the lookahead for the next read.
                    pushback = low;
                }
                // Unpaired high surrogate (or no follow-up in budget) — return as-is
                return OptionalInt.of((int) ch);
            }
            return OptionalInt.of((int) ch);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading", e);
        }
    }

    /**
     * Convert a timeout to nanos, saturating instead of overflowing for
     * absurd values so deadline arithmetic stays a simple subtraction.
     *
     * @param timeout the timeout value
     * @param unit the timeout unit
     * @return the timeout in nanos, saturated at {@link Long#MAX_VALUE}
     */
    private static long saturatedToNanos(long timeout, TimeUnit unit) {
        long nanos = unit.toNanos(timeout);
        if (nanos < 0 && timeout > 0) {
            return Long.MAX_VALUE;
        }
        return nanos;
    }

    /**
     * Take the retained lookahead char, if any.
     *
     * @return the stashed char, or null when empty
     */
    private Character takePushback() {
        Character ch = pushback;
        pushback = null;
        return ch;
    }

    @Override
    /** Method. */
    public boolean ready() throws IOException {
        ensureOpen();
        return pushback != null || !queue.isEmpty();
    }

    /**
     * Reads chars into a portion of a char array. Blocks on the first
     * char, then drains remaining available chars without blocking. A
     * retained lookahead is served first.
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
                Character event;
                if (count == 0) {
                    event = takePushback();
                    if (event == null) {
                        event = queue.take();
                    }
                } else {
                    event = queue.poll();
                }
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
        pushback = null;
        // Offer a dummy char to unblock any thread waiting on take()
        queue.offer('\0');
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("InputReader is closed");
        }
    }
}
